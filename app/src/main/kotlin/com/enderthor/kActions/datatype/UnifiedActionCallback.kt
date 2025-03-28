package com.enderthor.kActions.datatype

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.enderthor.kActions.data.StepStatus
import com.enderthor.kActions.data.WebhookStatus
import com.enderthor.kActions.extension.KActionsExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class UnifiedActionCallback : ActionCallback {
    companion object {
        val ID = ActionParameters.Key<Int>("id")
        val MESSAGE_TEXT = ActionParameters.Key<String>("message_text")
        val WEBHOOK_URL = ActionParameters.Key<String>("webhook_url")
        val STATUS = ActionParameters.Key<String>("status")
        val LAST_CLICK_TIME = ActionParameters.Key<Long>("last_click_time")

        private const val DOUBLE_CLICK_THRESHOLD = 10_000L
        private var lastClickTime: Long = 0
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {
            val currentTime = System.currentTimeMillis()
            val timeDiff = currentTime - lastClickTime
            val isDoubleClick = timeDiff < DOUBLE_CLICK_THRESHOLD
            lastClickTime = currentTime

            val extension = KActionsExtension.getInstance() ?: return
            val id = parameters[ID] ?: return
            val status = parameters[STATUS] ?: return

            if (isDoubleClick) {
                val messageText = parameters[MESSAGE_TEXT] ?: return
                handleCustomMessage(extension, id, messageText, status)
            } else {
                val webhookUrl = parameters[WEBHOOK_URL] ?: return
                handleWebhook(extension, id, webhookUrl, status)
            }

        } catch (e: Exception) {
            Timber.e(e, "Error en UnifiedActionCallback: ${e.message}")
        }
    }

    private suspend fun handleCustomMessage(
        extension: KActionsExtension,
        messageId: Int,
        messageText: String,
        statusStr: String
    ) {
        val currentStatus = try {
            StepStatus.valueOf(statusStr)
        } catch (e: Exception) {
            StepStatus.IDLE
        }

        withContext(Dispatchers.IO) {
            when (currentStatus) {
                StepStatus.IDLE -> {
                    extension.updateCustomMessageStatus(messageId, StepStatus.FIRST)
                    extension.scheduleResetCustomMessageToIdle(messageId, DOUBLE_CLICK_THRESHOLD)
                }
                StepStatus.FIRST -> {
                    extension.updateCustomMessageStatus(messageId, StepStatus.EXECUTING)
                    extension.sendCustomMessageWithStateTransitions(messageId, messageText)
                }
                else -> {}
            }
        }
    }

    private suspend fun handleWebhook(
        extension: KActionsExtension,
        webhookId: Int,
        webhookUrl: String,
        statusStr: String
    ) {
        val currentStatus = try {
            WebhookStatus.valueOf(statusStr)
        } catch (e: Exception) {
            WebhookStatus.IDLE
        }

        withContext(Dispatchers.IO) {
            when (currentStatus) {
                WebhookStatus.IDLE -> {
                    extension.updateWebhookStatus(webhookId, WebhookStatus.FIRST)
                    extension.scheduleResetToIdle(webhookId, DOUBLE_CLICK_THRESHOLD)
                }
                WebhookStatus.FIRST -> {
                    extension.updateWebhookStatus(webhookId, WebhookStatus.EXECUTING)
                    extension.executeWebhookWithStateTransitions(webhookId)
                }
                else -> {}
            }
        }
    }
}