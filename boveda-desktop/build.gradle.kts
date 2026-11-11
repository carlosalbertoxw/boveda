import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.detekt)
}

// La version vive en version.properties, en la raiz del repositorio, y la
// comparten las dos apps. El instalador .msi solo reemplaza la instalacion
// anterior si este numero sube.
val versionApp: String = Properties().apply {
    rootProject.file("../version.properties").inputStream().use { load(it) }
}.getProperty("version")

group = "com.carlosalbertoxw.boveda"
version = versionApp

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
    // Cripto, formato del respaldo, fusion y papelera: el mismo codigo que usa
    // la app de Android.
    implementation(libs.boveda.nucleo)

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
    // Pruebas de la interfaz que corren en la JVM, sin ventana visible.
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

// Compose trae una libreria nativa distinta por sistema, y la build solo
// descarga la del sistema donde corre. Esta configuracion no se usa para nada
// mas que para que `--write-verification-metadata` anote las sumas de las de
// todos los sistemas: sin ella, el archivo generado en Windows haria fallar la
// verificacion en los runners de macOS y Linux del CI.
val todasLasPlataformas: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    todasLasPlataformas(compose.desktop.linux_x64)
    todasLasPlataformas(compose.desktop.linux_arm64)
    todasLasPlataformas(compose.desktop.macos_x64)
    todasLasPlataformas(compose.desktop.macos_arm64)
    todasLasPlataformas(compose.desktop.windows_x64)
}

// El respaldo de referencia escrito con Node vive en el nucleo;
// FlujoRespaldoTest tambien lo importa contra la base real.
sourceSets.test {
    resources.srcDir("../boveda-nucleo/src/test/resources")
}

// La misma configuracion de detekt para las tres builds, en la raiz del repositorio.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("../config/detekt.yml"))
    source.setFrom(files("src/main/kotlin", "src/test/kotlin"))
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
            packageVersion = versionApp
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
