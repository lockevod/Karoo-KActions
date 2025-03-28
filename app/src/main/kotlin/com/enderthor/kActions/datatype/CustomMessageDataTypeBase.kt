package com.enderthor.kActions.datatype

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
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
import com.enderthor.kActions.data.StepStatus
import com.enderthor.kActions.extension.managers.ConfigurationManager
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import com.enderthor.kActions.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.glance.action.actionParametersOf
import com.enderthor.kActions.data.customMessage
import com.enderthor.kActions.extension.streamDataMonitorFlow
import com.enderthor.kActions.extension.streamUserProfile
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Locale
import androidx.glance.GlanceId
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.coroutines.cancellation.CancellationException


@OptIn(ExperimentalGlanceRemoteViewsApi::class)
abstract class CustomMessageDataTypeBase(
    datatype: String,
    protected val context: Context,
    protected val value: Int,
    private val karooSystem: KarooSystemService,
) : DataTypeImpl("kactions", datatype) {

    private val glance = GlanceRemoteViews()
    private val configManager by lazy { ConfigurationManager(context) }
    private val uniqueGlanceId: GlanceId by lazy {
        GlanceIdFactory.getUniqueGlanceId(context, "message_${datatype}_${value}")
    }


    private val _distanceFlow = MutableStateFlow(0.0)
    val distanceFlow: StateFlow<Double> = _distanceFlow.asStateFlow()


    fun KarooSystemService.startCollectingRemainingDistance(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            streamDataMonitorFlow("distance_to_end")
                .collect { state ->
                    if (state is StreamState.Streaming) {
                        val distanceValue = state.dataPoint.singleValue ?: 0.0
                        _distanceFlow.value = distanceValue
                        Timber.d("Distancia restante actualizada: $distanceValue")
                    }
                }
        }
    }

    private fun getRemainingDistance(units: UserProfile.PreferredUnit.UnitType, remaining: String =""): String {
        val distance = if (distanceFlow.value <= 0.0)  ""
            else if (units == UserProfile.PreferredUnit.UnitType.IMPERIAL) {
            remaining + "" + (distanceFlow.value / 1609).toInt().toString() + "mi"
            } else
            when {
                distanceFlow.value < 1.0 -> remaining + "" +"${(distanceFlow.value * 1000).toInt()}m"
                else -> remaining + "" + String.format(Locale.US, "%.1fkm", distanceFlow.value)
            }

        return distance
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {

        val scopeJob = Job()
        val scope = CoroutineScope(Dispatchers.IO + scopeJob)

        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            emitter.onNext(ShowCustomStreamState(message = "", color = null))
            awaitCancellation()
        }



        val viewJob = scope.launch {
            while (isActive) {
                try {
                    val units = karooSystem.streamUserProfile().first().preferredUnit.distance
                    karooSystem.startCollectingRemainingDistance(this)
                    configManager.loadPreferencesFlow().collect  { configData ->
                        val message = if (value == 0) configData.first().customMessage1 else configData.first().customMessage2
                        val result = updateConfigDataView(context,message, config, units)
                        emitter.updateView(result.remoteViews)
                    }
                } catch (e: CancellationException) {
                    Timber.d(e, "No hacemos nada, cancelacion normal")
                }
                catch (e: Exception) {

                    Timber.e(e, "Error cargando datos de preferencias")

                    updateConfigDataView(context, null, config,UserProfile.PreferredUnit.UnitType.METRIC).let { result ->
                        emitter.updateView(result.remoteViews)
                    }
                    delay(5000)
                }
            }
        }
        emitter.setCancellable {
            configJob.cancel()
            viewJob.cancel()
            scope.cancel()
            scopeJob.cancel()
        }

    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    suspend fun updateConfigDataView(
        context: Context,
        message: customMessage?,
        config: ViewConfig,
        units: UserProfile.PreferredUnit.UnitType
    ): RemoteViewsCompositionResult {


        val status_message = message?.status ?: StepStatus.NOT_AVAILABLE
        val name = message?.name ?: ""
        //val istracking = message?.istracking == true
        val isdistance = message?.isdistance == true
        var text = message?.message ?: ""

        if (isdistance) text = text + getRemainingDistance(units)

        //if (istracking && config.karooKey.isNotBlank()) text = text + "\n" + karooUrl + config.karooKey

        val idleActionString = context.getString(R.string.webhook_idle_action)
        val firstActionString = context.getString(R.string.webhook_first_action)
        val executingActionString = context.getString(R.string.webhook_excuting_action)
        val cancelActionString = context.getString(R.string.webhook_cancel_action)
        val successActionString = context.getString(R.string.webhook_success_action)
        val errorActionString = context.getString(R.string.webhook_error_action)
        val notAvailableActionString = context.getString(R.string.webhook_notavailable_action)


        val message = when (status_message) {
            StepStatus.IDLE -> "$idleActionString\n$name"
            StepStatus.FIRST -> firstActionString
            StepStatus.EXECUTING -> executingActionString
            StepStatus.CANCEL -> cancelActionString
            StepStatus.SUCCESS -> successActionString
            StepStatus.ERROR -> errorActionString
            StepStatus.NOT_AVAILABLE -> notAvailableActionString
        }

        return glance.compose(context, DpSize.Unspecified, uniqueGlanceId) {
            var modifier = GlanceModifier.fillMaxSize().padding(5.dp)


            if (!config.preview) {

                val statusName_message = status_message.name

                Timber.d("Configurando ExecuteCustomMessage con ID: $value y texto: $text y uniqueGlanceId: $uniqueGlanceId y config $config")
                modifier = modifier.clickable(
                    onClick = GlanceIdFactory.createMessageClickAction(
                        context = context,
                        messageId = value,
                        messageText = text,
                        status = statusName_message
                    )
                )
            }

            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = message, style = TextStyle(
                        fontSize = TextUnit(16f, TextUnitType.Sp),
                        textAlign = TextAlign.Center,
                        color = ColorProvider(Color.Black, Color.White)
                    )
                )
            }
        }
    }
}