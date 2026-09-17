import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

group = "com.carlosalbertoxw.boveda"
version = "1.0.0"

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    // Da un Dispatchers.Main atado al hilo de eventos de Swing, que es donde
    // corre Compose en escritorio.
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.json)
    implementation(libs.jna.platform)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    // PBKDF2 con 210 000 iteraciones por intento: algunas pruebas lo pagan de
    // verdad, y en paralelo se comen la CPU sin terminar antes.
    maxParallelForks = 1
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

compose.desktop {
    application {
        mainClass = "com.carlosalbertoxw.boveda.MainKt"

        jvmArgs += listOf("-Dfile.encoding=UTF-8")

        buildTypes.release.proguard {
            // ProGuard renombra lo que JNA busca por reflexion y rompe la
            // proteccion contra capturas sin dar ningun error visible.
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(
                TargetFormat.Msi,
                TargetFormat.Exe,
                TargetFormat.Dmg,
                TargetFormat.Deb,
                TargetFormat.Rpm
            )
            // Sin tilde: WiX y algunos empaquetadores de Linux no aceptan
            // caracteres fuera de ASCII en el nombre del paquete. La ventana y
            // el menu si dicen "Bóveda".
            packageName = "Boveda"
            packageVersion = "1.0.0"
            description = "Gestor de contraseñas sin conexión"
            vendor = "carlosalbertoxw"
            copyright = "© 2026 carlosalbertoxw"

            // Lo que `./gradlew suggestRuntimeModules` detecta por encima de los
            // modulos base: el runtime empaquetado no lleva el JDK entero.
            // AES-GCM y PBKDF2 vienen en java.base, sin proveedores extra.
            modules("java.instrument", "jdk.unsupported")

            windows {
                menuGroup = "Bóveda"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Fijo para siempre: es lo que permite que un .msi nuevo
                // actualice la instalacion anterior en vez de duplicarla.
                upgradeUuid = "6b0f3c1e-2d5a-4a8e-9c7b-3f1e8d2a5b64"
                iconFile.set(project.file("iconos/boveda.ico"))
            }
            macOS {
                bundleID = "com.carlosalbertoxw.boveda"
                iconFile.set(project.file("iconos/boveda.icns"))
            }
            linux {
                packageName = "boveda"
                iconFile.set(project.file("iconos/boveda.png"))
            }
        }
    }
}
