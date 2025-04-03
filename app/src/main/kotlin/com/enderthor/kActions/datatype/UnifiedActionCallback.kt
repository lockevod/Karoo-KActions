package com.enderthor.kActions.datatype

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.enderthor.kActions.data.StepStatus
import com.enderthor.kActions.extension.KActionsExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

class UnifiedActionCallback : ActionCallback {
    companion object {
        val MESSAGE_TEXT = ActionParameters.Key<String>("message_text")
        val STATUS = ActionParameters.Key<String>("status")
        val WEBHOOK_ENABLED = ActionParameters.Key<Boolean>("webhook_enabled")
        val WEBHOOK_URL = ActionParameters.Key<String>("webhook_url")

        private const val ACTION_THRESHOLD = 8_000L
        private const val CANCEL_THRESHOLD = 15_000L
        private const val EXECUTING_TIMEOUT = 30_000L // Timeout de 30 segundos para EXECUTING

        private var lastClickTime: Long = 0
        private var executingStartTime: Long = 0 // Tiempo cuando entró en EXECUTING
        private var consecutiveClicks: Int = 0 // Contador para clicks consecutivos en EXECUTING
        private var pendingAction: Boolean = false
        private var timerJob: Job? = null
        private var executingTimeoutJob: Job? = null
        private var readyForActionJob: Job? = null
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {
            val extension = KActionsExtension.getInstance() ?: return
            val statusString = parameters[STATUS] ?: return
            val currentStatus = try { StepStatus.valueOf(statusString) } catch (e: Exception) { StepStatus.IDLE }
            val messageText = parameters[MESSAGE_TEXT] ?: ""
            val webhookUrl = parameters[WEBHOOK_URL] ?: ""
            val webhookEnabled = parameters[WEBHOOK_ENABLED] == true
            val currentTime = System.currentTimeMillis()
            val timeDiff = currentTime - lastClickTime

            Timber.d("UnifiedActionCallback: $currentStatus, webhookEnabled: $webhookEnabled, webhookUrl: $webhookUrl")

            // Cancelar cualquier temporizador pendiente
            timerJob?.cancel()
            timerJob = null

            // Si está en EXECUTING
            if (currentStatus == StepStatus.EXECUTING) {
                // Incrementar contador de clicks en EXECUTING
                consecutiveClicks++

                // Verificar timeout (si lleva más de 60 segundos en EXECUTING)
                if (executingStartTime > 0 && currentTime - executingStartTime > EXECUTING_TIMEOUT) {
                    Timber.d("Timeout de EXECUTING superado, restableciendo estado")
                    resetState(extension)
                    return
                }

                // Reset forzado si hay 3 clicks consecutivos rápidos en EXECUTING
                if (consecutiveClicks >= 3 && timeDiff < 2000) {
                    Timber.d("Reset forzado por múltiples clicks en EXECUTING")
                    resetState(extension)
                    return
                }

                // Lógica existente para verificar si hay acciones válidas
                val webhookActive = webhookEnabled && webhookUrl.isNotEmpty()
                val messageActive = messageText.isNotEmpty()

                if (!webhookActive && !messageActive) {
                    resetState(extension)
                }
                return
            } else {
                // Reiniciar contador si no estamos en EXECUTING
                consecutiveClicks = 0
            }

            // Si hay error/éxito, resetear
            if (currentStatus == StepStatus.ERROR || currentStatus == StepStatus.SUCCESS) {
                resetState(extension)
                return
            }

            when (currentStatus) {
                StepStatus.IDLE -> {
                    // Primera pulsación: cambiar a FIRST
                    extension.updateCustomMessageStatus(0, StepStatus.FIRST)
                    extension.updateWebhookStatus(0, StepStatus.FIRST)

                    lastClickTime = currentTime
                    pendingAction = true

                    // Timer para resetear después de 15s
                    timerJob = CoroutineScope(Dispatchers.IO).launch {
                        try {
                            delay(CANCEL_THRESHOLD)
                            resetState(extension)
                        } catch (e: CancellationException) {
                            Timber.d("Timer cancelado")
                        }
                    }
                }

                StepStatus.FIRST -> {
                    val timeDiff = currentTime - lastClickTime

                    if (timeDiff < ACTION_THRESHOLD) {
                        // Segunda pulsación rápida: ejecutar mensaje
                        if (messageText.isNotEmpty()) {
                            Timber.d("Ejecutando mensaje personalizado")
                            executingStartTime = currentTime
                            extension.updateCustomMessageStatus(0, StepStatus.EXECUTING)
                            // No actualizamos el estado del webhook
                            executeMessage(extension, messageText)
                        } else {
                            Timber.d("No hay mensaje disponible")
                            resetState(extension)
                        }
                    } else if (timeDiff >= ACTION_THRESHOLD && timeDiff <= CANCEL_THRESHOLD) {
                        // Segunda pulsación después de 8s: confirmar webhook
                        if (webhookUrl.isNotEmpty() && webhookEnabled) {
                            Timber.d("Confirmando webhook")
                            extension.updateWebhookStatus(0, StepStatus.CONFIRM)
                            extension.updateCustomMessageStatus(0, StepStatus.CONFIRM)

                            // Timer para resetear si no hay tercera pulsación
                            timerJob = CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    delay(CANCEL_THRESHOLD)
                                    resetState(extension)
                                } catch (e: CancellationException) {
                                    Timber.d("Timer cancelado")
                                }
                            }
                        } else {
                            Timber.d("No hay webhook disponible")
                            resetState(extension)
                        }
                    } else {
                        resetState(extension)
                    }
                }

                StepStatus.CONFIRM -> {
                    // Tercera pulsación: ejecutar webhook
                    if (webhookUrl.isNotEmpty()) {
                        Timber.d("Ejecutando webhook")
                        executingStartTime = currentTime
                        executeWebhook(extension, 0)
                    } else {
                        resetState(extension)
                    }
                }

                else -> resetState(extension)
            }

            lastClickTime = currentTime

            // Configurar timeout para EXECUTING si entramos en ese estado
            if ((currentStatus == StepStatus.FIRST && timeDiff < ACTION_THRESHOLD && messageText.isNotEmpty()) ||
                (currentStatus == StepStatus.CONFIRM && webhookUrl.isNotEmpty())) {

                executingTimeoutJob?.cancel()
                executingTimeoutJob = CoroutineScope(Dispatchers.IO).launch {
                    delay(EXECUTING_TIMEOUT)
                    Timber.d("Timeout de EXECUTING, restableciendo estado")
                    resetState(extension)
                }
            }
            if (currentStatus != StepStatus.FIRST) {
                readyForActionJob?.cancel()
                readyForActionJob = null
            }


        } catch (e: Exception) {
            Timber.e(e, "Error en UnifiedActionCallback")
            try {
                val extension = KActionsExtension.getInstance()
                if (extension != null) {
                    resetState(extension)
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error al intentar restablecer estado después de excepción")
            }
        }
    }

    private fun resetState(extension: KActionsExtension) {
        Timber.d("Restableciendo estado a IDLE")
        executingTimeoutJob?.cancel()
        executingStartTime = 0
        consecutiveClicks = 0
        extension.updateCustomMessageStatus(0, StepStatus.IDLE)
        extension.updateWebhookStatus(0, StepStatus.IDLE)
        pendingAction = false
    }

    private suspend fun executeMessage(extension: KActionsExtension, messageText: String) {
        withContext(Dispatchers.IO) {
            try {
                Timber.d("Ejecutando mensaje personalizado: $messageText")
                extension.updateCustomMessageStatus(0, StepStatus.EXECUTING)
                extension.sendCustomMessageWithStateTransitions(0, messageText)
            } catch (e: Exception) {
                Timber.e(e, "Error ejecutando mensaje personalizado")
                extension.updateCustomMessageStatus(0, StepStatus.ERROR)
            } finally {
                pendingAction = false
            }
        }
    }

    private suspend fun executeWebhook(extension: KActionsExtension, webhookId: Int) {
        withContext(Dispatchers.IO) {
            try {
                Timber.d("Ejecutando webhook con ID: $webhookId")
                // Primero asignar ambos estados a EXECUTING para evitar desincronización
                extension.updateWebhookStatus(webhookId, StepStatus.EXECUTING)
                extension.updateCustomMessageStatus(0, StepStatus.EXECUTING)
                extension.executeWebhookWithStateTransitions(webhookId)
            } catch (e: Exception) {
                Timber.e(e, "Error ejecutando webhook")
                // Mantener sincronizados los estados en caso de error
                extension.updateWebhookStatus(webhookId, StepStatus.ERROR)
                extension.updateCustomMessageStatus(0, StepStatus.ERROR)
            } finally {
                pendingAction = false
            }
        }
    }

}