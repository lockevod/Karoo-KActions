package com.enderthor.kNotify.extension


import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enderthor.kNotify.activity.dataStore
import com.enderthor.kNotify.data.ConfigData
import com.enderthor.kNotify.data.WebhookData
import com.enderthor.kNotify.data.defaultConfigData
import com.enderthor.kNotify.data.defaultWebhookData
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.timeout

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds


val jsonWithUnknownKeys = Json { ignoreUnknownKeys = true }

val preferencesKey = stringPreferencesKey("configdata")
val webhookKey = stringPreferencesKey("webhook")

suspend fun savePreferences(context: Context, configDatas: MutableList<ConfigData>) {
    context.dataStore.edit { t ->
        t[preferencesKey] = Json.encodeToString(configDatas)
    }
}


fun Context.loadPreferencesFlow(): Flow<List<ConfigData>> {
    return dataStore.data.map { settingsJson ->
        try {
            jsonWithUnknownKeys.decodeFromString<List<ConfigData>>(
                settingsJson[preferencesKey] ?: defaultConfigData
            )

        } catch(e: Throwable){
            Timber.tag("kpower").e(e, "Failed to read preferences Flow Extension")
            jsonWithUnknownKeys.decodeFromString<List<ConfigData>>(defaultConfigData)
        }
    }.distinctUntilChanged()
}

suspend fun saveWebhookData(context: Context, configDatas: MutableList<WebhookData>) {
    context.dataStore.edit { t ->
        t[preferencesKey] = Json.encodeToString(configDatas)
    }
}


fun Context.loadWebhookDataFlow(): Flow<List<WebhookData>> {
    return dataStore.data.map { settingsJson ->
        try {
            jsonWithUnknownKeys.decodeFromString<List<WebhookData>>(
                settingsJson[webhookKey] ?: defaultWebhookData
            )

        } catch(e: Throwable){
            Timber.tag("kpower").e(e, "Failed to read preferences Flow Extension")
            jsonWithUnknownKeys.decodeFromString<List<WebhookData>>(defaultWebhookData)
        }
    }.distinctUntilChanged()
}



fun KarooSystemService.streamRide(): Flow<RideState> {
    return callbackFlow {
        val listenerId = addConsumer { event: RideState ->
            trySendBlocking(event)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

@OptIn(FlowPreview::class)
fun KarooSystemService.makeHttpRequest(method: String, url: String, queue: Boolean = false, headers: Map<String, String> = emptyMap(), body: ByteArray? = null): Flow<HttpResponseState.Complete> {
    val flow = callbackFlow {
        Timber.d("$method request to ${url}...")


        val listenerId = addConsumer(
            OnHttpResponse.MakeHttpRequest(
                method = method,
                url = url,
                waitForConnection = false,
                headers = headers,
                body = body
            ),
            onEvent = { event: OnHttpResponse ->

                if (event.state is HttpResponseState.Complete){
                    trySend(event.state as HttpResponseState.Complete)
                    close()
                }
            },
            onError = { s: String ->
                Timber.e("Failed to make http request: $s")
                close(IllegalStateException(s))
                Unit
            }
        )
        awaitClose {
            removeConsumer(listenerId)
        }
    }

    return if (queue){
        flow
    } else {
        flow.timeout(60.seconds).catch { e: Throwable ->
            if (e is TimeoutCancellationException){
                emit(HttpResponseState.Complete(500, mapOf(), null, "Timeout"))
            } else {
                throw e
            }
        }
    }
}



