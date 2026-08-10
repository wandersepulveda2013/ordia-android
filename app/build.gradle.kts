plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val ciRunAttempt = System.getenv("GITHUB_RUN_ATTEMPT")?.toIntOrNull()
val stableKeyPath = System.getenv("ORDIA_KEYSTORE_PATH")
val stableKeyPassword = System.getenv("ORDIA_KEYSTORE_PASSWORD")
val stableKeyAlias = System.getenv("ORDIA_KEY_ALIAS")
val stableKeyAliasPassword = System.getenv("ORDIA_KEY_PASSWORD")
val stableSigningConfigured = listOf(stableKeyPath, stableKeyPassword, stableKeyAlias, stableKeyAliasPassword)
    .all { !it.isNullOrBlank() }
val configuredKeyPath = stableKeyPath.orEmpty()
val configuredKeyPassword = stableKeyPassword.orEmpty()
val configuredKeyAlias = stableKeyAlias.orEmpty()
val configuredKeyAliasPassword = stableKeyAliasPassword.orEmpty()

android {
    namespace = "com.ordia.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ordia.app"
        minSdk = 26
        targetSdk = 36
        versionCode = if (ciRunNumber != null && ciRunAttempt != null) {
            require(ciRunNumber >= 0) { "GITHUB_RUN_NUMBER must be non-negative" }
            require(ciRunAttempt in 1..99) { "GITHUB_RUN_ATTEMPT must be between 1 and 99" }
            val candidate = 1_300_000_000L + (ciRunNumber.toLong() * 100L) + ciRunAttempt
            require(candidate <= Int.MAX_VALUE) { "CI versionCode exceeds Android's Int limit" }
            candidate.toInt()
        } else 1_300_000_000
        versionName = "3.0.0-preview"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("previewSafe") {
            dimension = "distribution"
            applicationId = "com.ordia.app.preview"
            versionName = if (ciRunNumber == null || ciRunAttempt == null) {
                "3.0.0-preview-safe"
            } else "3.0.${ciRunNumber}-preview-safe.${ciRunAttempt}"
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "false")
            buildConfigField("boolean", "OVERLAY_ENABLED", "false")
            buildConfigField("boolean", "CONTEXT_NOTIFICATION_ACCESS_ENABLED", "false")
            buildConfigField("boolean", "PREVIEW", "true")
            resValue("string", "app_name", "Ordía")
        }

        create("previewFull") {
            dimension = "distribution"
            applicationId = "com.ordia.app.preview.full"
            versionName = if (ciRunNumber == null || ciRunAttempt == null) {
                "3.0.0-preview-full"
            } else "3.0.${ciRunNumber}-preview-full.${ciRunAttempt}"
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "true")
            buildConfigField("boolean", "OVERLAY_ENABLED", "true")
            buildConfigField("boolean", "CONTEXT_NOTIFICATION_ACCESS_ENABLED", "true")
            buildConfigField("boolean", "PREVIEW", "true")
            resValue("string", "app_name", "Ordía")
        }

        create("previewAdvanced") {
            dimension = "distribution"
            applicationId = "com.ordia.app.preview.advanced"
            versionName = if (ciRunNumber == null || ciRunAttempt == null) {
                "3.0.0-preview-advanced"
            } else "3.0.${ciRunNumber}-preview-advanced.${ciRunAttempt}"
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "true")
            buildConfigField("boolean", "OVERLAY_ENABLED", "true")
            buildConfigField("boolean", "CONTEXT_NOTIFICATION_ACCESS_ENABLED", "true")
            buildConfigField("boolean", "PREVIEW", "true")
            resValue("string", "app_name", "Ordía")
        }
    }

    signingConfigs {
        create("stableUpdate") {
            if (stableSigningConfigured) {
                storeFile = file(configuredKeyPath)
                storePassword = configuredKeyPassword
                keyAlias = configuredKeyAlias
                keyPassword = configuredKeyAliasPassword
            }
        }
    }

    buildTypes {
        debug {
            if (stableSigningConfigured) signingConfig = signingConfigs.getByName("stableUpdate")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "false")
            if (stableSigningConfigured) signingConfig = signingConfigs.getByName("stableUpdate")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    // kapt, no KSP: KSP 2.1.0-1.0.29 (único para Kotlin 2.1.0) embebe
    // kotlinx-serialization-core 1.7.3 en el classloader del worker y sombrea el 1.8.1
    // que requiere el processor de Room 2.8.4 → AbstractMethodError (ver ORD-036).
    kapt("androidx.room:room-compiler:2.8.4")

    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // org.json real en tests JVM: la implementación de Android no está disponible
    // fuera del dispositivo y el flujo de backup (BackupManager) está construido
    // sobre JSONObject/JSONArray. Solo se usa en src/test (~72 KB).
    testImplementation("org.json:json:20231013")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
