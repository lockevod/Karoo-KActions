package com.enderthor.kActions.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.enderthor.kActions.R

const val MIN_TIME_BETWEEN_SAME_MESSAGES = 3 * 60 * 1000L
const val MIN_TIME_TEXTBELT_FREE = 24 * 60 * 60 * 1000L
const val karooUrl= "https://dashboard.hammerhead.io/live/"
const val export: Boolean = false

@Serializable
data class customMessage (
    var name: String = "",
    var message: String = "",
    var istracking: Boolean = false,
    var isdistance: Boolean = false,
    var status: StepStatus = StepStatus.IDLE,
)

@Serializable
data class ConfigData(
    val isActive: Boolean = true,
    val notifyOnStop: Boolean = false,
    val notifyOnStart: Boolean = true,
    val notifyOnPause: Boolean = false,
    val notifyOnResume: Boolean = false,
    val indoorMode: Boolean = true,
    val startMessage: String = "",
    val stopMessage: String = "",
    val pauseMessage: String = "",
    val resumeMessage: String = "",
    val customMessage1: customMessage = customMessage(),
    val customMessage2: customMessage = customMessage(),
    val karooKey: String = "",
    val phoneNumbers: List<String> = listOf(),
    val emails: List<String> = listOf(),
    val emailFrom: String = "",
    val delayIntents: Double =  60.0 * 4.0,  // 4 horas en minutos
    val activeProvider: ProviderType = ProviderType.CALLMEBOT,
)


fun createDefaultConfigData(context: Context): ConfigData {
    return ConfigData(
        startMessage = context.getString(R.string.default_start_message),
        stopMessage = context.getString(R.string.default_stop_message),
        pauseMessage = context.getString(R.string.default_pause_message),
        resumeMessage = context.getString(R.string.default_resume_message)
    )
}

fun getPreviewConfigData(context: Context): String =
    Json.encodeToString(listOf(createDefaultConfigData(context)))


enum class ProviderType {
    CALLMEBOT,
    WHAPI,
    TEXTBELT,
    RESEND
}


@Serializable
data class GpsCoordinates(
    val lat: Double,
    val lng: Double ,
)

@Serializable
data class WebhookData(
    val id: Int=0,
    val name: String="",
    val url: String= "",
    val header: String= "",
    val post: String= "",
    val enabled: Boolean = false,
    val actionOnStop: Boolean = false,
    val actionOnStart: Boolean = false,
    val actionOnPause: Boolean = false,
    val actionOnResume: Boolean = false,
    val actionOnCustom: Boolean = true,
    val onlyIfLocation: Boolean = true,
    val location: GpsCoordinates = GpsCoordinates(0.0,0.0),
    val status: WebhookStatus = WebhookStatus.IDLE,
)


enum class StepStatus { IDLE,FIRST, EXECUTING, SUCCESS, ERROR, NOT_AVAILABLE,CANCEL }


@Serializable
data class SenderConfig(
    val provider: ProviderType = ProviderType.TEXTBELT,
    val apiKey: String = ""
)

data class WebhookImportResult(
    val webhooks: List<WebhookData>,
    val status: String
)
data class SenderConfigImportResult(
    val senderConfigs: List<SenderConfig>,
    val status: String
)


val previewWebhookData = listOf(WebhookData(id=0))
val defaultWebhookData = Json.encodeToString(previewWebhookData)
val previewSenderConfig = listOf(SenderConfig(ProviderType.CALLMEBOT,""),SenderConfig(ProviderType.WHAPI,""),SenderConfig(ProviderType.TEXTBELT,"textbelt"))
val defaultSenderConfig = Json.encodeToString(previewSenderConfig)

