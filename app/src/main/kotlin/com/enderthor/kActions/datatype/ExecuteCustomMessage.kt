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

class ExecuteCustomMessage : ActionCallback {

    companion object {
        val MESSAGE_ID = ActionParameters.Key<Int>("message_id")
        val MESSAGE_TEXT = ActionParameters.Key<String>("message_text")
        val CURRENT_STATUS_MESSAGE = ActionParameters.Key<String>("current_status_message")

    }

   override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {


            val extension = KActionsExtension.getInstance()
            val messageId = parameters[MESSAGE_ID]
            val currentStatusStr = parameters[CURRENT_STATUS_MESSAGE]
            val messageText = parameters[MESSAGE_TEXT]

            Timber.d("Ejecutando mensaje personalizado: $messageId")
            Timber.d("Texto del mensaje: $messageText")

            val currentStatus = currentStatusStr?.let { StepStatus.valueOf(it) }
                ?: StepStatus.IDLE

            if (currentStatus != StepStatus.IDLE && currentStatus != StepStatus.FIRST) {
                Timber.d("Mensaje en estado $currentStatus, no se procesará la acción")
                return
            }

            if (extension != null && messageId != null && messageText != null) {
                withContext(Dispatchers.IO) {
                    when (currentStatus) {
                        StepStatus.IDLE -> {
                            extension.updateCustomMessageStatus(messageId, StepStatus.FIRST)
                            extension.scheduleResetCustomMessageToIdle(messageId, 10_000)
                        }
                        StepStatus.FIRST -> {
                            extension.updateCustomMessageStatus(messageId, StepStatus.EXECUTING)
                            extension.sendCustomMessageWithStateTransitions(messageId, messageText)
                        }
                        else -> {}
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error enviando mensaje personalizado: ${e.message}")
        }
    }
}