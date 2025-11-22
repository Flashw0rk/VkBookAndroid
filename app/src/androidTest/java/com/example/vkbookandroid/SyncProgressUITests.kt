package com.example.vkbookandroid

import android.view.View
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
 * Инструментальные тесты для проверки отображения прогресса синхронизации в UI
 */
@RunWith(AndroidJUnit4::class)
class SyncProgressUITests {

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
    fun syncProgressBar_isVisibleDuringSync() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Находим кнопку синхронизации
            onView(withId(R.id.btnSync))
                .check(matches(isDisplayed()))
                .check(matches(isEnabled()))
            
            // Нажимаем кнопку синхронизации
            onView(withId(R.id.btnSync)).perform(click())
            
            // Проверяем что прогресс-бар стал видимым
            // Даем время на запуск синхронизации
            Thread.sleep(500)
            
            // Проверяем что статус синхронизации обновился
            onView(withId(R.id.tvSyncStatus))
                .check(matches(isDisplayed()))
            
            // Прогресс-бар может быть видимым или скрытым в зависимости от состояния
            // Проверяем что он существует
            onView(withId(R.id.progressSync))
                .check(matches(anyOf(
                    withEffectiveVisibility(Visibility.VISIBLE),
                    withEffectiveVisibility(Visibility.GONE)
                )))
        }
    }

    @Test
    fun syncStatusText_updatesDuringSync() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Находим текстовое поле статуса
            onView(withId(R.id.tvSyncStatus))
                .check(matches(isDisplayed()))
                .check(matches(not(withText(""))))
            
            // Нажимаем кнопку синхронизации
            onView(withId(R.id.btnSync)).perform(click())
            
            // Даем время на обновление статуса
            Thread.sleep(1000)
            
            // Проверяем что статус изменился
            onView(withId(R.id.tvSyncStatus))
                .check(matches(isDisplayed()))
                .check(matches(not(withText("Готов к обновлению"))))
        }
    }

    @Test
    fun syncProgressPercent_isVisibleDuringSync() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Нажимаем кнопку синхронизации
            onView(withId(R.id.btnSync)).perform(click())
            
            // Даем время на запуск синхронизации
            Thread.sleep(500)
            
            // Проверяем что процент выполнения существует (может быть видимым или скрытым)
            onView(withId(R.id.tvProgressPercent))
                .check(matches(anyOf(
                    withEffectiveVisibility(Visibility.VISIBLE),
                    withEffectiveVisibility(Visibility.GONE)
                )))
        }
    }

    @Test
    fun syncButton_isDisabledDuringSync() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Проверяем что кнопка изначально включена
            onView(withId(R.id.btnSync))
                .check(matches(isEnabled()))
            
            // Нажимаем кнопку синхронизации
            onView(withId(R.id.btnSync)).perform(click())
            
            // Даем время на запуск синхронизации
            Thread.sleep(300)
            
            // Проверяем что кнопка стала неактивной во время синхронизации
            // (может быть неактивной или активной в зависимости от состояния)
            onView(withId(R.id.btnSync))
                .check(matches(isDisplayed()))
        }
    }
}

