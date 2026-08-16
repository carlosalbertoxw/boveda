import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Las credenciales de firma viven en keystore.properties, fuera del control de
// versiones (ver .gitignore). Si el archivo no esta, la build sigue funcionando
// y el release queda sin firmar en vez de romperse: asi clonar y compilar no
// exige tener el keystore.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.carlosalbertoxw.boveda"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.carlosalbertoxw.boveda"
        // 26 es el piso real: por debajo no hay Keystore con GCM ni
        // PBKDF2WithHmacSHA256, que son la base del cifrado de la boveda.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // Solo se empaquetan los recursos en espanol: la app esta escrita en
        // ese idioma y arrastrar el resto solo engorda el APK.
        localeFilters += listOf("es")
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v1 (JAR signing) solo hace falta por debajo de API 24 y aqui
                // el minSdk es 26; v2/v3 verifican el APK completo y v3 permite
                // rotar la clave sin perder la identidad de la app.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Null cuando no hay keystore.properties: el APK sale sin firmar.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            // Que el release NO sea depurable es parte del modelo de amenazas:
            // con android:debuggable un atacante con el telefono desbloqueado
            // se engancha al proceso y lee la frase de la boveda en memoria.
            isDebuggable = false
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Observa el ciclo de vida del proceso completo, no el de una Activity:
    // es lo que permite bloquear la boveda al irse la app a segundo plano.
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.biometric)
    // Debe ir explicito: sin el, biometric fija fragment en 1.2.5 y los
    // launchers de ActivityResult truenan al abrirse. Ver libs.versions.toml.
    implementation(libs.androidx.fragment)
    // El tema de la app hereda de Theme.AppCompat porque BiometricPrompt
    // infla su dialogo de huella con vistas de AppCompat en API 26-27.
    implementation(libs.androidx.appcompat)
    implementation(libs.sqlcipher.android)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
