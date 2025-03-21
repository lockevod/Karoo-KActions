package com.enderthor.kActions.extension.managers

import android.content.Context
import android.net.Uri
import com.enderthor.kActions.data.SenderConfig
import com.enderthor.kActions.data.SenderConfigImportResult
import com.enderthor.kActions.data.WebhookData
import com.enderthor.kActions.data.WebhookImportResult
import com.enderthor.kActions.extension.jsonWithUnknownKeys
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.BufferedReader


class ConfigFileHandler(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val json_import = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }


    fun readWebhookFromFile(uri: Uri): List<WebhookData>? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val content = inputStream?.bufferedReader()?.use(BufferedReader::readText)
            inputStream?.close()

            if (content.isNullOrBlank()) return null

            json.decodeFromString<List<WebhookData>>(content)
        } catch (e: Exception) {
            Timber.e(e, "Error leyendo configuración de webhook del archivo: ${e.message}")
            null
        }
    }

    fun readWebhookFromFile(uri: Uri, currentWebhooks:List<WebhookData> = emptyList()): WebhookImportResult {

        try {
            val jsonContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: throw Exception("Error: Reading File")

            val importedWebhooks = jsonWithUnknownKeys.decodeFromString<List<WebhookData>>(jsonContent)

            val result = currentWebhooks.toMutableList()

            importedWebhooks.forEach { imported ->


                val index =  if (jsonContent.contains("\"id\"")) result.indexOfFirst { it.id == imported.id } else 0

                if (index >= 0) {
                    val existingWebhook = result[index]

                    result[index] = existingWebhook.copy(
                        name = if (jsonContent.contains("\"name\"")) imported.name else existingWebhook.name,
                        url =if (jsonContent.contains("\"post\"")) imported.url else existingWebhook.url,
                        post = if (jsonContent.contains("\"post\"")) imported.post else existingWebhook.post,
                        header = if (jsonContent.contains("\"header\"")) imported.header else existingWebhook.header,
                    )
                } else {
                    result.add(imported)
                }
            }

            return WebhookImportResult(result, "Ok")
        } catch (e: Exception) {
            Timber.e(e, "Error read config from file: ${e.message}")
            return WebhookImportResult(emptyList(), "Error: ${e.message}")
        }
    }

    fun readSenderConfigFromFile(uri: Uri): List<SenderConfig>? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val content = inputStream?.bufferedReader()?.use(BufferedReader::readText)
            inputStream?.close()

            if (content.isNullOrBlank()) return null

            json.decodeFromString<List<SenderConfig>>(content)
        } catch (e: Exception) {
            Timber.e(e, "Error read config from file: ${e.message}")
            null
        }
    }

    fun readSenderConfigFromFile(uri: Uri, currentSenders: List<SenderConfig> = emptyList()): SenderConfigImportResult {
        try {

            val fileContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                String(inputStream.readBytes())
            } ?: return SenderConfigImportResult(currentSenders, "Error: Reading File")


            val importedConfigs = jsonWithUnknownKeys.decodeFromString<List<SenderConfig>>(fileContent)

            if (importedConfigs.isEmpty()) {
                return SenderConfigImportResult(currentSenders, "Error: Not valid config")
            }


            val resultMap = currentSenders.associateBy { it.provider }.toMutableMap()

            importedConfigs.forEach { importedConfig ->
                resultMap[importedConfig.provider] = importedConfig
            }


            return SenderConfigImportResult(resultMap.values.toList(), "Import Ok")
        } catch (e: Exception) {
            Timber.e(e, "Error read config from file: ${e.message}")
            return SenderConfigImportResult(currentSenders, "Error: ${e.message}")
        }
    }


    fun exportWebhookToFile(webhooks: List<WebhookData>, uri: Uri): Boolean {
        return try {
            Timber.d("Exportando configuración de webhook es $webhooks")
            val webhookJson = json.encodeToString(webhooks)
            Timber.d("Webhook JSON: $webhookJson")
            val outputStream = context.contentResolver.openOutputStream(uri)

            outputStream?.use {
                it.write(webhookJson.toByteArray())
                it.flush()
            }

            true
        } catch (e: Exception) {
            Timber.e(e, "Error exportando configuración de webhook: ${e.message}")
            false
        }
    }

    fun exportSenderConfigToFile(configs: List<SenderConfig>, uri: Uri): Boolean {
        return try {
            val configJson = json.encodeToString(configs)
            val outputStream = context.contentResolver.openOutputStream(uri)

            outputStream?.use {
                it.write(configJson.toByteArray())
                it.flush()
            }

            true
        } catch (e: Exception) {
            Timber.e(e, "Error exportando configuración de proveedores: ${e.message}")
            false
        }
    }
}