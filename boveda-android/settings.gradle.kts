pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Boveda"
include(":app")

// El codigo compartido con la app de escritorio. Gradle lo compila como parte
// de esta build y sustituye la dependencia "com.carlosalbertoxw.boveda:boveda-nucleo"
// por el proyecto, sin publicar nada.
includeBuild("../boveda-nucleo")
