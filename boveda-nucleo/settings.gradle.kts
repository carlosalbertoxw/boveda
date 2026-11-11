pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

// Este nombre es tambien la coordenada que usan las apps
// ("com.carlosalbertoxw.boveda:boveda-nucleo"): Gradle la sustituye por este
// proyecto al incluirlo con includeBuild, sin publicar nada en ningun lado.
rootProject.name = "boveda-nucleo"
