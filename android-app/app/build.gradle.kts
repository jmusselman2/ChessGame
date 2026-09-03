plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The Supabase URL is public and has a sensible default; the publishable key is
// supplied by whoever builds, through -PsupabaseAnonKey, gradle.properties, or the
// SUPABASE_ANON_KEY environment variable. It is never committed. See
// docs/DEVELOPMENT.md.
val supabaseUrl: String =
    providers
        .gradleProperty("supabaseUrl")
        .orElse(providers.environmentVariable("SUPABASE_URL"))
        .getOrElse("https://rkwymrtqayyyfahfgmbm.supabase.co")

val supabaseAnonKey: String =
    providers
        .gradleProperty("supabaseAnonKey")
        .orElse(providers.environmentVariable("SUPABASE_ANON_KEY"))
        .getOrElse("")

// Where the Chess server is. The default is the host machine as an emulator normally sees
// it; an Android 16/17 emulator cannot reach the host that way, so a development build
// there is given http://localhost:8080 alongside `adb reverse` (D034). A beta build is
// given the deployed HTTPS endpoint the same way, through configuration rather than a
// literal in source (M15.4). See docs/DEVELOPMENT.md.
val chessServerUrl: String =
    providers
        .gradleProperty("chessServerUrl")
        .orElse(providers.environmentVariable("CHESS_SERVER_URL"))
        .getOrElse("http://10.0.2.2:8080")

// A beta build is installed on someone else's phone, and an unsigned release APK cannot be
// installed at all. The signing key is the project owner's: like the Supabase publishable
// key, it is supplied at build time and never committed, through -PchessKeystoreFile and
// friends, gradle.properties, or CHESS_KEYSTORE_FILE. Leaving them out is the normal case --
// `./gradlew build` and CI assemble the release APK unsigned, exactly as before -- so the
// keystore path is what decides whether release signing is configured at all (M17.1).
val keystorePath: String =
    providers
        .gradleProperty("chessKeystoreFile")
        .orElse(providers.environmentVariable("CHESS_KEYSTORE_FILE"))
        .getOrElse("")

// Once a keystore *is* named, everything else it needs is mandatory. A half-configured
// signing setup must stop the build rather than quietly hand back an APK that cannot be
// installed -- that silent unsigned APK is the failure this whole block exists to prevent.
fun requiredSigningInput(
    property: String,
    variable: String,
): String =
    providers
        .gradleProperty(property)
        .orElse(providers.environmentVariable(variable))
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: throw GradleException(
            "-P$property (or the $variable environment variable) is required when " +
                "-PchessKeystoreFile names a release keystore. See docs/DEVELOPMENT.md.",
        )

// What the build calls itself. A tester reports a problem against a build, so a beta needs
// to be nameable, and a later beta needs a higher versionCode for Android to treat it as an
// upgrade rather than refuse it. Both default to what a development build has always had.
val chessVersionName: String =
    providers
        .gradleProperty("chessVersionName")
        .orElse(providers.environmentVariable("CHESS_VERSION_NAME"))
        .getOrElse("1.0")

val chessVersionCode: Int =
    providers
        .gradleProperty("chessVersionCode")
        .orElse(providers.environmentVariable("CHESS_VERSION_CODE"))
        .getOrElse("1")
        .toIntOrNull()
        ?.takeIf { it > 0 }
        ?: throw GradleException("chessVersionCode must be a positive integer.")

// A release-type build is the beta build, and it must not run against a cleartext address:
// its network security configuration forbids cleartext outright (D033), so such an APK
// would install, launch, and then fail every request with an error saying nothing about the
// cause -- and the emulator-loopback default above makes that the result of simply
// forgetting -PchessServerUrl.
//
// The refusal is in ChessServerConfig rather than here, because `./gradlew build` assembles
// the release APK with these very defaults; failing at configuration time would break the
// ordinary build and CI with it. ChessAppDependencies passes BuildConfig.DEBUG as the
// cleartext allowance, so a beta build refuses at startup, immediately and by name, instead
// of failing every request obscurely. See ChessServerConfig and M15.4.

android {
    namespace = "com.jmussel.chessgame"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.jmussel.chessgame"
        minSdk = 31
        targetSdk = 37
        versionCode = chessVersionCode
        versionName = chessVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "CHESS_SERVER_URL", "\"$chessServerUrl\"")
    }

    signingConfigs {
        if (keystorePath.isNotBlank()) {
            create("beta") {
                val keystore = file(keystorePath)
                if (!keystore.isFile) {
                    throw GradleException(
                        "No release keystore at $keystore. -PchessKeystoreFile must point at " +
                            "an existing keystore. See docs/DEVELOPMENT.md.",
                    )
                }
                storeFile = keystore
                storePassword = requiredSigningInput("chessKeystorePassword", "CHESS_KEYSTORE_PASSWORD")
                keyAlias = requiredSigningInput("chessKeyAlias", "CHESS_KEY_ALIAS")
                keyPassword = requiredSigningInput("chessKeyPassword", "CHESS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfigs.findByName("beta")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.all {
            // The live Supabase test skips itself unless these are set; see
            // SupabaseLiveAuthTest and docs/DEVELOPMENT.md.
            listOf("SUPABASE_URL", "SUPABASE_ANON_KEY").forEach { variable ->
                System.getenv(variable)?.let { it1 -> it.environment(variable, it1) }
            }
        }
    }
}

dependencies {
    implementation(project(":game-core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
