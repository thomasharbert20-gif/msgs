package com.msgs.app.engine

import org.json.JSONArray
import org.json.JSONObject

data class Participant(
    val id: String,
    val name: String,
    val first: String,
    val number: String,
    val rawNumber: String,
    val isMe: Boolean,
    val color: String,
    val contactId: String,
) {
    val displayName: String get() = name.ifBlank { number.ifBlank { rawNumber } }

    companion object {
        fun from(o: JSONObject) = Participant(
            id = o.optString("id"),
            name = o.optString("name"),
            first = o.optString("first"),
            number = o.optString("number"),
            rawNumber = o.optString("rawNumber"),
            isMe = o.optBoolean("isMe"),
            color = o.optString("color"),
            contactId = o.optString("contactId"),
        )
    }
}

data class Conversation(
    val id: String,
    val name: String,
    val isGroup: Boolean,
    val unread: Boolean,
    val pinnedRemote: Boolean,
    val lastTs: Long,
    val preview: String,
    val previewFromMe: Boolean,
    val previewSender: String,
    val previewStatus: Int,
    val rcs: Boolean,
    val status: Int,
    val readOnly: Boolean,
    val groupAvatarUrl: String,
    val outgoingId: String,
    val latestMessageId: String,
    val participants: List<Participant>,
) {
    val others: List<Participant> get() = participants.filter { !it.isMe }
    val title: String get() = name.ifBlank { others.joinToString(", ") { it.displayName } }.ifBlank { "Unknown" }
    val other: Participant? get() = if (isGroup) null else others.firstOrNull()

    /** Inbox conversations: active (1) or unknown (0). Archived, spam and deleted are hidden. */
    val isVisible: Boolean get() = status == 0 || status == 1
    val isArchived: Boolean get() = status == 2 || status == 4

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("isGroup", isGroup); put("unread", unread)
        put("pinned", pinnedRemote); put("lastTs", lastTs); put("preview", preview)
        put("previewFromMe", previewFromMe); put("previewSender", previewSender)
        put("previewStatus", previewStatus); put("rcs", rcs); put("status", status)
        put("readOnly", readOnly); put("groupAvatarUrl", groupAvatarUrl)
        put("outgoingId", outgoingId); put("latestMessageId", latestMessageId)
        put("participants", JSONArray().apply {
            participants.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("first", p.first); put("number", p.number)
                    put("rawNumber", p.rawNumber); put("isMe", p.isMe); put("color", p.color)
                    put("contactId", p.contactId)
                })
            }
        })
    }

    companion object {
        fun from(o: JSONObject): Conversation {
            val arr = o.optJSONArray("participants") ?: JSONArray()
            return Conversation(
                id = o.optString("id"),
                name = o.optString("name"),
                isGroup = o.optBoolean("isGroup"),
                unread = o.optBoolean("unread"),
                pinnedRemote = o.optBoolean("pinned"),
                lastTs = o.optLong("lastTs"),
                preview = o.optString("preview"),
                previewFromMe = o.optBoolean("previewFromMe"),
                previewSender = o.optString("previewSender"),
                previewStatus = o.optInt("previewStatus"),
                rcs = o.optBoolean("rcs"),
                status = o.optInt("status"),
                readOnly = o.optBoolean("readOnly"),
                groupAvatarUrl = o.optString("groupAvatarUrl"),
                outgoingId = o.optString("outgoingId"),
                latestMessageId = o.optString("latestMessageId"),
                participants = (0 until arr.length()).map { Participant.from(arr.getJSONObject(it)) },
            )
        }
    }
}

data class Media(
    val mediaId: String,
    val key: String,
    val thumbId: String,
    val thumbKey: String,
    val mime: String,
    val name: String,
    val size: Long,
    val width: Int,
    val height: Int,
) {
    val isImage get() = mime.startsWith("image")
    val isVideo get() = mime.startsWith("video")
    val isAudio get() = mime.startsWith("audio")
    val cacheKey get() = mediaId.ifBlank { thumbId }

    companion object {
        fun from(o: JSONObject) = Media(
            mediaId = o.optString("mediaId"),
            key = o.optString("key"),
            thumbId = o.optString("thumbId"),
            thumbKey = o.optString("thumbKey"),
            mime = o.optString("mime"),
            name = o.optString("name"),
            size = o.optLong("size"),
            width = o.optInt("width"),
            height = o.optInt("height"),
        )
    }
}

data class Reaction(val emoji: String, val count: Int, val mine: Boolean)

data class Message(
    val id: String,
    val convId: String,
    val fromMe: Boolean,
    val ts: Long,
    val participantId: String = "",
    val senderName: String = "",
    val status: Int = 0,
    val statusName: String = "",
    val text: String = "",
    val media: List<Media> = emptyList(),
    val reactions: List<Reaction> = emptyList(),
    val replyTo: String = "",
    val tmpId: String = "",
    val tombstone: Boolean = false,
    val deleted: Boolean = false,
    val failed: Boolean = false,
    val pending: Boolean = false,
    val isOld: Boolean = false,
    val localPreview: String? = null,
) {
    companion object {
        fun from(o: JSONObject): Message {
            val media = o.optJSONArray("media") ?: JSONArray()
            val reactions = o.optJSONArray("reactions") ?: JSONArray()
            return Message(
                id = o.optString("id"),
                convId = o.optString("convId"),
                fromMe = o.optBoolean("fromMe"),
                ts = o.optLong("ts"),
                participantId = o.optString("participantId"),
                senderName = o.optString("senderName"),
                status = o.optInt("status"),
                statusName = o.optString("statusName"),
                text = o.optString("text"),
                media = (0 until media.length()).map { Media.from(media.getJSONObject(it)) },
                reactions = (0 until reactions.length()).map {
                    val r = reactions.getJSONObject(it)
                    Reaction(r.optString("emoji"), r.optInt("count", 1), r.optBoolean("mine"))
                },
                replyTo = o.optString("replyTo"),
                tmpId = o.optString("tmpId"),
                tombstone = o.optBoolean("tombstone"),
                deleted = o.optBoolean("deleted"),
                failed = o.optBoolean("failed"),
                isOld = o.optBoolean("isOld"),
            )
        }
    }
}

private val sendingStatuses = setOf(3, 4, 5, 6, 7, 10, 20)

/** What to show under your last sent message. */
fun Message.deliveryText(): String? = when {
    failed -> "Not sent. Tap to retry"
    pending || status in sendingStatuses -> "Sending…"
    status == 11 -> "Read"
    status == 2 -> "Delivered"
    status == 1 || status == 14 -> "Sent"
    else -> null
}

fun Message.previewText(): String = when {
    text.isNotBlank() -> text
    media.any { it.isImage } -> "Photo"
    media.any { it.isVideo } -> "Video"
    media.any { it.isAudio } -> "Voice message"
    media.isNotEmpty() -> "Attachment"
    else -> ""
}

/** Friendly text for the system notices Google Messages puts in a chat; null hides it. */
fun Message.tombstoneText(): String? = when {
    !tombstone -> null
    statusName.contains("SWITCH_TO_ENCRYPTED") || statusName.contains("TO_E2EE") ||
        statusName.contains("ENCRYPTED_ONE_ON_ONE_RCS_CREATED") -> "Chats are now end-to-end encrypted"
    statusName.contains("SWITCH_TO_RCS") || statusName.contains("ONE_ON_ONE_RCS_CREATED") -> "Now chatting with RCS"
    statusName.contains("SWITCH_TO_TEXT") || statusName.contains("E2EE_TO_TEXT") -> "Now texting over SMS/MMS"
    statusName.contains("GROUP_RENAMED") -> "The group was renamed"
    statusName.contains("PARTICIPANT_JOINED") -> "Someone joined the group"
    statusName.contains("PARTICIPANT_LEFT") || statusName.contains("SELF_LEFT") -> "Someone left the group"
    statusName.contains("RCS_GROUP_CREATED") || statusName.contains("GROUP_CREATED") -> "Group created"
    else -> null
}
