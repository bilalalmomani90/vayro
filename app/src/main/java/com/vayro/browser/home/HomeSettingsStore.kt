package com.vayro.browser.home

import android.content.Context

/** تخزين تفضيلات الصفحة الرئيسية (عامة لكل المساحات). */
class HomeSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("vayro_home", Context.MODE_PRIVATE)

    fun load(): HomeSettings = HomeSettings(
        order = HomeLayout.parseOrder(prefs.getString(KEY_ORDER, null)),
        hidden = (prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()).mapNotNull { HomeSection.fromKey(it) }.toSet(),
        cardSize = CardSize.fromKey(prefs.getString(KEY_SIZE, null)),
        background = HomeBackground.fromKey(prefs.getString(KEY_BG, null)),
        showReload = prefs.getBoolean(KEY_RELOAD, true),
        showStar = prefs.getBoolean(KEY_STAR, true)
    )

    fun update(transform: (HomeSettings) -> HomeSettings) {
        val s = transform(load())
        prefs.edit()
            .putString(KEY_ORDER, HomeLayout.serializeOrder(s.order))
            .putStringSet(KEY_HIDDEN, s.hidden.map { it.key }.toSet())
            .putString(KEY_SIZE, s.cardSize.key)
            .putString(KEY_BG, s.background.key)
            .putBoolean(KEY_RELOAD, s.showReload)
            .putBoolean(KEY_STAR, s.showStar)
            .apply()
    }

    private companion object {
        const val KEY_ORDER = "order"
        const val KEY_HIDDEN = "hidden"
        const val KEY_SIZE = "card_size"
        const val KEY_BG = "background"
        const val KEY_RELOAD = "show_reload"
        const val KEY_STAR = "show_star"
    }
}
