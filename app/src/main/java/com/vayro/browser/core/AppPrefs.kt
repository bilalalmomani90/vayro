package com.vayro.browser.core

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** تفضيلات التطبيق المحلية: وضع المظهر فقط في هذه المرحلة (اللغة يحفظها AppCompat). */
object AppPrefs {
    private const val FILE = "vayro_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    fun themeMode(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun setThemeMode(context: Context, mode: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME_MODE, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode) // تطبيق فوري
    }
}
