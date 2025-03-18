package com.enderthor.kActions.extension.managers

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enderthor.kActions.activity.dataStore
import com.enderthor.kActions.data.WebhookData
import com.enderthor.kActions.data.WebhookStatus
import com.enderthor.kActions.extension.loadWebhookDataFlow
import com.enderthor.kActions.extension.makeHttpRequest
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

class WebhookManager(
    private val context: Context,
    private val karooSystem: KarooSystemService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webhookConfig: WebhookData? = null
    private val webhooksKey = stringPreferencesKey("webhooks")

    suspend fun loadWebhookConfiguration() {

        if (webhookConfig == null) {
            val webhooks = context.loadWebhookDataFlow().first()
            webhookConfig = webhooks.firstOrNull()
        }
    }

    suspend fun handleEvent(eventType: String): Boolean {
        try {
            loadWebhookConfiguration()

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


                val locationOk = if (config.onlyIfLocation && config.location.isNotBlank()) {
                    checkCurrentLocation(config.location)
                } else {
                    true
                }

                if (shouldTrigger && locationOk) {
                    return sendWebhook(config)
                }
            }
            return false
        } catch (e: Exception) {
            Timber.e(e, "Error al procesar webhook para evento $eventType")
            return false
        }
    }

    private  fun checkCurrentLocation(targetLocation: String): Boolean {
        // Implementar lógica para verificar ubicación
        return true
    }

    fun updateWebhookStatus(webhookId: Int, status: WebhookStatus) {
        scope.launch(Dispatchers.IO) {
            try {
                val webhooks = loadWebhooks()
                val updatedWebhooks = webhooks.map { webhook ->
                    if (webhook.id == webhookId) {
                        webhook.copy(status = status)
                    } else {
                        webhook
                    }
                }
                saveWebhooks(updatedWebhooks)
                Timber.d("Estado de webhook $webhookId actualizado a $status")
            } catch (e: Exception) {
                Timber.e(e, "Error actualizando estado del webhook")
            }
        }
    }

    private suspend fun loadWebhooks(): List<WebhookData> {
        return context.loadWebhookDataFlow().first()
    }

    private suspend fun saveWebhooks(webhooks: List<WebhookData>) {
        context.dataStore.edit { preferences ->
            preferences[webhooksKey] = Json.encodeToString(webhooks)
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