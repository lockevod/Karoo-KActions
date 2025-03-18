package com.enderthor.kActions.datatype

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.enderthor.kActions.extension.KActionsExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.enderthor.kActions.data.WebhookStatus
import timber.log.Timber

class ExecuteWebhookAction : ActionCallback {

    companion object {
        val WEBHOOK_ID = ActionParameters.Key<Int>("webhook_id")
        val WEBHOOK_URL = ActionParameters.Key<String>("webhook_url")
        val CURRENT_STATUS = ActionParameters.Key<String>("current_status")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {
            val extension = KActionsExtension.getInstance()
            val webhookId = parameters[WEBHOOK_ID]
            val currentStatusStr = parameters[CURRENT_STATUS]

            val currentStatus = currentStatusStr?.let { WebhookStatus.valueOf(it) }
                ?: WebhookStatus.IDLE

            if (currentStatus != WebhookStatus.IDLE && currentStatus != WebhookStatus.FIRST) {
                Timber.d("Webhook en estado $currentStatus, no se procesará la acción")
                return
            }

            if (extension != null) {
                withContext(Dispatchers.IO) {
                    when (currentStatus) {
                        WebhookStatus.IDLE -> {

                            extension.updateWebhookState(webhookId, WebhookStatus.FIRST)
                            extension.scheduleResetToIdle(webhookId, 10_000)
                        }
                        WebhookStatus.FIRST -> {

                            extension.updateWebhookState(webhookId, WebhookStatus.EXECUTING)
                            extension.executeWebhookWithStateTransitions(webhookId)
                        }
                        else -> {  }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error ejecutando webhook: ${e.message}")
        }
    }
}