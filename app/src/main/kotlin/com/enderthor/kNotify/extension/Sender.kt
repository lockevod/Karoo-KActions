package com.enderthor.kNotify.extension

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enderthor.kNotify.activity.dataStore
import com.enderthor.kNotify.data.SenderConfig
import com.enderthor.kNotify.data.Template
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

private val senderConfigKey = stringPreferencesKey("sender")
private val templatesKey = stringPreferencesKey("templates")

class Sender(
    private val context: Context,
    private val karooSystem: KarooSystemService? = null
) {


   suspend fun sendMessage(phoneNumber: String, message: String): Boolean {
        var totalAttempts = 0
        var currentCycle = 0

        val maxCycles = 3
        val attemptsPerCycle = 3
        val delaySeconds = listOf(60, 120, 180)
        val cycleDelayMinutes = listOf(5, 10)

        try {
            while (currentCycle < maxCycles) {
                repeat(attemptsPerCycle) { attempt ->
                    totalAttempts++

                    if (totalAttempts > 1) {
                        val delayTime = delaySeconds[currentCycle]
                        Timber.d("Reintentando envío a $phoneNumber. Intento $totalAttempts, esperando $delayTime segundos...")
                        kotlinx.coroutines.delay(delayTime * 1000L)
                    }

                    // Intentar con timeout para evitar bloqueos
                    val result = withTimeoutOrNull(30_000L) {
                        attemptSendMessage(phoneNumber, message)
                    } == true

                    if (result) {
                        Timber.d("Mensaje enviado correctamente a $phoneNumber en el intento $totalAttempts")
                        return true
                    }
                }

                if (currentCycle < maxCycles - 1) {
                    val waitMinutes = cycleDelayMinutes[currentCycle]
                    Timber.d("Fallaron $attemptsPerCycle intentos, esperando $waitMinutes minutos antes del siguiente ciclo...")
                    kotlinx.coroutines.delay(waitMinutes * 60 * 1000L)
                }

                currentCycle++
            }

            Timber.e("El mensaje a $phoneNumber falló después de $totalAttempts intentos")
            return false
        } catch (e: Exception) {
            Timber.e(e, "Error en el proceso de reintento: ${e.message}")
            return false
        }
    }


    private suspend fun attemptSendMessage(phoneNumber: String, message: String): Boolean {
        try {
            val config = getSenderConfig() ?: run {
                Timber.Forest.e("No WhAPI configuration available")
                return false
            }

            if (karooSystem == null) {
                Timber.Forest.e("KarooSystemService is required to send messages")
                return false
            }


            val formattedPhone = phoneNumber.trim()

            val jsonBody = buildJsonObject {
                put("to", formattedPhone)
                put("body", message)
            }.toString()


            val url = "https://gate.whapi.cloud/messages/text"

            val headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer ${config.apiKey}"
            )

            val response = karooSystem.makeHttpRequest(
                method = "POST",
                url = url,
                headers = headers,
                body = jsonBody.toByteArray()
            ).first()

            val responseText = response.body?.toString(Charsets.UTF_8) ?: ""
            return if (response.statusCode in 200..299 || responseText.contains("message_id")) {
                Timber.Forest.d("WhatsApp message sent successfully to $formattedPhone")
                true
            } else {
                Timber.Forest.e("Error in WhAPI response: Code ${response.statusCode}, $responseText")
                false
            }
        } catch (e: Exception) {
            Timber.Forest.e(e, "Error sending WhatsApp message: ${e.message}")
            return false
        }
    }

    suspend fun saveSenderConfig(config: SenderConfig) {
        try {
            context.dataStore.edit { preferences ->
                preferences[senderConfigKey] = Json.Default.encodeToString(config)
            }
            Timber.Forest.d("Configuración de Sender guardada")
        } catch (e: Exception) {
            Timber.Forest.e(e, "Error al guardar configuración de Sender")
        }
    }

    suspend fun getSenderConfig(): SenderConfig? {
        return try {
            context.dataStore.data.map { preferences ->
                val configJson = preferences[senderConfigKey]
                if (configJson != null) {
                    Json.Default.decodeFromString<SenderConfig>(configJson)
                } else {
                    SenderConfig()
                }
            }.first()
        } catch (e: Exception) {
            Timber.Forest.e(e, "Error al obtener configuración del Sender")
            SenderConfig()
        }
    }

    fun getSenderConfigFlow(): Flow<SenderConfig?> {
        return context.dataStore.data.map { preferences ->
            try {
                val configJson = preferences[senderConfigKey]
                if (configJson != null) {
                    Json.Default.decodeFromString<SenderConfig>(configJson)
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.Forest.e(e, "Error al leer configuración del Sender")
                null
            }
        }
    }

    fun getTemplatesFlow(): Flow<List<Template>> {
        return context.dataStore.data.map { preferences ->
            try {
                val templatesJson = preferences[templatesKey] ?: "[]"
                val jsonConfig = Json { ignoreUnknownKeys = true }
                jsonConfig.decodeFromString<List<Template>>(templatesJson)
            } catch (e: Exception) {
                Timber.e(e, "Error al leer plantillas de WhatsApp")
                emptyList()
            }
        }
    }

}