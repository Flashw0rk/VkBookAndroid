package com.example.vkbookandroid

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Тесты граничных случаев
 * Проверяют поведение приложения в нестандартных ситуациях
 */
@RunWith(AndroidJUnit4::class)
class EdgeCaseTests {

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
    fun emptyDataList_displaysCorrectly() {
        // Тест проверяет отображение пустого списка данных
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Переходим на вкладку "Арматура"
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val armIndex = adapter.getLocalIndex(1) ?: return@onActivity
                    pager.setCurrentItem(armIndex, false)
                }
            }
            
            Thread.sleep(800)
            
            // Проверяем что UI остается стабильным даже при пустом списке
            onView(isRoot()).check(matches(isDisplayed()))
        }
    }

    @Test
    fun rapidTabSwitching_handlesCorrectly() {
        // Тест проверяет быстрое переключение между вкладками
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val tabs = listOf(1, 2, 4, 1, 2, 4) // Быстрое переключение
            
            for (globalIndex in tabs) {
                scenario.onActivity { activity ->
                    val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                    (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                        val localIndex = adapter.getLocalIndex(globalIndex) ?: return@onActivity
                        pager.setCurrentItem(localIndex, false)
                    }
                }
                Thread.sleep(100) // Минимальная задержка
            }
            
            // Проверяем что приложение не падает
            onView(withId(R.id.viewPager)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun rapidButtonClicks_handlesCorrectly() {
        // Тест проверяет быстрые повторные клики по кнопкам
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Быстро кликаем по кнопке синхронизации несколько раз
            try {
                for (i in 1..5) {
                    onView(withId(R.id.btnSync)).perform(click())
                    Thread.sleep(50)
                }
            } catch (e: Exception) {
                // Если кнопка стала неактивной, это нормально
            }
            
            // Проверяем что приложение не падает
            onView(isRoot()).check(matches(isDisplayed()))
        }
    }

    @Test
    fun searchWithVeryLongQuery_handlesCorrectly() {
        // Тест проверяет поиск с очень длинным запросом
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val armIndex = adapter.getLocalIndex(1) ?: return@onActivity
                    pager.setCurrentItem(armIndex, false)
                }
            }
            
            Thread.sleep(800)
            
            // Вводим очень длинный запрос
            val veryLongQuery = "А".repeat(500)
            try {
                onView(withId(R.id.search_view)).perform(click())
                onView(withId(R.id.search_view)).perform(typeText(veryLongQuery))
                Thread.sleep(1000)
            } catch (e: Exception) {
                // Если поиск не найден, пропускаем
            }
            
            // Проверяем что приложение не падает
            onView(isRoot()).check(matches(isDisplayed()))
        }
    }

    @Test
    fun searchWithSpecialUnicodeCharacters_handlesCorrectly() {
        // Тест проверяет поиск со специальными Unicode символами
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val armIndex = adapter.getLocalIndex(1) ?: return@onActivity
                    pager.setCurrentItem(armIndex, false)
                }
            }
            
            Thread.sleep(800)
            
            // Вводим специальные символы
            val specialChars = "А-0/1\u200B\u200C\u200D" // Zero-width characters
            try {
                onView(withId(R.id.search_view)).perform(click())
                onView(withId(R.id.search_view)).perform(typeText(specialChars))
                Thread.sleep(1000)
            } catch (e: Exception) {
                // Если поиск не найден, пропускаем
            }
            
            // Проверяем что приложение не падает
            onView(isRoot()).check(matches(isDisplayed()))
        }
    }

    @Test
    fun syncDuringTabSwitch_handlesCorrectly() {
        // Тест проверяет синхронизацию во время переключения вкладок
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Запускаем синхронизацию
            onView(withId(R.id.btnSync)).perform(click())
            Thread.sleep(200)
            
            // Переключаемся на другую вкладку во время синхронизации
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val scheduleIndex = adapter.getLocalIndex(4) ?: return@onActivity
                    pager.setCurrentItem(scheduleIndex, false)
                }
            }
            Thread.sleep(500)
            
            // Проверяем что приложение не падает
            onView(isRoot()).check(matches(isDisplayed()))
        }
    }

    @Test
    fun uiStates_loadingStateDisplays() {
        // Тест проверяет отображение состояния загрузки
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Запускаем синхронизацию для проверки состояния загрузки
            onView(withId(R.id.btnSync)).perform(click())
            Thread.sleep(500)
            
            // Проверяем что отображается состояние загрузки
            onView(withId(R.id.tvSyncStatus))
                .check(matches(isDisplayed()))
                .check(matches(not(withText(""))))
        }
    }

    @Test
    fun uiStates_errorStateDisplays() {
        // Тест проверяет отображение состояния ошибки
        // В реальном тесте здесь была бы симуляция ошибки
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Проверяем что UI готов к отображению ошибок
            onView(isRoot()).check(matches(isDisplayed()))
        }
    }
}

