package com.enderthor.kActions.screens

import android.net.Uri
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
import com.enderthor.kActions.data.GpsCoordinates
import com.enderthor.kActions.data.WebhookData
import com.enderthor.kActions.extension.makeHttpRequest
import com.enderthor.kActions.extension.managers.ConfigurationManager
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import timber.log.Timber
import java.io.File


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhookConfigScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val karooSystem = remember { KarooSystemService(context) }
    var karooConnected by remember { mutableStateOf(false) }
    val configManager = remember { ConfigurationManager(context) }
    var webhook by remember { mutableStateOf<WebhookData?>(null) }
    var savedWebhooks by remember { mutableStateOf<List<WebhookData>>(emptyList()) }
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
    var header by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var ignoreAutoSave by remember { mutableStateOf(true) }
    var isConnecting by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val settingsSaved = stringResource(R.string.settings_saved)
    val testSuccess = stringResource(R.string.webhook_test_success)
    val testError = stringResource(R.string.webhook_test_error)
    val urlError = stringResource(R.string.webhook_url_error)
    val webhookExportedSuccess = stringResource(R.string.config_exported_path)
    val webhooksExportError = stringResource(R.string.webhooks_export_error)
    val webhooksImportedSuccess=stringResource(R.string.webhooks_imported_success)
    val webhooksImportedError=stringResource(R.string.webhooks_import_error)


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
            configManager.loadWebhookDataFlow().collect { webhooks ->
                if (webhooks.isNotEmpty()) {
                    savedWebhooks= webhooks
                    val savedWebhook = webhooks.first()
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
                    header = savedWebhook.header
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
                    header = header.trim(),
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

                configManager.saveWebhookData(mutableListOf(updatedWebhook))

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


                val customHeaders = if (header.isNotBlank()) {
                    header.split("\n").mapNotNull { line ->
                        val parts = line.split(":", limit = 2)
                        if (parts.size == 2) {
                            parts[0].trim() to parts[1].trim()
                        } else null
                    }.toMap()
                } else emptyMap()


                val contentType = customHeaders["Content-Type"]?.lowercase()


                val body = when {
                    postBody.isBlank() -> null
                    contentType?.contains("json") == true -> {

                        if (postBody.trim().startsWith("{") && postBody.trim().endsWith("}")) {

                            postBody.toByteArray()
                        } else {

                            try {
                                val jsonObject = buildJsonObject {
                                    postBody.split("\n").forEach { line ->
                                        val parts = line.split(":", limit = 2)
                                        if (parts.size == 2) {
                                            put(parts[0].trim(), JsonPrimitive(parts[1].trim()))
                                        }
                                    }
                                }
                                jsonObject.toString().toByteArray()
                            } catch (e: Exception) {
                                postBody.toByteArray()
                            }
                        }
                    }
                    else -> postBody.toByteArray()
                }


                val headers = customHeaders

                Timber.d("Headers: $headers")
                Timber.d("Body: ${body?.decodeToString()}")
                val response = karooSystem.makeHttpRequest(
                    method = "POST",
                    url = url,
                    headers = headers,
                    body = body
                ).first()

                Timber.d("Response: ${response.statusCode} ${response.body?.decodeToString()}")

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
                        },
                        label = { Text(stringResource(R.string.webhook_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged {  if (!it.isFocused) {
                                keyboardController?.hide()
                                saveData()
                            } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            saveData()
                        })
                    )

                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                        },
                        label = { Text(stringResource(R.string.webhook_url)) },
                        placeholder = { Text(stringResource(R.string.webhook_url_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged {  if (!it.isFocused) {
                                keyboardController?.hide()
                                saveData()
                            } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            saveData()
                        })
                    )

                    OutlinedTextField(
                        value = header,
                        onValueChange = {
                            header = it
                        },
                        label = { Text(stringResource(R.string.webhook_headers)) },
                        placeholder = { Text(stringResource(R.string.webhook_headers_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused) {
                                saveData()
                            }},
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            saveData()
                        })
                    )

                    OutlinedTextField(
                        value = postBody,
                        onValueChange = {
                            postBody = it
                        },
                        label = { Text(stringResource(R.string.webhook_post_body)) },
                        placeholder = { Text(stringResource(R.string.webhook_post_body_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .onFocusChanged {  if (!it.isFocused) {
                                keyboardController?.hide()
                                saveData()
                            } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            saveData()
                        })
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


            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            Timber.d("Importing webhooks from internal storage")
                            scope.launch {
                                isLoading = true
                                try {
                                    val file = File(context.getExternalFilesDir(null), "webhook_config.json")
                                    val uri = Uri.fromFile(file)
                                    val (success,message) = configManager.importWebhooksFromFile(uri,savedWebhooks)
                                    statusMessage = if (success)
                                        webhooksImportedSuccess
                                    else
                                        "$webhooksImportedError: $message"
                                } catch (e: Exception) {
                                    statusMessage =
                                        webhooksImportedError + " ${e.message ?: ""}"
                                } finally {
                                    isLoading = false
                                    delay(2000)
                                    statusMessage = null
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.import_webhooks))
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    val file = File(context.getExternalFilesDir(null), "webhook_config.json")
                                    val uri = Uri.fromFile(file)
                                    val success = configManager.exportWebhooksToFile(uri)
                                    statusMessage = if (success)
                                        webhookExportedSuccess +" ${file.absolutePath}"
                                    else
                                        webhooksExportError
                                } catch (e: Exception) {
                                    statusMessage = webhooksExportError +
                                            " ${e.message ?: ""}"
                                } finally {
                                    isLoading = false
                                    delay(20000)
                                    statusMessage = null
                                }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.export_webhooks))
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