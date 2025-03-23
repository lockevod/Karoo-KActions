package com.enderthor.kActions.extension.managers

import com.enderthor.kActions.data.ConfigData
import com.enderthor.kActions.data.SenderConfig
import com.enderthor.kActions.extension.streamActiveRideProfile
import com.enderthor.kActions.extension.streamRide
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ActiveRideProfile
import io.hammerhead.karooext.models.RideProfile
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import timber.log.Timber

class RideStateManager(
    private val karooSystem: KarooSystemService,
    private val notificationManager: NotificationManager,
    private val webhookManager: WebhookManager,
    private val scope: CoroutineScope
) {
    private var lastRideState: RideState? = null
    private var isFirstRecordingSinceBoot = true
    private var wasRecording = false
    private var wasPaused = false



    fun observeRideState(
        activeConfigs: List<ConfigData>,
        senderConfig: SenderConfig?
    ) {
        scope.launch {
            karooSystem.streamRide()
                .combine(karooSystem.streamActiveRideProfile().catch { emit(defaultActiveRideProfile()) }
                    .onStart { emit(defaultActiveRideProfile()) }) { rideState, activeProfile ->
                    Pair(rideState, activeProfile)
                }
                .collect { (rideState, activeProfile) ->
                    processRideStateChange(rideState, activeConfigs, senderConfig, activeProfile)
                }
        }
    }



    private fun defaultActiveRideProfile(): ActiveRideProfile {
        return ActiveRideProfile(profile = RideProfile(
            id = "default",
            name = "Default Profile",
            pages = emptyList(),
            indoor = false,
            defaultActivityType = "cycling",
            routingPreference = "fastest"
        ))
    }


    private fun processRideStateChange(
        newRideState: RideState,
        configs: List<ConfigData>,
        senderConfig: SenderConfig?,
        activeProfile: ActiveRideProfile
    ) {
        if (lastRideState == newRideState) return

        Timber.d("Ride state changed: $newRideState")

        val activeConfigs = configs.filter { it.isActive }
        val isIndoor = activeProfile.profile.indoor


        val currentTime = System.currentTimeMillis()


        if(!isIndoor || activeConfigs.firstOrNull()?.indoorMode == false)
            when (newRideState) {
                is RideState.Recording -> {
                    if (lastRideState == null || lastRideState is RideState.Idle) {
                        if (isFirstRecordingSinceBoot) {
                            isFirstRecordingSinceBoot = false


                            scope.launch {
                                notificationManager.handleEventWithTimeLimit(
                                    "start",
                                    currentTime,
                                    activeConfigs,
                                    senderConfig
                                )
                            }


                            scope.launch {
                                webhookManager.handleEvent("start", 0)
                            }
                        }
                    } else if (lastRideState is RideState.Paused) {

                        scope.launch {
                            notificationManager.handleEventWithTimeLimit(
                                "resume",
                                currentTime,
                                activeConfigs,
                                senderConfig
                            )
                        }


                        scope.launch {
                            webhookManager.handleEvent("resume", 0)
                        }
                    }
                }

                is RideState.Paused -> {
                    if (wasRecording) {

                        scope.launch {
                            notificationManager.handleEventWithTimeLimit(
                                "pause",
                                currentTime,
                                activeConfigs,
                                senderConfig
                            )
                        }


                        scope.launch {
                            webhookManager.handleEvent("pause", 0)
                        }
                    }
                }

                is RideState.Idle -> {
                    if (wasRecording || wasPaused) {

                        scope.launch {
                            notificationManager.handleEventWithTimeLimit(
                                "stop",
                                currentTime,
                                activeConfigs,
                                senderConfig
                            )
                        }


                        scope.launch {
                            webhookManager.handleEvent("stop", 0)
                        }
                    }
                }
            }


        lastRideState = newRideState


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
}