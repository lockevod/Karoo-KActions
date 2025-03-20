package com.enderthor.kActions.extension.managers

import android.content.Context
import androidx.core.content.edit

class WebhookStateStore(context: Context) {
    private val preferences = context.getSharedPreferences("webhook_states", Context.MODE_PRIVATE)

    fun saveWebhookState(webhookId: Int, status: String, targetTime: Long) {
        preferences.edit {
            putString("webhook_$webhookId.status", status)
            putLong("webhook_$webhookId.targetTime", targetTime)
            putLong("webhook_$webhookId.startTime", System.currentTimeMillis())
        }
    }

    fun getWebhookState(webhookId: Int): Pair<String?, Long> {
        val status = preferences.getString("webhook_$webhookId.status", null)
        val targetTime = preferences.getLong("webhook_$webhookId.targetTime", 0L)
        return Pair(status, targetTime)
    }

    fun clearWebhookState(webhookId: Int) {
        preferences.edit {
            remove("webhook_$webhookId.status")
            remove("webhook_$webhookId.targetTime")
            remove("webhook_$webhookId.startTime")
        }
    }
}