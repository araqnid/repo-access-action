plugins {
    kotlin("js") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    id("package-github-action")
}

repositories {
    mavenCentral()
}

kotlin {
    js(IR) {
        nodejs()
        useCommonJs()
        binaries.executable()
        compilations["main"].packageJson {
        }
        compilations.all {
            compileKotlinTask.kotlinOptions.freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
        }
    }
}

dependencies {
    api(kotlin("stdlib-js"))
    implementation(project(":github-client"))
    implementation(project(":actions-toolkit"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.3.2"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")

    testImplementation(kotlin("test-js"))
    testImplementation("org.araqnid.kotlin.assert-that:assert-that:0.1.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.0")
}

actionPackaging {
    nodeVersion.set("12.22.8")
    minify.set(false)
    v8cache.set(false)
}
