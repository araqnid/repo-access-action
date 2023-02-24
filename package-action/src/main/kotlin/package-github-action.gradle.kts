import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.task.NodeTask
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

plugins {
    base
    id("com.github.node-gradle.node")
}

val actionPackagingExtension = extensions.create<PackageGithubActionExtension>("actionPackaging")

val moduleNameProvider = actionPackagingExtension.moduleName.convention(providers.provider { project.name })

val packageExplodedTask = tasks.register("packageDistributableExploded") {
    group = "package"
    description = "Package action using a node_modules directory"
    dependsOn("productionExecutableCompileSync")
    val distDir = file("dist")
    val nodeModulesDir = rootProject.buildDir.resolve("js/node_modules")
    inputs.dir(nodeModulesDir)
    inputs.property("moduleName", moduleNameProvider)
    outputs.dir(distDir)

    doLast {
        copy {
            from(nodeModulesDir)
            into(distDir.resolve("node_modules"))
        }
        distDir.resolve("index.js").printWriter().use { pw ->
            if (actionPackagingExtension.sourceMap.get()) {
                pw.println("require('source-map-support').install()")
            }
            pw.println("require('${moduleNameProvider.get()}')")
        }
    }
}

val installNccTask = tasks.register<NpmTask>("installNCC") {
    val toolDir = buildDir.resolve(name)
    val nccScript = toolDir.resolve("node_modules/@vercel/ncc/dist/ncc/cli.js")

    // every run of @vercel/ncc touches the v8 cache files, so we can't simply `outputs.dir(toolDir)` here
    outputs.file(nccScript)

    workingDir.set(toolDir)
    npmCommand.set(listOf("install"))
    args.set(listOf("@vercel/ncc"))

    doFirst {
        delete(toolDir)
        toolDir.resolve("node_modules").mkdirs()
    }

    doLast {
        check(nccScript.exists()) { "npm install did not produce a @vercel/ncc executable" }
    }
}

val packageWithNccTask = tasks.register<NodeTask>("packageDistributableWithNCC") {
    group = "package"
    description = "Package action as a single file using BCC"

    dependsOn(installNccTask)
    dependsOn("productionExecutableCompileSync")

    val toolDir = buildDir.resolve(installNccTask.name)
    val distDir = file("dist")
    val jsModulesDir = moduleNameProvider.map { buildDir.resolve("js/packages/$it/kotlin") }
    val jsBuildFile = moduleNameProvider.map { buildDir.resolve("js/packages/$it/kotlin/$it.js") }

    inputs.dir(jsModulesDir)
    inputs.property("minify", actionPackagingExtension.minify)
    inputs.property("v8cache", actionPackagingExtension.v8cache)
    inputs.property("target", actionPackagingExtension.target.orElse(""))
    outputs.dir(distDir)

    doFirst {
        delete(distDir)
    }
    script.set(toolDir.resolve("node_modules/@vercel/ncc/dist/ncc/cli.js"))
    args.add("build")
    args.add(jsBuildFile.map { it.toString() })
    args.add("-o")
    args.add(distDir.toString())
    if (actionPackagingExtension.minify.get()) {
        args.add("-m")
        args.add("--license")
        args.add("LICENSE.txt")
    }
    if (actionPackagingExtension.v8cache.get()) {
        args.add("--v8-cache")
    }
    if (actionPackagingExtension.target.isPresent) {
        args.add("--target")
        args.add(actionPackagingExtension.target.get())
    }
    if (actionPackagingExtension.sourceMap.get()) {
        args.add("-s")
    }
    for (module in actionPackagingExtension.externalModules.get()) {
        args.add("-e")
        args.add(module)
    }
    doLast {
        if (actionPackagingExtension.v8cache.get()) {
            for (file in fileTree(distDir)) {
                logger.info("set permissions of $file")
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
