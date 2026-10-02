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
    // The matches between levels take minutes; `match` runs them (D089).
    filter.excludeTestsMatching("*EngineMatchTest")
}

// The seeded matches between the computer's levels (D089, M21.11): ./gradlew :chess-ai:match
tasks.register<Test>("match") {
    description = "Plays the seeded matches between the computer's levels."
    group = "verification"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter.includeTestsMatching("*EngineMatchTest")
    testLogging.showStandardStreams = true
    outputs.upToDateWhen { false }
}
