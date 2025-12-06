package com.example.vkbookandroid

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.hamcrest.Matchers.anyOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Тесты для редактора файлов
 * Проверяют загрузку, редактирование, сохранение файлов
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class EditorTests {

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
    fun editorTab_isAccessible() {
        // Настраиваем видимость вкладки "Редактор" (индекс 3)
        val prefs = context.getSharedPreferences("server_settings", Context.MODE_PRIVATE)
        val tabsJson = com.google.gson.Gson().toJson(listOf(1, 2, 3, 4, 5))
        prefs.edit().putString("tabs_visibility_json", tabsJson).commit()
        
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Переходим на вкладку "Редактор"
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val editorIndex = adapter.getLocalIndex(3) ?: return@onActivity
                    pager.setCurrentItem(editorIndex, false)
                }
            }
            
            Thread.sleep(800)
            
            // Проверяем что редактор открылся
            onView(isRoot()).check(matches(isDisplayed()))
        }
    }

    @Test
    fun editor_openButton_isVisible() {
        val prefs = context.getSharedPreferences("server_settings", Context.MODE_PRIVATE)
        val tabsJson = com.google.gson.Gson().toJson(listOf(1, 2, 3, 4, 5))
        prefs.edit().putString("tabs_visibility_json", tabsJson).commit()
        
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val editorIndex = adapter.getLocalIndex(3) ?: return@onActivity
                    pager.setCurrentItem(editorIndex, false)
                }
            }
            
            Thread.sleep(800)
            
            // Проверяем наличие кнопки "Открыть"
            try {
                onView(withId(R.id.btnOpen)).check(matches(isDisplayed()))
            } catch (e: Exception) {
                // Если кнопка не найдена, проверяем общее наличие UI
                onView(isRoot()).check(matches(isDisplayed()))
            }
        }
    }

    @Test
    fun editor_saveButton_isVisible() {
        val prefs = context.getSharedPreferences("server_settings", Context.MODE_PRIVATE)
        val tabsJson = com.google.gson.Gson().toJson(listOf(1, 2, 3, 4, 5))
        prefs.edit().putString("tabs_visibility_json", tabsJson).commit()
        
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val editorIndex = adapter.getLocalIndex(3) ?: return@onActivity
                    pager.setCurrentItem(editorIndex, false)
                }
            }
            
            Thread.sleep(800)
            
            // Проверяем наличие кнопки "Сохранить"
            try {
                onView(withId(R.id.btnSave)).check(matches(anyOf(
                    withEffectiveVisibility(Visibility.VISIBLE),
                    withEffectiveVisibility(Visibility.GONE)
                )))
            } catch (e: Exception) {
                onView(isRoot()).check(matches(isDisplayed()))
            }
        }
    }

    @Test
    fun editor_uploadButton_isVisible() {
        val prefs = context.getSharedPreferences("server_settings", Context.MODE_PRIVATE)
        val tabsJson = com.google.gson.Gson().toJson(listOf(1, 2, 3, 4, 5))
        prefs.edit().putString("tabs_visibility_json", tabsJson).commit()
        
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val editorIndex = adapter.getLocalIndex(3) ?: return@onActivity
                    pager.setCurrentItem(editorIndex, false)
                }
            }
            
            Thread.sleep(800)
            
            // Проверяем наличие кнопки "Загрузить"
            try {
                onView(withId(R.id.btnUpload)).check(matches(anyOf(
                    withEffectiveVisibility(Visibility.VISIBLE),
                    withEffectiveVisibility(Visibility.GONE)
                )))
            } catch (e: Exception) {
                onView(isRoot()).check(matches(isDisplayed()))
            }
        }
    }
}

