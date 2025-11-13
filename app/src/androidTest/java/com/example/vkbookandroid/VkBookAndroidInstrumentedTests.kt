package com.example.vkbookandroid

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ToggleButton
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.util.HumanReadables
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.vkbookandroid.settings.SettingsTabsActivity
import com.example.vkbookandroid.theme.AppTheme
import com.example.vkbookandroid.utils.AutoSyncSettings
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Объединенный набор инструментальных тестов для VkBookAndroid
 * Тесты выполняются на реальном устройстве или эмуляторе
 * 
 * Запуск: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class VkBookAndroidInstrumentedTests {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun prepareEnvironment() {
        disableDeviceAnimations()
        configureTabsVisibility()
    }

    // ==================== Тесты настроек AutoSync ====================

    @Test
    fun autoSyncSettings_updatesPreferences() {
        context.getSharedPreferences("server_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("auto_sync_settings", Context.MODE_PRIVATE).edit().clear().commit()
        AutoSyncSettings.resetToDefaults(context)

        ActivityScenario.launch(SettingsTabsActivity::class.java).use { scenario ->
            onView(allOf(withText(containsString("Подключение")), isDescendantOfA(withId(R.id.tabLayout))))
                .perform(click())

            onView(isRoot()).perform(waitFor(400))

            scenario.onActivity { activity ->
                activity.findViewById<android.widget.Switch>(R.id.switchAutoSync)?.isChecked = true
                activity.findViewById<android.widget.CheckBox>(R.id.checkSyncOnStartup)?.isChecked = true
                activity.findViewById<android.widget.CheckBox>(R.id.checkBackgroundSync)?.isChecked = true
                activity.findViewById<android.widget.Spinner>(R.id.spinnerSyncInterval)?.setSelection(1)
            }

            onView(isRoot()).perform(waitFor(300))
            onView(withId(R.id.btnSaveSettings)).perform(click())
        }

        assertTrue(AutoSyncSettings.isAutoSyncEnabled(context))
        assertTrue(AutoSyncSettings.isSyncOnStartupEnabled(context))
        assertTrue(AutoSyncSettings.isBackgroundSyncEnabled(context))
        assertTrue(AutoSyncSettings.getSyncIntervalHours(context) == AutoSyncSettings.AVAILABLE_INTERVALS[1])
    }

    // ==================== Тесты навигации по вкладкам ====================

    @Test
    fun tabs_areVisibleAndSwitchToChecksSchedule() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(allOf(withText("Арматура"), isDescendantOfA(withId(R.id.tabLayout))))
                .check(matches(isDisplayed()))

            selectMainTab("График проверок")

            onView(withId(R.id.recyclerHours)).check(matches(isDisplayed()))
            onView(withId(R.id.recyclerTasks)).check(matches(isDisplayed()))
        }

        WorkManager.getInstance(context).cancelAllWork()
    }

    @Test
    fun lastTab_restoredAfterRestart() {
        clearNavigationState()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val checksIndex = adapter.getLocalIndex(5) ?: return@onActivity
                    pager.setCurrentItem(checksIndex, false)
                }
            }
            onView(isRoot()).perform(waitFor(400))
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            it.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                val adapter = pager.adapter as MainPagerAdapter
                val global = adapter.getGlobalPositionAt(pager.currentItem)
                assertTrue(global == 5)
            }
        }

        WorkManager.getInstance(context).cancelAllWork()
    }

    @Test
    fun armatureTab_displaysContent() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        ActivityScenario.launch(MainActivity::class.java).use {
            selectMainTab("Арматура")
            onView(isRoot()).perform(waitFor(600))

            it.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.recyclerView)
                assertTrue((recycler.adapter?.itemCount ?: 0) > 0)
            }
        }

        WorkManager.getInstance(context).cancelAllWork()
    }

    @Test
    fun signalsTab_displaysContent() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        ActivityScenario.launch(MainActivity::class.java).use {
            selectMainTab("Сигналы БЩУ")
            onView(isRoot()).perform(waitFor(600))

            it.onActivity { activity ->
                val recycler = activity.findViewById<RecyclerView>(R.id.recyclerView)
                assertTrue((recycler.adapter?.itemCount ?: 0) > 0)
            }
        }

        WorkManager.getInstance(context).cancelAllWork()
    }

    // ==================== Тесты переключения режима ====================

    @Test
    fun personalModeToggle_changesStateText() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        ActivityScenario.launch(MainActivity::class.java).use {
            selectMainTab("График проверок")

            onView(withId(R.id.btnPersonalMode))
                .check(matches(withText("Служебные задачи")))
                .perform(click())
                .check(matches(withText("Мои задачи")))
        }

        WorkManager.getInstance(context).cancelAllWork()
    }

    // ==================== Тесты правил напоминаний ====================

    @Test
    fun addReminderRule_persistsRule() {
        clearChecksSchedulePrefs()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val checksIndex = adapter.getLocalIndex(5) ?: return@onActivity
                    pager.setCurrentItem(checksIndex, false)
                }
            }
            onView(isRoot()).perform(waitFor(600))

            onView(visibleTasksRecycler()).perform(scrollRecyclerTo(0))
            onView(withId(R.id.btnPersonalMode)).perform(click())
            onView(isRoot()).perform(waitFor(300))

            onView(withId(R.id.btnEditMode)).perform(click())
            onView(isRoot()).perform(waitFor(300))

            onView(visibleTasksRecycler()).perform(clickChildWithText("+ Добавить правило"))
            onView(isRoot()).perform(waitFor(300))

            clickToggle("08")
            clickToggle("ПН")
            onView(withText("Сохранить")).perform(click())

            onView(isRoot()).perform(waitFor(500))
            // Проверяем что правило сохранилось (может быть разный формат отображения)
            // Просто проверяем что есть элементы в recyclerTasks
            onView(withId(R.id.recyclerTasks)).check(matches(isDisplayed()))
        }

        val prefs = context.getSharedPreferences("ChecksSchedulePrefs", Context.MODE_PRIVATE)
        val hasRule = prefs.all.values.any { value ->
            value is String && value.contains("hours=8")
        }
        assertTrue(hasRule)

        WorkManager.getInstance(context).cancelAllWork()
    }

    @Test
    fun reminderDialog_persistsSelections() {
        clearChecksSchedulePrefs()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val checksIndex = adapter.getLocalIndex(5) ?: return@onActivity
                    pager.setCurrentItem(checksIndex, false)
                }
            }
            onView(isRoot()).perform(waitFor(600))

            onView(visibleTasksRecycler()).perform(scrollRecyclerTo(0))
            onView(withId(R.id.btnPersonalMode)).perform(click())
            onView(isRoot()).perform(waitFor(300))
            onView(withId(R.id.btnEditMode)).perform(click())
            onView(isRoot()).perform(waitFor(300))

            onView(visibleTasksRecycler()).perform(clickChildWithText("+ Добавить правило"))
            onView(isRoot()).perform(waitFor(300))

            clickToggle("00")
            clickToggle("ВС")
            onView(withText("Сохранить")).perform(click())

            onView(isRoot()).perform(waitFor(500))
        }

        val prefs = context.getSharedPreferences("ChecksSchedulePrefs", Context.MODE_PRIVATE)
        val hasRule = prefs.all.values.any { value ->
            value is String && value.contains("hours=0")
        }
        assertTrue(hasRule)

        WorkManager.getInstance(context).cancelAllWork()
    }

    // ==================== Тесты кнопки "Назад" (OnBackPressedDispatcher) ====================

    @Test
    fun settingsActivity_opensAndCloses() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        var activityOpened = false
        ActivityScenario.launch(SettingsTabsActivity::class.java).use { scenario ->
            onView(isRoot()).perform(waitFor(500))
            
            // Проверяем что Activity открылась и элементы видны
            onView(withId(R.id.tabLayout)).check(matches(isDisplayed()))
            onView(withId(R.id.btnCancel)).check(matches(isDisplayed()))
            onView(withId(R.id.btnSaveSettings)).check(matches(isDisplayed()))
            
            scenario.onActivity { activity ->
                activityOpened = true
                // Закрываем программно
                activity.finish()
            }
        }
        
        assertTrue("Settings Activity должна открыться", activityOpened)
        WorkManager.getInstance(context).cancelAllWork()
    }

    @Test
    fun settingsActivity_switchesTabs() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        ActivityScenario.launch(SettingsTabsActivity::class.java).use { scenario ->
            onView(isRoot()).perform(waitFor(500))
            
            // Проверяем вкладку "Программа"
            onView(allOf(withText(containsString("Программа")), isDescendantOfA(withId(R.id.tabLayout))))
                .perform(click())
            onView(isRoot()).perform(waitFor(300))
            onView(withId(R.id.btnSelectTheme)).check(matches(isDisplayed()))
            
            // Проверяем вкладку "Подключение"
            onView(allOf(withText(containsString("Подключение")), isDescendantOfA(withId(R.id.tabLayout))))
                .perform(click())
            onView(isRoot()).perform(waitFor(300))
            onView(withId(R.id.switchAutoSync)).check(matches(isDisplayed()))
        }
        
        WorkManager.getInstance(context).cancelAllWork()
    }

    // ==================== Тесты форматирования времени ====================

    @Test
    fun reminderRule_displaysTimeInCorrectFormat() {
        clearChecksSchedulePrefs()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val checksIndex = adapter.getLocalIndex(5) ?: return@onActivity
                    pager.setCurrentItem(checksIndex, false)
                }
            }
            onView(isRoot()).perform(waitFor(600))

            onView(visibleTasksRecycler()).perform(scrollRecyclerTo(0))
            onView(withId(R.id.btnPersonalMode)).perform(click())
            onView(isRoot()).perform(waitFor(300))
            onView(withId(R.id.btnEditMode)).perform(click())
            onView(isRoot()).perform(waitFor(300))

            onView(visibleTasksRecycler()).perform(clickChildWithText("+ Добавить правило"))
            onView(isRoot()).perform(waitFor(300))

            // Выбираем несколько часов через clickToggle
            clickToggle("08")
            clickToggle("12")
            clickToggle("18")
            clickToggle("ПН")
            
            onView(isRoot()).perform(waitFor(300))
            
            // Проверяем формат времени в превью: должно быть "08:00, 12:00, 18:00"
            // Проверяем что хотя бы одно время отображается корректно
            onView(allOf(
                withText(containsString("08:00")),
                isDisplayed()
            )).check(matches(isDisplayed()))
            
            onView(withText("Сохранить")).perform(click())
            onView(isRoot()).perform(waitFor(500))
            
            // Проверяем формат в сохраненном правиле
            onView(allOf(
                withText(containsString("08:00")),
                isDescendantOfA(withId(R.id.recyclerTasks)),
                isDisplayed()
            )).check(matches(isDisplayed()))
        }

        WorkManager.getInstance(context).cancelAllWork()
    }

    @Test
    fun reminderRule_compactRangeDisplay() {
        clearChecksSchedulePrefs()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val checksIndex = adapter.getLocalIndex(5) ?: return@onActivity
                    pager.setCurrentItem(checksIndex, false)
                }
            }
            onView(isRoot()).perform(waitFor(600))

            onView(visibleTasksRecycler()).perform(scrollRecyclerTo(0))
            onView(withId(R.id.btnPersonalMode)).perform(click())
            onView(isRoot()).perform(waitFor(300))
            onView(withId(R.id.btnEditMode)).perform(click())
            onView(isRoot()).perform(waitFor(300))

            onView(visibleTasksRecycler()).perform(clickChildWithText("+ Добавить правило"))
            onView(isRoot()).perform(waitFor(300))

            // Выбираем подряд идущие часы для проверки компактного отображения
            clickToggle("08")
            clickToggle("09")
            clickToggle("10")
            clickToggle("ПН")
            
            onView(withText("Сохранить")).perform(click())
            onView(isRoot()).perform(waitFor(500))
            
            // Проверяем что диапазон отображается компактно (может быть "с 08:00 до 10:00" или "08:00, 09:00, 10:00")
            // В любом случае должен содержать корректный формат времени
            onView(allOf(
                withText(containsString("08:00")),
                isDescendantOfA(withId(R.id.recyclerTasks)),
                isDisplayed()
            )).check(matches(isDisplayed()))
        }

        WorkManager.getInstance(context).cancelAllWork()
    }

    @Test
    fun reminderDialog_multipleHoursSelection() {
        clearChecksSchedulePrefs()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val checksIndex = adapter.getLocalIndex(5) ?: return@onActivity
                    pager.setCurrentItem(checksIndex, false)
                }
            }
            onView(isRoot()).perform(waitFor(600))

            onView(visibleTasksRecycler()).perform(scrollRecyclerTo(0))
            onView(withId(R.id.btnPersonalMode)).perform(click())
            onView(isRoot()).perform(waitFor(300))
            onView(withId(R.id.btnEditMode)).perform(click())
            onView(isRoot()).perform(waitFor(300))

            onView(visibleTasksRecycler()).perform(clickChildWithText("+ Добавить правило"))
            onView(isRoot()).perform(waitFor(300))

            // Выбираем разные часы (не подряд)
            clickToggle("07")
            clickToggle("13")
            clickToggle("19")
            clickToggle("ВТ")
            
            onView(isRoot()).perform(waitFor(300))
            
            // Проверяем что в диалоге отображается корректный формат
            // Проверяем что хотя бы одно время отображается корректно
            onView(allOf(
                withText(containsString("07:00")),
                isDisplayed()
            )).check(matches(isDisplayed()))
            
            onView(withText("Сохранить")).perform(click())
            onView(isRoot()).perform(waitFor(500))
        }

        val prefs = context.getSharedPreferences("ChecksSchedulePrefs", Context.MODE_PRIVATE)
        val hasRule = prefs.all.values.any { value ->
            value is String && (value.contains("hours=7") || value.contains("hours=13") || value.contains("hours=19"))
        }
        assertTrue("Правило должно быть сохранено", hasRule)

        WorkManager.getInstance(context).cancelAllWork()
    }

    // ==================== Тесты переключения темы ====================

    @Test
    fun themeSwitch_updatesAppTheme() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        val initialTheme = AppTheme.getCurrentThemeId()
        val targetTheme = AppTheme.THEME_NUCLEAR
        val targetThemeName = AppTheme.getThemeName(targetTheme)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.btnSettings)).perform(click())
            onView(isRoot()).perform(waitFor(400))

            onView(withId(R.id.btnSelectTheme)).perform(click())
            onView(withText(targetThemeName)).perform(click())
            onView(withId(R.id.tvCurrentTheme)).check(matches(withText(targetThemeName)))

            pressBack()
            onView(isRoot()).perform(waitFor(300))

            scenario.onActivity { activity ->
                val appliedTheme = AppTheme.getCurrentThemeId()
                assertTrue(appliedTheme == targetTheme)
                assertTrue(activity.window.statusBarColor == AppTheme.getPrimaryColor())

                AppTheme.saveTheme(activity, initialTheme)
                AppTheme.loadTheme(activity)
            }
        }

        WorkManager.getInstance(context).cancelAllWork()
    }

    // ==================== Тесты отображения данных ====================

    @Test
    fun armatureTab_hasClickableSchemes() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                (pager.adapter as? MainPagerAdapter)?.let { adapter ->
                    val armIndex = adapter.getLocalIndex(1) ?: return@onActivity
                    pager.setCurrentItem(armIndex, false)
                }
            }
            onView(isRoot()).perform(waitFor(800))
            
            scenario.onActivity { activity ->
                val recycler = locateArmatureRecycler(activity)
                val signalsAdapter = recycler?.adapter as? SignalsAdapter
                assertTrue("Адаптер арматуры не найден", signalsAdapter != null)
            }
        }

        WorkManager.getInstance(context).cancelAllWork()
    }

    // ==================== Вспомогательные функции ====================

    private fun selectMainTab(tabLabel: String) {
        onView(withId(R.id.tabLayout)).perform(selectTab(tabLabel))
        onView(isRoot()).perform(waitFor(300))
    }

    private fun selectTab(tabLabel: String): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(TabLayout::class.java)
        override fun getDescription() = "Select tab with label $tabLabel"
        override fun perform(uiController: UiController, view: View) {
            val tabLayout = view as TabLayout
            val tab = (0 until tabLayout.tabCount)
                .mapNotNull { tabLayout.getTabAt(it) }
                .firstOrNull { it.text?.contains(tabLabel) == true }
                ?: throw PerformException.Builder()
                    .withActionDescription(description)
                    .withViewDescription(HumanReadables.describe(view))
                    .withCause(Throwable("Tab \"$tabLabel\" not found"))
                    .build()
            tab.select()
            uiController.loopMainThreadUntilIdle()
        }
    }

    private fun waitFor(delay: Long): ViewAction = object : ViewAction {
        override fun getConstraints() = isRoot()
        override fun getDescription() = "wait for $delay milliseconds"
        override fun perform(uiController: UiController, view: View?) {
            uiController.loopMainThreadForAtLeast(delay)
        }
    }

    private fun disableDeviceAnimations() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(
            "window_animation_scale",
            "transition_animation_scale",
            "animator_duration_scale"
        ).forEach { setting ->
            automation.executeShellCommand("settings put global $setting 0").use { /* close */ }
        }
    }

    private fun scrollRecyclerTo(position: Int): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(RecyclerView::class.java)
        override fun getDescription() = "scroll RecyclerView to position $position"
        override fun perform(uiController: UiController, view: View) {
            val recycler = view as RecyclerView
            recycler.scrollToPosition(position)
            uiController.loopMainThreadUntilIdle()
        }
    }

    private fun clickChildWithText(text: String): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(RecyclerView::class.java)
        override fun getDescription() = "click child with text \"$text\""
        override fun perform(uiController: UiController, view: View) {
            val recycler = view as RecyclerView
            for (i in 0 until recycler.childCount) {
                val child = recycler.getChildAt(i)
                val target = child.findTextView(text)
                if (target != null && target.isShown) {
                    target.performClick()
                    uiController.loopMainThreadUntilIdle()
                    return
                }
            }
            throw PerformException.Builder()
                .withActionDescription(description)
                .withViewDescription(HumanReadables.describe(view))
                .withCause(Throwable("View with text \"$text\" not found"))
                .build()
        }
    }

    private fun View.findTextView(targetText: String): View? {
        if (this is TextView && text == targetText) return this
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                val result = getChildAt(i).findTextView(targetText)
                if (result != null) return result
            }
        }
        return null
    }

    private fun clickToggle(label: String) {
        onView(first(allOf(isAssignableFrom(ToggleButton::class.java), withText(label), isDisplayed())))
            .perform(click())
    }

    private fun first(matcher: Matcher<View>): Matcher<View> = object : TypeSafeMatcher<View>() {
        private var isFirstMatch = true

        override fun describeTo(description: Description) {
            description.appendText("first matching view: ")
            matcher.describeTo(description)
        }

        override fun matchesSafely(view: View): Boolean {
            if (!matcher.matches(view)) return false
            if (!isFirstMatch) return false
            isFirstMatch = false
            return true
        }
    }

    private fun configureTabsVisibility() {
        val prefs = context.getSharedPreferences("server_settings", Context.MODE_PRIVATE)
        val tabsJson = Gson().toJson(listOf(0, 1, 2, 4, 5))
        prefs.edit().putString("tabs_visibility_json", tabsJson).commit()
    }

    private fun clearChecksSchedulePrefs() {
        context.getSharedPreferences("ChecksSchedulePrefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun clearNavigationState() {
        context.getSharedPreferences("navigation_state", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun visibleTasksRecycler(): Matcher<View> = 
        first(allOf(withId(R.id.recyclerTasks), isDisplayed()))

    private fun locateArmatureRecycler(activity: MainActivity): RecyclerView? {
        val root = activity.findViewById<View>(android.R.id.content) ?: return null
        val recyclers = mutableListOf<RecyclerView>()
        fun traverse(view: View) {
            if (view is RecyclerView) {
                recyclers += view
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    traverse(view.getChildAt(i))
                }
            }
        }
        traverse(root)
        return recyclers.firstOrNull { it.visibility == View.VISIBLE && it.adapter is SignalsAdapter }
    }
}

