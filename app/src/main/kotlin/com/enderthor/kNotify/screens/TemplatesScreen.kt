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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.enderthor.kNotify.data.ConfigData
import com.enderthor.kNotify.extension.loadPreferencesFlow
import com.enderthor.kNotify.extension.savePreferences
import com.enderthor.kNotify.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()


    val messageTemplatesTitle = stringResource(R.string.message_templates)
    val configureMessagesDesc = stringResource(R.string.configure_messages)
    val startMessageLabel = stringResource(R.string.start_message)
    val pauseMessageLabel = stringResource(R.string.pause_message)
    val resumeMessageLabel = stringResource(R.string.resume_message)
    val stopMessageLabel = stringResource(R.string.stop_message)
    val messagesSavedText = stringResource(R.string.messages_saved)


    val defaultStartMsg = stringResource(R.string.default_start_message)
    val defaultStopMsg = stringResource(R.string.default_stop_message)
    val defaultPauseMsg = stringResource(R.string.default_pause_message)
    val defaultResumeMsg = stringResource(R.string.default_resume_message)

    var config by remember { mutableStateOf<ConfigData?>(null) }
    var startMessage by remember { mutableStateOf(defaultStartMsg) }
    var stopMessage by remember { mutableStateOf(defaultStopMsg) }
    var pauseMessage by remember { mutableStateOf(defaultPauseMsg) }
    var resumeMessage by remember { mutableStateOf(defaultResumeMsg) }
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
            statusMessage = messagesSavedText

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
                    messageTemplatesTitle,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    configureMessagesDesc,
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = startMessage,
                    onValueChange = { startMessage = it },
                    label = { Text(startMessageLabel) },
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
                    label = { Text(pauseMessageLabel) },
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
                    label = { Text(resumeMessageLabel) },
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
                    label = { Text(stopMessageLabel) },
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