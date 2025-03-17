/*package com.enderthor.kNotify.screens


import androidx.compose.foundation.focusable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.enderthor.kNotify.data.ConfigData
import com.enderthor.kNotify.data.SenderConfig
import com.enderthor.kNotify.extension.Sender
import com.enderthor.kNotify.extension.loadPreferencesFlow
import com.enderthor.kNotify.extension.savePreferences
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.enderthor.kNotify.R
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val karooSystem = remember { KarooSystemService(context) }
    var karooConnected by remember { mutableStateOf(false) }
    val sender = remember { Sender(context, karooSystem) }

    var config by remember { mutableStateOf<ConfigData?>(null) }
    var isActive by remember { mutableStateOf(true) }
    var notifyOnStart by remember { mutableStateOf(true) }
    var notifyOnStop by remember { mutableStateOf(true) }
    var notifyOnPause by remember { mutableStateOf(false) }
    var notifyOnResume by remember { mutableStateOf(false) }
    var karooKey by remember { mutableStateOf("") }
    var testPhoneNumber by remember { mutableStateOf("") }

    var senderConfig by remember { mutableStateOf<SenderConfig?>(null) }
    var apiKey by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var ignoreAutoSave by remember { mutableStateOf(true) }

    var isConnecting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {

        isConnecting = true
        karooSystem.connect { connected ->
            karooConnected = connected
            isConnecting = false
            if (connected) {
                Timber.d("Conectado a Karoo System en ConfigScreen")
            } else {
                Timber.e("Error conectando a Karoo System en ConfigScreen")
            }
        }

        launch {
            kotlinx.coroutines.delay(500)
            ignoreAutoSave = false
        }

        launch {
            context.loadPreferencesFlow().collect { configs ->
                if (configs.isNotEmpty()) {
                    val savedConfig = configs.first()
                    config = savedConfig
                    isActive = savedConfig.isActive
                    notifyOnStart = savedConfig.notifyOnStart
                    notifyOnStop = savedConfig.notifyOnStop
                    notifyOnPause = savedConfig.notifyOnPause
                    notifyOnResume = savedConfig.notifyOnResume
                    karooKey = savedConfig.karooKey
                }
            }
        }

        launch {
            try {
                val savedConfig = sender.getSenderConfig()
                senderConfig = savedConfig
                apiKey = savedConfig?.apiKey ?: ""
            } catch (e: Exception) {
                Timber.e(e, "Error loading sender config")
            }


            sender.getSenderConfigFlow().collect { updatedConfig ->
                senderConfig = updatedConfig
                if (updatedConfig != null) {
                    apiKey = updatedConfig.apiKey
                }
            }
        }
    }

    val errorKarooNotConnected = stringResource(R.string.error_karoo_not_connected)
    val testMessageContent = stringResource(R.string.test_message_content)
    val testMessageSent = stringResource(R.string.test_message_sent)
    val errorSendingMessage = stringResource(R.string.error_sending_message)
    val settingsSaved = stringResource(R.string.settings_saved)
    val connectingText = stringResource(R.string.connecting)
    val sendTestMessageText = stringResource(R.string.send_test_message)


    fun saveData() {
        if (ignoreAutoSave) {
            Timber.d("Guardado ignorado porque ignoreAutoSave = true")
            return
        }

        Timber.d("Guardando configuración...")

        scope.launch {
            isLoading = true
            statusMessage = null

            try {
                val updatedConfig = config?.copy(
                    isActive = isActive,
                    notifyOnStart = notifyOnStart,
                    notifyOnStop = notifyOnStop,
                    notifyOnPause = notifyOnPause,
                    notifyOnResume = notifyOnResume,
                    karooKey = karooKey.trim()
                ) ?: ConfigData(
                    isActive = isActive,
                    notifyOnStart = notifyOnStart,
                    notifyOnStop = notifyOnStop,
                    notifyOnPause = notifyOnPause,
                    notifyOnResume = notifyOnResume,
                    karooKey = karooKey.trim()
                )

                savePreferences(context, mutableListOf(updatedConfig))

                if (apiKey.isNotBlank()) {
                    val newSenderConfig = SenderConfig(
                        apiKey = apiKey.trim()
                    )

                    sender.saveSenderConfig(newSenderConfig)
                }

                statusMessage = settingsSaved
                kotlinx.coroutines.delay(2000)
                statusMessage = null
                Timber.d("Configuración guardada exitosamente")
            } catch (e: Exception) {
                Timber.e(e, "Error al guardar configuración")
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
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.notification_settings),
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.enable_notifications),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isActive,
                        onCheckedChange = {
                            isActive = it
                            saveData()
                        }
                    )
                }

                HorizontalDivider()
                /*
                Text(
                    stringResource(R.string.send_notifications_when),
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.ride_starts),
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(
                        checked = notifyOnStart,
                        onCheckedChange = {
                            notifyOnStart = it
                            saveData()
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.ride_ends),
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(
                        checked = notifyOnStop,
                        onCheckedChange = {
                            notifyOnStop = it
                            saveData()
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.ride_paused),
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(
                        checked = notifyOnPause,
                        onCheckedChange = {
                            notifyOnPause = it
                            saveData()
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.ride_resumed),
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(
                        checked = notifyOnResume,
                        onCheckedChange = {
                            notifyOnResume = it
                            saveData()
                        }
                    )
                }*/

                Text(
                    stringResource(R.string.enter_karoo_live_key),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = karooKey,
                    onValueChange = { karooKey = it },
                    label = { Text(stringResource(R.string.karoo_key)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) saveData() }
                        .focusable(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveData() })
                )
            }

            Text(
                stringResource(R.string.whapi_api_config),
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                stringResource(R.string.whapi_description),
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.whapi_api_key)) },
                placeholder = { Text("123456") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) saveData() }
                    .focusable(),
                singleLine = true,

                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { saveData() })
            )

            if (senderConfig != null && apiKey.isNotBlank()) {
                Text(
                    stringResource(R.string.test_sending),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )

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

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            statusMessage = null

                            try {
                                if (!karooConnected) {
                                    statusMessage = errorKarooNotConnected
                                    return@launch
                                }

                                val success = sender.sendMessage(
                                    phoneNumber = testPhoneNumber,
                                    message = testMessageContent
                                )

                                statusMessage = if (success) {
                                    testMessageSent
                                } else {
                                    errorSendingMessage
                                }
                            } catch (e: Exception) {
                                statusMessage = "Error: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && testPhoneNumber.isNotBlank() && karooConnected
                ) {
                    Text(
                        if (isConnecting)
                            connectingText
                        else
                            sendTestMessageText
                    )
                }


                if (isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                statusMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
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
}*/