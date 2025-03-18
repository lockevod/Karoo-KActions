package com.enderthor.kActions.extension.managers

import com.enderthor.kActions.data.ConfigData
import com.enderthor.kActions.data.ProviderType
import com.enderthor.kActions.data.SenderConfig
import com.enderthor.kActions.data.karooUrl
import com.enderthor.kActions.extension.Sender
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import timber.log.Timber

class NotificationManager(
    private val sender: Sender
) {
    private val lastMessageTimeByType = mutableMapOf<String, Long>()
    private val sentMessageStates = mutableSetOf<String>()
    private val MIN_TIME_BETWEEN_SAME_MESSAGES = 3 * 60 * 1000L

    suspend fun handleEventWithTimeLimit(
        eventType: String,
        currentTime: Long,
        configs: List<ConfigData>,
        senderConfig: SenderConfig?
    ): Boolean {
        val lastTime = lastMessageTimeByType[eventType] ?: 0L
        val timeElapsed = currentTime - lastTime

        val isTextBeltFree = senderConfig?.let {
            it.provider == ProviderType.TEXTBELT &&
                    (it.apiKey.isBlank() || it.apiKey == "textbelt")
        } ?: false

        val configuredDelay = (configs.firstOrNull()?.delayIntents ?: 0.0) * 60 * 1000

        val minTimeBetweenMessages = when {
            isTextBeltFree -> 24 * 60 * 60 * 1000L // 24 horas
            else -> maxOf(configuredDelay.toLong(), MIN_TIME_BETWEEN_SAME_MESSAGES)
        }

        if (timeElapsed >= minTimeBetweenMessages) {
            val configsWithNotify = when (eventType) {
                "start" -> configs.filter { it.notifyOnStart }
                "stop" -> configs.filter { it.notifyOnStop }
                "pause" -> configs.filter { it.notifyOnPause }
                "resume" -> configs.filter { it.notifyOnResume }
                else -> emptyList()
            }

            if (configsWithNotify.isNotEmpty()) {
                lastMessageTimeByType[eventType] = currentTime
                val stateKey = "$eventType-$currentTime"

                if (!sentMessageStates.contains(stateKey)) {
                    sendMessageForEvent(eventType, configs, senderConfig)
                    sentMessageStates.add(stateKey)

                    if (sentMessageStates.size > 10) {
                        val keysToRemove = sentMessageStates.toList()
                            .sortedBy { it.split("-").last().toLongOrNull() ?: 0L }
                            .take(sentMessageStates.size - 10)
                        sentMessageStates.removeAll(keysToRemove.toSet())
                    }
                    return true
                }
            }
        } else {
            val minutosRestantes = (minTimeBetweenMessages - timeElapsed) / (60 * 1000.0)
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