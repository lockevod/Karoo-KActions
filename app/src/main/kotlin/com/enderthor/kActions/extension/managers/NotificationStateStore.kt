package com.enderthor.kActions.extension.managers

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

class NotificationStateStore(context: Context) {
    private val preferences = context.getSharedPreferences("notification_states", Context.MODE_PRIVATE)
    private val sessionId: String = UUID.randomUUID().toString()

    init {
        preferences.edit {
            putString("last_session_id", sessionId)
        }
    }

    fun isNewSession(eventType: String): Boolean {
        val lastEventSessionId = preferences.getString("session_${eventType}", "")
        return lastEventSessionId != sessionId
    }

    fun saveNotificationTime(eventType: String, timestamp: Long) {
        preferences.edit {
            putLong("notification_$eventType.timestamp", timestamp)
            putString("session_${eventType}", sessionId)
        }
    }

    fun getLastNotificationTime(eventType: String): Long {
        return preferences.getLong("notification_$eventType.timestamp", 0L)
    }

    fun saveSentMessage(stateKey: String) {
        val sentMessages = getSentMessages().toMutableSet()
        sentMessages.add(stateKey)

        if (sentMessages.size > 10) {
            val keysToRemove = sentMessages.toList()
                .sortedBy { it.split("-").lastOrNull()?.toLongOrNull() ?: 0L }
                .take(sentMessages.size - 10)
            sentMessages.removeAll(keysToRemove.toSet())
        }

        preferences.edit {
            putStringSet("sent_messages", sentMessages)
        }
    }

    fun getSentMessages(): Set<String> {
        return preferences.getStringSet("sent_messages", emptySet()) ?: emptySet()
    }

    fun hasSentMessage(stateKey: String): Boolean {
        return getSentMessages().contains(stateKey)
    }
}