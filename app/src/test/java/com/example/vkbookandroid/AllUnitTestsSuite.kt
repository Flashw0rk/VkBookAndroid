package com.example.vkbookandroid

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Test Suite для запуска всех unit тестов приложения
 * 
 * Запуск всех unit тестов:
 * .\gradlew.bat test -Ptest.single=AllUnitTestsSuite
 * 
 * Или через Android Studio: Run -> Run 'AllUnitTestsSuite'
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Основные unit тесты
    VkBookAndroidTests::class,
    
    // Тесты синхронизации
    SyncServiceTests::class,
    ServerConnectionTests::class
)
class AllUnitTestsSuite {
    // Этот класс не содержит тестов, только конфигурацию Suite
}

