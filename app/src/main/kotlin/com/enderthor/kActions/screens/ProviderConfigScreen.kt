package com.enderthor.kActions.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.enderthor.kActions.R
import com.enderthor.kActions.data.ConfigData
import com.enderthor.kActions.data.ProviderType
import com.enderthor.kActions.data.SenderConfig
import com.enderthor.kActions.extension.managers.ConfigurationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configManager = remember { ConfigurationManager(context) }


    var senderConfig by remember { mutableStateOf<SenderConfig?>(null) }
    var configData by remember { mutableStateOf<ConfigData?>(null) }
    var selectedProvider by remember { mutableStateOf(ProviderType.TEXTBELT) }
    var apiKey by remember { mutableStateOf("") }
    var savedPhoneNumber by remember { mutableStateOf("") }
    var savedEmail by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var ignoreAutoSave by remember { mutableStateOf(true) }

    var providerConfigs by remember { mutableStateOf<Map<ProviderType, SenderConfig>>(emptyMap()) }

    val error_sending_message = stringResource(R.string.error_sending_message)
    val settings_saved = stringResource(R.string.settings_saved)
    val isTextBeltFree = selectedProvider == ProviderType.TEXTBELT &&
            (apiKey.isBlank() || apiKey == "textbelt")
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current


    LaunchedEffect(Unit) {
        launch {
            delay(500)
            Timber.d("Cambiando ignoreAutoSave de $ignoreAutoSave a false")
            ignoreAutoSave = false  // Esto debe ejecutarse siempre
        }

        launch {
            try {
                configManager.loadPreferencesFlow().collect { configDatas ->
                    configData = configDatas.firstOrNull()

                    // Actualizar las variables con los datos de configuración
                    if (configData?.phoneNumbers?.isNotEmpty() == true) {
                        savedPhoneNumber = configData?.phoneNumbers?.first() ?: ""
                    }

                    if (configData?.emails?.isNotEmpty() == true) {
                        savedEmail = configData?.emails?.first() ?: ""
                    }

                    val newSelectedProvider = configData?.activeProvider ?: ProviderType.TEXTBELT
                    if (selectedProvider != newSelectedProvider) {
                        selectedProvider = newSelectedProvider
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error cargando configuración")
            }
        }
        launch {
            try {
                configManager.loadSenderConfigFlow().collect { configs ->

                    providerConfigs = configs.associateBy { it.provider }


                    val config = providerConfigs[selectedProvider]
                    apiKey = config?.apiKey ?: ""
                    senderConfig = config
                }

                delay(500)
                ignoreAutoSave = false
            } catch (e: Exception) {
                Timber.e(e, "Error cargando configuración de proveedores")
            }
        }
    }

    fun updateFieldsForProvider() {
        val config = providerConfigs[selectedProvider]
        if (config != null) {
            Timber.d("Actualizando campos para proveedor $selectedProvider con apiKey: ${config.apiKey}")
            apiKey = config.apiKey
            senderConfig = config
        } else {
            Timber.d("No hay configuración para el proveedor $selectedProvider")
            apiKey = ""
            senderConfig = null
        }
    }

    LaunchedEffect(selectedProvider) {
        updateFieldsForProvider()
    }


    fun saveData() {
        Timber.d("saveData llamado, ignoreAutoSave=$ignoreAutoSave")
        if (ignoreAutoSave) {
            Timber.d("saveData - Ignorando guardado debido a ignoreAutoSave=true")
            return
        }

        scope.launch {
            isLoading = true
            statusMessage = null

            try {
                // Configurar API key para TextBelt si está vacía
                val currentApiKey = if (apiKey.isBlank() && selectedProvider == ProviderType.TEXTBELT) {
                    "textbelt"
                } else {
                    apiKey
                }

                // Actualizar configuración activa
                configData?.let { currentConfig ->
                    val updatedConfig = currentConfig.copy(
                        activeProvider = selectedProvider
                    )
                    configManager.savePreferences(mutableListOf(updatedConfig))
                }

                // Crear lista actualizada de configuraciones
                val updatedConfigs = mutableListOf<SenderConfig>()

                // Mantener todas las configuraciones existentes
                ProviderType.entries.forEach { providerType ->
                    if (providerType == selectedProvider) {
                        // Actualizar solo el proveedor seleccionado
                        updatedConfigs.add(SenderConfig(providerType, currentApiKey))
                    } else {
                        // Mantener configuración existente para otros proveedores
                        val existingConfig = providerConfigs[providerType]
                        if (existingConfig != null) {
                            updatedConfigs.add(existingConfig)
                        } else {
                            updatedConfigs.add(SenderConfig(providerType, ""))
                        }
                    }
                }

                Timber.d("Guardando configuraciones: $updatedConfigs")
                configManager.saveSenderConfig(updatedConfigs)

                statusMessage = settings_saved
                delay(2000)
                statusMessage = null
            } catch (e: Exception) {
                statusMessage = error_sending_message
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        stringResource(R.string.message_provider),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedProvider == ProviderType.TEXTBELT,
                                onClick = {
                                    if (selectedProvider != ProviderType.TEXTBELT) {
                                        // Guardar la configuración actual antes de cambiar
                                        if (!ignoreAutoSave) {
                                            saveData()
                                        }

                                        // Cambiar al nuevo proveedor
                                        selectedProvider = ProviderType.TEXTBELT

                                        // Actualizar UI con la configuración del nuevo proveedor
                                        updateFieldsForProvider()
                                    }
                                }
                            )
                            Text(
                                stringResource(R.string.textbelt_provider),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedProvider == ProviderType.CALLMEBOT,
                                onClick = {
                                    if (selectedProvider != ProviderType.CALLMEBOT) {
                                        // Guardar la configuración actual antes de cambiar
                                        if (!ignoreAutoSave) {
                                            saveData()
                                        }

                                        // Cambiar al nuevo proveedor
                                        selectedProvider = ProviderType.CALLMEBOT

                                        // Actualizar UI con la configuración del nuevo proveedor
                                        updateFieldsForProvider()
                                    }
                                }
                            )
                            Text(
                                stringResource(R.string.callmebot_provider),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedProvider == ProviderType.WHAPI,
                                onClick = {
                                    if (selectedProvider != ProviderType.WHAPI) {
                                        // Guardar la configuración actual antes de cambiar
                                        if (!ignoreAutoSave) {
                                            saveData()
                                        }

                                        // Cambiar al nuevo proveedor
                                        selectedProvider = ProviderType.WHAPI

                                        // Actualizar UI con la configuración del nuevo proveedor
                                        updateFieldsForProvider()
                                    }
                                }
                            )
                            Text(
                                stringResource(R.string.whapi_provider),
                                modifier = Modifier.weight(1f)
                            )
                        }



                       /* Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedProvider == ProviderType.RESEND,
                                onClick = {
                                    selectedProvider = ProviderType.RESEND
                                    saveData()
                                }
                            )
                            Text(
                                stringResource(R.string.resend_provider),
                                modifier = Modifier.weight(1f)
                            )
                        }*/
                    }
                }
            }


            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.provider_configuration),
                        style = MaterialTheme.typography.titleMedium
                    )

                    when (selectedProvider) {
                        ProviderType.CALLMEBOT -> {
                            Text(stringResource(R.string.configure_callmebot))
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = {
                                    apiKey = it
                                },
                                label = { Text(stringResource(R.string.api_key)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged {  if (!it.isFocused) {
                                        keyboardController?.hide()
                                        saveData()
                                    } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions( onDone = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    saveData()
                                })
                            )
                        }
                        ProviderType.WHAPI -> {
                            Text(stringResource(R.string.configure_whapi))
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = {
                                    apiKey = it
                                },
                                label = { Text(stringResource(R.string.api_key)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (!it.isFocused) {
                                        keyboardController?.hide()
                                        saveData()
                                    } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    saveData() })
                            )
                        }
                        ProviderType.TEXTBELT -> {
                            Text(stringResource(R.string.configure_textbelt))
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = {
                                    apiKey = it
                                },
                                label = { Text(stringResource(R.string.api_key)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (!it.isFocused) {
                                        keyboardController?.hide()
                                        saveData()
                                    } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    saveData() })
                            )

                            if (isTextBeltFree) {
                                Column {
                                    Text(
                                        stringResource(R.string.textbelt_free_warning),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        stringResource(R.string.textbelt_free_delay_warning),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        ProviderType.RESEND -> {
                            Text(stringResource(R.string.configure_resend))
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = {
                                    apiKey = it
                                    saveData()
                                },
                                label = { Text(stringResource(R.string.api_key)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (!it.isFocused) saveData() },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { saveData() })
                            )


                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Email configurado: $savedEmail",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Para modificarlo, ve a la pantalla de Configuración",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                    }
                }
            }


            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            statusMessage?.let { message ->
                Text(
                    text = message,
                    color = if (message.contains("Error"))
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}