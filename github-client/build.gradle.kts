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
    }
}

dependencies {
    api(kotlin("stdlib-js"))
    api(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.7.1"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.5.0"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    implementation(platform("org.jetbrains.kotlin-wrappers:kotlin-wrappers-bom:1.0.0-pre.507"))
    implementation("org.jetbrains.kotlin-wrappers:kotlin-actions-toolkit")

    testImplementation(kotlin("test-js"))
    testImplementation("org.araqnid.kotlin.assert-that:assert-that:0.1.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}
