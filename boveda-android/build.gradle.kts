plugins {
    alias(libs.plugins.android.application) apply false
    // Sin aplicar: solo pone en el classpath la version de Kotlin del catalogo
    // para que la use el Kotlin integrado de AGP 9.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
}
