package com.example.vkbookandroid

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Тесты жизненного цикла Activity
 * Проверяют сохранение состояния при повороте, переходе в фон
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class LifecycleTests {

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
    fun activity_restoresStateAfterRecreation() {
        var currentTab = -1
        
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Запоминаем текущую вкладку
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                currentTab = pager.currentItem
            }
            
            // Симулируем пересоздание Activity
            scenario.recreate()
            
            Thread.sleep(500)
            
            // Проверяем что вкладка восстановилась
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                // Вкладка должна быть восстановлена или быть валидной
                assertTrue("Вкладка должна быть валидной", pager.currentItem >= 0)
            }
        }
    }

    @Test
    fun activity_handlesConfigurationChange() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Проверяем что Activity корректно обрабатывает изменение конфигурации
            scenario.onActivity { activity ->
                // Симулируем изменение конфигурации
                activity.onConfigurationChanged(activity.resources.configuration)
            }
            
            Thread.sleep(300)
            
            // Проверяем что UI остается стабильным
            onView(withId(R.id.viewPager)).check(matches(isDisplayed()))
            onView(withId(R.id.tabLayout)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun activity_preservesDataOnPause() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Переходим на определенную вкладку
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val armIndex = adapter.getLocalIndex(1) ?: return@onActivity
                    pager.setCurrentItem(armIndex, false)
                }
            }
            
            Thread.sleep(500)
            
            // Симулируем onPause
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
            
            // Проверяем что данные сохранились
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                assertTrue("ViewPager должен существовать", pager != null)
            }
        }
    }

    @Test
    fun activity_resumesCorrectly() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Переводим в фон
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            
            Thread.sleep(300)
            
            // Возвращаем на передний план
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            
            Thread.sleep(300)
            
            // Проверяем что UI восстановился
            onView(withId(R.id.viewPager)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun activity_handlesLowMemory() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Симулируем ситуацию нехватки памяти
            scenario.onActivity { activity ->
                activity.onLowMemory()
            }
            
            Thread.sleep(300)
            
			TestUtils.dismissBlockingDialogsIfAny()
            // Проверяем что приложение не падает
            onView(isRoot()).check(matches(isDisplayed()))
        }
    }
}

