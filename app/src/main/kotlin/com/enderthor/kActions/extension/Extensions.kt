package com.enderthor.kActions.extension


import com.enderthor.kActions.data.GpsCoordinates
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnGlobalPOIs
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.timeout
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds


val jsonWithUnknownKeys = Json { ignoreUnknownKeys = true }


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

fun KarooSystemService.streamLocation(): Flow<OnLocationChanged> {
    return callbackFlow {
        val listenerId = addConsumer { event: OnLocationChanged ->
            trySendBlocking(event)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}

fun KarooSystemService.streamPOIs(): Flow<OnGlobalPOIs> {
    return callbackFlow {
        val listenerId = addConsumer { event: OnGlobalPOIs  ->
            trySendBlocking(event)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}


fun KarooSystemService.getGpsFlow(): Flow<GpsCoordinates> {
    return streamLocation()
        .map {
            GpsCoordinates(it.lat, it.lng)
        }
        .catch {
            Timber.e(it, "Error obteniendo ubicación GPS")
            emit(GpsCoordinates(10.0, 10.0))
        }
        .map { it }
}

fun KarooSystemService.getHomeFlow(): Flow<GpsCoordinates> {
    return streamPOIs()
        .map { globalPOIs ->
            globalPOIs.pois.find { poi -> poi.type == "home" }?.let { homePoi ->
                GpsCoordinates(homePoi.lat, homePoi.lng)
            } ?: GpsCoordinates(0.0, 0.0)
        }
        .catch {
            Timber.e(it, "Error obteniendo ubicación HOME")
            emit(GpsCoordinates(0.0, 0.0))
        }
}




