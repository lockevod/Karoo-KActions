package com.enderthor.kActions.datatype

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.enderthor.kActions.data.StepStatus
import com.enderthor.kActions.extension.KActionsExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            Timber.d("DENTRO webhook con ID: ${parameters[WEBHOOK_ID]}")
            Timber.d("Webhook URL: ${parameters[WEBHOOK_URL]}")
            Timber.d("Webhook Status: ${parameters[CURRENT_STATUS]}")

            val extension = KActionsExtension.getInstance()
            val webhookId = parameters[WEBHOOK_ID]
            val currentStatusStr = parameters[CURRENT_STATUS]

            val currentStatus = currentStatusStr?.let { StepStatus.valueOf(it) }
                ?: StepStatus.IDLE

            if (currentStatus != StepStatus.IDLE && currentStatus != StepStatus.FIRST) {
                Timber.d("Webhook en estado $currentStatus, no se procesará la acción")
                return
            }

            if (extension != null && webhookId != null) {
                Timber.d("Ejecutando webhook con ID: $webhookId y Webhook URL: ${parameters[WEBHOOK_URL]} y estado: $currentStatus")
                withContext(Dispatchers.IO) {
                    when (currentStatus) {
                        StepStatus.IDLE -> {
                            extension.updateWebhookStatus(
                                webhookId,
                                StepStatus.FIRST
                            )
                            extension.scheduleResetToIdle(webhookId, 10_000)
                        }

                        StepStatus.FIRST -> {
                            extension.updateWebhookStatus(
                                webhookId,
                                StepStatus.EXECUTING
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