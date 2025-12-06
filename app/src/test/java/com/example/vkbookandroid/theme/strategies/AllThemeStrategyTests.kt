package com.example.vkbookandroid.theme.strategies

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Test Suite для всех тестов архитектуры изолированных тем
 * 
 * Запустить все тесты:
 * ./gradlew test --tests "AllThemeStrategyTests"
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    ClassicThemeTest::class,
    RosatomThemeTest::class,
    ThemeFactoryTest::class,
    ThemeIsolationTest::class
)
class AllThemeStrategyTests







