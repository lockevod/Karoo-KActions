package com.enderthor.kActions.datatype

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionRunCallback
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object GlanceIdFactory {
    private val glanceIdCache = ConcurrentHashMap<String, CustomGlanceId>()

    fun getUniqueGlanceId(context: Context, identifier: String): GlanceId {
        return glanceIdCache.getOrPut(identifier) {
            Timber.d("Creando nuevo GlanceId para '$identifier'")
            CustomGlanceId("${context.packageName}_$identifier")
        }
    }

    fun createUnifiedClickAction(
        messageText: String,
        status: String
    ): Action {
        val uniqueId = UUID.randomUUID().toString()
        Timber.d("Creando acción unificada  uniqueId: $uniqueId")

        return actionRunCallback<UnifiedActionCallback>(
            actionParametersOf(
                UnifiedActionCallback.MESSAGE_TEXT to messageText,
                UnifiedActionCallback.STATUS to status,
                UnifiedActionCallback.LAST_CLICK_TIME to System.currentTimeMillis()
            )
        )
    }

    private class CustomGlanceId(private val uniqueId: String) : GlanceId {
        private val uuid = UUID.nameUUIDFromBytes(uniqueId.toByteArray())

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CustomGlanceId) return false
            return uuid == other.uuid
        }

        override fun hashCode(): Int = uuid.hashCode()

        override fun toString(): String = "CustomGlanceId($uniqueId)"
    }
}