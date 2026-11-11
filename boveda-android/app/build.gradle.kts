import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
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
    // Las librerias de AndroidX actuales piden compilar contra la 37. El
    // targetSdk se queda en 36 hasta probar en un dispositivo los cambios de
    // comportamiento de Android 17.
    compileSdk = 37

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

    buildFeatures {
        compose = true
    }

    // Los esquemas exportados viajan como assets de las pruebas instrumentadas:
    // es de donde MigrationTestHelper saca la version 1 para construirla tal y
    // como era antes de migrar.
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")

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

// Antes no habia linter, asi que los descuidos que una herramienta senala
// gratis —un import sin usar, un nombre totalmente cualificado a mitad de
// archivo— se acumulaban sin que nadie los viera.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt.yml"))
    source.setFrom(files("src/main/java", "src/test/java", "src/androidTest/java"))
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
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.biometric)
    // Debe ir explicito: sin el, biometric fija fragment en 1.2.5 y los
    // launchers de ActivityResult truenan al abrirse. Ver libs.versions.toml.
    implementation(libs.androidx.fragment)
    // El tema de la app hereda de Theme.AppCompat porque BiometricPrompt
    // infla su dialogo de huella con vistas de AppCompat en API 26-27.
    implementation(libs.androidx.appcompat)
    implementation(libs.sqlcipher.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)

    // Las pruebas instrumentadas de este proyecto no tocan la interfaz: prueban
    // el ciclo completo de la boveda contra SQLCipher de verdad, que es lo que
    // no se puede hacer en la JVM. Por eso no hay espresso ni compose-ui-test:
    // estaban declarados sin que existiera una sola prueba que los usara.
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    // Solo para MigrationTestHelper: migrar la base es lo unico de esta app que
    // puede perder datos del usuario en silencio, asi que se prueba de verdad.
    androidTestImplementation(libs.androidx.room.testing)
}
