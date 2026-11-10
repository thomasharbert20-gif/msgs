package com.msgs.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Small app settings kept in SharedPreferences, exposed as flows for the UI. */
object Prefs {
    private lateinit var sp: SharedPreferences

    private val _palette = MutableStateFlow("Moss")
    val palette: StateFlow<String> = _palette

    private val _pins = MutableStateFlow<List<String>>(emptyList())
    val pins: StateFlow<List<String>> = _pins

    private val _relay = MutableStateFlow(true)
    val relay: StateFlow<Boolean> = _relay

    fun init(context: Context) {
        sp = context.getSharedPreferences("msgs", Context.MODE_PRIVATE)
        _palette.value = sp.getString("palette", "Moss") ?: "Moss"
        _pins.value = sp.getString("pins", "")!!.split('\n').filter { it.isNotBlank() }
        _relay.value = sp.getBoolean("relay", true)
    }

    fun setPalette(name: String) {
        _palette.value = name
        sp.edit().putString("palette", name).apply()
    }

    fun isPinned(id: String) = _pins.value.contains(id)

    fun setPinned(id: String, pinned: Boolean) {
        val list = _pins.value.toMutableList()
        list.remove(id)
        if (pinned) list.add(id)
        savePins(list)
    }

    fun seedPins(ids: List<String>) {
        if (sp.getBoolean("pinsSeeded", false)) return
        sp.edit().putBoolean("pinsSeeded", true).apply()
        if (ids.isNotEmpty()) savePins((_pins.value + ids).distinct())
    }

    private fun savePins(list: List<String>) {
        _pins.value = list
        sp.edit().putString("pins", list.joinToString("\n")).apply()
    }

    fun setRelay(on: Boolean) {
        _relay.value = on
        sp.edit().putBoolean("relay", on).apply()
    }

    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0)
        set(v) = sp.edit().putLong("lastUpdateCheck", v).apply()

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboardingDone", false)
        set(v) = sp.edit().putBoolean("onboardingDone", v).apply()
}
