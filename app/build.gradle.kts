import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    jacoco
    id("com.google.gms.google-services") version "4.4.0" apply false
    id("com.google.firebase.crashlytics") version "2.9.9" apply false
}

// Загружаем конфигурацию keystore из local.properties
val keystorePropertiesFile = rootProject.file("local.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.example.vkbookandroid"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.vkbookandroid"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Изоляция окружения между тестами (для Orchestrator)
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        
        // API ключ из local.properties для безопасности
        val apiKey = keystoreProperties["API_KEY"] as String? ?: "vkbook-2025-secret-key-abc123"
        buildConfigField("String", "API_KEY", "\"$apiKey\"")
        
        // Базовый URL сервера (HTTP пока сервер не поддерживает HTTPS)
        val serverUrl = keystoreProperties["SERVER_URL"] as String? ?: "https://vkbookserver.onrender.com/"
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        
        // Флаг для принудительного использования HTTPS (когда сервер будет готов)
        val forceHttps = keystoreProperties["FORCE_HTTPS"] as String? ?: "false"
        buildConfigField("boolean", "FORCE_HTTPS", forceHttps)
    }

    signingConfigs {
        create("release") {
            // Читаем параметры из local.properties, используем значения по умолчанию если файла нет
            val keystoreFile = keystoreProperties["KEYSTORE_FILE"] as String? ?: "../vkbook-release-key.keystore"
            storeFile = file(keystoreFile)
            storePassword = keystoreProperties["KEYSTORE_PASSWORD"] as String? ?: ""
            keyAlias = keystoreProperties["KEY_ALIAS"] as String? ?: "vkbook_key"
            keyPassword = keystoreProperties["KEY_PASSWORD"] as String? ?: ""
        }
    }

    buildTypes {
        release {
            // ProGuard ОТКЛЮЧЕН - вызывает краш при запуске
            // Приложение работает стабильно без него
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false // Отключаем debug режим в release
				signingConfig = signingConfigs.getByName("release")
            // Запрещаем insecure TLS в релизе
            buildConfigField("boolean", "ALLOW_INSECURE_TLS_FOR_UPDATES", "false")
        }
        debug {
            isDebuggable = true
            // Разрешаем точечный insecure TLS для обновлений в debug (для эмуляторов/капризных устройств)
            buildConfigField("boolean", "ALLOW_INSECURE_TLS_FOR_UPDATES", "true")
        }
    }
    
    buildFeatures {
        buildConfig = true
    }
    
    // Отключаем линтер для быстрой сборки
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    
    // Конфигурация тестов
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        animationsDisabled = true
        // Изолированный запуск каждого теста
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
    
}

// Assets больше не используются, все файлы создаются программно

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    // ViewPager2 и TabLayout
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    
    // Core module
    implementation(project(":Core"))
    
    // Apache POI для Excel (если нужно)
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    
    // Gson для JSON
    implementation("com.google.code.gson:gson:2.10.1")
    
    // WorkManager для фоновой синхронизации
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    
    androidTestImplementation("androidx.work:work-testing:2.9.1")

    // Удалено: зависимости SQLite FTS5 (поиск работает на PersistentSearchEngine)

    // Сетевые зависимости для API
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Безопасное хранение данных
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Robolectric для JVM-юнит тестов Android-логики
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.5.0")
    
    // PDF Viewer (пока не используется)
    // implementation("com.github.barteksc:android-pdf-viewer:3.2.0-beta.1")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // Android Test Orchestrator для изоляции тестов
    androidTestUtil("androidx.test:orchestrator:1.5.1")
    
    // Firebase (добавляются только если файл google-services.json существует)
    if (rootProject.file("app/google-services.json").exists()) {
        implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
        implementation("com.google.firebase:firebase-crashlytics-ktx")
        implementation("com.google.firebase:firebase-analytics-ktx")
        implementation("com.google.firebase:firebase-config-ktx")
    }

    // Ускорение холодного старта с помощью ProfileInstaller (без изменения логики)
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
}

// ===== JaCoCo Configuration =====
jacoco {
    toolVersion = "0.8.11"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest", "createDebugCoverageReport")
    
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    
    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/databinding/*",
        "**/generated/**"
    )
    
    val debugTree = fileTree("${buildDir}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }
    
    val mainSrc = "${project.projectDir}/src/main/java"
    
    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(buildDir) {
        include(
            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            "outputs/code_coverage/debugAndroidTest/connected/**/*.ec"
        )
    })
}

android {
    buildTypes {
        debug {
            enableAndroidTestCoverage = true
            enableUnitTestCoverage = true
        }
    }
}