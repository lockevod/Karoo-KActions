package com.enderthor.kActions.extension.managers

import android.content.Context
import com.enderthor.kActions.data.ConfigData
import com.enderthor.kActions.data.SenderConfig
import com.enderthor.kActions.data.Template
import com.enderthor.kActions.extension.Sender
import com.enderthor.kActions.extension.loadPreferencesFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber

class ConfigurationManager(
    private val context: Context,
    private val sender: Sender
) {
    // Flujos de configuración
    fun loadConfigDataFlow(): Flow<List<ConfigData>> {
        return context.loadPreferencesFlow()
    }

    fun loadSenderConfigFlow(): Flow<SenderConfig?> {
        return sender.getSenderConfigFlow()
    }

    fun loadTemplatesFlow(): Flow<List<Template>> {
        return sender.getTemplatesFlow()
    }

    // Métodos de inicialización
    suspend fun loadInitialConfigurations(): Triple<List<ConfigData>, SenderConfig?, List<Template>> {
        return try {
            // Usar first() para obtener el primer valor de cada flujo
            val configs = loadConfigDataFlow().first()
            Timber.d("Loaded configurations: $configs")

            val senderConfig = loadSenderConfigFlow().first()
            Timber.d("Loaded Sender configuration: ${senderConfig != null}")

            val templates = loadTemplatesFlow().first()
            Timber.d("Loaded ${templates.size} templates")

            Triple(configs, senderConfig, templates)
        } catch (e: Exception) {
            Timber.e(e, "Error loading configurations")
            Triple(emptyList(), null, emptyList())
        }
    }
}