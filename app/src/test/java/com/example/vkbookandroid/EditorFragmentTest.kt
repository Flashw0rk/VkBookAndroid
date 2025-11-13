package com.example.vkbookandroid

import org.junit.Test
import org.junit.Assert.*
import java.util.Locale

/**
 * Тесты для функций EditorFragment
 */
class EditorFragmentTest {
    
    @Test
    fun testNormalizeArmatureId_basicCase() {
        val result = normalizeArmatureId("АВ-123")
        assertEquals("ав-123", result)
    }
    
    @Test
    fun testNormalizeArmatureId_withSpaces() {
        val result = normalizeArmatureId("АВ - 123")
        assertEquals("ав-123", result)
    }
    
    @Test
    fun testNormalizeArmatureId_latinToCyrillic() {
        val result = normalizeArmatureId("AB-123")
        assertEquals("ав-123", result)
    }
    
    @Test
    fun testNormalizeArmatureId_mixedDashes() {
        val result = normalizeArmatureId("АВ—123")  // em dash
        assertEquals("ав-123", result)
    }
    
    @Test
    fun testNormalizeArmatureId_multipleSpaces() {
        val result = normalizeArmatureId("АВ  -  123")
        assertEquals("ав-123", result)
    }
    
    private fun normalizeArmatureId(input: String): String {
        var s = input.trim()
        // Унифицируем дефисы
        s = s.replace("\u2010", "-")
            .replace("\u2011", "-")
            .replace("\u2012", "-")
            .replace("\u2013", "-")
            .replace("\u2014", "-")
            .replace("\u2212", "-")
        s = s.replace(Regex("\\s*-\\s*"), "-")
        s = s.replace(Regex("\\s+"), " ")
        s = s.lowercase(Locale.getDefault())
        s = s
            .replace('a', 'а')
            .replace('e', 'е')
            .replace('o', 'о')
            .replace('p', 'р')
            .replace('c', 'с')
            .replace('x', 'х')
            .replace('k', 'к')
            .replace('m', 'м')
            .replace('t', 'т')
            .replace('y', 'у')
            .replace('h', 'н')
            .replace('b', 'в')
        return s
    }
}























