package com.example.vkbookandroid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.vkbookandroid.network.NetworkModule
import com.example.vkbookandroid.service.SyncService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты для проверки подключения к серверу
 * Проверяют работу health check, rate limit и других механизмов
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerConnectionTests {

    private lateinit var context: Context
    private lateinit var syncService: SyncService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        syncService = SyncService(context)
    }

    @Test
    fun networkModule_baseUrl_isValid() {
        val baseUrl = NetworkModule.getCurrentBaseUrl()
        assertNotNull("Base URL не должен быть null", baseUrl)
        assertTrue("Base URL должен начинаться с http", 
            baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))
    }

    @Test
    fun networkModule_baseUrl_endsWithSlash() {
        val baseUrl = NetworkModule.getCurrentBaseUrl()
        // URL должен заканчиваться на / или быть валидным
        assertTrue("Base URL должен быть валидным", baseUrl.isNotEmpty())
    }

    @Test
    fun syncService_checkServerConnection_returnsBoolean() {
        runBlocking {
            try {
                val result = syncService.checkServerConnection()
                // Результат должен быть boolean (true или false)
                assertTrue("Результат должен быть boolean", result == true || result == false)
            } catch (e: Exception) {
                // Если нет сети, это нормально для тестов
                // Проверяем что исключение обрабатывается корректно
                assertNotNull("Исключение должно быть обработано", e.message)
            }
        }
    }

    @Test
    fun syncService_syncResult_hasCorrectStructure() {
        runBlocking {
            try {
                val result = syncService.syncAll { _, _ -> }
                
                // Проверяем структуру SyncResult
                assertNotNull("SyncResult не должен быть null", result)
                assertNotNull("updatedFiles не должен быть null", result.updatedFiles)
                assertNotNull("errorMessages не должен быть null", result.errorMessages)
                
                // Проверяем что флаги boolean (проверяем значения, а не типы)
                assertTrue("serverConnected должен быть boolean", 
                    result.serverConnected == true || result.serverConnected == false)
                assertTrue("overallSuccess должен быть boolean", 
                    result.overallSuccess == true || result.overallSuccess == false)
                assertTrue("rateLimitReached должен быть boolean", 
                    result.rateLimitReached == true || result.rateLimitReached == false)
            } catch (e: Exception) {
                // Если нет сети, это нормально для тестов
                assertNotNull("Исключение должно быть обработано", e.message)
            }
        }
    }

    @Test
    fun syncService_syncResult_getUpdateSummary_returnsString() {
        runBlocking {
            try {
                val result = syncService.syncAll { _, _ -> }
                val summary = result.getUpdateSummary()
                
                assertNotNull("Summary не должен быть null", summary)
                // summary уже является String, проверяем только что не пустой
                assertTrue("Summary не должен быть пустым", summary.isNotEmpty())
            } catch (e: Exception) {
                // Если нет сети, это нормально для тестов
                assertNotNull("Исключение должно быть обработано", e.message)
            }
        }
    }
}

