package com.enderthor.kActions.extension

import com.enderthor.kActions.BuildConfig
import com.enderthor.kActions.data.ConfigData
import com.enderthor.kActions.data.ProviderType
import com.enderthor.kActions.data.SenderConfig
import com.enderthor.kActions.data.StepStatus
import com.enderthor.kActions.datatype.CustomActionDataType
import com.enderthor.kActions.extension.managers.ConfigurationManager
import com.enderthor.kActions.extension.managers.NotificationManager
import com.enderthor.kActions.extension.managers.RideStateManager
import com.enderthor.kActions.extension.managers.WebhookManager
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class KActionsExtension : KarooExtension("kactions", BuildConfig.VERSION_NAME), CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job


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

    override val types by lazy {
        listOf(
            CustomActionDataType("custom-one", applicationContext, karooSystem),
        )
    }



    override fun onCreate() {
        super.onCreate()
        setInstance(this)

        Timber.d("KActions Service created")


        karooSystem = KarooSystemService(applicationContext)
        configManager = ConfigurationManager(applicationContext)
        sender = Sender(karooSystem, configManager)

        notificationManager = NotificationManager(sender, applicationContext,this)
        webhookManager = WebhookManager(applicationContext, karooSystem, this)
        webhookManager.restorePendingWebhookStates()
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
        checkAndResetBlockedState()
    }

    private fun initializeSystem() {
        launch {
            try {

                configManager.loadPreferencesFlow().combine(configManager.loadSenderConfigFlow()) { configs, senderConfigs ->
                    activeConfigs = configs
                    val activeProvider = configs.firstOrNull()?.activeProvider
                    senderConfig = findActiveSenderConfig(senderConfigs, activeProvider)
                    Pair(activeConfigs, senderConfig)
                }.collect { (configs, sender) ->
                    if (configs.isNotEmpty() && sender != null) {
                        rideStateManager.observeRideState(configs, sender)
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

    fun updateWebhookStatus(webhookId: Int, status: StepStatus) {
        webhookManager.updateWebhookStatus(webhookId, status)
    }


    fun executeWebhookWithStateTransitions(webhookId: Int) {
        webhookManager.executeWebhookWithStateTransitions(webhookId)
    }

    fun updateCustomMessageStatus(messageId: Int, status: StepStatus) {
        notificationManager.updateCustomMessageStatus(messageId, status)
    }


    fun sendCustomMessageWithStateTransitions(messageId: Int, messageText: String) {
        notificationManager.sendCustomMessageWithStateTransitions(messageId, messageText)
    }

    fun checkAndResetBlockedState() {
        launch(Dispatchers.IO) {
            try {

                val webhookData = configManager.loadWebhookDataFlow().firstOrNull() ?: emptyList()
                val messageDatas = configManager.loadPreferencesFlow().firstOrNull() ?: emptyList()

                val webhookBlocked = webhookData.any { it.status == StepStatus.EXECUTING }
                val messageBlocked = messageDatas.any { it.customMessage1.status == StepStatus.EXECUTING }

                if (webhookBlocked || messageBlocked) {
                    Timber.d("Detectado estado EXECUTING bloqueado, forzando restablecimiento")


                    webhookData.forEach { webhook ->
                        if (webhook.status == StepStatus.EXECUTING) {
                            updateWebhookStatus(0, StepStatus.IDLE)
                        }
                    }


                    messageDatas.forEach { config ->
                        config.customMessage1.let {
                            if (it.status == StepStatus.EXECUTING) {
                                Timber.d("Restableciendo mensaje1 de EXECUTING a IDLE")
                                updateCustomMessageStatus(0, StepStatus.IDLE)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error al verificar estados bloqueados")
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