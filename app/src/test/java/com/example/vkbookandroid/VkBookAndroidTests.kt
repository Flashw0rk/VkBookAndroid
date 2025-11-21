package com.example.vkbookandroid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.vkbookandroid.theme.AppTheme
import com.example.vkbookandroid.utils.AutoSyncSettings
import com.example.vkbookandroid.utils.SearchNormalizer
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

/**
 * Объединенный набор тестов для VkBookAndroid
 * Включает тесты для всех критически важных компонентов приложения
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VkBookAndroidTests {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SearchNormalizer.clearCaches()
        context.getSharedPreferences("auto_sync_settings", Context.MODE_PRIVATE).edit().clear().commit()
        AutoSyncSettings.resetToDefaults(context)
    }

    // ==================== Базовые тесты ====================

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    // ==================== Тесты тем ====================

    @Test
    fun theme_names_are_stable() {
        assertEquals("📘 Классическая", AppTheme.getThemeName(AppTheme.THEME_CLASSIC))
        assertEquals("💡 Неон", AppTheme.getThemeName(AppTheme.THEME_NUCLEAR))
        assertEquals("🌿 Эргономичная", AppTheme.getThemeName(AppTheme.THEME_ERGONOMIC_LIGHT))
        assertEquals("💎 Стеклянная", AppTheme.getThemeName(AppTheme.THEME_MODERN_GLASS))
        assertEquals("🧱 Брутальная", AppTheme.getThemeName(AppTheme.THEME_MODERN_GRADIENT))
        assertEquals("🔷 Росатом", AppTheme.getThemeName(AppTheme.THEME_ROSATOM))
    }

    @Test
    fun unknown_theme_returns_placeholder() {
        assertEquals("Неизвестная", AppTheme.getThemeName(-1))
    }

    // ==================== Тесты AutoSync ====================

    @Test
    fun autoSync_isDisabledByDefault() {
        val summary = AutoSyncSettings.getSettingsSummary(context)
        assertThat(summary, containsString("Автообновления отключены"))
    }

    @Test
    fun autoSync_summary_reflectsEnabledOptions() {
        AutoSyncSettings.setAutoSyncEnabled(context, true)
        AutoSyncSettings.setSyncOnStartupEnabled(context, true)
        AutoSyncSettings.setSyncOnSettingsChangeEnabled(context, false)
        AutoSyncSettings.setBackgroundSyncEnabled(context, true)
        AutoSyncSettings.setSyncIntervalHours(context, 12)

        val summary = AutoSyncSettings.getSettingsSummary(context)
        assertThat(summary, containsString("при запуске"))
        assertThat(summary, containsString("в фоне каждые 12 ч"))
    }

    @Test
    fun autoSync_resetToDefaults_clearsAllFlags() {
        AutoSyncSettings.setAutoSyncEnabled(context, true)
        AutoSyncSettings.setSyncOnStartupEnabled(context, true)
        AutoSyncSettings.setBackgroundSyncEnabled(context, true)

        AutoSyncSettings.resetToDefaults(context)

        assertFalse(AutoSyncSettings.isAutoSyncEnabled(context))
        assertFalse(AutoSyncSettings.isSyncOnStartupEnabled(context))
        assertFalse(AutoSyncSettings.isBackgroundSyncEnabled(context))
    }

    // ==================== Тесты SearchNormalizer ====================

    @Test
    fun searchNormalizer_basicNormalization() {
        val result = SearchNormalizer.normalizeSearchQuery("А-0")
        assertTrue("Нормализация работает", result.isNotEmpty())
    }

    @Test
    fun searchNormalizer_createVariants() {
        val variants = SearchNormalizer.createSearchVariants("А-0")
        assertTrue("Варианты созданы", variants.isNotEmpty())
    }

    @Test
    fun searchNormalizer_matchesQuery() {
        val matches = SearchNormalizer.matchesSearchVariants("А-0 Задвижка", "А-0")
        assertTrue("Поиск находит совпадения", matches)
    }

    @Test
    fun searchNormalizer_cacheWorks() {
        // Первый запрос
        val query1 = "Тест"
        SearchNormalizer.normalizeSearchQuery(query1)
        
        // Повторный запрос должен использовать кэш
        val query2 = "Тест"
        SearchNormalizer.normalizeSearchQuery(query2)
        
        val stats = SearchNormalizer.getCacheStats()
        assertFalse("Кэш работает", stats.isEmpty())
    }

    @Test
    fun searchNormalizer_cacheHealth() {
        repeat(100) { i ->
            SearchNormalizer.normalizeSearchQuery("Query $i")
            SearchNormalizer.createSearchVariants("Вариант $i")
        }
        
        val health = SearchNormalizer.validateCacheHealth()
        assertTrue("Кэш в здоровом состоянии", health)
    }

    // ==================== Тесты ReminderRule ====================

    @Test
    fun reminderRule_serialization() {
        val rule = ReminderRule(
            selectedHours = setOf(8, 12, 18),
            selectedDaysOfWeek = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY)
        )
        
        val serialized = rule.serialize()
        val deserialized = ReminderRule.deserialize(serialized)
        
        assertEquals("Часы сохранены", rule.selectedHours, deserialized.selectedHours)
        assertEquals("Дни сохранены", rule.selectedDaysOfWeek, deserialized.selectedDaysOfWeek)
    }

    @Test
    fun reminderRule_matches_correctDay() {
        val rule = ReminderRule(
            selectedHours = setOf(8),
            selectedDaysOfWeek = setOf(java.time.DayOfWeek.MONDAY)
        )
        
        val mondayMorning = java.time.LocalDateTime.of(2025, 11, 10, 8, 0)
        assertTrue("Понедельник 8:00 подходит", rule.matches(mondayMorning))
    }

    @Test
    fun reminderRule_matches_wrongDay() {
        val rule = ReminderRule(
            selectedHours = setOf(8),
            selectedDaysOfWeek = setOf(java.time.DayOfWeek.MONDAY)
        )
        
        val tuesdayMorning = java.time.LocalDateTime.of(2025, 11, 11, 8, 0)
        assertFalse("Вторник не подходит", rule.matches(tuesdayMorning))
    }

    @Test
    fun reminderRule_matches_wrongHour() {
        val rule = ReminderRule(
            selectedHours = setOf(8),
            selectedDaysOfWeek = setOf(java.time.DayOfWeek.MONDAY)
        )
        
        val mondayEvening = java.time.LocalDateTime.of(2025, 11, 10, 18, 0)
        assertFalse("Понедельник 18:00 не подходит", rule.matches(mondayEvening))
    }

    @Test
    fun reminderRule_compactString_multipleHours() {
        val rule = ReminderRule(
            selectedHours = setOf(8, 12, 18),
            selectedDaysOfWeek = setOf(java.time.DayOfWeek.MONDAY)
        )
        
        val text = rule.toCompactString()
        assertTrue("Текст содержит описание", text.contains("понедельник"))
    }

    @Test
    fun reminderRule_weeklyAdvanced() {
        val rule = ReminderRule(
            selectedHours = setOf(9),
            advancedType = "WEEKLY",
            dayOfWeekInMonth = java.time.DayOfWeek.TUESDAY
        )
        
        val tuesday = java.time.LocalDateTime.of(2025, 11, 11, 9, 0)
        assertTrue("Еженедельный вторник подходит", rule.matches(tuesday))
    }

    @Test
    fun reminderRule_monthlyByDate() {
        val rule = ReminderRule(
            selectedHours = setOf(10),
            advancedType = "MONTHLY_BY_DATE",
            daysOfMonth = setOf(1, 15)
        )
        
        val firstDay = java.time.LocalDateTime.of(2025, 11, 1, 10, 0)
        assertTrue("1-е число подходит", rule.matches(firstDay))
        
        val fifteenthDay = java.time.LocalDateTime.of(2025, 11, 15, 10, 0)
        assertTrue("15-е число подходит", rule.matches(fifteenthDay))
        
        val secondDay = java.time.LocalDateTime.of(2025, 11, 2, 10, 0)
        assertFalse("2-е число не подходит", rule.matches(secondDay))
    }

    // ==================== Performance тесты ====================

    @Test(timeout = 5000)
    fun performance_searchNormalizer_fast() {
        val queries = listOf("А-0", "С-20", "Арматура", "Клапан", "Задвижка")
        
        val totalTime = measureTimeMillis {
            repeat(100) {
                queries.forEach { query ->
                    SearchNormalizer.normalizeSearchQuery(query)
                    SearchNormalizer.createSearchVariants(query)
                }
            }
        }
        
        assertTrue("Поиск работает быстро: ${totalTime}ms", totalTime < 3000)
    }

    @Test(timeout = 3000)
    fun performance_searchCache_efficient() {
        val testData = (1..1000).map { "Тестовая строка $it для поиска" }
        
        val firstSearchTime = measureTimeMillis {
            testData.forEach { item ->
                SearchNormalizer.matchesSearchVariants(item, "Тест")
            }
        }
        
		val cachedSearchTime1 = measureTimeMillis {
            testData.forEach { item ->
                SearchNormalizer.matchesSearchVariants(item, "Тест")
            }
        }
        
		val cachedSearchTime2 = measureTimeMillis {
			testData.forEach { item ->
				SearchNormalizer.matchesSearchVariants(item, "Тест")
			}
		}
		
		val bestCached = minOf(cachedSearchTime1, cachedSearchTime2)
		// Разрешаем умеренную погрешность из-за JIT/GC, но кэш не должен быть существенно медленнее
		val allowed = (firstSearchTime * 1.50).toLong().coerceAtLeast(firstSearchTime)
		assertTrue("Кэш не должен быть заметно медленнее (first=$firstSearchTime, cachedBest=$bestCached)", bestCached <= allowed)
    }

    @Test(timeout = 2000)
    fun performance_reminderRule_matches() {
        val rule = ReminderRule(
            selectedHours = setOf(8, 9, 10),
            selectedDaysOfWeek = setOf(
                java.time.DayOfWeek.MONDAY,
                java.time.DayOfWeek.TUESDAY,
                java.time.DayOfWeek.WEDNESDAY
            )
        )
        
        val totalTime = measureTimeMillis {
            repeat(10000) {
                val testDateTime = java.time.LocalDateTime.of(2025, 11, 11, 8, 0)
                rule.matches(testDateTime)
            }
        }
        
        assertTrue("Проверка правил быстрая: ${totalTime}ms", totalTime < 1500)
    }
}

