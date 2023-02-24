plugins {
    kotlin("js")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

kotlin {
    js(IR) {
        nodejs()
        useCommonJs()
        compilations.all {
            compileKotlinTask.kotlinOptions.freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
        }
    }
}

dependencies {
    api(kotlin("stdlib-js"))
    api(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.1"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.4.1"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    implementation(platform("org.jetbrains.kotlin-wrappers:kotlin-wrappers-bom:1.0.0-pre.495"))
    implementation("org.jetbrains.kotlin-wrappers:kotlin-node")

    testImplementation(kotlin("test-js"))
    testImplementation("org.araqnid.kotlin.assert-that:assert-that:0.1.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}
