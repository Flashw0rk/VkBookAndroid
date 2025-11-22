package com.example.vkbookandroid

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Главный Test Suite для запуска ВСЕХ тестов приложения (инструментальных)
 * 
 * Этот файл объединяет все тестовые классы в один набор для удобного запуска.
 * 
 * ЗАПУСК ВСЕХ ТЕСТОВ:
 * 
 * 1. Через Gradle:
 *    .\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.vkbookandroid.AllTestsSuite
 * 
 * 2. Через Android Studio:
 *    - Правый клик на файл AllTestsSuite.kt
 *    - Run 'AllTestsSuite'
 * 
 * 3. Через командную строку ADB:
 *    adb shell am instrument -w -r -e class com.example.vkbookandroid.AllTestsSuite com.example.vkbookandroid.test/androidx.test.runner.AndroidJUnitRunner
 * 
 * СТРУКТУРА ТЕСТОВ:
 * 
 * Этап 1 (Критично):
 * - SyncIntegrationTests - тесты синхронизации
 * - SyncProgressUITests - тесты UI прогресса синхронизации
 * - SearchTests - тесты поиска
 * - ErrorHandlingTests - тесты обработки ошибок
 * 
 * Этап 2 (Важно):
 * - PDFViewerTests - тесты PDF просмотра
 * - EditorTests - тесты редактора
 * - ScheduleTests - тесты графика смен
 * 
 * Этап 3 (Желательно):
 * - LifecycleTests - тесты жизненного цикла
 * - PerformanceTests - тесты производительности
 * - UserJourneyTests - тесты пользовательских сценариев
 * - EdgeCaseTests - тесты граничных случаев
 * 
 * Основные тесты:
 * - VkBookAndroidInstrumentedTests - основные тесты приложения
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // ========== ОСНОВНЫЕ ТЕСТЫ ==========
    VkBookAndroidInstrumentedTests::class,
    
    // ========== ЭТАП 1: КРИТИЧНЫЕ ТЕСТЫ ==========
    SyncIntegrationTests::class,
    SyncProgressUITests::class,
    SearchTests::class,
    ErrorHandlingTests::class,
    
    // ========== ЭТАП 2: ВАЖНЫЕ ТЕСТЫ ==========
    PDFViewerTests::class,
    EditorTests::class,
    ScheduleTests::class,
    
    // ========== ЭТАП 3: ЖЕЛАТЕЛЬНЫЕ ТЕСТЫ ==========
    LifecycleTests::class,
    PerformanceTests::class,
    UserJourneyTests::class,
    EdgeCaseTests::class
)
class AllTestsSuite {
    /**
     * Этот класс не содержит тестов, только конфигурацию Suite.
     * Все тесты находятся в классах, перечисленных в @Suite.SuiteClasses.
     * 
     * При запуске этого класса будут выполнены все тесты из всех указанных классов.
     */
}


