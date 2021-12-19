import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

val actionModule = project.name

val extension = extensions.create<PackageGithubActionExtension>("actionPackaging")

val packageExplodedTask = tasks.register("packageDistributableExploded") {
    group = "package"
    description = "Package action using a node_modules directory"
    dependsOn("productionExecutableCompileSync")
    val distDir = file("dist")
    inputs.dir("build/js/node_modules")
    outputs.dir(distDir)

    doLast {
        copy {
            from(file("build/js/node_modules"))
            into(distDir.resolve("node_modules"))
        }
        file(distDir.resolve("index.js")).printWriter().use { pw ->
            pw.println("require('$actionModule')")
        }
    }
}

val nodeJsConfiguration = configurations.create("nodejs") {
    isTransitive = false
}

repositories {
    ivy(url = "https://nodejs.org/dist") {
        content {
            onlyForConfigurations(nodeJsConfiguration.name)
        }
        patternLayout {
            artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            ivy("v[revision]/ivy.xml")
        }
        metadataSources {
            artifact()
        }
    }
}

val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
val arch: ArchitectureInternal = DefaultNativePlatform.getCurrentArchitecture()

val nodeOs = when {
    os.isLinux -> "linux"
    os.isMacOsX -> "darwin"
    else -> error("Unhandled OS variant: ${os.name}")
}
val nodeArch = when {
    arch.isAmd64 -> "x64"
    arch.isI386 -> "x86"
    arch.isArm -> "arm64"
    else -> error("Unahandled arch variant: ${arch.name}")
}

afterEvaluate {
    dependencies {
        nodeJsConfiguration("org.nodejs:node:${extension.nodeVersion.get()}:$nodeOs-$nodeArch@tar.gz")
    }
}

val setupNodeTask = tasks.register("setupNodeJs") {
    val nodeDir = buildDir.resolve(name)

    dependsOn(nodeJsConfiguration)
    inputs.property("nodeOs", nodeOs)
    inputs.property("nodeArch", nodeArch)
    inputs.property("nodeVersion", extension.nodeVersion)
    outputs.dir(nodeDir)

    doLast {

        val downloadedNodeArchive: File = nodeJsConfiguration.resolve().single()
        logger.info("Downloaded NodeJS archive: $downloadedNodeArchive")
        delete {
            files(nodeDir)
        }
        copy {
            from(tarTree(downloadedNodeArchive))
            into(nodeDir)
        }
    }
}

fun execNode(workingDir: File, script: File, vararg params: String) {
    val nodeInstallDir =
        buildDir.resolve(setupNodeTask.name).resolve("node-v${extension.nodeVersion.get()}-$nodeOs-$nodeArch")
    val nodeExec = nodeInstallDir.resolve("bin/node")
    check(nodeExec.exists()) { "Node executable $nodeExec does not exist" }
    exec {
        this.workingDir = workingDir
        val args = mutableListOf<String>()
        args += nodeExec.toString()
        args += script.toString()
        args += params
        commandLine(args)
    }
}

fun execNpm(workingDir: File, command: String, vararg params: String) {
    val nodeInstallDir =
        buildDir.resolve(setupNodeTask.name).resolve("node-v${extension.nodeVersion.get()}-$nodeOs-$nodeArch")
    val nodeExec = nodeInstallDir.resolve("bin/node")
    val npmScript = nodeInstallDir.resolve("lib/node_modules/npm/bin/npm-cli.js")
    check(nodeExec.exists()) { "Node executable $nodeExec does not exist" }
    check(npmScript.exists()) { "NPM script $npmScript does not exist" }
    exec {
        this.workingDir = workingDir
        val args = mutableListOf<String>()
        args += nodeExec.toString()
        args += npmScript.toString()
        args += command
        args += params
        commandLine(args)
    }
}

val installNccTask = tasks.register("installNCC") {
    dependsOn(setupNodeTask)
    doNotTrackState("Running NCC updates cache files and defeats output tracking")
    val toolDir = buildDir.resolve(name)
    val nccScript = toolDir.resolve("node_modules/@vercel/ncc/dist/ncc/cli.js")

    doLast {
        if (!nccScript.exists()) {
            delete {
                files(toolDir)
            }
            toolDir.mkdirs()
            toolDir.resolve("package.json").writeText("{}")
            execNpm(toolDir, "install", "@vercel/ncc")
        }
        check(nccScript.exists()) { "npm install did not produce a @vercel/ncc executable" }
    }
}

val packageWithNccTask = tasks.register("packageDistributableWithNCC") {
    group = "package"
    description = "Package action as a single file using BCC"

    dependsOn(setupNodeTask)
    dependsOn(installNccTask)
    dependsOn("productionExecutableCompileSync")

    val toolDir = buildDir.resolve(installNccTask.name)
    val distDir = file("dist")
    val jsBuildOutput = buildDir.resolve("js")
    val jsBuildFile = jsBuildOutput.resolve("packages/$actionModule/kotlin/$actionModule.js")

    inputs.file(jsBuildFile)
    inputs.property("minify", extension.minify)
    inputs.property("v8cache", extension.v8cache)
    inputs.property("target", extension.target.orElse(""))
    inputs.property("nodeVersion", extension.nodeVersion) // could be important when generating v8 caches
    outputs.dir(distDir)

    doLast {
        delete {
            delete(distDir)
        }
        val nccScript = toolDir.resolve("node_modules/@vercel/ncc/dist/ncc/cli.js")
        val params = mutableListOf<String>(
            "build",
            jsBuildFile.toString(),
            "-o",
            distDir.toString(),
        )
        if (extension.minify.get()) {
            params += listOf("-m", "--license", "LICENSE.txt")
        }
        if (extension.v8cache.get()) {
            params += listOf("--v8-cache")
        }
        if (extension.target.isPresent) {
            params += listOf("--target", extension.target.get())
        }
        execNode(projectDir, nccScript, *params.toTypedArray())
        if (extension.v8cache.get()) {
            for (file in fileTree(distDir)) {
                Files.setPosixFilePermissions(
                    file.toPath(), setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.OTHERS_READ,
                    )
                )
            }
        }
    }
}

val packageTask = tasks.register("package") {
    group = "package"
    description = "Produce dist directory with all dependencies for GitHub Actions"

    dependsOn(
        when (val packageStyle = rootProject.properties["githubAction.package"] ?: "ncc") {
            "ncc" -> packageWithNccTask
            "exploded" -> packageExplodedTask
            else -> error("Unhandled githubAction.package value: $packageStyle")
        }
    )
}

tasks.named("assemble").configure {
    dependsOn(packageTask)
}
