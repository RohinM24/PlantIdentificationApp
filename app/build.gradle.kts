plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("kotlin-kapt")
}

val plantnetKey: String = if (project.hasProperty("PLANTNET_API_KEY"))
    project.property("PLANTNET_API_KEY") as String
else
    ""

android {
    namespace = "com.example.roleaf"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.roleaf"
        minSdk = 25
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "PLANTNET_API_KEY",
            "\"${project.findProperty("PLANTNET_API_KEY") ?: ""}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val trefleKey = project.findProperty("TREFLE_API_KEY") ?: ""
        buildConfigField("String", "TREFLE_API_KEY", "\"$trefleKey\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // Compose compiler configuration: use a published compiler version that's compatible with Kotlin 1.9.21
    composeOptions {
        // Set the kotlinCompilerExtensionVersion to a published 1.5.x compiler that supports Kotlin 1.9.21.
        // 1.5.6 is known to support Kotlin 1.9.21 (see Compose release notes).
        kotlinCompilerExtensionVersion = "1.5.6"

        // Make the Kotlin compiler version explicit so the Compose plugin can find the right artifact.
        // This should match the Kotlin version in libs.versions.toml (1.9.21).
        // If you later update the Kotlin version, update this value to match.
        kotlinCompilerVersion = "1.9.21"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM already for compile/runtime
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))

    // Ensure the BOM also controls the androidTest configuration
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.11.01"))

    // Now these can be versionless and will be resolved by the BOM:
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))

    // Compose UI (versions controlled by BOM)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3") // Material3 (no explicit version — BOM picks it)

    // Navigation / accompanist / tooling
    implementation("androidx.navigation:navigation-compose:2.6.0")
    implementation("com.google.accompanist:accompanist-pager:0.30.1")
    implementation("com.google.accompanist:accompanist-pager-indicators:0.30.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // AndroidX lifecycle & viewmodel compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")

    // Material Components (for any classic widgets)
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.4.0") // keep the newer coil-compose
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // JSON
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.json:json:20210307")

    // Other libraries
    implementation("com.airbnb.android:lottie-compose:5.2.0")
    implementation("org.jsoup:jsoup:1.16.1")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
