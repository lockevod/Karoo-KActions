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
import com.enderthor.kActions.data.ProviderType
import com.enderthor.kActions.data.SenderConfig
import com.enderthor.kActions.extension.Sender
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sender = remember { Sender(context, null) }


    var senderConfig by remember { mutableStateOf<SenderConfig?>(null) }
    var selectedProvider by remember { mutableStateOf(ProviderType.WHAPI) }
    var apiKey by remember { mutableStateOf("") }
    var emailTo by remember { mutableStateOf("") }
    var emailFrom by remember { mutableStateOf("") }
    var testPhoneNumber by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var ignoreAutoSave by remember { mutableStateOf(true) }

    val test_message_content = stringResource(R.string.test_message_content)
    val error_sending_message = stringResource(R.string.error_sending_message)
    val test_message_sent = stringResource(R.string.test_message_sent)
    val settings_saved = stringResource(R.string.settings_saved)



    LaunchedEffect(Unit) {
        launch {
            try {
                val savedConfig = sender.getSenderConfig()
                senderConfig = savedConfig
                selectedProvider = savedConfig?.provider ?: ProviderType.WHAPI
                apiKey = savedConfig?.apiKey ?: ""
                emailTo = savedConfig?.emailTo ?: ""
                emailFrom = savedConfig?.emailFrom ?: ""

                delay(500)
                ignoreAutoSave = false
            } catch (e: Exception) {
                Timber.e(e, "Error cargando configuración del remitente")
            }
        }
    }


    val isTextBeltFree = selectedProvider == ProviderType.TEXTBELT &&
                        (apiKey.isBlank() || apiKey == "textbelt")


    fun saveData() {
        if (ignoreAutoSave) return

        scope.launch {
            isLoading = true
            statusMessage = null

            try {
                val newSenderConfig = SenderConfig(
                    apiKey = apiKey.trim(),
                    provider = selectedProvider,
                    emailTo = emailTo.trim(),
                    emailFrom = emailFrom.trim()
                )

                sender.saveSenderConfig(newSenderConfig)

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

            try {
                val success = when (selectedProvider) {
                    ProviderType.WHAPI -> sender.sendMessage(
                        phoneNumber = testPhoneNumber,
                        message = test_message_content
                    )
                    ProviderType.TEXTBELT -> sender.sendSMSMessage(
                        phoneNumber = testPhoneNumber,
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
                        ProviderType.WHAPI -> {
                            Text(stringResource(R.string.configure_whapi))
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = {
                                    apiKey = it
                                    saveData()
                                },
                                label = { Text(stringResource(R.string.whapi_api_key)) },
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
                                label = { Text(stringResource(R.string.whapi_api_key)) },
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
                                label = { Text(stringResource(R.string.whapi_api_key)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (!it.isFocused) saveData() },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { saveData() })
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.email_from))
                            OutlinedTextField(
                                value = emailFrom,
                                onValueChange = {
                                    emailFrom = it
                                    saveData()
                                },
                                label = { Text(stringResource(R.string.email_from)) },
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

            // Test del mensaje
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
                            OutlinedTextField(
                                value = testPhoneNumber,
                                onValueChange = { testPhoneNumber = it },
                                label = { Text(stringResource(R.string.test_phone_number)) },
                                placeholder = { Text(stringResource(R.string.phone_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Done
                                )
                            )
                        }

                        Button(
                            onClick = { sendTestMessage() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading &&
                                    (selectedProvider == ProviderType.RESEND || testPhoneNumber.isNotBlank())
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