package com.msgs.app.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.msgs.app.data.Prefs
import gmbridge.Bridge
import gmbridge.Gmbridge
import gmbridge.Listener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class EngineStatus(
    val paired: Boolean = false,
    val state: String = "disconnected",
    val detail: String = "",
    val phoneResponding: Boolean = true,
    val active: Boolean = true,
)

/**
 * Owns the Go engine and turns its events into flows the screens collect.
 * The engine is the paired "Messages for web" session; Google Messages on the
 * phone does the actual RCS/SMS sending.
 */
object Engine : Listener {
    private const val TAG = "MsgsEngine"
    private const val PAGE = 60L

    lateinit var bridge: Bridge
        private set
    private lateinit var app: Context
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status

    private val _conversations = MutableStateFlow<Map<String, Conversation>>(emptyMap())
    val conversations: StateFlow<Map<String, Conversation>> = _conversations

    private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messages: StateFlow<Map<String, List<Message>>> = _messages

    /** conversation id -> (phone number -> typing expiry in ms) */
    private val _typing = MutableStateFlow<Map<String, Map<String, Long>>>(emptyMap())
    val typing: StateFlow<Map<String, Map<String, Long>>> = _typing

    private val _loading = MutableStateFlow<Set<String>>(emptySet())
    val loading: StateFlow<Set<String>> = _loading

    private val _hasOlder = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val hasOlder: StateFlow<Map<String, Boolean>> = _hasOlder

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts

    private val olderCursors = mutableMapOf<String, String>()
    private val connectLock = Mutex()
    private var disconnectJob: Job? = null
    private var cacheJob: Job? = null
    private var lastTypingSent = 0L

    @Volatile var foreground = false
        private set

    /** The chat currently on screen, so new messages there get marked read. */
    @Volatile var openConversationId: String? = null

    fun init(context: Context) {
        app = context.applicationContext
        val dir = File(app.filesDir, "engine").apply { mkdirs() }
        bridge = Gmbridge.newBridge(dir.absolutePath, this)
        _status.value = EngineStatus(paired = bridge.isPaired())
        if (_status.value.paired) loadCache()
    }

    // ---------------------------------------------------------------- events

    override fun onEvent(kind: String, payload: String) {
        try {
            val o = JSONObject(payload)
            when (kind) {
                "status" -> onStatus(o)
                "conversation" -> upsert(Conversation.from(o))
                "message" -> onMessage(Message.from(o))
                "typing" -> onTyping(o)
                "paired" -> _status.update { it.copy(paired = true) }
                "resync" -> scope.launch {
                    refreshConversations()
                    openConversationId?.let { loadMessages(it) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle $kind", e)
        }
    }

    private fun onStatus(o: JSONObject) {
        val state = o.optString("state")
        _status.update {
            it.copy(
                state = state,
                detail = o.optString("detail"),
                phoneResponding = o.optBoolean("phoneResponding", true),
                active = o.optBoolean("active", true),
                paired = if (state == "unpaired") false else it.paired,
            )
        }
        if (state == "unpaired") clearLocalData()
    }

    private fun upsert(c: Conversation) {
        if (c.id.isEmpty()) return
        _conversations.update { it + (c.id to c) }
        saveCacheSoon()
    }

    private fun onMessage(m: Message) {
        if (m.id.isEmpty() || m.convId.isEmpty()) return
        merge(m.convId, listOf(m))
        val conv = _conversations.value[m.convId]
        if (conv != null && !m.tombstone && !m.deleted && m.ts >= conv.lastTs) {
            val viewing = foreground && openConversationId == m.convId
            upsert(
                conv.copy(
                    lastTs = m.ts,
                    preview = m.previewText(),
                    previewFromMe = m.fromMe,
                    previewSender = if (m.fromMe) "" else m.senderName,
                    previewStatus = m.status,
                    unread = if (m.fromMe) conv.unread else !viewing,
                    latestMessageId = m.id,
                )
            )
        } else if (conv == null && !m.isOld) {
            scope.launch { fetchConversation(m.convId) }
        }
        if (!m.fromMe && !m.isOld && foreground && openConversationId == m.convId) {
            markRead(m.convId, m.id)
        }
        if (!m.fromMe) clearTypingFor(m.convId)
    }

    private fun onTyping(o: JSONObject) {
        val convId = o.optString("conversationId")
        val number = o.optString("number")
        val typing = o.optBoolean("typing")
        _typing.update { all ->
            val m = all[convId].orEmpty().toMutableMap()
            if (typing) m[number] = System.currentTimeMillis() + 15_000 else m.remove(number)
            all + (convId to m)
        }
    }

    private fun clearTypingFor(convId: String) {
        _typing.update { it - convId }
    }

    // ---------------------------------------------------------------- connection

    fun onForeground() {
        foreground = true
        disconnectJob?.cancel()
        if (_status.value.paired) connect()
    }

    fun onBackground() {
        foreground = false
        disconnectJob?.cancel()
        disconnectJob = scope.launch {
            delay(90_000)
            runCatching { bridge.disconnect() }
        }
    }

    fun connect() {
        scope.launch {
            connectLock.withLock {
                if (!bridge.isPaired()) return@withLock
                if (!bridge.isConnected()) {
                    try {
                        bridge.connect()
                    } catch (e: Exception) {
                        report(e)
                        return@withLock
                    }
                }
            }
            refreshConversations()
            openConversationId?.let { loadMessages(it) }
        }
    }

    fun setActiveHere() {
        scope.launch {
            try {
                bridge.setActive()
            } catch (e: Exception) {
                report(e)
            }
        }
    }

    // ---------------------------------------------------------------- pairing

    suspend fun startPairing(cookiesJson: String): String = withContext(Dispatchers.IO) {
        bridge.startPairing(cookiesJson)
    }

    suspend fun finishPairing() = withContext(Dispatchers.IO) {
        bridge.finishPairing()
        _status.update { it.copy(paired = true) }
        refreshConversations()
    }

    fun cancelPairing() {
        scope.launch { runCatching { bridge.cancelPairing() } }
    }

    fun unpair() {
        scope.launch {
            runCatching { bridge.unpair() }
            clearLocalData()
            _status.update { it.copy(paired = false, state = "unpaired") }
        }
    }

    // ---------------------------------------------------------------- conversations

    suspend fun refreshConversations() = withContext(Dispatchers.IO) {
        if (!bridge.isConnected()) return@withContext
        _refreshing.value = true
        try {
            val o = JSONObject(bridge.listConversations(100, 1, ""))
            val arr = o.optJSONArray("conversations") ?: JSONArray()
            val list = (0 until arr.length()).map { Conversation.from(arr.getJSONObject(it)) }
            _conversations.update { old -> old + list.associateBy { it.id } }
            Prefs.seedPins(list.filter { it.pinnedRemote }.map { it.id })
            saveCacheSoon()
            AvatarRepo.prefetch(list)
        } catch (e: Exception) {
            report(e)
        } finally {
            _refreshing.value = false
        }
    }

    private suspend fun fetchConversation(convId: String) {
        try {
            upsert(Conversation.from(JSONObject(bridge.getConversation(convId))))
        } catch (e: Exception) {
            Log.w(TAG, "fetchConversation failed", e)
        }
    }

    fun setArchived(convId: String, archived: Boolean) {
        _conversations.value[convId]?.let { upsert(it.copy(status = if (archived) 2 else 1)) }
        scope.launch {
            try {
                bridge.setArchived(convId, archived)
            } catch (e: Exception) {
                report(e)
            }
        }
    }

    fun deleteConversation(convId: String) {
        _conversations.update { it - convId }
        _messages.update { it - convId }
        Prefs.setPinned(convId, false)
        saveCacheSoon()
        scope.launch {
            try {
                bridge.deleteConversation(convId)
            } catch (e: Exception) {
                report(e)
            }
        }
    }

    fun setMuted(convId: String, muted: Boolean) {
        scope.launch {
            try {
                bridge.setMuted(convId, muted)
                _toasts.tryEmit(if (muted) "Muted" else "Unmuted")
            } catch (e: Exception) {
                report(e)
            }
        }
    }

    suspend fun startConversation(numbers: List<String>, groupName: String = ""): String? = withContext(Dispatchers.IO) {
        try {
            val c = Conversation.from(JSONObject(bridge.startConversation(JSONArray(numbers).toString(), groupName)))
            upsert(c)
            c.id
        } catch (e: Exception) {
            report(e)
            null
        }
    }

    suspend fun listContacts(): List<Participant> = withContext(Dispatchers.IO) {
        try {
            val arr = JSONArray(bridge.listContacts())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Participant(
                    id = o.optString("participantId"),
                    name = o.optString("name"),
                    first = "",
                    number = o.optString("number"),
                    rawNumber = o.optString("rawNumber"),
                    isMe = false,
                    color = o.optString("color"),
                    contactId = o.optString("contactId"),
                )
            }
        } catch (e: Exception) {
            report(e)
            emptyList()
        }
    }

    // ---------------------------------------------------------------- messages

    fun loadMessages(convId: String) {
        scope.launch {
            if (convId in _loading.value || !bridge.isConnected()) return@launch
            _loading.update { it + convId }
            try {
                val o = JSONObject(bridge.fetchMessages(convId, PAGE, ""))
                val list = parseMessages(o)
                o.optJSONObject("cursor")?.let { olderCursors[convId] = it.toString() }
                _hasOlder.update { it + (convId to (list.size >= PAGE)) }
                merge(convId, list)
                list.filter { !it.fromMe }.maxByOrNull { it.ts }?.let { latest ->
                    if (foreground && openConversationId == convId && _conversations.value[convId]?.unread == true) {
                        markRead(convId, latest.id)
                    }
                }
            } catch (e: Exception) {
                report(e)
            } finally {
                _loading.update { it - convId }
            }
        }
    }

    fun loadOlder(convId: String) {
        val cursor = olderCursors[convId] ?: return
        scope.launch {
            if (convId in _loading.value) return@launch
            _loading.update { it + convId }
            try {
                val o = JSONObject(bridge.fetchMessages(convId, PAGE, cursor))
                val list = parseMessages(o)
                val next = o.optJSONObject("cursor")?.toString()
                if (next != null && next != cursor) olderCursors[convId] = next else olderCursors.remove(convId)
                _hasOlder.update { it + (convId to (list.size >= PAGE && next != null)) }
                merge(convId, list)
            } catch (e: Exception) {
                report(e)
            } finally {
                _loading.update { it - convId }
            }
        }
    }

    private fun parseMessages(o: JSONObject): List<Message> {
        val arr = o.optJSONArray("messages") ?: JSONArray()
        return (0 until arr.length()).map { Message.from(arr.getJSONObject(it)) }
    }

    /** Merge messages into a chat, replacing by id and resolving pending sends by tmpId. */
    private fun merge(convId: String, incoming: List<Message>) {
        if (incoming.isEmpty()) return
        _messages.update { all ->
            val byId = LinkedHashMap<String, Message>()
            all[convId].orEmpty().forEach { byId[it.id] = it }
            for (m in incoming) {
                if (m.tmpId.isNotEmpty()) {
                    byId.values.firstOrNull { it.pending && it.tmpId == m.tmpId }?.let { byId.remove(it.id) }
                }
                if (m.deleted) byId.remove(m.id) else byId[m.id] = m
            }
            all + (convId to byId.values.sortedBy { it.ts })
        }
    }

    private fun updateMessage(convId: String, id: String, change: (Message) -> Message) {
        _messages.update { all ->
            val list = all[convId] ?: return@update all
            all + (convId to list.map { if (it.id == id) change(it) else it })
        }
    }

    fun sendText(convId: String, text: String, replyTo: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val tmpId = UUID.randomUUID().toString()
        val localId = "local:$tmpId"
        merge(
            convId,
            listOf(Message(id = localId, convId = convId, fromMe = true, ts = System.currentTimeMillis(), text = trimmed, tmpId = tmpId, pending = true, replyTo = replyTo.orEmpty()))
        )
        bumpPreview(convId, trimmed)
        scope.launch {
            try {
                bridge.sendText(convId, trimmed, replyTo.orEmpty(), tmpId)
            } catch (e: Exception) {
                updateMessage(convId, localId) { it.copy(failed = true) }
                report(e)
            }
        }
    }

    fun retry(message: Message) {
        _messages.update { all -> all + (message.convId to all[message.convId].orEmpty().filter { it.id != message.id }) }
        if (message.text.isNotBlank()) sendText(message.convId, message.text, message.replyTo.ifBlank { null })
    }

    fun sendMedia(context: Context, convId: String, uri: Uri, replyTo: String? = null) {
        val tmpId = UUID.randomUUID().toString()
        val localId = "local:$tmpId"
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        merge(
            convId,
            listOf(Message(id = localId, convId = convId, fromMe = true, ts = System.currentTimeMillis(), tmpId = tmpId, pending = true, localPreview = uri.toString(), text = ""))
        )
        bumpPreview(convId, if (mime.startsWith("video")) "Video" else "Photo")
        scope.launch {
            try {
                var name = "attachment"
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) name = c.getString(0) ?: name
                }
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't read that file")
                if (bytes.size > 100 * 1024 * 1024) error("That file is over 100 MB")
                bridge.sendMedia(convId, bytes, name, mime, replyTo.orEmpty(), tmpId)
            } catch (e: Exception) {
                updateMessage(convId, localId) { it.copy(failed = true) }
                report(e)
            }
        }
    }

    private fun bumpPreview(convId: String, preview: String) {
        _conversations.value[convId]?.let {
            upsert(it.copy(preview = preview, previewFromMe = true, lastTs = System.currentTimeMillis(), previewStatus = 5))
        }
    }

    fun react(message: Message, emoji: String) {
        val existing = message.reactions.firstOrNull { it.mine }
        val action = when {
            existing?.emoji == emoji -> "remove"
            existing != null -> "switch"
            else -> "add"
        }
        // Show it right away; the phone's echo replaces this.
        val updated = message.reactions.filter { !it.mine }.toMutableList()
        if (action != "remove") {
            val same = updated.indexOfFirst { it.emoji == emoji }
            if (same >= 0) updated[same] = updated[same].copy(count = updated[same].count + 1, mine = true)
            else updated.add(Reaction(emoji, 1, true))
        }
        updateMessage(message.convId, message.id) { it.copy(reactions = updated) }
        scope.launch {
            try {
                bridge.sendReaction(message.convId, message.id, emoji, action)
            } catch (e: Exception) {
                report(e)
            }
        }
    }

    fun deleteMessage(message: Message) {
        _messages.update { all -> all + (message.convId to all[message.convId].orEmpty().filter { it.id != message.id }) }
        if (message.pending) return
        scope.launch {
            try {
                bridge.deleteMessage(message.id)
            } catch (e: Exception) {
                report(e)
            }
        }
    }

    fun markRead(convId: String, messageId: String) {
        _conversations.value[convId]?.let { if (it.unread) upsert(it.copy(unread = false)) }
        scope.launch {
            try {
                bridge.markRead(convId, messageId)
            } catch (e: Exception) {
                Log.w(TAG, "markRead failed", e)
            }
        }
    }

    fun markConversationRead(convId: String) {
        val c = _conversations.value[convId] ?: return
        val latest = _messages.value[convId]?.lastOrNull { !it.fromMe && !it.pending }?.id ?: c.latestMessageId
        if (latest.isNotEmpty()) markRead(convId, latest)
    }

    fun userTyping(convId: String) {
        val now = System.currentTimeMillis()
        if (now - lastTypingSent < 5_000) return
        lastTypingSent = now
        scope.launch { runCatching { bridge.setTyping(convId) } }
    }

    // ---------------------------------------------------------------- cache

    private fun cacheFile() = File(app.filesDir, "conversations.json")

    private fun loadCache() {
        runCatching {
            val f = cacheFile()
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            _conversations.value = (0 until arr.length()).map { Conversation.from(arr.getJSONObject(it)) }.associateBy { it.id }
        }
    }

    private fun saveCacheSoon() {
        cacheJob?.cancel()
        cacheJob = scope.launch {
            delay(1500)
            runCatching {
                val arr = JSONArray()
                _conversations.value.values.sortedByDescending { it.lastTs }.take(300).forEach { arr.put(it.toJson()) }
                val tmp = File(app.filesDir, "conversations.json.tmp")
                tmp.writeText(arr.toString())
                tmp.renameTo(cacheFile())
            }
        }
    }

    private fun clearLocalData() {
        _conversations.value = emptyMap()
        _messages.value = emptyMap()
        _typing.value = emptyMap()
        olderCursors.clear()
        runCatching { cacheFile().delete() }
    }

    // ---------------------------------------------------------------- errors

    fun report(e: Throwable) {
        val msg = e.message?.substringAfterLast("Exception: ")?.trim().orEmpty().ifBlank { "Something went wrong" }
        Log.w(TAG, msg, e)
        runCatching { bridge.logLine("warn", msg) }
        _toasts.tryEmit(msg.replaceFirstChar { it.uppercase() })
    }

    fun toast(message: String) {
        _toasts.tryEmit(message)
    }
}
