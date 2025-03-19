package com.enderthor.kActions.extension

import com.enderthor.kActions.data.ProviderType
import com.enderthor.kActions.extension.managers.ConfigurationManager
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber


class Sender(
    private val karooSystem: KarooSystemService? = null,
    private val configManager: ConfigurationManager
) {

    suspend fun sendNotification(phoneNumber: String? = null, message: String): Boolean {

        val configData = configManager.loadPreferencesFlow().first().firstOrNull()
        val activeProvider = configData?.activeProvider ?: ProviderType.CALLMEBOT

        val senderConfigs = configManager.loadSenderConfigFlow().first()
        val senderConfig = senderConfigs.find { it.provider == activeProvider }
            ?: senderConfigs.firstOrNull()
            ?: run {
                Timber.e("No hay configuración del proveedor disponible")
                return false
            }

        return when (senderConfig.provider) {
            ProviderType.CALLMEBOT -> sendMessage(phoneNumber ?: return false, message, activeProvider)
            ProviderType.WHAPI -> sendMessage(phoneNumber ?: return false, message, activeProvider)
            ProviderType.TEXTBELT -> sendSMSMessage(phoneNumber ?: return false, message)
            ProviderType.RESEND -> sendEmailMessage(message)
        }
    }


   suspend fun sendMessage(phoneNumber: String, message: String,senderProvider: ProviderType): Boolean {
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


                    val result = withTimeoutOrNull(30_000L) {
                        attemptSendMessage(phoneNumber, message,senderProvider)
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


    suspend fun sendSMSMessage(phoneNumber: String, message: String): Boolean {
        try {
            val configs = configManager.loadSenderConfigFlow().first()
            val config = configs.find { it.provider == ProviderType.TEXTBELT }
                ?: configs.firstOrNull()
                ?: return false

            if (karooSystem == null) {
                Timber.e("KarooSystemService es necesario para enviar mensajes")
                return false
            }

            val formattedPhone = "+" +phoneNumber.trim()

            val jsonBody = buildJsonObject {
                put("phone", formattedPhone)
                put("message", message)
                put("key", config.apiKey)
            }.toString()

            val url = "https://textbelt.com/text"

            val headers = mapOf(
                "Content-Type" to "application/json"
            )

            val response = karooSystem.makeHttpRequest(
                method = "POST",
                url = url,
                headers = headers,
                body = jsonBody.toByteArray()
            ).first()

            val responseText = response.body?.toString(Charsets.UTF_8) ?: ""
            return if (response.statusCode in 200..299 && responseText.contains("\"success\":true")) {
                Timber.d("SMS enviado correctamente a $formattedPhone")
                true
            } else {
                Timber.e("Error en respuesta de TextBelt: Código ${response.statusCode}, $responseText")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error enviando SMS: ${e.message}")
            return false
        }
    }

/*
    suspend fun sendEmailMessage(message: String): Boolean {
        try {
            val configs = configManager.loadSenderConfigFlow().first()
            val config = configs.find { it.provider == ProviderType.RESEND }
                ?: configs.firstOrNull()
                ?: return false

            val configData = configManager.loadPreferencesFlow().first().firstOrNull() ?: return false

            if (configData.emails.isEmpty()) {
                Timber.e("No hay direcciones de email configuradas")
                return false
            }

            if (karooSystem == null) {
                Timber.e("KarooSystemService es necesario para enviar emails")
                return false
            }

            // Usar el mismo email como remitente y destinatario si solo hay uno configurado
            val emailFrom = "karoo@resend.dev" // Email genérico como remitente
            val emailTo = configData.emails[0]

            val jsonBody = buildJsonObject {
                put("from", emailFrom)
                put("to", emailTo)
                put("subject", "Karoo Notification")
                put("html", "<p>$message</p>")
            }.toString()

            val url = "https://api.resend.com/emails"

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
            return if (response.statusCode in 200..299) {
                Timber.d("Email enviado correctamente a $emailTo")
                true
            } else {
                Timber.e("Error en respuesta de Resend: Código ${response.statusCode}, $responseText")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error enviando email: ${e.message}")
            return false
        }
    }*/




    suspend fun sendEmailMessage(message: String): Boolean {
        try {
            val configs = configManager.loadSenderConfigFlow().first()
            val config = configs.find { it.provider == ProviderType.RESEND }
                ?: configs.firstOrNull()
                ?: return false

            val configData = configManager.loadPreferencesFlow().first().firstOrNull() ?: return false

            if (configData.emails.isEmpty()) {
                Timber.e("No hay direcciones de email configuradas")
                return false
            }

            if (karooSystem == null) {
                Timber.e("KarooSystemService es necesario para enviar emails")
                return false
            }

            // Usar la primera dirección de correo configurada como destinatario
            val emailTo = configData.emails[0]

            // Remitente para Elastic Email (debe ser un dominio verificado)
            val emailFrom = configData.emailFrom

            // Datos para la API de Elastic Email
            val formParams = mapOf(
                "apikey" to config.apiKey,
                "from" to emailFrom,
                "to" to emailTo,
                "subject" to "Karoo Notification",
                "bodyHtml" to "<p>$message</p>",
                "bodyText" to message,
                "isTransactional" to "true"
            )

            val formBody = formParams.entries.joinToString("&") { (key, value) ->
                "${key}=${java.net.URLEncoder.encode(value, "UTF-8")}"
            }

            val url = "https://api.elasticemail.com/v2/email/send"

            val headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded"
            )

            val response = karooSystem.makeHttpRequest(
                method = "POST",
                url = url,
                headers = headers,
                body = formBody.toByteArray()
            ).first()

            val responseText = response.body?.toString(Charsets.UTF_8) ?: ""
            return if (response.statusCode in 200..299 && responseText.contains("\"success\":true")) {
                Timber.d("Email enviado correctamente a $emailTo mediante Elastic Email")
                true
            } else {
                Timber.e("Error en respuesta de Elastic Email: Código ${response.statusCode}, $responseText")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error enviando email mediante Elastic Email: ${e.message}")
            return false
        }
    }

    private fun formatPhoneForWhapi(phoneNumber: String): String {

        var formattedNumber = phoneNumber.trim()
        if (formattedNumber.startsWith("+")) {
            formattedNumber = formattedNumber.substring(1)
        }


        if (!formattedNumber.all { it.isDigit() || it == '-' }) {
            formattedNumber = formattedNumber.filter { it.isDigit() }
        }

        return formattedNumber
    }


    private suspend fun attemptSendMessage(phoneNumber: String, message: String,senderProvider: ProviderType): Boolean {
        try {
            val configs = configManager.loadSenderConfigFlow().first()
            val config = configs.find { it.provider == senderProvider }
                ?: configs.firstOrNull()
                ?: return false
            if (karooSystem == null) {
                Timber.Forest.e("KarooSystemService is required to send messages")
                return false
            }


            val formattedPhone = when (senderProvider) {
                ProviderType.WHAPI -> formatPhoneForWhapi(phoneNumber)
                else -> phoneNumber.trim()
            }

            when (senderProvider) {
                ProviderType.WHAPI -> {
                    val jsonBody = buildJsonObject {
                        put("to", formattedPhone)
                        put("body", message)
                    }.toString()

                    val url = "https://gate.whapi.cloud/messages/text"

                    val headers = mapOf(
                        "Content-Type" to "application/json",
                        "Authorization" to "Bearer ${config.apiKey}"
                    )

                    Timber.d("Sending WhatsApp message to $formattedPhone: $jsonBody")
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
                }

                ProviderType.CALLMEBOT -> {
                    val encodedMessage = java.net.URLEncoder.encode(message, "UTF-8")
                    val url = "https://api.callmebot.com/whatsapp.php?phone=$formattedPhone&text=$encodedMessage&apikey=${config.apiKey}"

                    val response = karooSystem.makeHttpRequest(
                        method = "GET",
                        url = url,
                        headers = emptyMap(),
                        body = null
                    ).first()

                    val responseText = response.body?.toString(Charsets.UTF_8) ?: ""
                    return if (response.statusCode in 200..299 && !responseText.contains("ERROR")) {
                        Timber.Forest.d("CallMeBot message sent successfully to $formattedPhone")
                        true
                    } else {
                        Timber.Forest.e("Error in CallMeBot response: Code ${response.statusCode}, $responseText")
                        false
                    }
                }

                else -> {
                    Timber.Forest.e("Proveedor no soportado: $senderProvider")
                    return false
                }
            }
        } catch (e: Exception) {
            Timber.Forest.e(e, "Error sending WhatsApp message: ${e.message}")
            return false
        }
    }

}