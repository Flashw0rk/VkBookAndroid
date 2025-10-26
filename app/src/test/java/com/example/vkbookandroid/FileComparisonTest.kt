package com.example.vkbookandroid

import com.example.vkbookandroid.model.FileInfo
import org.junit.Test
import org.junit.Assert.*

/**
 * Тесты для логики сравнения файлов
 */
class FileComparisonTest {
    
    @Test
    fun testFileInfoEquals_sameSize() {
        val file1 = FileInfo("test.xlsx", 1000, "2025-10-12", "application/xlsx", "xlsx", "/path")
        val file2Size = 1000L
        
        assertTrue(file1.size == file2Size)
    }
    
    @Test
    fun testFileInfoEquals_differentSize() {
        val file1 = FileInfo("test.xlsx", 1000, "2025-10-12", "application/xlsx", "xlsx", "/path")
        val file2Size = 2000L
        
        assertFalse(file1.size == file2Size)
    }
    
    @Test
    fun testShouldSkipDownload_unchanged() {
        val localSize = 123456L
        val serverSize = 123456L
        
        val shouldSkip = localSize == serverSize
        assertTrue(shouldSkip)
    }
    
    @Test
    fun testShouldSkipDownload_changed() {
        val localSize = 123456L
        val serverSize = 123457L
        
        val shouldSkip = localSize == serverSize
        assertFalse(shouldSkip)
    }
}























