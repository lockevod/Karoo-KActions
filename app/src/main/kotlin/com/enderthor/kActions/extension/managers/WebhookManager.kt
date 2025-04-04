package com.enderthor.kActions.extension.managers

import android.content.Context
import com.enderthor.kActions.data.GpsCoordinates
import com.enderthor.kActions.data.WebhookData
import com.enderthor.kActions.data.StepStatus
import com.enderthor.kActions.extension.makeHttpRequest
import com.enderthor.kActions.extension.getGpsFlow
import com.enderthor.kActions.extension.getHomeFlow
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import timber.log.Timber
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt


class WebhookManager(
    context: Context,
    private val karooSystem: KarooSystemService,
    private val scope: CoroutineScope
) {

    private val webhookStateStore = WebhookStateStore(context)
    private val configManager = ConfigurationManager(context)
    private var webhookConfig: WebhookData? = null


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

            //Timber.w("Estamos en  HANDLE EVENT")

            webhookConfig?.let { config ->
                //if (!config.enabled) return false

                val shouldTrigger = when (eventType) {
                    "start" -> config.actionOnStart
                    "stop" -> config.actionOnStop
                    "pause" -> config.actionOnPause
                    "resume" -> config.actionOnResume
                    "custom" -> config.actionOnCustom
                    else -> false
                }

                val locationOk = if (config.onlyIfLocation) {
                    //Timber.w("Comprobando ubicación actual")
                    val poi = karooSystem.getHomeFlow().first()
                    checkCurrentLocation(poi)
                } else {
                    true
                }
               // Timber.w("Ubicación actual comprobada antes de salir")
                if (shouldTrigger && locationOk) {
                    //Timber.w("Webhook activado")
                    return sendWebhook(config)
                }
                else return false
            }
            return false
        } catch (e: Exception) {
            Timber.e(e, "Error al procesar webhook para evento $eventType y ID $webhookId")
            return false
        }
    }


    fun restorePendingWebhookStates() {
        scope.launch(Dispatchers.IO) {
            try {
                val webhooks = configManager.loadWebhookDataFlow().first()

                webhooks.forEach { webhook ->
                    val (statusStr, targetTime) = webhookStateStore.getWebhookState(webhook.id)

                    if (statusStr != null) {
                        val status = StepStatus.valueOf(statusStr)
                        val currentTime = System.currentTimeMillis()
                        val remainingTime = targetTime - currentTime

                        if (remainingTime > 0) {

                            updateWebhookStatus(webhook.id, status)


                            delay(remainingTime)


                            when (status) {
                                StepStatus.FIRST -> {
                                    updateWebhookStatus(webhook.id, StepStatus.IDLE)
                                    webhookStateStore.clearWebhookState(webhook.id)
                                }
                                StepStatus.EXECUTING -> {
                                    updateWebhookStatus(webhook.id, StepStatus.SUCCESS)
                                    scheduleResetToIdle(webhook.id, 5_000)
                                }
                                StepStatus.SUCCESS,StepStatus.ERROR -> {
                                    updateWebhookStatus(webhook.id,StepStatus.IDLE)
                                    webhookStateStore.clearWebhookState(webhook.id)
                                }
                                else -> webhookStateStore.clearWebhookState(webhook.id)
                            }
                        } else {

                            updateWebhookStatus(webhook.id, StepStatus.IDLE)
                            webhookStateStore.clearWebhookState(webhook.id)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error restaurando estados de webhooks: ${e.message}")
            }
        }
    }


    fun updateWebhookStatus(webhookId: Int, newStatus: StepStatus) {
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
           // Timber.w("Comprobando ubicación actual")
            val currentLocation = karooSystem.getGpsFlow().first()

            val distance = distanceTo(currentLocation, targetLocation)
           // Timber.w("Distancia a la ubicación objetivo: $distance km")
            return distance <= 0.070 // 70 metros

        } catch (e: Exception) {
            Timber.e(e, "Error al comprobar la ubicación: ${e.message}")
            return false
        }

    }


    fun scheduleResetToIdle(webhookId: Int, delayMillis: Long) {
        webhookStateStore.saveWebhookState(
            webhookId,
            StepStatus.FIRST.name,
            System.currentTimeMillis() + delayMillis
        )

        scope.launch(Dispatchers.IO) {
            delay(delayMillis)
            updateWebhookStatus(webhookId, StepStatus.IDLE)
            webhookStateStore.clearWebhookState(webhookId)
        }
    }

    fun executeWebhookWithStateTransitions(webhookId: Int) {
        scope.launch(Dispatchers.IO) {
            try {

                webhookStateStore.clearWebhookState(webhookId)
                updateWebhookStatus(webhookId, StepStatus.EXECUTING)


                delay(4_000)


                val statuscode = handleEvent("custom", webhookId)


                val finalState = if (statuscode) StepStatus.SUCCESS else StepStatus.ERROR
                val visibilityTime = if (statuscode) 4_000L else 5_000L

                updateWebhookStatus(webhookId, finalState)
                webhookStateStore.saveWebhookState(
                    webhookId,
                    finalState.name,
                    System.currentTimeMillis() + visibilityTime
                )


                delay(visibilityTime)


                delay(600)


                webhookStateStore.clearWebhookState(webhookId)
                updateWebhookStatus(webhookId, StepStatus.IDLE)

            } catch (e: Exception) {
                Timber.e(e, "Error al ejecutar webhook $webhookId: ${e.message}")


                updateWebhookStatus(webhookId, StepStatus.ERROR)
                webhookStateStore.saveWebhookState(
                    webhookId,
                    StepStatus.ERROR.name,
                    System.currentTimeMillis() + 10_000
                )

                delay(10_000)
                delay(600)

                webhookStateStore.clearWebhookState(webhookId)
                updateWebhookStatus(webhookId, StepStatus.IDLE)
            }
        }
    }

    suspend fun sendWebhook(config: WebhookData): Boolean {
        try {

            Timber.d("Enviando webhook a: ${config.url}")


            if (!config.url.startsWith("http")) {
                Timber.e("URL de webhook inválida: ${config.url}")
                return false
            }

            val defaultHeaders = mapOf("Content-Type" to "application/json")
            val customHeaders = if (config.header.isNotBlank()) {
                config.header.split("\n").mapNotNull { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        parts[0].trim() to parts[1].trim()
                    } else null
                }.toMap()
            } else emptyMap()

            val headers = defaultHeaders + customHeaders

            val contentType = customHeaders["Content-Type"]?.lowercase()

            val postBody = config.post

            val body = when {
                postBody.isBlank() -> null
                contentType?.contains("json") == true -> {

                    if (postBody.trim().startsWith("{") && postBody.trim().endsWith("}")) {

                        postBody.toByteArray()
                    } else {

                        try {
                            val jsonObject = buildJsonObject {
                                postBody.split("\n").forEach { line ->
                                    val parts = line.split(":", limit = 2)
                                    if (parts.size == 2) {
                                        put(parts[0].trim(), JsonPrimitive(parts[1].trim()))
                                    }
                                }
                            }
                            jsonObject.toString().toByteArray()
                        } catch (e: Exception) {
                            postBody.toByteArray()
                        }
                    }
                }
                else -> postBody.toByteArray()
            }



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