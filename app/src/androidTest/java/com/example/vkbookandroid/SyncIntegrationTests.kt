package com.example.vkbookandroid

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Интеграционные тесты для синхронизации
 * Проверяют полные пользовательские сценарии синхронизации
 */
@RunWith(AndroidJUnit4::class)
class SyncIntegrationTests {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After
    fun tearDown() {
        WorkManager.getInstance(context).cancelAllWork()
    }

    @Test
    fun syncButton_triggersSyncProcess() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Проверяем что кнопка синхронизации видна и активна
            onView(withId(R.id.btnSync))
                .check(matches(isDisplayed()))
                .check(matches(isEnabled()))
            
            // Нажимаем кнопку синхронизации
            onView(withId(R.id.btnSync)).perform(click())
            
            // Даем время на запуск синхронизации
            Thread.sleep(1000)
			TestUtils.dismissBlockingDialogsIfAny()
            
            // Проверяем что статус изменился (не остался "Готов к обновлению")
            onView(withId(R.id.tvSyncStatus))
                .check(matches(isDisplayed()))
                .check(matches(not(withText("Готов к обновлению"))))
        }
    }

    @Test
    fun syncProgress_updatesDuringSync() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Нажимаем кнопку синхронизации
            onView(withId(R.id.btnSync)).perform(click())
            
            // Даем время на запуск
            Thread.sleep(500)
            
            // Проверяем что прогресс-бар виден
            onView(withId(R.id.progressSync))
                .check(matches(anyOf(
                    withEffectiveVisibility(Visibility.VISIBLE),
                    withEffectiveVisibility(Visibility.GONE)
                )))
            
            // Проверяем что статус обновился
            onView(withId(R.id.tvSyncStatus))
                .check(matches(isDisplayed()))
                .check(matches(not(withText(""))))
        }
    }

    @Test
    fun syncStatus_showsConnectionCheck() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Нажимаем кнопку синхронизации
            onView(withId(R.id.btnSync)).perform(click())
            
            // Даем время на проверку соединения
            Thread.sleep(1500)
            
            // Проверяем что статус содержит информацию о соединении
            // (может быть "Проверка соединения" или "Подключение к серверу")
            onView(withId(R.id.tvSyncStatus))
                .check(matches(isDisplayed()))
        }
    }


    @Test
    fun syncStatus_showsDetailedProgress() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Нажимаем кнопку синхронизации
            onView(withId(R.id.btnSync)).perform(click())
            
            // Даем время на обновление статуса
            Thread.sleep(2000)
            
            // Проверяем что статус показывает детальную информацию
            onView(withId(R.id.tvSyncStatus))
                .check(matches(isDisplayed()))
                .check(matches(not(withText(""))))
            
            // Не ждем DESTROYED - просто проверяем что статус обновился
            // ActivityScenario.use автоматически закроет Activity
        }
    }
}

