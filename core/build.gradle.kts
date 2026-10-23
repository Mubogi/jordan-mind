plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit5)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
