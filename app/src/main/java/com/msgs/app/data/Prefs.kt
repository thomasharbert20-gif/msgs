package com.msgs.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** Small app settings kept in SharedPreferences, exposed as flows for the UI. */
object Prefs {
    const val DEFAULT_BUBBLE = "Racing green"

    private lateinit var sp: SharedPreferences

    private val _pins = MutableStateFlow<List<String>>(emptyList())
    val pins: StateFlow<List<String>> = _pins

    private val _relay = MutableStateFlow(true)
    val relay: StateFlow<Boolean> = _relay

    /** Your bubble color everywhere, unless a chat has its own. */
    private val _bubbleDefault = MutableStateFlow(DEFAULT_BUBBLE)
    val bubbleDefault: StateFlow<String> = _bubbleDefault

    /** Conversation id -> bubble color name, for chats with their own color. */
    private val _bubbles = MutableStateFlow<Map<String, String>>(emptyMap())
    val bubbles: StateFlow<Map<String, String>> = _bubbles

    fun init(context: Context) {
        sp = context.getSharedPreferences("msgs", Context.MODE_PRIVATE)
        _pins.value = sp.getString("pins", "")!!.split('\n').filter { it.isNotBlank() }
        _relay.value = sp.getBoolean("relay", true)
        _bubbleDefault.value = sp.getString("bubbleDefault", DEFAULT_BUBBLE) ?: DEFAULT_BUBBLE
        _bubbles.value = runCatching {
            val o = JSONObject(sp.getString("bubbles", "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
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

    fun setBubbleDefault(name: String) {
        _bubbleDefault.value = name
        sp.edit().putString("bubbleDefault", name).apply()
    }

    /** Give a chat its own bubble color, or pass null to follow the default again. */
    fun setBubble(convId: String, name: String?) {
        val m = _bubbles.value.toMutableMap()
        if (name == null) m.remove(convId) else m[convId] = name
        _bubbles.value = m
        sp.edit().putString("bubbles", JSONObject(m as Map<*, *>).toString()).apply()
    }

    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0)
        set(v) = sp.edit().putLong("lastUpdateCheck", v).apply()

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboardingDone", false)
        set(v) = sp.edit().putBoolean("onboardingDone", v).apply()
}
