plugins {
    kotlin("js")
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
    implementation(npm("@actions/core", "latest"))
    implementation(npm("@actions/cache", "latest"))
    implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.1"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    testImplementation(kotlin("test-js"))
    testImplementation("org.araqnid.kotlin.assert-that:assert-that:0.1.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}
