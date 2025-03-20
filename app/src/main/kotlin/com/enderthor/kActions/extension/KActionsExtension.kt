package com.enderthor.kActions.extension

import com.enderthor.kActions.BuildConfig
import com.enderthor.kActions.data.ConfigData
import com.enderthor.kActions.data.ProviderType
import com.enderthor.kActions.data.SenderConfig
import com.enderthor.kActions.data.WebhookStatus
import com.enderthor.kActions.datatype.WebhookDataType
import com.enderthor.kActions.extension.managers.ConfigurationManager
import com.enderthor.kActions.extension.managers.NotificationManager
import com.enderthor.kActions.extension.managers.RideStateManager
import com.enderthor.kActions.extension.managers.WebhookManager
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class KActionsExtension : KarooExtension("kactions", BuildConfig.VERSION_NAME), CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override val types by lazy {
        listOf(
            WebhookDataType("webhook-one", applicationContext,0),
        )
    }


    lateinit var karooSystem: KarooSystemService
    private lateinit var sender: Sender


    private lateinit var configManager: ConfigurationManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var rideStateManager: RideStateManager
    private lateinit var webhookManager: WebhookManager


    private var activeConfigs: List<ConfigData> = emptyList()
    private var senderConfig: SenderConfig? = null

    companion object {
        private var instance: KActionsExtension? = null

        fun getInstance(): KActionsExtension? = instance

        internal fun setInstance(ext: KActionsExtension) {
            instance = ext
        }
    }

    override fun onCreate() {
        super.onCreate()
        setInstance(this)

        Timber.d("KActions Service created")


        karooSystem = KarooSystemService(applicationContext)
        configManager = ConfigurationManager(applicationContext)
        sender = Sender(karooSystem, configManager)

        notificationManager = NotificationManager(sender, applicationContext)
        webhookManager = WebhookManager(applicationContext, karooSystem, this)
        rideStateManager = RideStateManager(
            karooSystem,
            notificationManager,
            webhookManager,
            this
        )


        karooSystem.connect { connected ->
            if (connected) {
                Timber.d("Connected to Karoo system")
                initializeSystem()
            } else {
                Timber.w("Failed to connect to Karoo system")
            }
        }
    }

    private fun initializeSystem() {

        launch {
            try {

                configManager.loadPreferencesFlow().collect { configs ->
                    activeConfigs = configs


                    val activeProvider = configs.first().activeProvider


                    if (senderConfig == null) {
                        configManager.loadSenderConfigFlow().first().let { senderConfigs ->
                            senderConfig = findActiveSenderConfig(senderConfigs, activeProvider)
                        }
                    }

                    if (senderConfig != null) {
                        rideStateManager.observeRideState(activeConfigs, senderConfig)
                    }
                }


                configManager.loadSenderConfigFlow().collect { configList ->
                    val activeProvider = activeConfigs.first().activeProvider
                    senderConfig = findActiveSenderConfig(configList, activeProvider)

                    if (activeConfigs.isNotEmpty()) {
                        rideStateManager.observeRideState(activeConfigs, senderConfig)
                    }
                }


            } catch (e: Exception) {
                Timber.e(e, "Error inicializando configuraciones")
            }
        }
    }

    private fun findActiveSenderConfig(configList: List<SenderConfig>, activeProvider: ProviderType?): SenderConfig? {
        return when {
            activeProvider == null -> configList.firstOrNull()
            else -> configList.find { it.provider == activeProvider } ?: configList.firstOrNull()
        }
    }

    fun triggerCustomWebhook(webhookId: Int? = null) {
        if (webhookId == null) {
            Timber.w("Webhook ID no proporcionado")
            return
        }
        rideStateManager.triggerCustomWebhook(webhookId)
    }



    fun updateWebhookState(webhookId: Int?, newStatus: WebhookStatus) {
        webhookId?.let { id ->
            launch(Dispatchers.IO) {
                webhookManager.updateWebhookStatus(id, newStatus)
                Timber.d("Webhook $id actualizado a estado $newStatus")
            }
        }
    }

    fun scheduleResetToIdle(webhookId: Int?, delayMillis: Long) {
        webhookId?.let { id ->
            launch(Dispatchers.IO) {
                delay(delayMillis)
                updateWebhookState(id, WebhookStatus.IDLE)
            }
        }
    }


    fun executeWebhookWithStateTransitions(webhookId: Int?) {
        webhookId?.let { id ->
            launch(Dispatchers.IO) {
                try {

                    triggerCustomWebhook(id)


                    delay(10_000)
                    updateWebhookState(id, WebhookStatus.SUCCESS)


                    delay(5_000)
                    updateWebhookState(id, WebhookStatus.IDLE)
                } catch (e: Exception) {

                    Timber.e(e, "Error al ejecutar webhook $id: ${e.message}")
                    updateWebhookState(id, WebhookStatus.ERROR)


                    delay(10_000)
                    updateWebhookState(id, WebhookStatus.IDLE)
                }
            }
        }
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        job.cancel()
        instance = null
        super.onDestroy()
    }
}