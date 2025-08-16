// settings.gradle.kts — корень проекта

pluginManagement {
    repositories {
        // Репозитории для самих Gradle-плагинов (AGP, Kotlin и т.д.)
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // Жёстко запрещаем задавать репозитории в модулях — всё централизовано здесь
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Репозитории для библиотек проекта
        google()
        mavenCentral()
        // Для зависимостей из GitHub (например, AndroidPdfViewer и пр.)
        maven("https://jitpack.io")
    }
}

// (Опционально, но удобно) типобезопасные ссылки на проекты: projects.app, projects.core и т.п.
// enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "VkBookAndroid"

// Подключаем только реально существующие модули
include(":app")
include(":Core")
