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
import com.enderthor.kActions.data.WebhookData
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
import io.hammerhead.karooext.models.ShowCustomStreamState
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
abstract class WebhookDataTypeBase(
    datatype: String,
    protected val context: Context,
    protected val webhookIndex: Int
) : DataTypeImpl("kactions", datatype) {

    private val glance = GlanceRemoteViews()
    private val configManager by lazy { ConfigurationManager(context) }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {

        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            emitter.onNext(ShowCustomStreamState(message = "", color = null))
            awaitCancellation()
        }


        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    configManager.loadWebhookDataFlow().collect  { webhooks ->
                        val result = updateWebhookView(context, webhooks[webhookIndex], config)
                        emitter.updateView(result.remoteViews)
                    }
                } catch (e: CancellationException) {

                    Timber.d(e, "No hacemos nada, cancelacion normal")

                }
                catch (e: Exception) {

                    Timber.e(e, "Error cargando datos del webhook")
                    updateWebhookView(context, null, config).let { result ->
                        emitter.updateView(result.remoteViews)
                    }
                    delay(5000)
                }
            }
        }
        emitter.setCancellable {
            configJob.cancel()
            viewJob.cancel()
        }

    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    suspend fun updateWebhookView(
        context: Context,
        webhookData: WebhookData?,
        config: ViewConfig
    ): RemoteViewsCompositionResult {

        val status = webhookData?.status ?: StepStatus.NOT_AVAILABLE
        val name = webhookData?.name ?: ""


        val idleActionString = context.getString(R.string.webhook_idle_action)
        val firstActionString = context.getString(R.string.webhook_first_action)
        val executingActionString = context.getString(R.string.webhook_excuting_action)
        val cancelActionString = context.getString(R.string.webhook_cancel_action)
        val successActionString = context.getString(R.string.webhook_success_action)
        val errorActionString = context.getString(R.string.webhook_error_action)
        val notAvailableActionString = context.getString(R.string.webhook_notavailable_action)


        val webhookMessage = when (status) {
            StepStatus.IDLE -> "$idleActionString\n$name"
            StepStatus.FIRST -> firstActionString
            StepStatus.EXECUTING -> executingActionString
            StepStatus.CANCEL -> cancelActionString
            StepStatus.SUCCESS -> successActionString
            StepStatus.ERROR -> errorActionString
            StepStatus.NOT_AVAILABLE -> notAvailableActionString
        }

        return glance.compose(context, DpSize.Unspecified) {
            var modifier = GlanceModifier.fillMaxSize().padding(5.dp)

            Timber.d("WebhookData: $webhookData")
            if (!config.preview && webhookData != null) {

                val id = webhookData.id
                val url = webhookData.url
                val statusName = status.name
                Timber.d("Dentro click WebhookData:")

                modifier = modifier.clickable(
                    onClick = actionRunCallback<ExecuteWebhookAction>(
                        parameters = actionParametersOf(
                                ExecuteWebhookAction.WEBHOOK_ID to id,
                                ExecuteWebhookAction.WEBHOOK_URL to url,
                                ExecuteWebhookAction.CURRENT_STATUS to statusName
                        )
                    )
                )
            }

            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = webhookMessage, style = TextStyle(
                        fontSize = TextUnit(16f, TextUnitType.Sp),
                        textAlign = TextAlign.Center,
                        color = ColorProvider(Color.Black, Color.White)
                    )
                )
            }
        }
    }
}