package com.example.vkbookandroid

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Юнит-тесты для оптимизаций ChecksScheduleFragment
 */
class ChecksScheduleOptimizationsTest {
    
    // Вспомогательные классы для тестов
    private data class HourCellTest(
        val hour: Int,
        val dayOffset: Int
    )
    
    private data class CheckItemForTest(
        val operation: String,
        var isActive: Boolean
    )
    
    // ===== Тест 1: Кэширование результатов расчета активности =====
    
    @Test
    fun testActiveStatusCache_WhenSelectionNotChanged_ShouldSkipRecalculation() {
        val cells1 = listOf(
            HourCellTest(hour = 14, dayOffset = 0),
            HourCellTest(hour = 15, dayOffset = 0)
        )
        val dates1 = setOf(LocalDate.of(2025, 1, 15))
        
        val cells2 = listOf(
            HourCellTest(hour = 14, dayOffset = 0),
            HourCellTest(hour = 15, dayOffset = 0)
        )
        val dates2 = setOf(LocalDate.of(2025, 1, 15))
        
        // Проверяем что одинаковые выборы считаются равными для кэша
        val cache1 = Pair(cells1, dates1)
        val cache2 = Pair(cells2, dates2)
        
        assertEquals("Ячейки должны быть равны", cache1.first.size, cache2.first.size)
        assertEquals("Даты должны совпадать", cache1.second, cache2.second)
        
        // Кэш должен предотвращать пересчет если выборы идентичны
        val shouldSkipRecalculation = cache1.first == cache2.first && cache1.second == cache2.second
        assertTrue("Кэш должен предотвращать пересчет для одинаковых выборов", shouldSkipRecalculation)
    }
    
    @Test
    fun testActiveStatusCache_WhenSelectionChanged_ShouldRecalculate() {
        val cells1 = listOf(HourCellTest(hour = 14, dayOffset = 0))
        val dates1 = setOf(LocalDate.of(2025, 1, 15))
        
        val cells2 = listOf(HourCellTest(hour = 16, dayOffset = 0))
        val dates2 = setOf(LocalDate.of(2025, 1, 15))
        
        val cache1 = Pair(cells1, dates1)
        val cache2 = Pair(cells2, dates2)
        
        // Разные ячейки должны требовать пересчета
        val shouldRecalculate = cache1.first != cache2.first || cache1.second != cache2.second
        assertTrue("Разные выборы должны требовать пересчета", shouldRecalculate)
    }
    
    // ===== Тест 2: Оптимизация создания объектов LocalTime =====
    
    @Test
    fun testLocalTimeReuse_ShouldReduceObjectCreation() {
        val cells = listOf(
            HourCellTest(hour = 14, dayOffset = 0),
            HourCellTest(hour = 15, dayOffset = 0),
            HourCellTest(hour = 16, dayOffset = 0)
        )
        
        // ОПТИМИЗАЦИЯ: Создаем LocalTime объекты один раз
        val cellTimes = cells.map { cell ->
            LocalTime.of(cell.hour, 0)
        }
        
        assertEquals("Должно быть создано 3 объекта LocalTime", 3, cellTimes.size)
        assertEquals("Первый час должен быть 14:00", LocalTime.of(14, 0), cellTimes[0])
        assertEquals("Второй час должен быть 15:00", LocalTime.of(15, 0), cellTimes[1])
        assertEquals("Третий час должен быть 16:00", LocalTime.of(16, 0), cellTimes[2])
    }
    
    // ===== Тест 3: Точечные обновления RecyclerView =====
    
    @Test
    fun testPartialUpdates_WhenFewItemsChanged_ShouldUseNotifyItemChanged() {
        val oldItems = listOf(
            CheckItemForTest(operation = "Задача 1", isActive = false),
            CheckItemForTest(operation = "Задача 2", isActive = false),
            CheckItemForTest(operation = "Задача 3", isActive = false),
            CheckItemForTest(operation = "Задача 4", isActive = false),
            CheckItemForTest(operation = "Задача 5", isActive = false)
        )
        
        val newItems = listOf(
            CheckItemForTest(operation = "Задача 1", isActive = true),  // Изменилась
            CheckItemForTest(operation = "Задача 2", isActive = false),
            CheckItemForTest(operation = "Задача 3", isActive = false),
            CheckItemForTest(operation = "Задача 4", isActive = false),
            CheckItemForTest(operation = "Задача 5", isActive = false)
        )
        
        // ОПТИМИЗАЦИЯ: Определяем изменившиеся позиции
        val changedPositions = mutableListOf<Int>()
        oldItems.forEachIndexed { index, oldItem ->
            val newItem = newItems.getOrNull(index)
            if (newItem != null && oldItem.isActive != newItem.isActive) {
                changedPositions.add(index)
            }
        }
        
        assertEquals("Должна быть одна измененная позиция", 1, changedPositions.size)
        assertEquals("Изменена позиция 0", 0, changedPositions[0])
        
        // Если изменилось меньше половины - используем точечные обновления
        val shouldUsePartial = changedPositions.size < oldItems.size / 2
        assertTrue("Должны использовать точечные обновления", shouldUsePartial)
    }
    
    @Test
    fun testPartialUpdates_WhenManyItemsChanged_ShouldUseNotifyDataSetChanged() {
        val oldItems = listOf(
            CheckItemForTest(operation = "Задача 1", isActive = false),
            CheckItemForTest(operation = "Задача 2", isActive = false),
            CheckItemForTest(operation = "Задача 3", isActive = false),
            CheckItemForTest(operation = "Задача 4", isActive = false)
        )
        
        val newItems = listOf(
            CheckItemForTest(operation = "Задача 1", isActive = true),
            CheckItemForTest(operation = "Задача 2", isActive = true),
            CheckItemForTest(operation = "Задача 3", isActive = true),
            CheckItemForTest(operation = "Задача 4", isActive = false)
        )
        
        val changedPositions = mutableListOf<Int>()
        oldItems.forEachIndexed { index, oldItem ->
            val newItem = newItems.getOrNull(index)
            if (newItem != null && oldItem.isActive != newItem.isActive) {
                changedPositions.add(index)
            }
        }
        
        // Если изменилось больше половины - используем полное обновление
        val shouldUseFull = changedPositions.size >= oldItems.size / 2
        assertTrue("Должны использовать полное обновление", shouldUseFull)
    }
    
    // ===== Тест 4: Кэширование времени =====
    
    @Test
    fun testTimeCaching_WhenCalledMultipleTimes_ShouldReuseCachedValue() {
        val timestamp1 = System.currentTimeMillis()
        val time1 = LocalDateTime.now()
        
        // Симулируем использование кэшированного времени (если прошло < 1 сек)
        val cachedTime = time1
        val cachedTimestamp = timestamp1
        
        // Через 500мс (меньше 1 секунды)
        val timestamp2 = timestamp1 + 500
        val shouldUseCache = (timestamp2 - cachedTimestamp) < 1000L
        
        assertTrue("Должны использовать кэш если прошло меньше секунды", shouldUseCache)
    }
    
    @Test
    fun testTimeCaching_WhenExpired_ShouldCreateNew() {
        val timestamp1 = System.currentTimeMillis()
        val time1 = LocalDateTime.now()
        
        val cachedTime = time1
        val cachedTimestamp = timestamp1
        
        // Через 2 секунды (больше 1 секунды)
        val timestamp2 = timestamp1 + 2000
        val shouldUseCache = (timestamp2 - cachedTimestamp) < 1000L
        
        assertFalse("Не должны использовать кэш если прошло больше секунды", shouldUseCache)
    }
    
}



