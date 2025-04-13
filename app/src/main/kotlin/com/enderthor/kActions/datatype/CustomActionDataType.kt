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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber


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
    private var distanceFlow: Flow<Double>? = null

    private fun KarooSystemService.getRemainingDistanceFlow(): Flow<Double> {
        return streamDataMonitorFlow(DataType.Type.DISTANCE_TO_DESTINATION)
            .map { state ->
                if (state is StreamState.Streaming) {
                    state.dataPoint.values["FIELD_DISTANCE_TO_DESTINATION_ID"] ?: 0.0
                } else 0.0
            }
            .catch { e ->
                Timber.e(e, "Error obteniendo distancia: ${e.message}")
                emit(0.0)
            }
    }

    private suspend fun getRemainingDistance(units: UserProfile.PreferredUnit.UnitType, remaining: String = ""): String {
        val distance = distanceFlow?.first() ?: 0.0
        return when {
            distance <= 0.0 -> "0"
            units == UserProfile.PreferredUnit.UnitType.IMPERIAL ->
                "$remaining${(distance / 1609).toInt()} mi"
            distance < 1000 ->
                "$remaining${distance.toInt()} m"
            else ->
                "$remaining${(distance / 1000).toInt()} km"
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

        distanceFlow = karooSystem.getRemainingDistanceFlow()

        val viewJob = scope.launch {
            try {
                val units = karooSystem.streamUserProfile().first().preferredUnit.distance

                configManager.loadPreferencesFlow()
                    .combine(configManager.loadWebhookDataFlow()) { configData, webhooks ->
                        Pair(configData, webhooks)
                    }
                    .collect { (configData, webhooks) ->

                        val message = if (configData.isNotEmpty()) configData.first().customMessage1 else null
                        val webhook = if (webhooks.isNotEmpty()) webhooks.first() else null

                       // Timber.d("Actualizando vista - Webhook: ${webhook?.status}, Mensaje: ${message?.status}")

                        if (message?.status == StepStatus.FIRST && lastStatusUpdateTime == 0L) {
                            lastStatusUpdateTime = System.currentTimeMillis()
                            statusUpdateJob?.cancel()

                            statusUpdateJob = scope.launch {
                                delay(ACTION_THRESHOLD)
                                if (message.status == StepStatus.FIRST) {

                                    val exten = KActionsExtension.getInstance() ?: return@launch
                                    exten.updateCustomMessageStatus(0, StepStatus.CONFIRM)
                                    exten.updateWebhookStatus(0, StepStatus.CONFIRM)


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
                //Timber.d(e, "Cancelación normal del flujo")
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
            statusUpdateJob?.cancel()
            scope.cancel()
            scopeJob.cancel()
        }
    }

    private fun getReadyMessageText(messageAvailable: Boolean, webhookAvailable: Boolean): String {
        return when {
            messageAvailable && webhookAvailable -> context.getString(R.string.webhook_first_action)
            messageAvailable -> context.getString(R.string.message_only_action)
            webhookAvailable -> context.getString(R.string.webhook_only_action)
            else -> context.getString(R.string.idle_action)
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

                lastStatusUpdateTime = currentTime
                readyMessageShown = false
            } else if (statusMessage != StepStatus.FIRST) {

                lastStatusUpdateTime = 0L
                readyMessageShown = false
            }
        }


        val name = ""
        var displayText = message?.message ?: ""

        val displayStatus = when {

            webhook == null && message == null -> StepStatus.NOT_AVAILABLE
            webhookStatus == StepStatus.CONFIRM ||
                    webhookStatus == StepStatus.EXECUTING ||
                    webhookStatus == StepStatus.ERROR ||
                    webhookStatus == StepStatus.SUCCESS -> webhookStatus

            displayText.isEmpty() && webhook?.enabled == true && webhook.url.isNotEmpty() -> webhookStatus


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
                       // context.getString(R.string.message_first_action)
                        val messageAvailable = message != null && message.message.isNotEmpty()
                        val webhookAvailable = webhook != null && webhook.url.isNotEmpty()
                        getReadyMessageText(messageAvailable, webhookAvailable)
                    }
                } else {
                    val messageAvailable = message != null && message.message.isNotEmpty()
                    val webhookAvailable = webhook != null && webhook.url.isNotEmpty()
                    getReadyMessageText(messageAvailable, webhookAvailable)
                    //context.getString(R.string.message_first_action)
                }
            }
            StepStatus.CONFIRM -> context.getString(R.string.webhook_confirm_action)
            StepStatus.EXECUTING -> context.getString(R.string.webhook_excuting_action)
            StepStatus.CANCEL -> context.getString(R.string.webhook_cancel_action)
            StepStatus.SUCCESS -> context.getString(R.string.webhook_success_action)
            StepStatus.ERROR -> context.getString(R.string.webhook_error_action)
            StepStatus.NOT_AVAILABLE -> context.getString(R.string.webhook_notavailable_action)
        }

        val displayMessage = if (webhook == null && message == null) {
            context.getString(R.string.webhook_notavailable_action)
        } else {
            "$actionString\n$name"
        }
        val webhookEnabled = webhook?.enabled == true
        val webhookUrl = webhook?.url ?: ""

        return glance.compose(context, DpSize.Unspecified) {
            var modifier = GlanceModifier.fillMaxSize().padding(5.dp)

            if (!config.preview) {

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