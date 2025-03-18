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
import androidx.compose.ui.unit.dp
import com.enderthor.kActions.R
import com.enderthor.kActions.data.GpsCoordinates
import com.enderthor.kActions.data.WebhookData
import com.enderthor.kActions.extension.loadWebhookDataFlow
import com.enderthor.kActions.extension.makeHttpRequest
import com.enderthor.kActions.extension.saveWebhookData
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhookConfigScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val karooSystem = remember { KarooSystemService(context) }
    var karooConnected by remember { mutableStateOf(false) }

    var webhook by remember { mutableStateOf<WebhookData?>(null) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var postBody by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var actionOnStart by remember { mutableStateOf(true) }
    var actionOnStop by remember { mutableStateOf(false) }
    var actionOnPause by remember { mutableStateOf(false) }
    var actionOnResume by remember { mutableStateOf(false) }
    var actionOnCustom by remember { mutableStateOf(false) }
    var onlyIfLocation by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf(GpsCoordinates(0.0, 0.0)) }

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var ignoreAutoSave by remember { mutableStateOf(true) }
    var isConnecting by remember { mutableStateOf(false) }

    val settingsSaved = stringResource(R.string.settings_saved)
    val testSuccess = stringResource(R.string.webhook_test_success)
    val testError = stringResource(R.string.webhook_test_error)
    val urlError = stringResource(R.string.webhook_url_error)

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
            context.loadWebhookDataFlow().collect { webhooks ->
                if (webhooks.isNotEmpty()) {
                    val savedWebhook = webhooks.firstOrNull() ?: WebhookData()
                    webhook = savedWebhook
                    name = savedWebhook.name
                    url = savedWebhook.url
                    postBody = savedWebhook.post
                    enabled = savedWebhook.enabled
                    actionOnStart = savedWebhook.actionOnStart
                    actionOnStop = savedWebhook.actionOnStop
                    actionOnPause = savedWebhook.actionOnPause
                    actionOnResume = savedWebhook.actionOnResume
                    actionOnCustom = savedWebhook.actionOnCustom
                    onlyIfLocation = savedWebhook.onlyIfLocation
                    location = savedWebhook.location
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
                val updatedWebhook = webhook?.copy(
                    name = name.trim(),
                    url = url.trim(),
                    post = postBody.trim(),
                    enabled = enabled,
                    actionOnStart = actionOnStart,
                    actionOnStop = actionOnStop,
                    actionOnPause = actionOnPause,
                    actionOnResume = actionOnResume,
                    actionOnCustom = actionOnCustom,
                    onlyIfLocation = onlyIfLocation,
                    location = location
                ) ?: WebhookData(
                    name = name.trim(),
                    url = url.trim(),
                    post = postBody.trim(),
                    enabled = enabled,
                    actionOnStart = actionOnStart,
                    actionOnStop = actionOnStop,
                    actionOnPause = actionOnPause,
                    actionOnResume = actionOnResume,
                    actionOnCustom = actionOnCustom,
                    onlyIfLocation = onlyIfLocation,
                    location = location
                )

                saveWebhookData(context, mutableListOf(updatedWebhook))

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

    fun testWebhook() {
        scope.launch {
            isLoading = true
            statusMessage = null

            try {
                if (!url.startsWith("http")) {
                    statusMessage = urlError
                    isLoading = false
                    return@launch
                }

                val headers = mapOf("Content-Type" to "application/json")
                val body = if (postBody.isBlank()) null else postBody.toByteArray()

                val response = karooSystem.makeHttpRequest(
                    method = "POST",
                    url = url,
                    headers = headers,
                    body = body
                ).first()

                statusMessage = if (response.statusCode in 200..299) {
                    testSuccess
                } else {
                    "$testError (${response.statusCode})"
                }
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
                        stringResource(R.string.webhook_config_title),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.enable_webhook),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                saveData()
                            }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.webhook_only_at_location), modifier = Modifier.weight(1f))
                        Switch(
                            checked = onlyIfLocation,
                            onCheckedChange = {
                                onlyIfLocation = it
                                saveData()
                            }
                        )
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            saveData()
                        },
                        label = { Text(stringResource(R.string.webhook_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused) saveData() },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { saveData() })
                    )

                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            saveData()
                        },
                        label = { Text(stringResource(R.string.webhook_url)) },
                        placeholder = { Text(stringResource(R.string.webhook_url_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused) saveData() },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { saveData() })
                    )

                    OutlinedTextField(
                        value = postBody,
                        onValueChange = {
                            postBody = it
                            saveData()
                        },
                        label = { Text(stringResource(R.string.webhook_post_body)) },
                        placeholder = { Text(stringResource(R.string.webhook_post_body_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .onFocusChanged { if (!it.isFocused) saveData() },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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
                        stringResource(R.string.webhook_trigger_events),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.webhook_on_start), modifier = Modifier.weight(1f))
                        Switch(
                            checked = actionOnStart,
                            onCheckedChange = {
                                actionOnStart = it
                                saveData()
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.webhook_on_stop), modifier = Modifier.weight(1f))
                        Switch(
                            checked = actionOnStop,
                            onCheckedChange = {
                                actionOnStop = it
                                saveData()
                            }
                        )
                    }

                   /*Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.webhook_on_pause), modifier = Modifier.weight(1f))
                        Switch(
                            checked = actionOnPause,
                            onCheckedChange = {
                                actionOnPause = it
                                saveData()
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.webhook_on_resume), modifier = Modifier.weight(1f))
                        Switch(
                            checked = actionOnResume,
                            onCheckedChange = {
                                actionOnResume = it
                                saveData()
                            }
                        )
                    }*/

                }
            }

            if (url.isNotBlank()) {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.webhook_test_section),
                            style = MaterialTheme.typography.titleMedium
                        )

                        Button(
                            onClick = { testWebhook() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading && url.isNotBlank()
                        ) {
                            Text(stringResource(R.string.webhook_send_test))
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