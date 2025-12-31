pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    // Если используете version catalogs
    plugins {
        id("com.android.application") version "8.2.0"
        id("org.jetbrains.kotlin.android") version "1.9.0"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // Дополнительные репозитории, если нужны
        maven { url = uri("https://jitpack.io") }
    }

    // Включить version catalogs, если используете
    versionCatalogs {
        create("libs") {
            // Здесь можно определить версии, но обычно это в libs.versions.toml
        }
    }
}

rootProject.name = "Scrippy"
include(":app")