package com.enderthor.kActions.extension.managers

import android.content.Context
import com.enderthor.kActions.data.ConfigData
import com.enderthor.kActions.data.ProviderType
import com.enderthor.kActions.data.SenderConfig
import com.enderthor.kActions.data.karooUrl
import com.enderthor.kActions.extension.Sender
import com.enderthor.kActions.data.MIN_TIME_BETWEEN_SAME_MESSAGES
import com.enderthor.kActions.data.MIN_TIME_TEXTBELT_FREE
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

class NotificationManager(
private val sender: Sender,
context: Context
) {
    private val notificationStateStore = NotificationStateStore(context)

    suspend fun handleEventWithTimeLimit(
        eventType: String,
        currentTime: Long,
        configs: List<ConfigData>,
        senderConfig: SenderConfig?
    ): Boolean {
        val lastTime = notificationStateStore.getLastNotificationTime(eventType)
        val timeElapsed = currentTime - lastTime
        val isNewSession = notificationStateStore.isNewSession(eventType)

        val isTextBeltFree = senderConfig?.let {
            it.provider == ProviderType.TEXTBELT &&
                    (it.apiKey.isBlank() || it.apiKey == "textbelt")
        } == true

        val configuredDelay = (configs.firstOrNull()?.delayIntents ?: 0.0) * 60 * 1000

        val minTimeBetweenMessages = when {
            isTextBeltFree -> MIN_TIME_TEXTBELT_FREE
            else -> maxOf(configuredDelay.toLong(), MIN_TIME_BETWEEN_SAME_MESSAGES)
        }


        if (isNewSession || timeElapsed >= minTimeBetweenMessages) {
            val configsWithNotify = when (eventType) {
                "start" -> configs.filter { it.notifyOnStart }
                "stop" -> configs.filter { it.notifyOnStop }
                "pause" -> configs.filter { it.notifyOnPause }
                "resume" -> configs.filter { it.notifyOnResume }
                else -> emptyList()
            }

            if (configsWithNotify.isNotEmpty()) {
                notificationStateStore.saveNotificationTime(eventType, currentTime)
                val stateKey = "$eventType-$currentTime"

                if (!notificationStateStore.hasSentMessage(stateKey)) {
                    val success = sendMessageForEvent(eventType, configs, senderConfig)
                    if (success) {
                        notificationStateStore.saveSentMessage(stateKey)
                    }
                    return success
                }
            }
        } else {
            Timber.d("Mensaje tipo $eventType ignorado - tiempo insuficiente entre mensajes")
        }
        return false
    }
    suspend fun sendMessageForEvent(
        eventType: String,
        configs: List<ConfigData>,
        senderConfig: SenderConfig?
    ): Boolean = coroutineScope {
        try {
            val config = configs.firstOrNull() ?: run {
                Timber.e("No hay configuración disponible para enviar mensajes")
                return@coroutineScope false
            }

            val sConfig = senderConfig ?: run {
                Timber.e("No hay configuración de remitente disponible")
                return@coroutineScope false
            }

            val karooLive = if (config.karooKey.isNotBlank()) karooUrl + config.karooKey else ""
            val message = when (eventType) {
                "start" -> config.startMessage + "   " + karooLive
                "stop" -> config.stopMessage + "   " + karooLive
                "pause" -> config.pauseMessage + "   " + karooLive
                "resume" -> config.resumeMessage + "   " + karooLive
                else -> return@coroutineScope false
            }


            if (sConfig.provider == ProviderType.RESEND) {

                return@coroutineScope sender.sendNotification(message = message)
            } else if (config.phoneNumbers.isNotEmpty()) {

                val sendJobs = config.phoneNumbers.map { phoneNumber ->
                    async {
                        sender.sendNotification(phoneNumber = phoneNumber, message = message)
                    }
                }

                val results = sendJobs.awaitAll()
                val successCount = results.count { it }
                Timber.d("Resumen de envíos para evento $eventType: $successCount éxitos de ${config.phoneNumbers.size} intentos")
                return@coroutineScope successCount > 0
            } else {
                Timber.d("No hay destinatarios configurados para evento: $eventType")
                return@coroutineScope false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error enviando mensajes para evento: $eventType")
            return@coroutineScope false
        }
    }
}