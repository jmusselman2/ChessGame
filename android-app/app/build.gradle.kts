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

// Where the Chess server is, for a build that is not talking to the beta endpoint. The
// default is the host machine as an emulator normally sees it; an Android 16/17 emulator
// cannot reach the host that way, so a development build there is given
// http://localhost:8080 alongside `adb reverse`. See docs/DEVELOPMENT.md.
val chessServerUrl: String =
    providers
        .gradleProperty("chessServerUrl")
        .orElse(providers.environmentVariable("CHESS_SERVER_URL"))
        .getOrElse("http://10.0.2.2:8080")

android {
    namespace = "com.jmussel.chessgame"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.jmussel.chessgame"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "CHESS_SERVER_URL", "\"$chessServerUrl\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
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
