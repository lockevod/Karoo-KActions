package com.enderthor.kActions.datatype

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.enderthor.kActions.data.StepStatus
import com.enderthor.kActions.extension.KActionsExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

class UnifiedActionCallback : ActionCallback {
    companion object {
        val MESSAGE_TEXT = ActionParameters.Key<String>("message_text")
        val STATUS = ActionParameters.Key<String>("status")
        val WEBHOOK_ENABLED = ActionParameters.Key<Boolean>("webhook_enabled")
        val WEBHOOK_URL = ActionParameters.Key<String>("webhook_url")

        private const val ACTION_THRESHOLD = 10_000L      // 10 seg para decidir qué acción
        private const val CANCEL_THRESHOLD = 20_000L      // 20 seg para cancelar
        private var lastClickTime: Long = 0
        private var pendingAction: Boolean = false
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {
            val currentTime = System.currentTimeMillis()
            val timeDiff = currentTime - lastClickTime
            val isDoubleClick = pendingAction && timeDiff < ACTION_THRESHOLD

            val extension = KActionsExtension.getInstance() ?: return
            val statusString = parameters[STATUS] ?: return
            val currentStatus = try { StepStatus.valueOf(statusString) } catch (e: Exception) { StepStatus.IDLE }
            val messageText = parameters[MESSAGE_TEXT] ?: ""
            val webhookEnabled = parameters[WEBHOOK_ENABLED] == true
            val webhookUrl = parameters[WEBHOOK_URL] ?: ""

            // Si ya está ejecutando, no hacer nada
            if (currentStatus == StepStatus.EXECUTING) {
                Timber.d("Acción ya en ejecución, ignorando clic")
                return
            }

            // Si hay error/éxito, resetear al estado IDLE
            if (currentStatus == StepStatus.ERROR || currentStatus == StepStatus.SUCCESS) {
                resetState(extension)
                return
            }

            // Si es el primer clic
            if (!pendingAction) {
                lastClickTime = currentTime
                pendingAction = true

                // Actualizar estados a FIRST
                extension.updateCustomMessageStatus(0, StepStatus.FIRST)
                extension.updateWebhookStatus(0, StepStatus.FIRST)

                // Programar acción webhook si no hay segundo clic
                withContext(Dispatchers.IO) {
                    delay(ACTION_THRESHOLD)
                    if (pendingAction && webhookEnabled && webhookUrl.isNotEmpty()) {
                        Timber.d("Ejecutando webhook después de espera")
                        executeWebhook(extension, 0)
                    } else if (pendingAction) {
                        Timber.d("No hay webhook disponible o ya se procesó otra acción")
                        resetState(extension)
                    }
                }

                // Programar cancelación automática
                withContext(Dispatchers.IO) {
                    delay(CANCEL_THRESHOLD)
                    if (pendingAction) {
                        Timber.d("Cancelando acción por tiempo excedido")
                        resetState(extension)
                    }
                }

                return
            }

            // Si es el segundo clic dentro del umbral
            if (isDoubleClick && messageText.isNotEmpty()) {
                pendingAction = false
                Timber.d("Ejecutando mensaje personalizado")
                executeMessage(extension, messageText)
            }

        } catch (e: Exception) {
            Timber.e(e, "Error en UnifiedActionCallback: ${e.message}")
        }
    }

    private suspend fun resetState(extension: KActionsExtension) {
        extension.updateCustomMessageStatus(0, StepStatus.IDLE)
        extension.updateWebhookStatus(0, StepStatus.IDLE)
        pendingAction = false
    }
    private suspend fun executeMessage(extension: KActionsExtension, messageText: String) {
        withContext(Dispatchers.IO) {
            extension.updateCustomMessageStatus(0, StepStatus.EXECUTING)
            extension.sendCustomMessageWithStateTransitions(0, messageText)
        }
    }

    private suspend fun executeWebhook(extension: KActionsExtension, webhookId: Int) {
        withContext(Dispatchers.IO) {
            // Actualizar estado del webhook, no solo del mensaje personalizado
            extension.updateWebhookStatus(webhookId, StepStatus.EXECUTING)
            extension.updateCustomMessageStatus(0, StepStatus.EXECUTING) // Mantener para UI
            extension.executeWebhookWithStateTransitions(webhookId)
            pendingAction = false
        }
    }

}