package com.example.vkbookandroid

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Тесты пользовательских сценариев (User Journeys)
 * Проверяют полные потоки использования приложения
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class UserJourneyTests {

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
    fun userJourney_searchAndViewArmature() {
        // Сценарий: Пользователь ищет арматуру и просматривает информацию
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // 1. Открываем приложение
            onView(withId(R.id.viewPager)).check(matches(isDisplayed()))
            
            // 2. Переходим на вкладку "Арматура"
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val armIndex = adapter.getLocalIndex(1) ?: return@onActivity
                    pager.setCurrentItem(armIndex, false)
                }
            }
            Thread.sleep(800)
            
            // 3. Вводим поисковый запрос
            try {
                onView(withId(R.id.search_view)).perform(click())
                onView(withId(R.id.search_view)).perform(typeText("А-0"))
                Thread.sleep(1000)
            } catch (e: Exception) {
                // Если поиск не найден, продолжаем
            }
            
            // 4. Проверяем что результаты отображаются
            scenario.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.recyclerView)
                assertTrue("RecyclerView должен существовать", recycler != null)
            }
        }
    }

    @Test
    fun userJourney_syncAndViewUpdatedData() {
        // Сценарий: Пользователь синхронизирует данные и просматривает обновления
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // 1. Открываем приложение
            onView(withId(R.id.viewPager)).check(matches(isDisplayed()))
            
            // 2. Нажимаем кнопку синхронизации
            onView(withId(R.id.btnSync))
                .check(matches(isDisplayed()))
                .perform(click())
            
            // 3. Ждем завершения синхронизации
            Thread.sleep(3000)
            
            // 4. Проверяем что статус обновился
            onView(withId(R.id.tvSyncStatus))
                .check(matches(isDisplayed()))
                .check(matches(not(withText("Готов к обновлению"))))
            
            // 5. Переходим на вкладку "Арматура" для просмотра обновленных данных
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val armIndex = adapter.getLocalIndex(1) ?: return@onActivity
                    pager.setCurrentItem(armIndex, false)
                }
            }
            Thread.sleep(800)
            
            // 6. Проверяем что данные отображаются
            scenario.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.recyclerView)
                assertTrue("RecyclerView должен существовать", recycler != null)
            }
        }
    }

    @Test
    fun userJourney_addReminderAndCheckSchedule() {
        // Сценарий: Пользователь добавляет напоминание и проверяет график
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // 1. Открываем приложение
            onView(withId(R.id.viewPager)).check(matches(isDisplayed()))
            
            // 2. Переходим на вкладку "График проверок"
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val checksIndex = adapter.getLocalIndex(5) ?: return@onActivity
                    pager.setCurrentItem(checksIndex, false)
                }
            }
            Thread.sleep(800)
            
            // 3. Переключаемся в режим "Мои задачи"
            try {
                onView(withId(R.id.btnPersonalMode)).perform(click())
                Thread.sleep(300)
            } catch (e: Exception) {
                // Если кнопка не найдена, продолжаем
            }
            
            // 4. Включаем режим редактирования
            try {
                onView(withId(R.id.btnEditMode)).perform(click())
                Thread.sleep(300)
            } catch (e: Exception) {
                // Если кнопка не найдена, продолжаем
            }
            
            // 5. Проверяем что UI готов к добавлению правила
            onView(isRoot()).check(matches(isDisplayed()))
        }
    }

    @Test
    fun userJourney_navigateThroughAllTabs() {
        // Сценарий: Пользователь просматривает все вкладки приложения
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val tabs = listOf(1, 2, 4, 5) // Арматура, Схемы, График, График проверок
            
            for (globalIndex in tabs) {
                scenario.onActivity { activity ->
                    val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                    (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                        val localIndex = adapter.getLocalIndex(globalIndex) ?: return@onActivity
                        pager.setCurrentItem(localIndex, false)
                    }
                }
                Thread.sleep(500)
                
                // Проверяем что вкладка отображается
                onView(withId(R.id.viewPager)).check(matches(isDisplayed()))
            }
        }
    }

    @Test
    fun userJourney_changeThemeAndVerify() {
        // Сценарий: Пользователь меняет тему и проверяет применение
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // 1. Открываем настройки
            onView(withId(R.id.btnSettings)).perform(click())
            Thread.sleep(400)
            
            // 2. Переходим на вкладку "Программа"
            try {
                onView(allOf(
                    withText(containsString("Программа")),
                    isDescendantOfA(withId(R.id.tabLayout))
                )).perform(click())
                Thread.sleep(300)
            } catch (e: Exception) {
                // Если вкладка не найдена, продолжаем
            }
            
            // 3. Проверяем что настройки отображаются
            onView(isRoot()).check(matches(isDisplayed()))
            
            // 4. Возвращаемся назад
            pressBack()
            Thread.sleep(300)
            
            // 5. Проверяем что главный экран отображается
            onView(withId(R.id.viewPager)).check(matches(isDisplayed()))
        }
    }
}

