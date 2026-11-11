pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Compose Multiplatform arrastra algunas piezas de androidx (lifecycle,
        // collection) que solo se publican en el repositorio de Google.
        google()
    }
}

rootProject.name = "boveda"

// El codigo compartido con la app de Android. Gradle lo compila como parte de
// esta build y sustituye la dependencia "com.carlosalbertoxw.boveda:boveda-nucleo"
// por el proyecto, sin publicar nada. Sus tareas se lanzan desde aqui:
// ./gradlew :boveda-nucleo:test
includeBuild("../boveda-nucleo")
