plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(24)
}

application {
    mainClass.set("com.jmussel.chessgame.server.ApplicationKt")
}

dependencies {
    implementation(project(":game-core"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)

    // PostgreSQL access: Exposed over a HikariCP pool (D030).
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
}

// The SQL migrations live in database/migrations/ at the repository root, not inside a
// module. Copying them onto the classpath at Flyway's default location means the server
// and the tests migrate from exactly the same files.
tasks.named<ProcessResources>("processResources") {
    from(rootProject.layout.projectDirectory.dir("database/migrations")) {
        include("*.sql")
        into("db/migration")
    }
}

tasks.test {
    useJUnitPlatform()

    // Integration tests need the disposable PostgreSQL from docs/DEVELOPMENT.md. They
    // skip themselves when it is not configured; see DatabaseTestSupport.
    listOf(
        "TEST_DATABASE_URL",
        "DATABASE_URL",
    ).forEach { variable ->
        System.getenv(variable)?.let { environment(variable, it) }
    }
}
