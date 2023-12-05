package com.dailysdkdemo.data.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleService
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.daily.CallClient
import co.daily.CallClientListener
import co.daily.exception.UnknownCallClientError
import co.daily.model.*
import co.daily.settings.*
import co.daily.settings.subscription.*
import co.daily.view.VideoView
import com.dailysdkdemo.ui.activities.HomeActivity.Companion.availableDevicesForCall
import com.dailysdkdemo.ui.activities.HomeActivity.Companion.lastUpdatedParticipant
import com.dailysdkdemo.ui.viewmodels.AppViewModel
import com.dailysdkdemo.data.utils.ACTION_STOP_FOREGROUND
import com.dailysdkdemo.data.utils.DemoState
import com.dailysdkdemo.data.utils.DemoStateListener
import com.dailysdkdemo.data.utils.TAG
import org.webrtc.SurfaceViewRenderer
import java.util.concurrent.CopyOnWriteArrayList

private const val ACTION_LEAVE = "action_leave"

class CallService : LifecycleService() {
    companion object {
        fun leaveIntent(context: Context): Intent = Intent(context, CallService::class.java).apply {
            action = ACTION_LEAVE
        }
    }

    var callClient: CallClient? = null

    private val profileActiveCamera = SubscriptionProfile("activeCamera")
    private val profileActiveScreenShare = SubscriptionProfile("activeScreenShare")

    private val listeners = CopyOnWriteArrayList<DemoStateListener>()
    private var state: DemoState = DemoState.default()

    fun addListener(listener: DemoStateListener) {
        listeners.add(listener)
        listener.onStateChanged(state)
    }

    fun removeListener(listener: DemoStateListener) {
        listeners.remove(listener)
    }

    private fun updateServiceState(stateUpdate: (DemoState) -> DemoState) {
        val newState = stateUpdate(state)
        state = newState
        listeners.forEach { it.onStateChanged(newState) }
    }

    /** join call **/
    fun joinVideoCall(context: Context, url: String, name: String, message: String) {
        try {
            val token =
                MeetingToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJvIjp0cnVlLCJkIjoiNzdmODc1MzMtNzk1NS00MWMyLThkZjMtMTg2MzZmNGVhNDlmIiwiaWF0IjoxNjc2MDA2MjAzfQ.Zo3YHCtr0p3jV1Y2X5LCasgi6aRMGK97TNNVghptfGM")
            callClient?.join(url, null, createClientSettings()) {
                it.error?.apply {
                    Log.e(TAG, "Got error while joining call: $msg")
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                it.success?.apply {
                    Log.i(TAG, "Successfully joined call in $meetingSession mode")
                    setUserName(name)
                    val audioDeviceInUse = callClient?.audioDevice()
                    Log.d(TAG, "Current audio route $audioDeviceInUse")
                    startNotificationService()
                }
            }
        } catch (e: UnknownCallClientError) {
            Log.e(TAG, "Failed to join call $e")
            callClient?.leave()
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun updateRemoteParticipantsCamera(
        participant: Participant
    ) {
        callClient?.updateRemoteParticipants(participant.run {
            mapOf(
                id to RemoteParticipantUpdate(
                    permissions = ParticipantPermissionsUpdate(
                        canSend = setOf(CanSendPermission.microphone)
                    )
                )
            )
        })
        callClientListener.onParticipantUpdated(participant)
        reInitiatePermission(participant)
    }

    fun updateRemoteParticipantsMic(
        participant: Participant
    ) {
        callClient?.updateRemoteParticipants(participant.run {
            mapOf(
                id to RemoteParticipantUpdate(
                    permissions = ParticipantPermissionsUpdate(
                        canSend = setOf(CanSendPermission.camera)
                    )
                )
            )
        })
        callClientListener.onParticipantUpdated(participant)
        reInitiatePermission(participant)
    }

    private fun reInitiatePermission(participant: Participant) {
        callClient?.updateRemoteParticipants(updatesById = participant.run {
            mapOf(
                id to RemoteParticipantUpdate(
                    ParticipantPermissionsUpdate(
                        true,
                        canSend = listOf(
                            CanSendPermission.camera,
                            CanSendPermission.microphone,
                            CanSendPermission.customAudio,
                            CanSendPermission.customVideo,
                            CanSendPermission.screenVideo,
                            CanSendPermission.screenAudio
                        ).toSet()
                    )
                )
            )
        })
    }

    /** leave call **/
    fun setUserName(name: String) {
        callClient?.setUserName(name)
    }

    /** leave call **/
    fun leaveCall() {
        stopNotificationService()
        callClient?.leave()
//        callState = LEFT
    }

    /** get present facing mode **/
    fun getFacingMode(): String? {
        return callClient?.inputs()?.camera?.settings?.facingMode
    }

    /** get participants input **/
    fun getAllParticipants(): Map<ParticipantId, Participant> {
        return callClient?.participants()?.all!!
    }

    /** get active participant **/
    fun getActiveSpeaker(): Participant? {
        return callClient?.activeSpeaker()
    }

    /** set default input **/
    private fun defaultInput() {
        callClient?.updateInputs(
            inputSettings = InputSettingsUpdate(
                microphone = Enable(), camera = Enable()
            )
        )
    }

    /** get saved default input **/
    fun getSavedInputStates(enabledCamera: Boolean, enabledMic: Boolean) {
        callClient?.updateInputs(
            inputSettings = InputSettingsUpdate(
                microphone = if (enabledMic) Enable() else Disable(),
                camera = if (enabledCamera) Enable() else Disable()
            )
        )
    }

    /** get camera present input **/
    fun getCamInput(): Boolean {
        return callClient?.inputs()?.camera?.isEnabled!!
    }

    /** get mic present input **/
    fun getMicInput(): Boolean {
        return callClient?.inputs()?.microphone?.isEnabled!!
    }

    /** to change camera input settings **/
    fun cameraOnOff(enabled: Boolean) {
        Log.d(TAG, "cameraOnOff $enabled")
        callClient?.updateInputs(
            InputSettingsUpdate(
                camera = if (enabled) Enable() else Disable()
            )
        )
    }

    /** to change mic input settings **/
    fun microphoneOnOff(enabled: Boolean) {
        Log.d(TAG, "microphoneOnOff $enabled")
        callClient?.updateInputs(
            InputSettingsUpdate(
                microphone = if (enabled) Enable() else Disable()
            )
        )
    }

    /** for mirroring **/
    fun setMirrorVideoViewWorkAround(view: VideoView, mirror: Boolean) {
        if (view.childCount > 0 && view.getChildAt(0) is SurfaceViewRenderer) {
            val surfaceViewRenderer: SurfaceViewRenderer = view.getChildAt(0) as SurfaceViewRenderer
            surfaceViewRenderer.setMirror(mirror)
        }
    }

    /** to show front/user camera state **/
    fun cameraFlip(view: VideoView, facingMode: FacingModeUpdate, mirror: Boolean) {
        callClient?.updateInputs(
            inputSettings = InputSettingsUpdate(
                camera = CameraInputSettingsUpdate(
                    settings = VideoMediaTrackSettingsUpdate(
                        facingMode = facingMode
                    )
                )
            )
        )
        Handler(Looper.getMainLooper()).postDelayed({
            setMirrorVideoViewWorkAround(view, mirror)
        }, 100)
    }

    /** set audio id **/
    fun setAudioId(deviceId: String) {
        callClient?.setAudioDevice(deviceId)
    }

    /** get available audio devices **/
    fun getAvailableDevices(): List<MediaDeviceInfo> {
        return callClient?.availableDevices()?.audio!!
    }

    inner class CallBinder : android.os.Binder() {
        val service: CallService
            get() = this@CallService
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i(TAG, "onStartCommand(${intent?.action})")
        if (intent?.action == ACTION_LEAVE) {
            callClient?.leave()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        Log.i(TAG, "onBind")
        return CallBinder()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind")
        stopSelf()
        stopNotificationService()
        return false
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")

        try {
            callClient = CallClient(appContext = applicationContext).apply {
                addListener(callClientListener)
            }
            setupParticipantSubscriptionProfiles(VideoMaxQualityUpdate.medium)
            defaultInput()
        } catch (e: Exception) {
            Log.e(TAG, "Got exception while creating CallClient", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
        callClient?.release()
        callClient = null
    }

    /** call client listeners **/
    private val callClientListener = object : CallClientListener {
        override fun onError(message: String) {
            Log.d(TAG, "Received error $message")
        }

        override fun onCallStateUpdated(
            state: CallState
        ) {
            Log.d(TAG, "onCallStateUpdated: $state")
            Log.i(TAG, "onCallStateUpdated($state)")
            updateServiceState { it.with(newStatus = state) }
        }

        override fun onInputsUpdated(inputSettings: InputSettings) {
            Log.d(TAG, "onInputsUpdated: $inputSettings")
            sendBroadcastMessage("refreshInputButtonsStateAndUpdateSelfVideoState")
        }

        override fun onPublishingUpdated(publishingSettings: PublishingSettings) {
            Log.d(TAG, "onPublishingUpdated: $publishingSettings")
            sendBroadcastMessage("refreshInputButtonsState")
        }

        override fun onParticipantJoined(participant: Participant) {
            Log.d(TAG, "onParticipantJoined: $participant")
            lastUpdatedParticipant = participant
            sendBroadcastMessage("onParticipantJoined")
        }

        override fun onParticipantUpdated(participant: Participant) {
            Log.d(TAG, "onParticipantUpdated: $participant")
            lastUpdatedParticipant = participant
            sendBroadcastMessage("onParticipantUpdated")
        }

        override fun onActiveSpeakerChanged(activeSpeaker: Participant?) {
            Log.d(TAG, "onActiveSpeakerChanged: ${activeSpeaker?.info?.userName}")
            sendBroadcastMessage("onActiveSpeakerChanged")
        }

        override fun onParticipantLeft(participant: Participant, reason: ParticipantLeftReason) {
            Log.d(TAG, "onParticipantLeft + ${participant.id}")
            lastUpdatedParticipant = participant
            sendBroadcastMessage("onParticipantLeft")
        }

        override fun onAvailableDevicesUpdated(availableDevices: AvailableDevices) {
            Log.d(TAG, "onAvailableDevicesUpdated $availableDevices")
            availableDevicesForCall = availableDevices
            sendBroadcastMessage("onAvailableDevicesUpdated")
        }
    }

    private fun sendBroadcastMessage(status: String) {
        val intent = Intent("ParticipantUpdates")
        // You can also include some extra data.
        intent.putExtra("Status", status)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun setupParticipantSubscriptionProfiles(videoMaxQualityUpdate: VideoMaxQualityUpdate) {
        callClient?.updateSubscriptionProfiles(
            mapOf(
                profileActiveCamera to SubscriptionProfileSettingsUpdate(
                    camera = VideoSubscriptionSettingsUpdate(
                        subscriptionState = Subscribed(),
                        receiveSettings = VideoReceiveSettingsUpdate(
                            maxQuality = videoMaxQualityUpdate
                        )
                    ), screenVideo = VideoSubscriptionSettingsUpdate(
                        subscriptionState = Subscribed()
                    )
                ), profileActiveScreenShare to SubscriptionProfileSettingsUpdate(
                    camera = VideoSubscriptionSettingsUpdate(
                        subscriptionState = Subscribed()
                    ), screenVideo = VideoSubscriptionSettingsUpdate(
                        subscriptionState = Subscribed(),
                        receiveSettings = VideoReceiveSettingsUpdate(
                            maxQuality = videoMaxQualityUpdate
                        )
                    )
                ), SubscriptionProfile.base to SubscriptionProfileSettingsUpdate(
                    camera = VideoSubscriptionSettingsUpdate(
                        subscriptionState = Subscribed()
                    ), screenVideo = VideoSubscriptionSettingsUpdate(
                        subscriptionState = Subscribed()
                    )
                )
            )
        )
    }

    fun changePreferredRemoteParticipantSubscription(
        activeParticipant: Participant?, trackType: AppViewModel.VideoTrackType?
    ) {
        val subscriptionsResult = callClient?.updateSubscriptions(
            // Improve the video quality of the remote participant that is currently displayed
            forParticipants = activeParticipant?.run {
                mapOf(
                    id to SubscriptionSettingsUpdate(
                        profile = when (trackType) {
                            AppViewModel.VideoTrackType.Camera -> profileActiveCamera
                            AppViewModel.VideoTrackType.ScreenShare -> profileActiveScreenShare
                            null -> SubscriptionProfile.base
                        }
                    )
                )
            } ?: mapOf(),
            // Unsubscribe from remote participants not currently displayed
            forParticipantsWithProfiles = mapOf(
                profileActiveCamera to SubscriptionSettingsUpdate(
                    profile = SubscriptionProfile.base
                ), profileActiveScreenShare to SubscriptionSettingsUpdate(
                    profile = SubscriptionProfile.base
                )
            ))
        Log.d(TAG, "Update subscriptions result $subscriptionsResult")
    }

    private fun createClientSettings(): ClientSettingsUpdate {
        return ClientSettingsUpdate(
            publishingSettings = PublishingSettingsUpdate(
                camera = CameraPublishingSettingsUpdate(
                    sendSettings = VideoSendSettingsUpdate(
                        encodings = VideoEncodingsSettingsUpdate(
                            settings = mapOf(
                                VideoMaxQualityUpdate.low to VideoEncodingSettingsUpdate(
                                    maxBitrate = BitRate(80000),
                                    maxFramerate = FrameRate(10),
                                    scaleResolutionDownBy = Scale(4F)
                                ), VideoMaxQualityUpdate.medium to VideoEncodingSettingsUpdate(
                                    maxBitrate = BitRate(680000),
                                    maxFramerate = FrameRate(30),
                                    scaleResolutionDownBy = Scale(1F)
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    /** start notification service **/
    private fun startNotificationService() {
        startService(Intent(this, NotificationService::class.java))
    }

    /** stop notification service **/
    private fun stopNotificationService() {
        val intentStop = Intent(this, NotificationService::class.java)
        intentStop.action = ACTION_STOP_FOREGROUND
        startService(intentStop)
    }
}