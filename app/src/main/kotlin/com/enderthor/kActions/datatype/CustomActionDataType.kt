package com.enderthor.kActions.datatype

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.RemoteViewsCompositionResult
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.enderthor.kActions.R
import com.enderthor.kActions.data.StepStatus
import com.enderthor.kActions.data.WebhookData
import com.enderthor.kActions.data.customMessage
import com.enderthor.kActions.extension.KActionsExtension
import com.enderthor.kActions.extension.managers.ConfigurationManager
import com.enderthor.kActions.extension.streamDataMonitorFlow
import com.enderthor.kActions.extension.streamUserProfile
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class CustomActionDataType(
    datatype: String,
    private val context: Context,
    private val karooSystem: KarooSystemService,
) : DataTypeImpl("kactions", datatype) {


    companion object {
        private var lastStatusUpdateTime: Long = 0
        private const val ACTION_THRESHOLD = 8_000L
        private var statusUpdateJob: Job? = null

        private var readyMessageShown: Boolean = false
    }


    private val glance = GlanceRemoteViews()
    private val configManager by lazy { ConfigurationManager(context) }


    private val _distanceFlow = MutableStateFlow(0.0)
    val distanceFlow: StateFlow<Double> = _distanceFlow.asStateFlow()

    private fun KarooSystemService.startCollectingRemainingDistance(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            streamDataMonitorFlow(DataType.Type.DISTANCE_TO_DESTINATION)
                .collect { state ->
                    if (state is StreamState.Streaming) {
                        val distanceValue = state.dataPoint.singleValue ?: 0.0
                        _distanceFlow.value = distanceValue
                        //Timber.d("Distancia restante actualizada: $distanceValue")
                    }
                }
        }
    }

    private fun getRemainingDistance(units: UserProfile.PreferredUnit.UnitType, remaining: String = ""): String {
        return when {
            distanceFlow.value <= 0.0 -> "0"
            units == UserProfile.PreferredUnit.UnitType.IMPERIAL ->
                "$remaining${(distanceFlow.value / 1609).toInt()}mi"
            distanceFlow.value < 1.0 ->
                "$remaining${(distanceFlow.value * 1000).toInt()}m"
            else ->
                "$remaining${String.format(Locale.US, "%.1fkm", distanceFlow.value)}"
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val scopeJob = Job()
        val scope = CoroutineScope(Dispatchers.IO + scopeJob)

        val configJob = scope.launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            emitter.onNext(ShowCustomStreamState(message = "", color = null))
            awaitCancellation()
        }

        val viewJob = scope.launch {
            try {
                val units = karooSystem.streamUserProfile().first().preferredUnit.distance
                karooSystem.startCollectingRemainingDistance(this)


                configManager.loadPreferencesFlow()
                    .combine(configManager.loadWebhookDataFlow()) { configData, webhooks ->
                        Pair(configData, webhooks)
                    }
                    .collect { (configData, webhooks) ->

                        val message = if (configData.isNotEmpty()) configData.first().customMessage1 else null
                        val webhook = if (webhooks.isNotEmpty()) webhooks.first() else null

                       // Timber.d("Actualizando vista - Webhook: ${webhook?.status}, Mensaje: ${message?.status}")


                        // Detectar cambio a estado FIRST e iniciar timer
                        if (message?.status == StepStatus.FIRST && lastStatusUpdateTime == 0L) {
                            lastStatusUpdateTime = System.currentTimeMillis()

                            // Cancelar timer anterior si existe
                            statusUpdateJob?.cancel()

                            // Programar actualización automática después de 8 segundos
                            statusUpdateJob = scope.launch {
                                delay(ACTION_THRESHOLD)
                                if (message.status == StepStatus.FIRST) {
                                    // Cambiar estado interno a CONFIRM
                                    val exten = KActionsExtension.getInstance() ?: return@launch
                                    exten.updateCustomMessageStatus(0, StepStatus.CONFIRM)
                                    exten.updateWebhookStatus(0, StepStatus.CONFIRM)

                                    // Actualizar la UI con el nuevo estado
                                    val result = updateConfigDataView(
                                        context = context,
                                        webhook = webhook?.copy(status = StepStatus.CONFIRM),
                                        message = message.copy(status = StepStatus.CONFIRM),
                                        config = config,
                                        units = units,
                                    )
                                    emitter.updateView(result.remoteViews)
                                }
                            }
                        } else if (message?.status != StepStatus.FIRST) {
                            // Cancelar timer si salimos de estado FIRST
                            statusUpdateJob?.cancel()
                            lastStatusUpdateTime = 0L
                        }


                        val result = updateConfigDataView(
                            context = context,
                            webhook = webhook,
                            message = message,
                            config = config,
                            units = units,
                        )
                        emitter.updateView(result.remoteViews)
                    }
            } catch (e: CancellationException) {
                Timber.d(e, "Cancelación normal del flujo")
            } catch (e: Exception) {
                Timber.e(e, "Error actualizando vista: ${e.message}")

                    val result = updateConfigDataView(
                        context = context,
                        webhook = null,
                        message = null,
                        config = config,
                        units = UserProfile.PreferredUnit.UnitType.METRIC,
                    )
                    emitter.updateView(result.remoteViews)
                    delay(5000)
                }

        }

        emitter.setCancellable {
            configJob.cancel()
            viewJob.cancel()
            statusUpdateJob?.cancel() // Cancelar el timer
            scope.cancel()
            scopeJob.cancel()
        }
    }

    private suspend fun updateConfigDataView(
        context: Context,
        webhook: WebhookData?,
        message: customMessage?,
        config: ViewConfig,
        units: UserProfile.PreferredUnit.UnitType,
    ): RemoteViewsCompositionResult {

        val statusMessage = message?.status ?: StepStatus.NOT_AVAILABLE
        val webhookStatus = webhook?.status ?: StepStatus.NOT_AVAILABLE
        val currentTime = System.currentTimeMillis()


        if (message?.status != null) {
            if (statusMessage == StepStatus.FIRST && lastStatusUpdateTime == 0L) {
                // Inicializar el timestamp cuando entramos en FIRST por primera vez
                //Timber.d("Inicializando contador para mensaje de confirmación")
                lastStatusUpdateTime = currentTime
                readyMessageShown = false
            } else if (statusMessage != StepStatus.FIRST) {
                // Resetear timestamp cuando salimos de FIRST
                lastStatusUpdateTime = 0L
                readyMessageShown = false
            }
        }

       // val name = message?.name ?: ""
        val name = ""
        var displayText = message?.message ?: ""

        val displayStatus = when {
            // Cuando el webhook está en CONFIRM o EXECUTING, priorizar su estado
            webhookStatus == StepStatus.CONFIRM || webhookStatus == StepStatus.EXECUTING -> webhookStatus

            // Cuando el mensaje está vacío y hay webhook configurado
            displayText.isEmpty() && webhook?.enabled == true && webhook.url.isNotEmpty() -> webhookStatus

            // En otros casos, usar el estado del mensaje
            else -> statusMessage
        }

        val distance = getRemainingDistance(units)
        displayText = displayText.replace("#dst#", distance)

        val actionString = when (displayStatus) {
            StepStatus.IDLE -> context.getString(R.string.idle_action)
            StepStatus.FIRST -> {
                if (lastStatusUpdateTime > 0L) {
                    val timeElapsed = currentTime - lastStatusUpdateTime
                    if (timeElapsed >= ACTION_THRESHOLD) {
                        //Timber.d("Tiempo transcurrido: ${timeElapsed}ms - Listo para webhook")
                        context.getString(R.string.webhook_confirm_action)
                    } else {
                        //Timber.d("Tiempo transcurrido: ${timeElapsed}ms - Mostrando opciones")
                        context.getString(R.string.message_first_action)
                    }
                } else {
                    context.getString(R.string.message_first_action)
                }
            }
            StepStatus.CONFIRM -> context.getString(R.string.webhook_confirm_action)
            StepStatus.EXECUTING -> context.getString(R.string.webhook_excuting_action)
            StepStatus.CANCEL -> context.getString(R.string.webhook_cancel_action)
            StepStatus.SUCCESS -> context.getString(R.string.webhook_success_action)
            StepStatus.ERROR -> context.getString(R.string.webhook_error_action)
            StepStatus.NOT_AVAILABLE -> context.getString(R.string.webhook_notavailable_action)
        }

        val displayMessage = "$actionString\n$name"
        val webhookEnabled = webhook?.enabled == true
        val webhookUrl = webhook?.url ?: ""

        return glance.compose(context, DpSize.Unspecified) {
            var modifier = GlanceModifier.fillMaxSize().padding(5.dp)

            if (!config.preview) {
               // Timber.d("Configurando acción unificada , texto: $displayText")
                modifier = modifier.clickable(
                    onClick = actionRunCallback<UnifiedActionCallback>(
                        actionParametersOf(
                            UnifiedActionCallback.MESSAGE_TEXT to displayText,
                            UnifiedActionCallback.STATUS to statusMessage.name,
                            UnifiedActionCallback.WEBHOOK_ENABLED to webhookEnabled,
                            UnifiedActionCallback.WEBHOOK_URL to webhookUrl
                        )
                    )
                )
            }

            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayMessage,
                    style = TextStyle(
                        fontSize = TextUnit(16f, TextUnitType.Sp),
                        textAlign = TextAlign.Center,
                        color = ColorProvider(Color.Black, Color.White)
                    )
                )
            }
        }
    }
}