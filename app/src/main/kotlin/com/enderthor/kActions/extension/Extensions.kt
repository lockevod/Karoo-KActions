package com.enderthor.kActions.extension


import com.enderthor.kActions.data.GpsCoordinates
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnGlobalPOIs
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.ActiveRideProfile
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds



const val RETRY_CHECK_STREAMS = 4
const val WAIT_STREAMS_SHORT = 3000L // 3 seconds
const val STREAM_TIMEOUT = 15000L // 15 seconds
const val WAIT_STREAMS_LONG = 120000L // 120 seconds
const val WAIT_STREAMS_MEDIUM = 10000L // 10 seconds
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
                Timber.d("Received event: $event")
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

fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> {
    return callbackFlow {
        val listenerId = addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
            trySendBlocking(event.state)
        }
        awaitClose {
            removeConsumer(listenerId)
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

fun KarooSystemService.streamActiveRideProfile(): Flow<ActiveRideProfile> {
    return callbackFlow {
        val listenerId = addConsumer { event: ActiveRideProfile ->
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

@OptIn(FlowPreview::class)
fun KarooSystemService.streamDataMonitorFlow(
    dataTypeID: String,
    noCheck: Boolean = false,
    defaultValue: Double = 0.0,
): Flow<StreamState> = flow {

    if (noCheck) {
        streamDataFlow(dataTypeID).collect { emit(it) }
        return@flow
    }

    var retryAttempt = 0


    val initialState = StreamState.Streaming(
        DataPoint(
            dataTypeId = dataTypeID,
            values = mapOf(DataType.Field.SINGLE to defaultValue),
        )
    )

    emit(initialState)

    while (currentCoroutineContext().isActive) {
        try {
            streamDataFlow(dataTypeID)
                .distinctUntilChanged()
                .timeout(STREAM_TIMEOUT.milliseconds)
                .collect { state ->
                    when (state) {
                        is StreamState.Idle -> {
                            Timber.w("Stream estado inactivo: $dataTypeID, esperando...")
                            delay(WAIT_STREAMS_SHORT)
                        }
                        is StreamState.NotAvailable -> {
                            Timber.w("Stream estado NotAvailable: $dataTypeID, esperando...")
                            emit(initialState)
                            delay(WAIT_STREAMS_SHORT * 2)
                        }
                        is StreamState.Searching -> {
                            Timber.w("Stream estado searching: $dataTypeID, esperando...")
                            emit(initialState)
                            delay(WAIT_STREAMS_SHORT/2)
                        }
                        else -> {
                            retryAttempt = 0
                            Timber.d("Stream estado: $state")
                            emit(state)
                        }
                    }
                }

        } catch (e: Exception) {
            when (e) {
                is TimeoutCancellationException -> {
                    if (retryAttempt++ < RETRY_CHECK_STREAMS) {
                        val backoffDelay = (1000L * (1 shl retryAttempt))
                            .coerceAtMost(WAIT_STREAMS_MEDIUM)
                        Timber.w("Timeout/Cancel en stream $dataTypeID, reintento $retryAttempt en ${backoffDelay}ms. Motivo $e")
                        delay(backoffDelay)
                    } else {
                        Timber.e("Máximo de reintentos alcanzado")
                        retryAttempt = 0
                        delay(WAIT_STREAMS_LONG)
                    }
                }
                is CancellationException -> {
                    Timber.d("Cancelación ignorada en streamDataFlow")
                }
                else -> {
                    Timber.e(e, "Error en stream")
                    delay(WAIT_STREAMS_LONG)
                }
            }
        }
    }
}

fun KarooSystemService.streamUserProfile(): Flow<UserProfile> {
    return callbackFlow {
        val listenerId = addConsumer { userProfile: UserProfile ->
            trySendBlocking(userProfile)
        }
        awaitClose {
            removeConsumer(listenerId)
        }
    }
}







