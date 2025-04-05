package com.enderthor.kActions.datatype

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.enderthor.kActions.data.StepStatus
import com.enderthor.kActions.extension.KActionsExtension
import com.enderthor.kActions.extension.managers.ConfigurationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
        private const val EXECUTING_TIMEOUT = 30_000L

        private var lastClickTime: Long = 0
        private var executingStartTime: Long = 0
        private var consecutiveClicks: Int = 0
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

           // Timber.d("UnifiedActionCallback: $currentStatus, webhookEnabled: $webhookEnabled, webhookUrl: $webhookUrl")



            timerJob?.cancel()
            timerJob = null


            if (currentStatus == StepStatus.EXECUTING) {

                consecutiveClicks++


                if (executingStartTime > 0 && currentTime - executingStartTime > EXECUTING_TIMEOUT) {
                  //  Timber.d("Timeout de EXECUTING superado, restableciendo estado")
                    resetState(extension)
                    return
                }


                if (consecutiveClicks >= 3 && timeDiff < 2000) {
                  //  Timber.d("Reset forzado por múltiples clicks en EXECUTING")
                    resetState(extension)
                    return
                }


                val webhookActive = webhookEnabled && webhookUrl.isNotEmpty()
                val messageActive = messageText.isNotEmpty()

                if (!webhookActive && !messageActive) {
                    resetState(extension)
                }
                return
            } else {

                consecutiveClicks = 0
            }


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
                           // Timber.d("Timer cancelado")
                        }
                    }
                }

                StepStatus.FIRST -> {
                    val timeDiff = currentTime - lastClickTime

                    if (timeDiff < ACTION_THRESHOLD) {

                        if (messageText.isNotEmpty()) {
                           // Timber.d("Ejecutando mensaje personalizado")
                            executingStartTime = currentTime
                            extension.updateCustomMessageStatus(0, StepStatus.EXECUTING)

                            executeMessage(extension, messageText)
                        } else {
                            //Timber.d("No hay mensaje disponible")
                            resetState(extension)
                        }
                    } else if (timeDiff <= CANCEL_THRESHOLD) {

                        if (webhookUrl.isNotEmpty() && webhookEnabled) {
                          //  Timber.d("Confirmando webhook")
                            extension.updateWebhookStatus(0, StepStatus.CONFIRM)
                            extension.updateCustomMessageStatus(0, StepStatus.CONFIRM)


                            timerJob = CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    delay(CANCEL_THRESHOLD)
                                    resetState(extension)
                                } catch (e: CancellationException) {
                                 //   Timber.d("Timer cancelado")
                                }
                            }
                        } else {
                           // Timber.d("No hay webhook disponible")
                            resetState(extension)
                        }
                    } else {
                        resetState(extension)
                    }
                }

                StepStatus.CONFIRM -> {

                    if (webhookUrl.isNotEmpty()) {
                       // Timber.d("Ejecutando webhook")
                        executingStartTime = currentTime
                        executeWebhook(extension, 0, context)
                    } else {
                        resetState(extension)
                    }
                }

                else -> resetState(extension)
            }

            lastClickTime = currentTime


            if ((currentStatus == StepStatus.FIRST && timeDiff < ACTION_THRESHOLD && messageText.isNotEmpty()) ||
                (currentStatus == StepStatus.CONFIRM && webhookUrl.isNotEmpty())) {

                executingTimeoutJob?.cancel()
                executingTimeoutJob = CoroutineScope(Dispatchers.IO).launch {
                    delay(EXECUTING_TIMEOUT)
                   // Timber.d("Timeout de EXECUTING, restableciendo estado")
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
       // Timber.d("Restableciendo estado a IDLE")
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
              //  Timber.d("Ejecutando mensaje personalizado: $messageText")
                extension.updateCustomMessageStatus(0, StepStatus.EXECUTING)
                extension.sendCustomMessageWithStateTransitions(0, messageText)
            } catch (e: Exception) {
             //   Timber.e(e, "Error ejecutando mensaje personalizado")
                extension.updateCustomMessageStatus(0, StepStatus.ERROR)
            } finally {
                pendingAction = false
            }
        }
    }

   private suspend fun executeWebhook(extension: KActionsExtension, webhookId: Int, context: Context) {
    withContext(Dispatchers.IO) {
        try {
          //  Timber.d("Ejecutando webhook con ID: $webhookId")

            executingTimeoutJob?.cancel()
            executingTimeoutJob = null
            executingStartTime = 0

            val scope = CoroutineScope(Dispatchers.IO)
            val monitorJob = scope.launch {
                val configManager = ConfigurationManager(context)
                var previousStatus: StepStatus? = null

                configManager.loadWebhookDataFlow().collect { webhooks ->
                    val webhook = webhooks.find { it.id == webhookId }
                    webhook?.let {
                        val currentStatus = it.status

                        if (previousStatus != currentStatus) {
                        //    Timber.w("Cambio de estado detectado: $previousStatus -> ${it.status}")
                            previousStatus = currentStatus


                            if (currentStatus == StepStatus.IDLE) {

                            //    Timber.d("Estado final detectado: $currentStatus - Limpiando recursos")
                                executingTimeoutJob?.cancel()
                                executingTimeoutJob = null
                                executingStartTime = 0
                                consecutiveClicks = 0
                                pendingAction = false
                                resetState(extension)


                                this.cancel()
                            }
                        }
                    }
                }
            }


            extension.updateWebhookStatus(webhookId, StepStatus.EXECUTING)
            extension.updateCustomMessageStatus(0, StepStatus.EXECUTING)


            extension.executeWebhookWithStateTransitions(webhookId)


            monitorJob.join()

        } catch (e: Exception) {
            Timber.e(e, "Error ejecutando webhook")
        }
    }
}}