package com.dailysdkdemo.ui.activities

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.util.PatternsCompat
import androidx.core.view.updateLayoutParams
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import co.daily.model.*
import co.daily.settings.FacingMode
import co.daily.settings.FacingModeUpdate
import co.daily.view.VideoView
import com.dailysdkdemo.data.modelclasses.AllParticipants
import com.dailysdkdemo.data.preferences.Preferences
import com.dailysdkdemo.data.service.CallService
import com.dailysdkdemo.ui.adapters.AudioDevicesAdapter
import com.dailysdkdemo.ui.adapters.HomeAdapter
import com.dailysdkdemo.ui.adapters.PagerAdapter
import com.dailysdkdemo.ui.adapters.PeopleAdapter
import com.dailysdkdemo.ui.callbacks.AudioDeviceIdCallback
import com.dailysdkdemo.ui.callbacks.ChangeNameCallback
import com.dailysdkdemo.ui.callbacks.ClickCallBack
import com.dailysdkdemo.ui.callbacks.ClickWithPositionCallBack
import com.dailysdkdemo.ui.viewmodels.AppViewModel
import com.dailysdkdemo.R
import com.dailysdkdemo.data.utils.DemoState
import com.dailysdkdemo.data.utils.DemoStateListener
import com.dailysdkdemo.data.utils.TAG
import com.dailysdkdemo.data.utils.Utils
import com.dailysdkdemo.databinding.ActivityHomeBinding
import com.google.android.material.tabs.TabLayoutMediator
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.roundToInt

class HomeActivity : AppBaseActivity(), View.OnClickListener, DemoStateListener {
    companion object {
        var lastUpdatedParticipant: Participant? = null
        lateinit var availableDevicesForCall: AvailableDevices
    }

    private var demoState: DemoState? = null
    private lateinit var binding: ActivityHomeBinding
    private var selfVideoView: VideoView? = null
    private var localParticipant: Participant? = null
    private var displayedRemoteParticipant: Participant? = null
    private var facingMode: String? = ""
    private var toolsHidingEnabled = false
    private var toolsHidingOnClick = true
    private lateinit var peopleAdapter: PeopleAdapter
    private lateinit var pageIndicatorHomeAdapter: HomeAdapter
    private lateinit var alertDialog: Dialog
    private var participantsList: ArrayList<Participant> = ArrayList()
    private var mPosition: Int = 0
    private var pagerAdapter: PagerAdapter? = null
    private var newParticipantsList: ArrayList<AllParticipants> = ArrayList()
    private lateinit var viewModel: AppViewModel
    lateinit var mBoundService: CallService
    private var mServiceBound = false
    private var userName = ""
    private var userUrl = ""
    private var disableTimer = true
    private var mTabClickJoin: Long = 0
    private var mTabClickLeave: Long = 0
    private var isMorePopupShowing = false
    private var activeParticipant: Participant? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewModel = ViewModelProvider(this)[AppViewModel::class.java]
        binding = DataBindingUtil.setContentView(this, R.layout.activity_home)

        executeWithPermission()
        init()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        /** make design changes as per orientation */
        val orientation = newConfig.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE || orientation == Configuration.ORIENTATION_PORTRAIT) {
            binding = DataBindingUtil.setContentView(this, R.layout.activity_home)
            init()
            isMorePopupShowing = false
            if (this::mBoundService.isInitialized) {
                /** refresh inputs after orientation */
                refreshInputButtonsState()
                /* set self video state after orientation */
                updateSelfVideoView()

                if (demoState?.status == CallState.joined) {
                    showVideoLayoutAllViews()

                    checkMicrophoneStatus()
                    enableToolsHiding()
                    handleDraggableView()
                    updateNoOfPeopleText()
                    modifySelfVideoViewSize()

                    sortListAccordingToJoinTime()
                    if (participantsList.isNotEmpty() && pagerAdapter != null) {
                        setParticipantsAdapter()
                        binding.videoLayout.draggableLayout.mViewPagerVP2.visibility = View.VISIBLE
                    } else {
                        binding.videoLayout.draggableLayout.mViewPagerVP2.visibility = View.GONE
                    }
                } else {
                    binding.joinLayout.joinCallLayout.visibility = View.VISIBLE
                    binding.videoLayout.videoCallLayout.visibility = View.GONE

                    binding.joinLayout.linkEditText.setText(userUrl)
                    binding.joinLayout.nameEditText.setText(userName)
                    binding.joinLayout.userDetailsTV.text =
                        if (binding.joinLayout.nameEditText.text.isNotEmpty()) binding.joinLayout.nameEditText.text.toString()
                        else getString(R.string.guest)
                    binding.joinLayout.localCameraMaskView.text =
                        if (binding.joinLayout.nameEditText.text.isNotEmpty()) binding.joinLayout.nameEditText.text.toString()
                        else getString(R.string.guest)

                    checkJoinMicrophoneStatus()
                }
            }
        }
    }

    private fun updateSelfVideoView() {
        binding.apply {
            val track = localParticipant?.media?.camera?.track

            if (track != null && mBoundService.getCamInput()) {
                val view = VideoView(this@HomeActivity)
                selfVideoView = view
                if (demoState?.status == CallState.joined) {
                    VideoView.VideoScaleMode.FIT
                } else {
                    view.videoScaleMode =
                        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                            VideoView.VideoScaleMode.FIT
                        } else {
                            VideoView.VideoScaleMode.FILL
                        }
                }
                when (demoState?.status) {
                    CallState.joined -> {
                        view.bringVideoToFront = true
                        videoLayout.draggableLayout.selfVideoViewContainer.addView(view)
                        videoLayout.draggableLayout.localCameraMaskView.visibility = View.GONE
                    }
                    else -> {
                        view.bringVideoToFront = false
                        joinLayout.selfVideoViewContainer.addView(view)
                        joinLayout.localCameraMaskView.visibility = View.GONE
                    }
                }
                if (facingMode == FacingMode.environment.toString()) {
                    mBoundService.setMirrorVideoViewWorkAround(selfVideoView!!, false)
                } else {
                    mBoundService.setMirrorVideoViewWorkAround(selfVideoView!!, true)
                }
                view.track = track
            } else {
                hideSelfVideoView()
            }
        }
    }

    /** modify bottom linear layout containing mic, camera option acc to orientation */
    private fun modifyBottomOptionsLayout() {
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (this::mBoundService.isInitialized && demoState?.status == CallState.joined) {
                binding.bottomLayout.bottomLL?.gravity = Gravity.CENTER

                binding.bottomLayout.bottomToolsLayout.updateLayoutParams {
                    width = resources.getDimension(R.dimen.dimen_343dp).roundToInt()
                }
            } else {
                binding.bottomLayout.bottomLL?.gravity = Gravity.START
                binding.bottomLayout.bottomLL?.weightSum = 2f

                val params = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.weight = 1.0f
                binding.bottomLayout.bottomToolsLinearLayout?.layoutParams = params
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleDraggableView() {
        // to handle draggable self view
        binding.videoLayout.draggableLayout.dndView.setDragHandle(findViewById(R.id.videoViewRl))
        binding.videoLayout.draggableLayout.dndView.addDropTarget(findViewById(R.id.videoViewRlTarget1))
        binding.videoLayout.draggableLayout.dndView.addDropTarget(findViewById(R.id.videoViewRlTarget2))
        binding.videoLayout.draggableLayout.dndView.addDropTarget(findViewById(R.id.videoViewRlTarget3))
        binding.videoLayout.draggableLayout.dndView.addDropTarget(findViewById(R.id.videoViewRlTarget4))
        binding.videoLayout.draggableLayout.videoViewRl.setOnTouchListener { _, _ ->
            binding.videoLayout.draggableLayout.mViewPagerVP2.isUserInputEnabled = false
            true
        }
    }

    private fun startService() {
        val intent = Intent(this, CallService::class.java)
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    private val mServiceConnection = object : ServiceConnection {

        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "onServiceDisconnected")
            mServiceBound = false
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.d(TAG, "onServiceConnected")
            val myBinder = service as CallService.CallBinder
            mBoundService = myBinder.service
            mBoundService.addListener(this@HomeActivity)
            mServiceBound = true

            receiveBroadcastReceiver()
            refreshInputButtonsState()
            loadAudioDevice()
        }
    }

    private fun stopService() {
        if (this::mBoundService.isInitialized) {
            mBoundService.removeListener(this)
            mBoundService.leaveCall()
            if (mServiceBound) {
                unbindService(mServiceConnection)
                mServiceBound = false
            }
            val intent = Intent(this, CallService::class.java)
            stopService(intent)
            CallService.leaveIntent(this)
        }
    }

    override fun onDestroy() {
        // leave call after killing the app
        stopService()
        super.onDestroy()
    }

    private fun receiveBroadcastReceiver() {
        val mMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                when (intent.getStringExtra("Status")) {
                    "refreshInputButtonsStateAndUpdateSelfVideoState" -> {
                        refreshInputButtonsState()
                        updateSelfVideoState()
                    }
                    "refreshInputButtonsState" -> {
                        refreshInputButtonsState()
                    }
                    "onParticipantJoined" -> {
                        lastUpdatedParticipant?.let { updateParticipantVideoView(it) }
                    }
                    "onParticipantUpdated" -> {
                        lastUpdatedParticipant?.let { updateParticipantVideoView(it) }
                    }
                    "onActiveSpeakerChanged" -> {
                        lastUpdatedParticipant?.let { updateParticipantVideoView(it) }
                    }
                    "onParticipantLeft" -> {
                        removeRemoteParticipants()
                    }
                    "onAvailableDevicesUpdated" -> {
                        initAudioDialog(availableDevicesForCall.audio)
                    }
                }
            }
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            mMessageReceiver, IntentFilter("ParticipantUpdates")
        )
    }

    private fun init() {
        initPreferences()
        clickListeners()
        initEditTextListeners()
        initToolsHiding()
        modifyBottomOptionsLayout()
    }

    /** initializing daily sdk **/
    private fun initializeSDK() {
        startService()
    }

    /** get preferences data **/
    private fun initPreferences() {
        binding.apply {
            userUrl = getLastURL().toString()
            userName = getLastName().toString()
            joinLayout.linkEditText.setText(getLastURL())
            joinLayout.nameEditText.setText(getLastName())
            joinLayout.localCameraMaskView.text = getLastName()

            val containsUrl =
                PatternsCompat.WEB_URL.matcher(binding.joinLayout.linkEditText.text.toString())
                    .matches()
            binding.joinLayout.joinButton.isEnabled =
                containsUrl && binding.joinLayout.nameEditText.text.toString() != ""
            modifyJoinButton(
                binding.joinLayout.joinButton.isEnabled, binding.joinLayout.joinButton
            )
        }
    }

    /** click listeners **/
    private fun clickListeners() {
        binding.apply {
            bottomLayout.cameraToggleButton.setOnClickListener(this@HomeActivity)
            bottomLayout.microphoneToggleButton.setOnClickListener(this@HomeActivity)
            bottomLayout.tvMore.setOnClickListener(this@HomeActivity)

            joinLayout.joinButton.setOnClickListener(this@HomeActivity)
            joinLayout.cameraFlipIV.setOnClickListener(this@HomeActivity)
            joinLayout.audioIV.setOnClickListener(this@HomeActivity)

            videoLayout.leaveButton.setOnClickListener(this@HomeActivity)
            videoLayout.cameraFlipIV.setOnClickListener(this@HomeActivity)
            videoLayout.infoIV.setOnClickListener(this@HomeActivity)
            videoLayout.audioIV.setOnClickListener(this@HomeActivity)

            videoLayout.peopleTV.setOnClickListener(this@HomeActivity)
            videoLayout.crossIV.setOnClickListener(this@HomeActivity)

            pagerRl.setOnClickListener(this@HomeActivity)
            arrowIV.setOnClickListener(this@HomeActivity)

            mainLayout.setOnClickListener(this@HomeActivity)
        }
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.joinButton -> {
                // Preventing multiple clicks, using threshold of 1 second
                if (SystemClock.elapsedRealtime() - mTabClickJoin < 1000) {
                    return
                }
                mTabClickJoin = SystemClock.elapsedRealtime()
                if (checkPermission()) {
                    joinButtonClick()
                } else {
                    requestPermission()
                }
            }

            R.id.leaveButton -> {
                // Preventing multiple clicks, using threshold of 1 second
                if (SystemClock.elapsedRealtime() - mTabClickLeave < 1000) {
                    return
                }
                mTabClickLeave = SystemClock.elapsedRealtime()
                leaveButtonClick()
            }

            R.id.cameraToggleButton -> {
                if (checkPermission() && this::mBoundService.isInitialized) {
                    enableToolsHidingAfterClick()
                    clickVideoCamera()
                } else {
                    binding.bottomLayout.cameraToggleButton.isChecked = false
                    requestPermission()
                }
            }

            R.id.microphoneToggleButton -> {
                if (checkPermission() && this::mBoundService.isInitialized) {
                    enableToolsHidingAfterClick()
                    clickMicrophone()
                } else {
                    binding.bottomLayout.microphoneToggleButton.isChecked = false
                    requestPermission()
                }
            }

            R.id.cameraFlipIV -> {
                enableToolsHidingAfterClick()
                changeFacingMode()
            }

            R.id.audioIV -> {
                enableToolsHidingAfterClick()
                showAudioDialog()
            }

            R.id.infoIV -> {
                enableToolsHidingAfterClick()
                initInfoPopup()
            }

            R.id.tv_more -> {
                enableToolsHidingAfterClick()
                clickMoreOption()
            }

            R.id.peopleTV -> {
                binding.videoLayout.peopleLL.visibility = View.VISIBLE
                binding.videoLayout.moreLL.visibility = View.GONE
            }

            R.id.crossIV -> {
                binding.videoLayout.peopleLL.visibility = View.GONE
                binding.videoLayout.moreLL.visibility = View.VISIBLE
            }

            R.id.arrowIV -> {
                initPagerPopup()
            }

            R.id.pagerRl -> {
                initPagerPopup()
            }

            R.id.mainLayout -> {
                when (demoState?.status) {
                    CallState.joined -> onShowTools()
                    else -> {}
                }
            }
        }
    }

    private fun joinButtonClick() {
        if (isNetworkAvailable(this@HomeActivity)) {
            savePreferences()
            modifyJoinButton(false, binding.joinLayout.joinButton)
            Utils.hideSoftKeyBoard(this@HomeActivity, binding.joinLayout.nameEditText)
            mBoundService.joinVideoCall(
                this@HomeActivity,
                binding.joinLayout.linkEditText.text.toString(),
                binding.joinLayout.nameEditText.text.toString(),
                getString(R.string.failed_to_join_call)
            )
        } else {
            showMessage(getString(R.string.no_internet_connection))
        }
    }

    private fun savePreferences() {
        Preferences.writeString(
            this@HomeActivity, Preferences.LAST_URL, binding.joinLayout.linkEditText.text.toString()
        )
        Preferences.writeString(
            this@HomeActivity, Preferences.NAME, binding.joinLayout.nameEditText.text.toString()
        )
    }

    private fun leaveButtonClick() {
        mBoundService.leaveCall()
        CallService.leaveIntent(this)
    }

    private fun clickVideoCamera() {
        mBoundService.cameraOnOff(binding.bottomLayout.cameraToggleButton.isChecked)
    }

    private fun clickMicrophone() {
        mBoundService.microphoneOnOff(binding.bottomLayout.microphoneToggleButton.isChecked)
        checkJoinMicrophoneStatus()
    }

    /** to manage mic state of user in join screen with video view */
    private fun checkJoinMicrophoneStatus() {
        if (binding.bottomLayout.microphoneToggleButton.isChecked) {
            binding.joinLayout.userDetailsTV.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_microphone_white, 0, 0, 0
            )
            binding.videoLayout.draggableLayout.userDetailsTV.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_microphone_white, 0, 0, 0
            )
        } else {
            binding.joinLayout.userDetailsTV.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_microphone_off, 0, 0, 0
            )
            binding.videoLayout.draggableLayout.userDetailsTV.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_microphone_off, 0, 0, 0
            )
        }
    }

    private fun changeFacingMode() {
        Log.d(TAG, "onFacingMode: $facingMode")
        if (facingMode == FacingMode.user.toString()) {
            selfVideoView?.let { mBoundService.cameraFlip(it, FacingModeUpdate.environment, false) }
        } else if (facingMode == FacingMode.environment.toString()) {
            selfVideoView?.let { mBoundService.cameraFlip(it, FacingModeUpdate.user, true) }
        }
    }

    private fun clickMoreOption() {
        if (demoState?.status == CallState.joined) {
            if (isMorePopupShowing) {
                isMorePopupShowing = false
                binding.videoLayout.morePopupLL.visibility = View.GONE
                return
            }
            binding.videoLayout.morePopupLL.visibility = View.VISIBLE
            initMorePopup(participantsList)
            isMorePopupShowing = true
        }
    }

    private fun initEditTextListeners() {
        binding.apply {
            addTextChangedListener(joinLayout.linkEditText)
            addTextChangedListener(joinLayout.nameEditText)
        }
    }

    private fun addTextChangedListener(editText: EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (editText == binding.joinLayout.nameEditText) userName =
                    s.toString() else userUrl = s.toString()
                val containsUrl =
                    PatternsCompat.WEB_URL.matcher(binding.joinLayout.linkEditText.text.toString())
                        .matches()
                binding.joinLayout.joinButton.isEnabled =
                    containsUrl && binding.joinLayout.nameEditText.text.toString() != ""
                modifyJoinButton(
                    binding.joinLayout.joinButton.isEnabled, binding.joinLayout.joinButton
                )
            }

            override fun afterTextChanged(s: Editable?) {
                if (editText == binding.joinLayout.nameEditText) userName =
                    s.toString() else userUrl = s.toString()
                binding.joinLayout.localCameraMaskView.text =
                    if (binding.joinLayout.nameEditText.text.isNotEmpty()) binding.joinLayout.nameEditText.text.toString()
                    else getString(R.string.guest)
                manageJoinScreenMicStatus()
            }
        })
    }

    private fun initToolsHiding() {
        val callback: ViewPager2.OnPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(
                position: Int, positionOffset: Float, positionOffsetPixels: Int
            ) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                Log.e(TAG, "onPageScrolled: $mPosition")
                mPosition = position
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                Log.e(TAG, "onPageSelected: $position")
                mPosition = position
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                Log.e(TAG, "onPageSelected: $state")
            }
        }

        binding.videoLayout.draggableLayout.mViewPagerVP2.registerOnPageChangeCallback(callback)

        //noinspection ClickableViewAccessibility
        binding.videoLayout.backgroundTapInterceptor.setOnTouchListener { _, _ ->
            binding.videoLayout.draggableLayout.mViewPagerVP2.isUserInputEnabled = true

            resetInfoPopup()
            resetPagerPopup()
            resetMorePopup()
            if (participantsList.isEmpty()) {
                when (demoState?.status) {
                    CallState.joined -> onShowTools()
                    else -> {}
                }
            }
            false // Always return false so the touch passes through
        }
    }

    private fun enableToolsHiding() {
//        if (disableTimer) {
//            binding.apply {
//                // Hide the buttons after 3 seconds
//                videoLayout.optionsRl.postDelayed(buttonToolsRunnable, 3000)
//                bottomLayout.bottomToolsLayout.postDelayed(buttonToolsRunnable, 3000)
//            }
//        }
        toolsHidingEnabled = true
    }

    private fun enableToolsHidingAfterClick() {
        if (this::mBoundService.isInitialized && /*mBoundService.callState == JOINED*/ demoState?.status == CallState.joined) {
            disableTimer = true
            enableToolsHiding()
        }
    }

    private fun onShowTools() {
        if (toolsHidingEnabled) {
            if (toolsHidingOnClick) {
                disableToolsHidingOnClick()
            } else {
                disableTimer = false
                disableToolsHiding()
                enableToolsHiding()
            }
        }
    }

    private fun disableToolsHidingOnClick() {
        binding.apply {
            // for top tools
            videoLayout.optionsRl.visibility = View.INVISIBLE
            videoLayout.scrimView.visibility = View.INVISIBLE

            // for bottom tools
            bottomLayout.bottomToolsLayout.visibility = View.INVISIBLE
        }
        toolsHidingOnClick = false
    }

    private fun disableToolsHiding() {
        binding.apply {
            // for top tools
            videoLayout.optionsRl.animate().cancel()
            videoLayout.optionsRl.visibility = View.VISIBLE
            videoLayout.scrimView.visibility = View.VISIBLE
            videoLayout.optionsRl.alpha = 1.0f
            videoLayout.optionsRl.removeCallbacks(buttonToolsRunnable)

            // for bottom tools
            bottomLayout.bottomToolsLayout.animate().cancel()
            bottomLayout.bottomToolsLayout.visibility = View.VISIBLE
            bottomLayout.bottomToolsLayout.alpha = 1.0f
            bottomLayout.bottomToolsLayout.removeCallbacks(buttonToolsRunnable)
        }
        toolsHidingOnClick = true
        toolsHidingEnabled = false
    }

    private val buttonToolsRunnable: Runnable = Runnable {
        binding.apply {
            videoLayout.optionsRl.animate().alpha(0.0f).setDuration(500)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        videoLayout.optionsRl.visibility = View.INVISIBLE
                        videoLayout.scrimView.visibility = View.INVISIBLE
                    }
                })

            bottomLayout.bottomToolsLayout.animate().alpha(0.0f).setDuration(500)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        bottomLayout.bottomToolsLayout.visibility = View.INVISIBLE
                    }
                })
            toolsHidingOnClick = false
        }
    }

    /** to update self video view **/
    private fun updateSelfVideoState() {
        // Due to a bug in older versions of Android (including Android 9), it's not
        // sufficient to simply hide a SurfaceView if it overlaps with another
        // SurfaceView, so we destroy and recreate it as necessary.
        binding.apply {

            val track = localParticipant?.media?.camera?.track

            if (track != null && mBoundService.getCamInput()) {
                val view: VideoView = selfVideoView ?: run {
                    val view = VideoView(this@HomeActivity)
                    selfVideoView = view
                    if (demoState?.status == CallState.joined) {
                        VideoView.VideoScaleMode.FIT
                    } else {
                        view.videoScaleMode =
                            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                                VideoView.VideoScaleMode.FIT
                            } else {
                                VideoView.VideoScaleMode.FILL
                            }
                    }
                    when (demoState?.status) {
                        CallState.joined -> {
                            view.bringVideoToFront = true
                            videoLayout.draggableLayout.selfVideoViewContainer.addView(view)
                            videoLayout.draggableLayout.localCameraMaskView.visibility = View.GONE
                        }
                        else -> {
                            view.bringVideoToFront = false
                            joinLayout.selfVideoViewContainer.addView(view)
                            joinLayout.localCameraMaskView.visibility = View.GONE
                        }
                    }
                    getFacingMode()
                    view
                }
                view.track = track
            } else {
                hideSelfVideoView()
            }
        }
    }

    private fun hideSelfVideoView() {
        binding.apply {
            selfVideoView?.apply {
                when (demoState?.status) {
                    CallState.joined -> {
                        videoLayout.draggableLayout.selfVideoViewContainer.removeView(this)
                        videoLayout.draggableLayout.localCameraMaskView.visibility = View.VISIBLE
                        videoLayout.draggableLayout.localCameraMaskView.text =
                            resources.getText(R.string.you_lbl)
                    }
                    else -> {
                        joinLayout.selfVideoViewContainer.removeView(this)
                        joinLayout.localCameraMaskView.visibility = View.VISIBLE
                        joinLayout.localCameraMaskView.text = userName
                    }
                }
            }
            selfVideoView = null
        }
    }

    /** to update participant's video view **/
    private fun updateParticipantVideoView(participant: Participant) {
        if (participant.info.isLocal) {
            localParticipant = participant
            updateSelfVideoState()
        } else {
            showRemoteParticipants()
        }
    }

    /** refresh input button states for local user **/
    private fun refreshInputButtonsState() {
        binding.bottomLayout.cameraToggleButton.isChecked = mBoundService.getCamInput()
        binding.bottomLayout.microphoneToggleButton.isChecked = mBoundService.getMicInput()
        facingMode = mBoundService.getFacingMode()
        manageJoinScreenMicStatus()
    }

    /** manage mic status for local user **/
    private fun manageJoinScreenMicStatus() {
        when {
            binding.bottomLayout.cameraToggleButton.isChecked -> {
                binding.joinLayout.userDetailsTV.compoundDrawablePadding =
                    resources.getDimensionPixelSize(R.dimen.dimen_7dp)
                binding.joinLayout.userDetailsTV.text =
                    if (binding.joinLayout.nameEditText.text.isNotEmpty()) binding.joinLayout.nameEditText.text.toString()
                    else getString(R.string.guest)
            }
            else -> {
                binding.joinLayout.userDetailsTV.compoundDrawablePadding = 0
                binding.joinLayout.userDetailsTV.text = ""
            }
        }
    }

    /** update state once local participant has joined the call **/
    private fun onJoinedMeeting() {
        Log.i(TAG, "onJoinedMeeting")
        showVideoLayout()
        checkMicrophoneStatus()
        showRemoteParticipants()
        enableToolsHiding()
    }

    /** check mic status for remote user **/
    private fun checkMicrophoneStatus() {
        when {
            mBoundService.getMicInput() -> {
                binding.videoLayout.draggableLayout.userDetailsTV.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_microphone_white, 0, 0, 0
                )
            }
            else -> {
                binding.videoLayout.draggableLayout.userDetailsTV.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_microphone_off, 0, 0, 0
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @SuppressLint("NotifyDataSetChanged")
    private fun removeRemoteParticipants() {
        if (participantsList.isNotEmpty()) {
            sortListAccordingToJoinTime()
            participantsList.remove(
                lastUpdatedParticipant
            )
            newParticipantsList.clear()
            val chunkedList: ArrayList<ArrayList<Participant>> =
                participantsList.chunked(3) as ArrayList<ArrayList<Participant>>

            if (participantsList.size > 1) {
                val activeParticipantList: ArrayList<Participant> = ArrayList()
                if (mBoundService.getActiveSpeaker() != null) {
                    if (activeParticipantList.contains(activeParticipant)) {
                        activeParticipantList.remove(activeParticipant)
                    }
                } else {
                    activeParticipant = participantsList[0]
                }
                activeParticipant?.let { activeParticipantList.add(it) }
                chunkedList.add(
                    0, activeParticipantList
                )
            }

            for ((index, value) in chunkedList.toList().withIndex()) {
                newParticipantsList.add(
                    AllParticipants(
                        (index + 1), value
                    )
                )
            }
            pagerAdapter?.setItems(newParticipantsList)
        } else {
            if (pagerAdapter != null) {
                pagerAdapter?.notifyDataSetChanged()
            }
            binding.videoLayout.draggableLayout.mViewPagerVP2.visibility = View.GONE
        }
        updateNoOfPeopleText()
        modifySelfVideoViewSize()
        updatePeopleInMoreAdapter(participantsList)
        updatePeopleInPageIndicatorHomeAdapter()
        if (participantsList.isEmpty()) {
            // to handle self video size if changed
            binding.videoLayout.draggableLayout.dndView.updateDragPositionToBottomEnd()
        }
    }

    private fun sortListAccordingToJoinTime() {
        participantsList = mBoundService.getAllParticipants().values.filter {
            !it.info.isLocal
        } as ArrayList<Participant>
        this.participantsList.sortWith { o1: Participant, o2: Participant ->
            if (o1.info.joinedAt == null || o2.info.joinedAt == null) Log.d(TAG, "Collections.sort")
            o1.info.joinedAt!!.compareTo(o2.info.joinedAt)
        }
    }

    private fun showRemoteParticipants() {

        val allParticipants = mBoundService.getAllParticipants()
        Log.d(TAG, "allParticipants: ${allParticipants.values}")

        val participant: Participant?
        val track: MediaStreamTrack?
        val trackType: AppViewModel.VideoTrackType?

        val participantWhoIsSharingScreen =
            allParticipants.values.firstOrNull { isMediaAvailable(it.media?.screenVideo) }?.id

        val activeSpeaker = mBoundService.getActiveSpeaker()?.takeUnless { it.info.isLocal }?.id

        /*
            The preference is:
                - The participant who is sharing their screen
                - The active speaker
                - The last displayed remote participant
                - Any remote participant who has their video opened
        */
        val participantId =
            participantWhoIsSharingScreen ?: activeSpeaker ?: displayedRemoteParticipant?.id
            ?: mBoundService.getAllParticipants().values.firstOrNull {
                !it.info.isLocal && isMediaAvailable(it.media?.camera)
            }?.id

        // Get the latest information about the participant
        participant = allParticipants[participantId]
        displayedRemoteParticipant = participant

        if (participantsList.isNotEmpty()) {
            activeParticipant = participant ?: participantsList[0]
        }

        if (isMediaAvailable(participant?.media?.screenVideo)) {
            track = participant?.media?.screenVideo?.track
            trackType = AppViewModel.VideoTrackType.ScreenShare
        } else if (isMediaAvailable(participant?.media?.camera)) {
            track = participant?.media?.camera?.track
            trackType = AppViewModel.VideoTrackType.Camera
        } else {
            track = null
            trackType = null
        }

        sortListAccordingToJoinTime()

        // update all the adapters data
        setUpAdapterData(participantsList)
        updatePeopleInMoreAdapter(participantsList)
        updatePeopleInPageIndicatorHomeAdapter()
    }

    @Suppress("UNCHECKED_CAST")
    @SuppressLint("NotifyDataSetChanged")
    private fun setUpAdapterData(
        allParticipants: List<Participant>
    ) {
        if (allParticipants.isNotEmpty()) {
            val chunkedList: ArrayList<ArrayList<Participant>> =
                allParticipants.chunked(3) as ArrayList<ArrayList<Participant>>

            if (participantsList.size > 1) {
                val activeParticipantList: ArrayList<Participant> = ArrayList()
                if (activeParticipantList.contains(activeParticipant)) {
                    activeParticipantList.remove(activeParticipant)
                }
                activeParticipant?.let { activeParticipantList.add(it) }
                chunkedList.add(
                    0, activeParticipantList
                )
            }

            for ((index, value) in chunkedList.toList().withIndex()) {
                newParticipantsList.add(
                    AllParticipants(
                        (index + 1), value
                    )
                )
            }

            newParticipantsList =
                newParticipantsList.distinctBy { it.id } as ArrayList<AllParticipants>

            setAdapter()
            binding.videoLayout.draggableLayout.mViewPagerVP2.visibility = View.VISIBLE
        } else {
            if (pagerAdapter != null) {
                pagerAdapter?.notifyDataSetChanged()
            }
            binding.videoLayout.draggableLayout.mViewPagerVP2.visibility = View.GONE
        }
        modifySelfVideoViewSize()
        updateNoOfPeopleText()
    }

    /** modify self video view size according to the designs
     * if no participants: show self video view in full screen and disable draggable view touch
     * if there are participants reduce size of self video view and make it draggable */
    private fun modifySelfVideoViewSize() {
        if (participantsList.isNotEmpty()) {
            // change height and width of self view in case there are no other participants
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT
            )
            // Set the layout_alignParentStart property
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            params.addRule(RelativeLayout.ALIGN_PARENT_END)
            params.removeRule(RelativeLayout.CENTER_IN_PARENT)

            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                val horizontalMargin = resources.getDimension(R.dimen.dimen_16dp).toInt()
                val verticalMargin = resources.getDimension(R.dimen.dimen_100dp).toInt()
                params.setMargins(
                    horizontalMargin,
                    verticalMargin,
                    horizontalMargin,
                    verticalMargin
                )
            } else {
                val horizontalMargin = resources.getDimension(R.dimen.dimen_16dp).toInt()
                params.setMargins(horizontalMargin, 0, horizontalMargin, 0)
            }

            binding.videoLayout.draggableLayout.videoViewRl.layoutParams = params

            binding.videoLayout.draggableLayout.videoViewRl.updateLayoutParams {
                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    height = resources.getDimension(R.dimen.dimen_128dp).roundToInt()
                    width = resources.getDimension(R.dimen.dimen_72dp).roundToInt()
                } else {
                    height = resources.getDimension(R.dimen.dimen_72dp).roundToInt()
                    width = resources.getDimension(R.dimen.dimen_128dp).roundToInt()
                }
            }
            // enable user interaction
            binding.videoLayout.draggableLayout.dndView.setInterceptTouchEvents(true)

            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (participantsList.size > 1) {
                    val params1 =
                        binding.videoLayout.draggableLayout.dndView.layoutParams as ConstraintLayout.LayoutParams
                    params1.topToBottom = binding.videoLayout.optionsRl.id
                    binding.videoLayout.draggableLayout.dndView.layoutParams = params1
                } else {
                    val params1 =
                        binding.videoLayout.draggableLayout.dndView.layoutParams as ConstraintLayout.LayoutParams
                    params1.topToBottom = ConstraintLayout.LayoutParams.UNSET
                    binding.videoLayout.draggableLayout.dndView.layoutParams = params1
                }
            }

        } else {
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.MATCH_PARENT
            )
            // Set the layout_alignParentStart property
            params.addRule(RelativeLayout.CENTER_IN_PARENT)
            params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            params.removeRule(RelativeLayout.ALIGN_PARENT_END)
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                params.setMargins(0, 10, 0, 0)

            } else {
                params.setMargins(10, 60, 10, 60)
            }

            binding.videoLayout.draggableLayout.videoViewRl.layoutParams = params

            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                binding.videoLayout.draggableLayout.videoViewRl.updateLayoutParams {
                    width = resources.getDimension(R.dimen.dimen_568dp).roundToInt()
                }
            }
            // disable user interaction
            binding.videoLayout.draggableLayout.dndView.setInterceptTouchEvents(false)
        }
    }

    @Suppress("UNCHECKED_CAST")
    @SuppressLint("NotifyDataSetChanged")
    private fun setAdapter() {
        if (pagerAdapter != null) {
            val chunkedList: ArrayList<ArrayList<Participant>> =
                participantsList.chunked(3) as ArrayList<ArrayList<Participant>>

            if (participantsList.size > 1) {
                val activeParticipantList: ArrayList<Participant> = ArrayList()
                if (activeParticipantList.contains(activeParticipant)) {
                    activeParticipantList.remove(activeParticipant)
                }
                activeParticipant?.let { activeParticipantList.add(it) }
                chunkedList.add(
                    0, activeParticipantList
                )
            }

            for ((index, _) in newParticipantsList.toList().withIndex()) {
                for ((index1, value2) in chunkedList.toList().withIndex()) {
                    if (index == index1) {
                        val oldItem = newParticipantsList[index]
                        val newItem = oldItem.copy(participant = value2)
                        newParticipantsList[index] = newItem
                    }
                }
            }
            Log.d(TAG, "new list: update $newParticipantsList")
            pagerAdapter?.setItems(newParticipantsList)
        } else {
            setParticipantsAdapter()
        }
    }

    private fun setParticipantsAdapter() {
        pagerAdapter = PagerAdapter(this@HomeActivity, object : ClickCallBack {
            override fun onClick() {
                when (demoState?.status) {
                    CallState.joined -> onShowTools()
                    else -> {}
                }
            }
        })
        Log.d(TAG, "new list: add $newParticipantsList")
        pagerAdapter?.setItems(newParticipantsList)
        binding.videoLayout.draggableLayout.mViewPagerVP2.adapter = pagerAdapter
        binding.videoLayout.draggableLayout.mViewPagerVP2.setCurrentItem(mPosition, false)
        val child = binding.videoLayout.draggableLayout.mViewPagerVP2.getChildAt(0)
        (child as? RecyclerView)?.overScrollMode = View.OVER_SCROLL_NEVER
        TabLayoutMediator(
            binding.tabDots, binding.videoLayout.draggableLayout.mViewPagerVP2
        ) { _, _ ->
        }.attach()
        binding.videoLayout.draggableLayout.mViewPagerVP2.canScrollVertically(-1)

    }

    /** update mic status and name for remote user **/
    private fun updateNoOfPeopleText() {
        binding.pagerRl.visibility = View.GONE
        binding.peopleRL.visibility = View.VISIBLE
        if (mBoundService.getAllParticipants().size > 1) {
            if (newParticipantsList.size > 1) {
                binding.pagerRl.visibility = View.VISIBLE
            }
            val msg = resources.getString(
                R.string.people_in_call, mBoundService.getAllParticipants().size
            )
            binding.peopleTV.text = msg
        } else {
            binding.peopleTV.text = resources.getString(R.string.no_one_else_in_meeting)
        }
    }

    /** reset app after leaving the call **/
    private fun resetAppState() {
        mPosition = 0
        lastUpdatedParticipant = null
        showJoinLayout()
        activeParticipant = null
        displayedRemoteParticipant = null
        mBoundService.getSavedInputStates(
            binding.bottomLayout.cameraToggleButton.isChecked,
            binding.bottomLayout.microphoneToggleButton.isChecked
        )
        disableToolsHiding()
        participantsList.clear()
        newParticipantsList.clear()
    }

    /** show join layout in starting by default and after leaving call **/
    private fun showJoinLayout() {
        if (demoState?.status == CallState.left) {
            initPreferences()
            modifyBottomOptionsLayout()
            binding.apply {
                mainLayout.setBackgroundColor(
                    ContextCompat.getColor(
                        this@HomeActivity, R.color.white
                    )
                )
                bottomLayout.tvMore.visibility = View.INVISIBLE
                peopleRL.visibility = View.GONE
                videoLayout.videoCallLayout.visibility = View.GONE
                joinLayout.joinCallLayout.visibility = View.VISIBLE
                joinLayout.selfVideoViewContainer.visibility = View.VISIBLE

                if (selfVideoView != null) {
                    videoLayout.draggableLayout.selfVideoViewContainer.removeView(selfVideoView)
                    joinLayout.selfVideoViewContainer.addView(selfVideoView)

                    getFacingMode()

                    if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                        selfVideoView?.videoScaleMode = VideoView.VideoScaleMode.FIT
                    } else {
                        selfVideoView?.videoScaleMode = VideoView.VideoScaleMode.FILL
                    }
                }
            }
            if (pagerAdapter != null) {
                pagerAdapter = null
            }
        }
    }

    /** show video layout joining the call **/
    private fun showVideoLayout() {
        if (demoState?.status == CallState.joined) {
            modifyBottomOptionsLayout()
            handleDraggableView()
            binding.apply {
                showVideoLayoutAllViews()

                if (selfVideoView != null) {
                    joinLayout.selfVideoViewContainer.removeView(selfVideoView)
                    joinLayout.selfVideoViewContainer.removeAllViews()
                    joinLayout.selfVideoViewContainer.invalidate()
                    System.gc()
                    selfVideoView?.bringVideoToFront = true
                    videoLayout.draggableLayout.selfVideoViewContainer.addView(selfVideoView)
                    getFacingMode()
                    selfVideoView?.videoScaleMode = VideoView.VideoScaleMode.FILL
                }
            }
        }
    }

    /** show video layout's inner views */
    private fun showVideoLayoutAllViews() {
        binding.apply {
            mainLayout.setBackgroundColor(
                ContextCompat.getColor(
                    this@HomeActivity, R.color.eerie_black
                )
            )
            bottomLayout.tvMore.visibility = View.VISIBLE
            peopleRL.visibility = View.VISIBLE
            joinLayout.joinCallLayout.visibility = View.GONE
            joinLayout.selfVideoViewContainer.visibility = View.GONE
            fadeOut(joinLayout.joinCallLayout)
            joinLayout.linkEditText.text.clear()
            joinLayout.nameEditText.text.clear()

            videoLayout.videoCallLayout.visibility = View.VISIBLE
            fadeIn(videoLayout.videoCallLayout)
            videoLayout.draggableLayout.localCameraMaskView.text =
                resources.getText(R.string.you_lbl)
        }
    }

    /** check if facing mode is environment or user
     * if user then set mirror true
     * else false */
    private fun getFacingMode() {
        if (facingMode == FacingMode.environment.toString()) {
            mBoundService.setMirrorVideoViewWorkAround(selfVideoView!!, false)
        } else {
            mBoundService.setMirrorVideoViewWorkAround(selfVideoView!!, true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    /** setting up audio dialog */
    private fun loadAudioDevice() {
        val audioDevices = mBoundService.getAvailableDevices()
        mBoundService.setAudioId(audioDevices[0].deviceId)
        initAudioDialog(audioDevices)
    }

    private fun initAudioDialog(audioDevices: List<MediaDeviceInfo>) {
        alertDialog = Dialog(this)
        alertDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        alertDialog.setContentView(R.layout.audio_switcher_dialog)
        alertDialog.setCanceledOnTouchOutside(true)
        alertDialog.setCancelable(true)
        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // set the custom dialog components - text, image and button
        val audioDevicesRV: RecyclerView = alertDialog.findViewById(R.id.audioDevicesRV)
        val audioDevicesAdapter =
            AudioDevicesAdapter(this@HomeActivity, audioDevices, object : AudioDeviceIdCallback {
                override fun onClick(deviceId: String) {
                    mBoundService.setAudioId(deviceId)
                }
            })
        val mLayoutManager = LinearLayoutManager(this@HomeActivity)
        audioDevicesRV.layoutManager = mLayoutManager
        audioDevicesRV.adapter = audioDevicesAdapter
    }

    private fun showAudioDialog() {
        alertDialog.show()
    }

    /** setting up more popup on click of more option */
    @SuppressLint("NotifyDataSetChanged")
    private fun initMorePopup(allParticipants: ArrayList<Participant>) {
        resetPagerPopup()
        resetInfoPopup()
        updateNoOfPeopleTextInPopup()

        allParticipants.sortWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.info.userName.toString() }
        )

        if (allParticipants.contains(localParticipant)) {
            allParticipants.remove(localParticipant)
        }
        localParticipant?.let { allParticipants.add(0, it) }

        // set people adapter
        peopleAdapter = PeopleAdapter(this@HomeActivity,
            allParticipants, localParticipant,
            object : ChangeNameCallback {
                override fun onChangeName(name: String) {
                    mBoundService.setUserName(name)
                    Preferences.writeString(this@HomeActivity, Preferences.NAME, name)
                    peopleAdapter.notifyDataSetChanged()
                }

                override fun onUpdateMic(participant: Participant) {
                    mBoundService.updateRemoteParticipantsMic(participant)
                    peopleAdapter.notifyDataSetChanged()
                }

                override fun onUpdateCam(participant: Participant) {
                    mBoundService.updateRemoteParticipantsCamera(participant)
                    peopleAdapter.notifyDataSetChanged()
                }
            })
        val mLayoutManager = LinearLayoutManager(this@HomeActivity)
        binding.videoLayout.peopleRV.layoutManager = mLayoutManager
        binding.videoLayout.peopleRV.adapter = peopleAdapter

        if (participantsList.isNotEmpty() && localParticipant?.info?.isOwner == true) {
            binding.videoLayout.muteAllTV.visibility = View.VISIBLE
        } else {
            binding.videoLayout.muteAllTV.visibility = View.GONE
        }

        binding.videoLayout.muteAllTV.setOnClickListener {
            for (item in mBoundService.getAllParticipants().values.toList()) {
                mBoundService.updateRemoteParticipantsMic(item)
            }
        }
    }

    private fun updateNoOfPeopleTextInPopup() {
        if (mBoundService.getAllParticipants().values.toList().size > 2) {
            val msg = resources.getString(
                R.string.people_in_call, mBoundService.getAllParticipants().values.toList().size
            )
            binding.videoLayout.noOfPeopleTV.text = msg
        } else {
            binding.videoLayout.noOfPeopleTV.text = getString(R.string.no_one_else_in_meeting)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updatePeopleInMoreAdapter(allParticipants: ArrayList<Participant>) {
        if (this::peopleAdapter.isInitialized) {
            updateNoOfPeopleTextInPopup()

            allParticipants.sortWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.info.userName.toString() }
            )

            if (allParticipants.contains(localParticipant)) {
                allParticipants.remove(localParticipant)
            }
            localParticipant?.let { allParticipants.add(0, it) }

            peopleAdapter.setData(allParticipants)
            peopleAdapter.notifyDataSetChanged()
        }
    }

    private fun resetMorePopup() {
        binding.videoLayout.morePopupLL.visibility = View.GONE
        binding.videoLayout.peopleLL.visibility = View.GONE
        binding.videoLayout.moreLL.visibility = View.VISIBLE
    }

    /** setting up pager popup on click of bottom arrow */
    private fun initPagerPopup() {
        resetInfoPopup()
        resetMorePopup()
        binding.videoLayout.pagerPopupLL.visibility = View.VISIBLE

        pageIndicatorHomeAdapter =
            HomeAdapter(
                this@HomeActivity,
                newParticipantsList,
                mPosition,
                object : ClickWithPositionCallBack {
                    override fun onClick(position: Int) {
                        binding.videoLayout.pagerPopupLL.visibility = View.GONE
                        mPosition = position
                        binding.videoLayout.draggableLayout.mViewPagerVP2.setCurrentItem(
                            mPosition, false
                        )
                    }
                })
        val mLayoutManager = LinearLayoutManager(this@HomeActivity)
        binding.videoLayout.homeRV.layoutManager = mLayoutManager
        binding.videoLayout.homeRV.adapter = pageIndicatorHomeAdapter
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updatePeopleInPageIndicatorHomeAdapter() {
        if (this::pageIndicatorHomeAdapter.isInitialized) {
            pageIndicatorHomeAdapter.setData(newParticipantsList)
            pageIndicatorHomeAdapter.notifyDataSetChanged()
        }
    }

    private fun resetPagerPopup() {
        binding.videoLayout.pagerPopupLL.visibility = View.GONE
    }

    /** setting up info popup on click of bottom arrow */
    private fun initInfoPopup() {
        resetMorePopup()
        resetPagerPopup()
        binding.videoLayout.infoPopupLL.visibility = View.VISIBLE
        binding.videoLayout.linkTV.text = getLastURL()
        binding.videoLayout.copyLinkButton.setOnClickListener {
            binding.videoLayout.copyLinkButton.icon =
                ContextCompat.getDrawable(this, R.drawable.ic_copied)
            binding.videoLayout.copyLinkButton.text = resources.getString(R.string.link_copied)

            val clipboard: ClipboardManager =
                getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip =
                ClipData.newPlainText(resources.getString(R.string.link_copied), getLastURL())
            clipboard.setPrimaryClip(clip)
        }
    }

    private fun resetInfoPopup() {
        binding.videoLayout.infoPopupLL.visibility = View.GONE
    }

    /** to manage on back pressed when user is already in the call **/
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (demoState?.status == CallState.initialized || demoState?.status == CallState.left) super.onBackPressed()
    }

    /**
     * Initialize Manifest Permissions:
     * & Camera Request @params
     * */
    private var camera = Manifest.permission.CAMERA
    private var recordAudio = Manifest.permission.RECORD_AUDIO
    private var modifyAudioSettings = Manifest.permission.MODIFY_AUDIO_SETTINGS

    private fun executeWithPermission() {
        if (checkPermission()) {
            initializeSDK()
        } else {
            requestPermission()
        }
    }

    /*
     * Update Profile Picture
     * */
    private fun checkPermission(): Boolean {
        val camera = ContextCompat.checkSelfPermission(this, camera)
        val recordAudio = ContextCompat.checkSelfPermission(this, recordAudio)
        val modifyAudioSettings = ContextCompat.checkSelfPermission(this, modifyAudioSettings)
        return camera == PackageManager.PERMISSION_GRANTED && recordAudio == PackageManager.PERMISSION_GRANTED && modifyAudioSettings == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(camera, recordAudio, modifyAudioSettings), 369
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            369 ->
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    initializeSDK()
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Log.e("TAG", "**Permission Denied**")
                    showSettingsDialog()
                }
        }
    }

    override fun onResume() {
        super.onResume()

        /** to handle initialization of sdk after coming back from settings */
        if (checkPermission()) {
            initializeSDK()
        }
    }

    override fun onStateChanged(newState: DemoState) {
        Log.i(TAG, "onCallStateChanged: $newState")
        demoState = newState

        when (newState.status) {
            CallState.left -> {
                resetAppState()
            }
            CallState.joined -> {
                onJoinedMeeting()
            }
            else -> {}
        }
    }

    override fun onError(msg: String) {
        Log.e(TAG, "Got error: $msg")
        showMessage(msg)
    }
}