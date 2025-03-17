package com.enderthor.kNotify.screens

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
import com.enderthor.kNotify.R
import com.enderthor.kNotify.data.ConfigData
import com.enderthor.kNotify.extension.loadPreferencesFlow
import com.enderthor.kNotify.extension.savePreferences
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseConfigScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val karooSystem = remember { KarooSystemService(context) }
    var karooConnected by remember { mutableStateOf(false) }


    var config by remember { mutableStateOf<ConfigData?>(null) }
    var isActive by remember { mutableStateOf(true) }
    var karooKey by remember { mutableStateOf("") }
    var delayBetweenNotifications by remember { mutableStateOf("0.0") }

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var ignoreAutoSave by remember { mutableStateOf(true) }
    var isConnecting by remember { mutableStateOf(false) }

    val settingsSaved = stringResource(R.string.settings_saved)


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
                    isActive = savedConfig.isActive
                    karooKey = savedConfig.karooKey
                    delayBetweenNotifications = savedConfig.delayIntents.toString()
                }
            }
        }
    }


    fun saveData() {
        if (ignoreAutoSave) return

        scope.launch {
            isLoading = true
            statusMessage = null

            try {
                val updatedConfig = config?.copy(
                    isActive = isActive,
                    karooKey = karooKey.trim(),
                    delayIntents = delayBetweenNotifications.toDoubleOrNull() ?: 0.0
                ) ?: ConfigData(
                    isActive = isActive,
                    karooKey = karooKey.trim(),
                    delayIntents = delayBetweenNotifications.toDoubleOrNull() ?: 0.0
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
            // Configuración de notificaciones
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
