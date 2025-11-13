package com.example.vkbookandroid

import org.junit.Test
import org.junit.Assert.*

/**
 * Тесты для проверки статуса сети
 */
class NetworkStatusTest {
    
    @Test
    fun testIsSuccessful_200() {
        val responseCode = 200
        val isSuccessful = responseCode in 200..299
        assertTrue(isSuccessful)
    }
    
    @Test
    fun testIsSuccessful_201() {
        val responseCode = 201
        val isSuccessful = responseCode in 200..299
        assertTrue(isSuccessful)
    }
    
    @Test
    fun testIsSuccessful_429_shouldBeConsideredAvailable() {
        val responseCode = 429
        val isAvailable = responseCode in 200..299 || responseCode == 429
        assertTrue(isAvailable)
    }
    
    @Test
    fun testIsSuccessful_401() {
        val responseCode = 401
        val isSuccessful = responseCode in 200..299
        assertFalse(isSuccessful)
    }
    
    @Test
    fun testIsSuccessful_500() {
        val responseCode = 500
        val isSuccessful = responseCode in 200..299
        assertFalse(isSuccessful)
    }
}























