package com.example.vkbookandroid

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.vkbookandroid.network.NetworkModule
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Тесты для обработки ошибок
 * Проверяют поведение приложения при различных ошибках
 */
@RunWith(AndroidJUnit4::class)
class ErrorHandlingTests {

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
    fun syncWithInvalidServerUrl_showsError() {
        // Сохраняем оригинальный URL
        val originalUrl = NetworkModule.getCurrentBaseUrl()
        
        try {
            // Устанавливаем неверный URL
            val prefs = context.getSharedPreferences("server_settings", Context.MODE_PRIVATE)
            prefs.edit().putString("server_url", "https://invalid-url-that-does-not-exist.com/").commit()
            
            // Перезагружаем настройки
            NetworkModule.updateBaseUrl("https://invalid-url-that-does-not-exist.com/")
            
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                // Нажимаем кнопку синхронизации
                onView(withId(R.id.btnSync)).perform(click())
                
                // Даем время на попытку подключения
                Thread.sleep(3000)
                
                // Проверяем что отображается сообщение об ошибке
                onView(withId(R.id.tvSyncStatus))
                    .check(matches(isDisplayed()))
                    .check(matches(not(withText("Готов к обновлению"))))
            }
        } finally {
            // Восстанавливаем оригинальный URL
            NetworkModule.updateBaseUrl(originalUrl)
            val prefs = context.getSharedPreferences("server_settings", Context.MODE_PRIVATE)
            prefs.edit().putString("server_url", originalUrl).commit()
        }
    }

    @Test
    fun syncStatus_displaysErrorMessages() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Нажимаем кнопку синхронизации
            onView(withId(R.id.btnSync)).perform(click())
            
            // Даем время на выполнение
            Thread.sleep(2000)
            
            // Проверяем что статус обновился (может быть ошибка или успех)
            onView(withId(R.id.tvSyncStatus))
                .check(matches(isDisplayed()))
                .check(matches(not(withText(""))))
        }
    }

    @Test
    fun app_handlesNetworkErrorsGracefully() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Приложение должно корректно обрабатывать ошибки сети
            // Проверяем что UI остается стабильным
            onView(withId(R.id.viewPager))
                .check(matches(isDisplayed()))
            
            onView(withId(R.id.tabLayout))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun syncHandlesTimeout_gracefully() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Нажимаем кнопку синхронизации
            onView(withId(R.id.btnSync)).perform(click())
            
            // Даем время на обработку (может быть timeout)
            Thread.sleep(5000)
            
            // Проверяем что приложение не падает
            onView(isRoot()).check(matches(isDisplayed()))
            
            // Проверяем что статус обновился
            onView(withId(R.id.tvSyncStatus))
                .check(matches(isDisplayed()))
			
			// Закрываем возможные перекрывающие диалоги и корректно завершаем Activity,
			// чтобы избежать зависания на закрытии сценария
			TestUtils.dismissBlockingDialogsIfAny()
			try {
				scenario.onActivity { it.finish() }
			} catch (_: Throwable) { /* ignore */ }
        }
    }

    @Test
    fun syncHandlesServerError_gracefully() {
        // Сохраняем оригинальный URL
        val originalUrl = com.example.vkbookandroid.network.NetworkModule.getCurrentBaseUrl()
        
        try {
            // Устанавливаем URL, который вернет ошибку сервера (добавляем слэш в конце)
            val errorUrl = "https://httpstat.us/500/"
            val prefs = context.getSharedPreferences("server_settings", Context.MODE_PRIVATE)
            prefs.edit().putString("server_url", errorUrl).commit()
            
            com.example.vkbookandroid.network.NetworkModule.updateBaseUrl(errorUrl)
            
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                // Нажимаем кнопку синхронизации
                onView(withId(R.id.btnSync)).perform(click())
                
                // Даем время на обработку ошибки
                Thread.sleep(3000)
                
                // Проверяем что приложение не падает
                onView(isRoot()).check(matches(isDisplayed()))
            }
        } finally {
            // Восстанавливаем оригинальный URL
            com.example.vkbookandroid.network.NetworkModule.updateBaseUrl(originalUrl)
            val prefs = context.getSharedPreferences("server_settings", Context.MODE_PRIVATE)
            prefs.edit().putString("server_url", originalUrl).commit()
        }
    }

    @Test
    fun syncHandlesRateLimit_gracefully() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Нажимаем кнопку синхронизации несколько раз подряд
            // (может привести к rate limit)
            for (i in 1..3) {
                try {
                    onView(withId(R.id.btnSync)).perform(click())
                    Thread.sleep(500)
                } catch (e: Exception) {
                    // Если кнопка стала неактивной, это нормально
                }
            }
            
            // Даем время на обработку
            Thread.sleep(2000)
            
            // Проверяем что приложение не падает
            onView(isRoot()).check(matches(isDisplayed()))
        }
    }

    @Test
    fun errorMessages_areUserFriendly() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Нажимаем кнопку синхронизации
            onView(withId(R.id.btnSync)).perform(click())
            
            // Даем время на выполнение
            Thread.sleep(2000)
            
            // Проверяем что сообщения об ошибках отображаются
            onView(withId(R.id.tvSyncStatus))
                .check(matches(isDisplayed()))
                .check(matches(not(withText(""))))
        }
    }
}

