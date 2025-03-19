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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.enderthor.kActions.R
import com.enderthor.kActions.data.ConfigData
import com.enderthor.kActions.data.ProviderType
import com.enderthor.kActions.data.SenderConfig
import com.enderthor.kActions.extension.Sender
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
    val sender = remember { Sender(karooSystem = null, configManager = configManager) }

    var senderConfig by remember { mutableStateOf<SenderConfig?>(null) }
    var configData by remember { mutableStateOf<ConfigData?>(null) }
    var selectedProvider by remember { mutableStateOf(ProviderType.TEXTBELT) }
    var apiKey by remember { mutableStateOf("") }
    var testPhoneNumber by remember { mutableStateOf("") } // Esta variable ya no será necesaria para entrada
    var savedPhoneNumber by remember { mutableStateOf("") } // Para guardar el teléfono de configuración
    var savedEmail by remember { mutableStateOf("") } // Para guardar el email de configuración
    var emailTo by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var ignoreAutoSave by remember { mutableStateOf(true) }

    var providerConfigs by remember { mutableStateOf<Map<ProviderType, SenderConfig>>(emptyMap()) }


    val test_message_content = stringResource(R.string.test_message_content)
    val error_sending_message = stringResource(R.string.error_sending_message)
    val test_message_sent = stringResource(R.string.test_message_sent)
    val settings_saved = stringResource(R.string.settings_saved)

    val isTextBeltFree = selectedProvider == ProviderType.TEXTBELT &&
            (apiKey.isBlank() || apiKey == "textbelt")


    LaunchedEffect(Unit) {


        launch {
            try {
                configManager.loadPreferencesFlow().collect { configDatas ->
                    configData = configDatas.firstOrNull()


                    if (configData?.phoneNumbers?.isNotEmpty() == true) {
                        savedPhoneNumber = configData?.phoneNumbers?.first() ?: ""
                    }

                    if (configData?.emails?.isNotEmpty() == true) {
                        savedEmail = configData?.emails?.first() ?: ""
                        emailTo = savedEmail
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
        apiKey = config?.apiKey ?: ""
        senderConfig = config
    }

    LaunchedEffect(selectedProvider) {
        updateFieldsForProvider()
    }


    fun saveData() {
        if (ignoreAutoSave) return

        scope.launch {
            isLoading = true
            statusMessage = null

            try {

                if (apiKey.isBlank() && selectedProvider == ProviderType.TEXTBELT) {
                    apiKey = "textbelt"
                }

                configData?.let { currentConfig ->
                    val updatedConfig = currentConfig.copy(
                        activeProvider = selectedProvider
                    )
                    configManager.savePreferences(mutableListOf(updatedConfig))
                }

                val updatedConfigs = ProviderType.entries.map { providerType ->
                    if (providerType == selectedProvider) {

                        SenderConfig(
                            provider = selectedProvider,
                            apiKey = apiKey
                        )
                    } else {

                        providerConfigs[providerType] ?: SenderConfig(provider = providerType)
                    }
                }

                configManager.saveSenderConfig(updatedConfigs.first())



                if (selectedProvider == ProviderType.RESEND) {
                    val updatedConfigData = configData?.copy(
                        emails = listOf(emailTo.trim())
                    ) ?: ConfigData(
                        emails = listOf(emailTo.trim())
                    )

                    configManager.savePreferences(mutableListOf(updatedConfigData))
                }

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


    fun sendTestMessage() {
        scope.launch {
            isLoading = true
            statusMessage = null

            val contactToUse = if (selectedProvider == ProviderType.RESEND) savedEmail else savedPhoneNumber

            try {
                val success = when (selectedProvider) {
                    ProviderType.CALLMEBOT -> sender.sendMessage(
                        phoneNumber = contactToUse,
                        message = test_message_content,
                        ProviderType.CALLMEBOT
                    )
                    ProviderType.WHAPI -> sender.sendMessage(
                        phoneNumber = contactToUse,
                        message = test_message_content,
                        ProviderType.WHAPI
                    )
                    ProviderType.TEXTBELT -> sender.sendSMSMessage(
                        phoneNumber = contactToUse,
                        message = test_message_content
                    )
                    ProviderType.RESEND -> sender.sendEmailMessage(
                        message = test_message_content
                    )
                }

                statusMessage = if (success) test_message_sent
                else error_sending_message
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message}"
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
                                    selectedProvider = ProviderType.TEXTBELT
                                    saveData()
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
                                    selectedProvider = ProviderType.CALLMEBOT
                                    saveData()
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
                                    selectedProvider = ProviderType.WHAPI
                                    saveData()
                                }
                            )
                            Text(
                                stringResource(R.string.whapi_provider),
                                modifier = Modifier.weight(1f)
                            )
                        }



                        Row(
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
                        }
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
                        }
                        ProviderType.WHAPI -> {
                            Text(stringResource(R.string.configure_whapi))
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
                        }
                        ProviderType.TEXTBELT -> {
                            Text(stringResource(R.string.configure_textbelt))
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
                            Text(stringResource(R.string.email_to))
                            OutlinedTextField(
                                value = emailTo,
                                onValueChange = {
                                    emailTo = it
                                    saveData()
                                },
                                label = { Text(stringResource(R.string.email_to)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (!it.isFocused) saveData() },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { saveData() })
                            )
                        }
                    }
                }
            }

            if (apiKey.isNotBlank()) {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.test_sending),
                            style = MaterialTheme.typography.titleMedium
                        )

                        if (selectedProvider != ProviderType.RESEND) {
                            Text(
                                "Se enviará a: $savedPhoneNumber",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                "Se enviará a: $savedEmail",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Button(
                            onClick = { sendTestMessage() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading &&
                                    ((selectedProvider == ProviderType.RESEND && savedEmail.isNotBlank()) ||
                                            (selectedProvider != ProviderType.RESEND && savedPhoneNumber.isNotBlank()))
                        ) {
                            Text(stringResource(R.string.send_test_message))
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