package com.enderthor.kActions.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.enderthor.kActions.R
import com.enderthor.kActions.data.ConfigData
import com.enderthor.kActions.extension.loadPreferencesFlow
import com.enderthor.kActions.extension.savePreferences
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val karooSystem = remember { KarooSystemService(context) }
    var karooConnected by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }

    // BaseConfigScreen variables
    var isActive by remember { mutableStateOf(true) }
    var karooKey by remember { mutableStateOf("") }
    var delayBetweenNotifications by remember { mutableStateOf("0.0") }

    // ConfigurationScreen variables
    var phoneNumber1 by remember { mutableStateOf("") }
    var isPhone1Valid by remember { mutableStateOf(true) }
    var phone1ErrorMessage by remember { mutableStateOf("") }
    var startMessage by remember { mutableStateOf("") }
    var stopMessage by remember { mutableStateOf("") }
    var pauseMessage by remember { mutableStateOf("") }
    var resumeMessage by remember { mutableStateOf("") }

    // Shared variables
    var config by remember { mutableStateOf<ConfigData?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var ignoreAutoSave by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }

    // String resources
    val errorNoPlus = stringResource(R.string.error_no_plus)
    val errorDigitsOnly = stringResource(R.string.error_digits_only)
    val errorLength = stringResource(R.string.error_length)
    val settingsSaved = stringResource(R.string.settings_saved)
    val cannotSaveMessage = stringResource(R.string.cannot_save_invalid_format)

    LaunchedEffect(Unit) {
        isConnecting = true
        karooSystem.connect { connected ->
            karooConnected = connected
            isConnecting = false
            Timber.d(if (connected) "Conectado a Karoo System" else "Error conectando a Karoo System")
        }

        launch {
            delay(500)
            ignoreAutoSave = false
        }

        launch {
            context.loadPreferencesFlow().collect { configs ->
                if (configs.isNotEmpty()) {
                    val savedConfig = configs.first()
                    config = savedConfig

                    // BaseConfigScreen values
                    isActive = savedConfig.isActive
                    karooKey = savedConfig.karooKey
                    delayBetweenNotifications = savedConfig.delayIntents.toString()

                    // ConfigurationScreen values
                    val phoneNumbers = savedConfig.phoneNumbers
                    if (phoneNumbers.isNotEmpty()) {
                        phoneNumber1 = phoneNumbers[0]
                    }

                    startMessage = savedConfig.startMessage
                    stopMessage = savedConfig.stopMessage
                    pauseMessage = savedConfig.pauseMessage
                    resumeMessage = savedConfig.resumeMessage
                }
            }
        }
    }

    fun validatePhoneNumber(phoneNumber: String): Pair<Boolean, String> {
        val trimmedPhone = phoneNumber.trim()

        if (trimmedPhone.isEmpty()) {
            return Pair(true, "")
        }

        if (trimmedPhone.contains("+")) {
            return Pair(false, errorNoPlus)
        }

        if (!trimmedPhone.all { it.isDigit() }) {
            return Pair(false, errorDigitsOnly)
        }

        if (trimmedPhone.length < 10 || trimmedPhone.length > 15) {
            return Pair(false, errorLength)
        }

        return Pair(true, "")
    }

    fun saveData() {
        if (ignoreAutoSave) return

        val phone1Result = validatePhoneNumber(phoneNumber1)
        isPhone1Valid = phone1Result.first
        phone1ErrorMessage = phone1Result.second

        if (isPhone1Valid) {
            scope.launch {
                isLoading = true
                statusMessage = null

                try {
                    val phoneNumbers = listOfNotNull(
                        phoneNumber1.trim().takeIf { it.isNotBlank() }
                    )

                    val updatedConfig = config?.copy(
                        isActive = isActive,
                        karooKey = karooKey.trim(),
                        delayIntents = delayBetweenNotifications.toDoubleOrNull() ?: 0.0,
                        phoneNumbers = phoneNumbers,
                        startMessage = startMessage.trim(),
                        stopMessage = stopMessage.trim(),
                        pauseMessage = pauseMessage.trim(),
                        resumeMessage = resumeMessage.trim()
                    ) ?: ConfigData(
                        isActive = isActive,
                        karooKey = karooKey.trim(),
                        delayIntents = delayBetweenNotifications.toDoubleOrNull() ?: 0.0,
                        phoneNumbers = phoneNumbers,
                        startMessage = startMessage.trim(),
                        stopMessage = stopMessage.trim(),
                        pauseMessage = pauseMessage.trim(),
                        resumeMessage = resumeMessage.trim()
                    )

                    savePreferences(context, mutableListOf(updatedConfig))
                    statusMessage = settingsSaved
                    delay(2000)
                    statusMessage = null
                } catch (e: Exception) {
                    statusMessage = "Error: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        } else {
            statusMessage = cannotSaveMessage
            scope.launch {
                delay(3000)
                statusMessage = null
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Notifications Settings
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        stringResource(R.string.notification_settings),
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Activar/desactivar notificaciones
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

                    // Delay entre notificaciones
                    OutlinedTextField(
                        value = delayBetweenNotifications,
                        onValueChange = {
                            delayBetweenNotifications = it
                            saveData()
                        },
                        label = { Text(stringResource(R.string.notification_delay)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { saveData() })
                    )
                }
            }

            // 2. Phone Number Configuration
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.phone_numbers),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        stringResource(R.string.enter_up_to_3_phones),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    PhoneNumberInput(
                        value = phoneNumber1,
                        onValueChange = {
                            phoneNumber1 = it
                            val (valid, message) = validatePhoneNumber(it)
                            isPhone1Valid = valid
                            phone1ErrorMessage = message
                        },
                        label = stringResource(R.string.number_1),
                        isValid = isPhone1Valid,
                        errorMessage = phone1ErrorMessage,
                        onClear = {
                            phoneNumber1 = ""
                            isPhone1Valid = true
                            phone1ErrorMessage = ""
                            saveData()
                        },
                        onDone = { saveData() },
                        onFocusChange = { focusState ->
                            if (!focusState.isFocused) saveData()
                        }
                    )
                }
            }

            // 3. Karoo Tracking Key Configuration
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.karoo_configuration),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(stringResource(R.string.enter_karoo_live_key))

                    OutlinedTextField(
                        value = karooKey,
                        onValueChange = { karooKey = it },
                        label = { Text(stringResource(R.string.karoo_key)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused) saveData() },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { saveData() })
                    )
                }
            }

            // 4. Message Templates
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.message_templates),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        stringResource(R.string.configure_messages),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = startMessage,
                        onValueChange = { startMessage = it },
                        label = { Text(stringResource(R.string.start_message)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused) saveData() },
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = stopMessage,
                        onValueChange = { stopMessage = it },
                        label = { Text(stringResource(R.string.stop_message)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused) saveData() },
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = pauseMessage,
                        onValueChange = { pauseMessage = it },
                        label = { Text(stringResource(R.string.pause_message)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused) saveData() },
                        minLines = 2
                    )

                    OutlinedTextField(
                        value = resumeMessage,
                        onValueChange = { resumeMessage = it },
                        label = { Text(stringResource(R.string.resume_message)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused) saveData() },
                        minLines = 2
                    )
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

@Composable
fun PhoneNumberInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isValid: Boolean,
    errorMessage: String,
    onClear: () -> Unit,
    onDone: () -> Unit,
    onFocusChange: (FocusState) -> Unit
) {
    val phonePlaceholder = stringResource(R.string.phone_placeholder)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(phonePlaceholder) },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged(onFocusChange)
            .focusable(),
        singleLine = true,
        isError = !isValid,
        supportingText = {
            if (!isValid) {
                Text(errorMessage)
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() }
        ),
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear")
                }
            }
        }
    )
}