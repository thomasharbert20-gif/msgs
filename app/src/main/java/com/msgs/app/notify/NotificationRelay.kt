package com.msgs.app.notify

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.msgs.app.AppState
import com.msgs.app.MainActivity
import com.msgs.app.R
import com.msgs.app.data.Prefs
import com.msgs.app.engine.Engine
import java.util.concurrent.ConcurrentHashMap

/**
 * Watches Google Messages' notifications, removes them, and posts the same
 * conversation as a Msgs notification. Replies and mark-read go back through
 * Google Messages' own notification actions, so they work even when Msgs isn't
 * connected.
 */
class NotificationRelay : NotificationListenerService() {

    class Relayed(
        val reply: Notification.Action?,
        val markRead: Notification.Action?,
        val conversationId: String?,
        val title: String,
    )

    companion object {
        val relayed = ConcurrentHashMap<Int, Relayed>()
        const val ACTION_REPLY = "com.msgs.app.REPLY"
        const val ACTION_READ = "com.msgs.app.MARK_READ"
        const val EXTRA_ID = "relay_id"
        const val REPLY_KEY = "reply_text"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != Notifications.GOOGLE_MESSAGES || !Prefs.relay.value) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            cancelNotification(sbn.key)
            return
        }
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) ?: return
        if (style.messages.isEmpty()) return

        val title = (style.conversationTitle ?: n.extras.getCharSequence(Notification.EXTRA_TITLE) ?: "Message").toString()
        val convId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) n.shortcutId else null
        val id = (convId ?: title).hashCode()
        val actions = n.actions.orEmpty()
        val reply = actions.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
        val read = actions.firstOrNull { it.remoteInputs.isNullOrEmpty() && it.title?.toString()?.contains("read", ignoreCase = true) == true }
        relayed[id] = Relayed(reply, read, convId, title)

        // Already looking at this chat in Msgs: just clear Google's alert.
        val viewing = AppState.visible && convId != null && Engine.openConversationId == convId
        if (!viewing) post(id, title, style, convId, reply != null, read != null)
        cancelNotification(sbn.key)
    }

    private fun post(
        id: Int,
        title: String,
        source: NotificationCompat.MessagingStyle,
        convId: String?,
        canReply: Boolean,
        canMarkRead: Boolean,
    ) {
        val me = Person.Builder().setName("You").build()
        val style = NotificationCompat.MessagingStyle(me)
            .setGroupConversation(source.isGroupConversation)
            .setConversationTitle(if (source.isGroupConversation) title else null)
        source.messages.takeLast(8).forEach { style.addMessage(it) }

        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conv_id", convId)
            putExtra("conv_title", title)
        }
        val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setColor(0xFF2F5641.toInt())
            .setContentIntent(
                PendingIntent.getActivity(this, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )

        if (canReply) {
            val input = RemoteInput.Builder(REPLY_KEY).setLabel("Reply").build()
            val replyIntent = Intent(this, ReplyReceiver::class.java).setAction(ACTION_REPLY).putExtra(EXTRA_ID, id)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(this, id, replyIntent, flags)
            builder.addAction(
                NotificationCompat.Action.Builder(0, "Reply", pi)
                    .addRemoteInput(input)
                    .setAllowGeneratedReplies(true)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .build()
            )
        }
        if (canMarkRead) {
            val readIntent = Intent(this, ReplyReceiver::class.java).setAction(ACTION_READ).putExtra(EXTRA_ID, id)
            val pi = PendingIntent.getBroadcast(this, id + 1, readIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(
                NotificationCompat.Action.Builder(0, "Mark as read", pi)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .setShowsUserInterface(false)
                    .build()
            )
        }
        runCatching { NotificationManagerCompat.from(this).notify(id, builder.build()) }
    }
}
