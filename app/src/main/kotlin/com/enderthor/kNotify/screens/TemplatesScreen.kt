package com.enderthor.kNotify.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.enderthor.kNotify.data.ConfigData
import com.enderthor.kNotify.extension.loadPreferencesFlow
import com.enderthor.kNotify.extension.savePreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<ConfigData?>(null) }
    var startMessage by remember { mutableStateOf("I started a ride") }
    var stopMessage by remember { mutableStateOf("I finished my ride") }
    var pauseMessage by remember { mutableStateOf("I paused my ride") }
    var resumeMessage by remember { mutableStateOf("I resumed my ride") }
    var statusMessage by remember { mutableStateOf<String?>(null) }


    var ignoreAutoSave by remember { mutableStateOf(true) }


    LaunchedEffect(Unit) {
        context.loadPreferencesFlow().collect { configs ->
            if (configs.isNotEmpty()) {
                val savedConfig = configs.first()
                config = savedConfig
                startMessage = savedConfig.startMessage
                stopMessage = savedConfig.stopMessage
                pauseMessage = savedConfig.pauseMessage
                resumeMessage = savedConfig.resumeMessage

                ignoreAutoSave = false
            }
        }
    }


    fun saveData() {
        if (ignoreAutoSave) return

        val updatedConfig = config?.copy(
            startMessage = startMessage.trim(),
            stopMessage = stopMessage.trim(),
            pauseMessage = pauseMessage.trim(),
            resumeMessage = resumeMessage.trim()
        ) ?: ConfigData(
            startMessage = startMessage.trim(),
            stopMessage = stopMessage.trim(),
            pauseMessage = pauseMessage.trim(),
            resumeMessage = resumeMessage.trim()
        )

        scope.launch {
            savePreferences(context, mutableListOf(updatedConfig))
            statusMessage = "Messages saved automatically"

            kotlinx.coroutines.delay(2000)
            statusMessage = null
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
                    "Message Templates",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "Configure the messages that will be sent for each ride event",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = startMessage,
                    onValueChange = { startMessage = it },
                    label = { Text("Start Message") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) saveData() }
                        .focusable(),
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveData() })
                )

                OutlinedTextField(
                    value = pauseMessage,
                    onValueChange = { pauseMessage = it },
                    label = { Text("Pause Message") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) saveData() }
                        .focusable(),
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveData() })
                )

                OutlinedTextField(
                    value = resumeMessage,
                    onValueChange = { resumeMessage = it },
                    label = { Text("Resume Message") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) saveData() }
                        .focusable(),
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveData() })
                )

                OutlinedTextField(
                    value = stopMessage,
                    onValueChange = { stopMessage = it },
                    label = { Text("Stop Message") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (!it.isFocused) saveData() }
                        .focusable(),
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveData() })
                )
            }

            statusMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}