package com.msgs.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.msgs.app.engine.Engine

/** Handles Reply and Mark as read on Msgs notifications by firing Google Messages' own actions. */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(NotificationRelay.EXTRA_ID, 0)
        val relayed = NotificationRelay.relayed[id]
        when (intent.action) {
            NotificationRelay.ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(NotificationRelay.REPLY_KEY)?.toString()
                if (text.isNullOrBlank()) return
                val original = relayed?.reply
                var sent = false
                if (original != null) {
                    val inputs = original.remoteInputs
                    if (inputs != null && inputs.isNotEmpty()) {
                        val fill = Intent()
                        val results = Bundle()
                        inputs.forEach { results.putCharSequence(it.resultKey, text) }
                        android.app.RemoteInput.addResultsToIntent(inputs, fill, results)
                        sent = runCatching { original.actionIntent.send(context, 0, fill) }.isSuccess
                    }
                }
                if (!sent && relayed?.conversationId != null) {
                    Engine.sendText(relayed.conversationId, text)
                    sent = true
                }
                NotificationManagerCompat.from(context).cancel(id)
                if (!sent) Engine.toast("Couldn't send that reply. Open the chat and try again.")
            }
            NotificationRelay.ACTION_READ -> {
                relayed?.markRead?.let { action ->
                    try {
                        action.actionIntent.send()
                    } catch (_: PendingIntent.CanceledException) {
                    }
                }
                NotificationManagerCompat.from(context).cancel(id)
            }
        }
    }
}
