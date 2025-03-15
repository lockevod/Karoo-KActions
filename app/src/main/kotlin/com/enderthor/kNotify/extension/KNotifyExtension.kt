package com.enderthor.kNotify.extension

import com.enderthor.kNotify.BuildConfig
import com.enderthor.kNotify.data.ConfigData
import com.enderthor.kNotify.data.SenderConfig
import com.enderthor.kNotify.data.Template
import com.enderthor.kNotify.data.karooUrl
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class KNotifyExtension : KarooExtension("knotify", BuildConfig.VERSION_NAME), CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    lateinit var karooSystem: KarooSystemService

    private lateinit var sender: Sender

    private var activeConfigs: List<ConfigData> = emptyList()
    private var availableTemplates: List<Template> = emptyList()
    private var senderConfig: SenderConfig? = null

    private var lastRideState: RideState? = null

    private var isFirstRecordingSinceBoot = true
    private var wasRecording = false
    private var wasPaused = false

    // Control de mensajes duplicados
    private val sentMessageStates = mutableSetOf<String>()

    // Control de tiempo entre mensajes del mismo tipo
    private val lastMessageTimeByType = mutableMapOf<String, Long>()
    // 5 minutos entre mensajes del mismo tipo
    private val MIN_TIME_BETWEEN_SAME_MESSAGES = 3 * 60 * 1000L

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override fun onCreate() {
        super.onCreate()

        Timber.d("KNotify Service created")

        karooSystem = KarooSystemService(applicationContext)

        sender = Sender(applicationContext, karooSystem)

        karooSystem.connect { connected ->
            if (connected) {
                Timber.d("Connected to Karoo system")

                loadConfigurations()
                loadSenderConfig()
                loadTemplates()
                observeRideState()
            } else {
                Timber.w("Failed to connect to Karoo system")
            }
        }
    }

    private fun loadConfigurations() {
        launch {
            try {
                applicationContext.loadPreferencesFlow().collect { configs ->
                    Timber.d("Loaded configurations: $configs")
                    activeConfigs = configs
                    Timber.d("Phone numbers loaded: ${configs.firstOrNull()?.phoneNumbers ?: "none"}")
                    Timber.d("Message templates loaded: Start=${configs.firstOrNull()?.startMessage}, Stop=${configs.firstOrNull()?.stopMessage}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading configurations")
            }
        }
    }

    private fun loadSenderConfig() {
        launch {
            try {
                sender.getSenderConfigFlow().collect { config ->
                    Timber.d("Loaded Sender configuration: ${config != null}")
                    senderConfig = config
                    if (config != null) {
                        Timber.d("Sender conf: $config")
                    } else {
                        Timber.w("No Sender configuration available")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading Sender configuration")
            }
        }
    }

    private fun loadTemplates() {
        launch {
            try {
                sender.getTemplatesFlow().collect { templates ->
                    Timber.d("Loaded ${templates.size} templates")
                    availableTemplates = templates
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading templates")
            }
        }
    }

    private fun observeRideState() {
        launch {
            try {
                karooSystem.streamRide().collect { rideState ->
                    processRideStateChange(rideState)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error observing ride state")
            }
        }
    }

    private fun processRideStateChange(newRideState: RideState) {
        // Verificar si el estado realmente cambió
        if (lastRideState == newRideState) return

        Timber.d("Ride state changed: $newRideState")

        val configs = activeConfigs.filter { it.isActive }
        Timber.d("Active configurations for sending messages: $configs")

        if (configs.isEmpty()) {
            Timber.w("No active configurations found")
        } else if (senderConfig == null) {
            Timber.w("Sender configuration not found")
        } else {
            val currentTime = System.currentTimeMillis()

            when (newRideState) {
                is RideState.Recording -> {
                    if (lastRideState == null || lastRideState is RideState.Idle) {
                        if (isFirstRecordingSinceBoot) {
                            isFirstRecordingSinceBoot = false
                            handleEventWithTimeLimit("start", currentTime, configs)
                        }
                    } else if (lastRideState is RideState.Paused) {
                        handleEventWithTimeLimit("resume", currentTime, configs)
                    }
                }

                is RideState.Paused -> {
                    if (wasRecording) {
                        handleEventWithTimeLimit("pause", currentTime, configs)
                    }
                }

                is RideState.Idle -> {
                    if (wasRecording || wasPaused) {
                        handleEventWithTimeLimit("stop", currentTime, configs)
                    }
                }
            }
        }

        lastRideState = newRideState
        // Actualizar las variables de estado
        when (newRideState) {
            is RideState.Recording -> {
                wasRecording = true
                wasPaused = false
            }
            is RideState.Paused -> {
                wasPaused = true
                wasRecording = false
            }
            is RideState.Idle -> {
                wasRecording = false
                wasPaused = false
            }
        }
    }

    private fun handleEventWithTimeLimit(eventType: String, currentTime: Long, configs: List<ConfigData>) {

        val lastTime = lastMessageTimeByType[eventType] ?: 0L
        val timeElapsed = currentTime - lastTime

        if (timeElapsed >= MIN_TIME_BETWEEN_SAME_MESSAGES) {
            val configsWithNotify = when (eventType) {
                "start" -> configs.filter { it.notifyOnStart }
                "stop" -> configs.filter { it.notifyOnStop }
                "pause" -> configs.filter { it.notifyOnPause }
                "resume" -> configs.filter { it.notifyOnResume }
                else -> emptyList()
            }

            if (configsWithNotify.isNotEmpty()) {

                lastMessageTimeByType[eventType] = currentTime


                val stateKey = "$eventType-$currentTime"
                if (!sentMessageStates.contains(stateKey)) {
                    sendMessageForEvent(eventType)
                    sentMessageStates.add(stateKey)


                    if (sentMessageStates.size > 10) {
                        val keysToRemove = sentMessageStates.toList()
                            .sortedBy { it.split("-").last() }
                            .take(sentMessageStates.size - 10)
                        sentMessageStates.removeAll(keysToRemove.toSet())
                    }
                }
            }
        } else {
            Timber.d("Mensaje tipo $eventType ignorado - han pasado solo ${timeElapsed/1000} segundos desde el último (mínimo ${MIN_TIME_BETWEEN_SAME_MESSAGES/1000}s)")
        }
    }

    private fun sendMessageForEvent(eventType: String) {
        launch(Dispatchers.IO) {
            try {
                val config = activeConfigs.firstOrNull() ?: run {
                    Timber.e("No hay configuración disponible para enviar mensajes")
                    return@launch
                }

                val karooLive = if (config.karooKey.isNotBlank()) karooUrl + config.karooKey else ""
                val message = when (eventType) {
                    "start" -> config.startMessage + "   " + karooLive
                    "stop" -> config.stopMessage + "   " + karooLive
                    "pause" -> config.pauseMessage + "   " + karooLive
                    "resume" -> config.resumeMessage + "   " + karooLive
                    else -> return@launch
                }

                if (config.phoneNumbers.isNotEmpty()) {

                    val sendJobs = config.phoneNumbers.map { phoneNumber ->
                        async {
                            try {
                                val success = sender.sendMessage(phoneNumber, message)
                                if (success) {
                                    Timber.d("Mensaje enviado para evento: $eventType a $phoneNumber")
                                } else {
                                    Timber.e("Error enviando mensaje para evento: $eventType a $phoneNumber")
                                }
                                success
                            } catch (e: Exception) {
                                Timber.e(e, "Excepción enviando mensaje para evento: $eventType a $phoneNumber")
                                false
                            }
                        }
                    }

                    // Esperar a que todos los envíos terminen
                    val results = sendJobs.awaitAll()
                    val successCount = results.count { it }

                    Timber.d("Resumen de envíos para evento $eventType: $successCount éxitos de ${config.phoneNumbers.size} intentos")
                } else {
                    Timber.d("No hay números de teléfono configurados para evento: $eventType")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error enviando mensajes para evento: $eventType")
            }
        }
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        job.cancel()
        super.onDestroy()
    }
}