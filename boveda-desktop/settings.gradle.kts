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
