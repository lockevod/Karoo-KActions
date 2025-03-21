package com.enderthor.kActions.extension.managers

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enderthor.kActions.activity.dataStore
import com.enderthor.kActions.data.ConfigData
import com.enderthor.kActions.data.SenderConfig
import com.enderthor.kActions.data.WebhookData
import com.enderthor.kActions.data.defaultSenderConfig
import com.enderthor.kActions.data.defaultWebhookData
import com.enderthor.kActions.data.getPreviewConfigData
import com.enderthor.kActions.extension.jsonWithUnknownKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber


class ConfigurationManager(
    private val context: Context,
) {
    private val preferencesKey = stringPreferencesKey("configdata")
    private val senderConfigKey = stringPreferencesKey("sender")
    private val webhookKey = stringPreferencesKey("webhook")

    private val fileHandler = ConfigFileHandler(context)



    suspend fun savePreferences(configDatas: MutableList<ConfigData>) {
        context.dataStore.edit { t ->
            t[preferencesKey] = Json.encodeToString(configDatas)
        }
    }

    fun loadPreferencesFlow(): Flow<List<ConfigData>> {
        return context.dataStore.data.map { settingsJson ->
            try {
                jsonWithUnknownKeys.decodeFromString<List<ConfigData>>(
                    settingsJson[preferencesKey] ?: getPreviewConfigData(context)
                )

            } catch(e: Throwable){
                Timber.tag("kpower").e(e, "Failed to read preferences Flow Extension")
                jsonWithUnknownKeys.decodeFromString<List<ConfigData>>(getPreviewConfigData(context))
            }
        }.distinctUntilChanged()
    }

    suspend fun saveWebhookData(configDatas: MutableList<WebhookData>) {
        context.dataStore.edit { t ->
            t[webhookKey] = Json.encodeToString(configDatas)
        }
    }


    fun loadWebhookDataFlow(): Flow<List<WebhookData>> {
        return context.dataStore.data.map { settingsJson ->
            try {
                jsonWithUnknownKeys.decodeFromString<List<WebhookData>>(
                    settingsJson[webhookKey] ?: defaultWebhookData
                )

            } catch(e: Throwable){
                Timber.tag("kpower").e(e, "Failed to read preferences Flow Extension")
                jsonWithUnknownKeys.decodeFromString<List<WebhookData>>(defaultWebhookData)
            }
        }.distinctUntilChanged()
    }


    suspend fun saveSenderConfig(configs: MutableList<SenderConfig>) {
        context.dataStore.edit { preferences ->
            preferences[senderConfigKey] = Json.encodeToString(configs)
        }
    }

    fun loadSenderConfigFlow(): Flow<List<SenderConfig>> {
        return context.dataStore.data.map { settingsJson ->
            try {
                jsonWithUnknownKeys.decodeFromString<List<SenderConfig>>(
                    settingsJson[senderConfigKey] ?: defaultSenderConfig
                )

            } catch(e: Throwable){
                Timber.tag("kpower").e(e, "Failed to read preferences Flow Extension")
                jsonWithUnknownKeys.decodeFromString<List<SenderConfig>>(defaultSenderConfig)
            }
        }.distinctUntilChanged()
    }


    suspend fun importWebhooksFromFile(uri: Uri, currentWebhooks: List<WebhookData>): Pair<Boolean, String> {
        return try {
            val result =  fileHandler.readWebhookFromFile(uri, currentWebhooks)

            if (result.webhooks.isEmpty() || result.status.startsWith("Error")) {
                Pair(false, result.status)
            } else {
                saveWebhookData(result.webhooks.toMutableList())
                Pair(true, result.status)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error import webhooks: ${e.message}")
            Pair(false, "Error: ${e.message}")
        }
    }

    suspend fun importSenderConfigFromFile(uri: Uri, currentSenders: List<SenderConfig>): Pair<Boolean, String> {
        return try {
            val result =  fileHandler.readSenderConfigFromFile(uri, currentSenders)

            if (result.senderConfigs.isEmpty() || result.status.startsWith("Error")) {
                Pair(false, result.status)
            } else {
                saveSenderConfig(result.senderConfigs.toMutableList())
                Pair(true, result.status)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error import Senders: ${e.message}")
            Pair(false, "Error: ${e.message}")
        }
    }


    suspend fun exportWebhooksToFile(uri: Uri): Boolean {
        try {
            val configs = loadWebhookDataFlow().first()
            Timber.d("Exportando configuraciones de webhooks $configs")
            return fileHandler.exportWebhookToFile(configs, uri)
        } catch (e: Exception) {
            Timber.e(e, "Error exportando configuraciones: ${e.message}")
            return false
        }
    }


    suspend fun exportSenderConfigToFile(uri: Uri): Boolean {
        try {
            val configs = loadSenderConfigFlow().first()
            return fileHandler.exportSenderConfigToFile(configs, uri)
        } catch (e: Exception) {
            Timber.e(e, "Error exportando configuraciones: ${e.message}")
            return false
        }
    }



}