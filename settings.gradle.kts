pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ChessGame"

// The server's Docker image builds only the JVM modules. `:chess-app` needs an Android
// SDK, and a JDK build image has no reason to carry one, so configuring it there would
// fail the deploy before a line of server code compiled. `-PserverOnly=true` (or
// `CHESSGAME_SERVER_ONLY=true`) leaves it out; nothing else sets either, so a normal build
// and CI still include it.
val serverOnly =
    startParameter.projectProperties["serverOnly"].toBoolean() ||
        System.getenv("CHESSGAME_SERVER_ONLY").toBoolean()

if (!serverOnly) {
    include(":chess-app")
    project(":chess-app").projectDir = file("chess-app/app")
}

include(":chess-core")

include(":server")
