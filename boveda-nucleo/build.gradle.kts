import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

group = "com.carlosalbertoxw.boveda"

// Bytecode de Java 17, igual que las dos apps: D8/R8 lo convierte para
// Android y el runtime que empaqueta la app de escritorio lo corre tal cual.
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
    // Android ya trae org.json en el sistema; meter la libreria en el APK
    // chocaria con esas clases. Cada app pone la suya.
    compileOnly(libs.json)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}

tasks.test {
    // PBKDF2 con cientos de miles de iteraciones por intento: en paralelo se
    // comen la CPU sin terminar antes.
    maxParallelForks = 1
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/../config/detekt.yml"))
    source.setFrom(files("src/main/kotlin", "src/test/kotlin"))
}
