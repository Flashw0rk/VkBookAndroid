package com.example.vkbookandroid

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.allOf

object TestUtils {
	fun dismissBlockingDialogsIfAny() {
		// Пытаемся закрыть стандартные диалоги, если они перекрывают экран
		try {
			onView(allOf(withText("OK"), isDisplayed())).perform(click())
		} catch (_: Throwable) { /* ignore */ }
		try {
			onView(allOf(withText("Очистить хеши"), isDisplayed())).perform(click())
		} catch (_: Throwable) { /* ignore */ }
		try {
			onView(allOf(withText("Отмена"), isDisplayed())).perform(click())
		} catch (_: Throwable) { /* ignore */ }
		// НЕ используем pressBack() - он может закрыть Activity!
	}
}



