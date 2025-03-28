package com.enderthor.kActions.datatype

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.enderthor.kActions.data.WebhookStatus
import com.enderthor.kActions.extension.KActionsExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class ExecuteWebhookAction : ActionCallback {

    companion object {
        val WEBHOOK_ID = ActionParameters.Key<Int>("webhook_id")
        val WEBHOOK_URL = ActionParameters.Key<String>("webhook_url")
        val CURRENT_STATUS_WEBHOOK = ActionParameters.Key<String>("current_status_webhook")
       // val ACTION_TYPE = ActionParameters.Key<String>("action_type")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {
          /*  val actionType = parameters[ACTION_TYPE]
            if (actionType != "webhook") {
                Timber.w("Tipo de acción incorrecto: $actionType - se esperaba 'webhook'")
                return
            }*/
            Timber.d("DENTRO webhook con ID: ${parameters[WEBHOOK_ID]}")
            Timber.d("Webhook URL: ${parameters[WEBHOOK_URL]}")
            Timber.d("Webhook Status: ${parameters[CURRENT_STATUS_WEBHOOK]}")

            val extension = KActionsExtension.getInstance()
            val webhookId = parameters[WEBHOOK_ID]
            val currentStatusStr = parameters[CURRENT_STATUS_WEBHOOK]

            val currentStatus = currentStatusStr?.let { WebhookStatus.valueOf(it) }
                ?: WebhookStatus.IDLE

            if (currentStatus != WebhookStatus.IDLE && currentStatus != WebhookStatus.FIRST) {
                Timber.d("Webhook en estado $currentStatus, no se procesará la acción")
                return
            }

            if (extension != null && webhookId != null) {
                Timber.d("Ejecutando webhook con ID: $webhookId y Webhook URL: ${parameters[WEBHOOK_URL]} y estado: $currentStatus")
                withContext(Dispatchers.IO) {
                    when (currentStatus) {
                        WebhookStatus.IDLE -> {
                            extension.updateWebhookStatus(
                                webhookId,
                                WebhookStatus.FIRST
                            )
                            extension.scheduleResetToIdle(webhookId, 10_000)
                        }

                        WebhookStatus.FIRST -> {
                            extension.updateWebhookStatus(
                                webhookId,
                                WebhookStatus.EXECUTING
                            )
                            extension.executeWebhookWithStateTransitions(webhookId)
                        }

                        else -> {}
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error ejecutando webhook: ${e.message}")
        }
    }
}