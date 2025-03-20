package com.enderthor.kActions.extension.managers

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enderthor.kActions.activity.dataStore
import com.enderthor.kActions.data.GpsCoordinates
import com.enderthor.kActions.data.WebhookData
import com.enderthor.kActions.data.WebhookStatus
import com.enderthor.kActions.extension.makeHttpRequest
import com.enderthor.kActions.extension.getGpsFlow
import com.enderthor.kActions.extension.getHomeFlow
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class WebhookManager(
    private val context: Context,
    private val karooSystem: KarooSystemService,
    private val scope: CoroutineScope
) {

    private val webhookStateStore = WebhookStateStore(context)
    private val configManager = ConfigurationManager(context)
    private var webhookConfig: WebhookData? = null
    private val webhooksKey = stringPreferencesKey("webhooks")


    suspend fun loadWebhookConfiguration(webhookId: Int? = null) {
        val webhooks = configManager.loadWebhookDataFlow().first()
        webhookConfig = if (webhookId != null) {
            webhooks.find { it.id == webhookId }
        } else {
            webhooks.firstOrNull()
        }
    }


    suspend fun handleEvent(eventType: String, webhookId: Int): Boolean {
        try {
            loadWebhookConfiguration(webhookId)

            webhookConfig?.let { config ->
                if (!config.enabled) return false

                val shouldTrigger = when (eventType) {
                    "start" -> config.actionOnStart
                    "stop" -> config.actionOnStop
                    "pause" -> config.actionOnPause
                    "resume" -> config.actionOnResume
                    "custom" -> config.actionOnCustom
                    else -> false
                }

                val locationOk = if (config.onlyIfLocation) {
                    val poi = karooSystem.getHomeFlow().first()
                    checkCurrentLocation(poi)
                } else {
                    true
                }

                if (shouldTrigger && locationOk) {
                    return sendWebhook(config)
                }
            }
            return false
        } catch (e: Exception) {
            Timber.e(e, "Error al procesar webhook para evento $eventType y ID $webhookId")
            return false
        }
    }

    fun triggerCustomWebhook(webhookId: Int?) {
        webhookId?.let { id ->
            scope.launch(Dispatchers.IO) {
                Timber.d("Activando webhook: $id")
                handleEvent("custom", id)
            }
        } ?: run {
            Timber.w("Webhook ID no proporcionado")
        }
    }

    fun restorePendingWebhookStates() {
        scope.launch(Dispatchers.IO) {
            try {
                val webhooks = configManager.loadWebhookDataFlow().first()

                webhooks.forEach { webhook ->
                    val (statusStr, targetTime) = webhookStateStore.getWebhookState(webhook.id)

                    if (statusStr != null) {
                        val status = WebhookStatus.valueOf(statusStr)
                        val currentTime = System.currentTimeMillis()
                        val remainingTime = targetTime - currentTime

                        if (remainingTime > 0) {
                            // Restaurar el estado actual
                            updateWebhookStatus(webhook.id, status)

                            // Esperar el tiempo restante
                            delay(remainingTime)

                            // Realizar la siguiente transición
                            when (status) {
                                WebhookStatus.FIRST -> {
                                    updateWebhookStatus(webhook.id, WebhookStatus.IDLE)
                                    webhookStateStore.clearWebhookState(webhook.id)
                                }
                                WebhookStatus.EXECUTING -> {
                                    updateWebhookStatus(webhook.id, WebhookStatus.SUCCESS)
                                    scheduleResetToIdle(webhook.id, 5_000)
                                }
                                WebhookStatus.SUCCESS, WebhookStatus.ERROR -> {
                                    updateWebhookStatus(webhook.id, WebhookStatus.IDLE)
                                    webhookStateStore.clearWebhookState(webhook.id)
                                }
                                else -> webhookStateStore.clearWebhookState(webhook.id)
                            }
                        } else {
                            // Si el tiempo ya pasó, limpiar
                            updateWebhookStatus(webhook.id, WebhookStatus.IDLE)
                            webhookStateStore.clearWebhookState(webhook.id)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error restaurando estados de webhooks: ${e.message}")
            }
        }
    }


    fun updateWebhookStatus(webhookId: Int, newStatus: WebhookStatus) {
        scope.launch(Dispatchers.IO) {
            try {
                val webhooks = configManager.loadWebhookDataFlow().first().toMutableList()
                val index = webhooks.indexOfFirst { it.id == webhookId }

                if (index != -1) {
                    webhooks[index] = webhooks[index].copy(status = newStatus)
                    configManager.saveWebhookData(webhooks)
                    Timber.d("Webhook $webhookId actualizado a estado $newStatus")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error actualizando estado del webhook")
            }
        }
    }

    private fun distanceTo(first: GpsCoordinates, other: GpsCoordinates): Double {
        val lat1 = Math.toRadians(first.lat)
        val lon1 = Math.toRadians(first.lng)
        val lat2 = Math.toRadians(other.lat)
        val lon2 = Math.toRadians(other.lng)
        val dlat = lat2 - lat1
        val dlon = lon2 - lon1
        val a = sin(dlat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dlon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val r = 6371.0
        val distance = r * c

        return distance
    }

    private suspend fun checkCurrentLocation(targetLocation: GpsCoordinates): Boolean {
        try {
            val currentLocation = karooSystem.getGpsFlow().first() // Obtenemos solo el primer valor

            val distance = distanceTo(currentLocation, targetLocation)
            Timber.d("Distancia a la ubicación objetivo: $distance km")
            return distance <= 0.010 // 10 metros

        } catch (e: Exception) {
            Timber.e(e, "Error al comprobar la ubicación: ${e.message}")
            return false
        }

    }



    private suspend fun loadWebhooks(): List<WebhookData> {
        return configManager.loadWebhookDataFlow().first()
    }

    private suspend fun saveWebhooks(webhooks: List<WebhookData>) {
        context.dataStore.edit { preferences ->
            preferences[webhooksKey] = Json.encodeToString(webhooks)
        }
    }

    fun scheduleResetToIdle(webhookId: Int, delayMillis: Long) {
        webhookStateStore.saveWebhookState(
            webhookId,
            WebhookStatus.FIRST.name,
            System.currentTimeMillis() + delayMillis
        )

        scope.launch(Dispatchers.IO) {
            delay(delayMillis)
            updateWebhookStatus(webhookId, WebhookStatus.IDLE)
            webhookStateStore.clearWebhookState(webhookId)
        }
    }

    fun executeWebhookWithStateTransitions(webhookId: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                // Guardar estado inicial
                webhookStateStore.saveWebhookState(
                    webhookId,
                    WebhookStatus.EXECUTING.name,
                    System.currentTimeMillis() + 10_000
                )

                updateWebhookStatus(webhookId, WebhookStatus.EXECUTING)
                handleEvent("custom", webhookId)

                delay(10_000)
                updateWebhookStatus(webhookId, WebhookStatus.SUCCESS)

                // Guardar estado para transición a IDLE
                webhookStateStore.saveWebhookState(
                    webhookId,
                    WebhookStatus.SUCCESS.name,
                    System.currentTimeMillis() + 5_000
                )

                delay(5_000)
                updateWebhookStatus(webhookId, WebhookStatus.IDLE)
                webhookStateStore.clearWebhookState(webhookId)

            } catch (e: Exception) {
                Timber.e(e, "Error al ejecutar webhook $webhookId: ${e.message}")
                updateWebhookStatus(webhookId, WebhookStatus.ERROR)

                // Guardar estado para transición a ERROR
                webhookStateStore.saveWebhookState(
                    webhookId,
                    WebhookStatus.ERROR.name,
                    System.currentTimeMillis() + 10_000
                )

                delay(10_000)
                updateWebhookStatus(webhookId, WebhookStatus.IDLE)
                webhookStateStore.clearWebhookState(webhookId)
            }
        }
    }

    suspend fun sendWebhook(config: WebhookData): Boolean {
        try {
            if (!config.url.startsWith("http")) {
                Timber.e("URL de webhook inválida: ${config.url}")
                return false
            }

            val headers = mapOf("Content-Type" to "application/json")
            val body = if (config.post.isBlank()) null else config.post.toByteArray()

            val response = karooSystem.makeHttpRequest(
                method = "POST",
                url = config.url,
                headers = headers,
                body = body
            ).first()

            val success = response.statusCode in 200..299
            if (success) {
                Timber.d("Webhook enviado correctamente a: ${config.url}")
            } else {
                Timber.e("Error enviando webhook: ${response.statusCode}")
            }

            return success
        } catch (e: Exception) {
            Timber.e(e, "Error enviando webhook")
            return false
        }
    }
}