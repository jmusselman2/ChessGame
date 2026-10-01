// The computer opponent's move choice (D086): pure Kotlin/JVM, depending on chess-core
// alone. Only chess-app uses it; the server and any deck-* module never do.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(24)
}

dependencies {
    implementation(project(":chess-core"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
