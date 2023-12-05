package com.dailysdkdemo.ui.adapters

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import co.daily.model.MediaState
import co.daily.model.Participant
import co.daily.view.VideoView
import com.dailysdkdemo.data.utils.TAG
import com.dailysdkdemo.ui.adapters.diffutilsclasses.MainAdapterDiffUtil
import com.dailysdkdemo.ui.callbacks.ClickCallBack
import com.dailysdkdemo.R
import com.dailysdkdemo.databinding.AdapterMainBinding
import kotlinx.coroutines.*
import java.net.URLDecoder

class MainAdapter(
    private val mActivity: Activity,
    private var participantList: ArrayList<Participant>,
    private val height: Int,
    private val clickCallBack: ClickCallBack
) : RecyclerView.Adapter<MainAdapter.VideoHolder>() {

    private lateinit var itemBinding: AdapterMainBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoHolder {
        itemBinding = AdapterMainBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: VideoHolder, position: Int) {
        val participant: Participant = participantList[position]
        height.let {
            holder.bind(
                mActivity,
                participant,
                participantList.size,
                it,
                clickCallBack,
                position
            )
        }
    }

    override fun onBindViewHolder(holder: VideoHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {

            val noOfParticipant = participantList.size
            val participant = participantList[position]

            holder.updateAdapterData(
                mActivity,
                participant,
                holder.itemBinding,
                noOfParticipant,
                position
            )

        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
        MainScope().launch {
            withContext(Dispatchers.Default) {
                delay(500)
            }
            if (mActivity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Set the height of your item view to wrap its content
                val width1 = itemBinding.remoteCardView.width

                withContext(Dispatchers.Main) {
                    holder.itemView.layoutParams.width = width1
                }
            } else {
                // Set the height of your item view to wrap its content
                val height1 = itemBinding.remoteCardView.height

                withContext(Dispatchers.Main) {
                    holder.itemView.layoutParams.height = height1
                }
            }
        }
    }

    override fun getItemCount(): Int = participantList.size

    class VideoHolder(val itemBinding: AdapterMainBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {
        fun bind(
            mActivity: Activity,
            participant: Participant,
            size: Int,
            height: Int,
            clickCallBack: ClickCallBack,
            position: Int
        ) {
            var noOfParticipant = size
            if (noOfParticipant > 3) noOfParticipant = 3

            itemBinding.mainLayout.doOnLayout {
                Log.d(TAG, "onParticipantUpdatedInInsideAdapter: $participant")
                val param = it.layoutParams as ViewGroup.MarginLayoutParams
                if (mActivity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    param.setMargins(5, 5, 5, 5)
                } else {
                    param.setMargins(10, 5, 10, 5)
                }
                it.layoutParams = param
                val mHeight = if (noOfParticipant == 2) {
                    (height / 3) - 12
                } else {
                    (height / noOfParticipant) - 12
                }
                if (mActivity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    it.layoutParams.width = mHeight
                } else {
                    it.layoutParams.height = mHeight
                }

                updateAdapterData(mActivity, participant, itemBinding, noOfParticipant, position)
            }

            itemBinding.root.setOnClickListener {
                clickCallBack.onClick()
            }
        }

        fun updateAdapterData(
            mActivity: Activity,
            participant: Participant,
            itemBinding: AdapterMainBinding,
            noOfParticipant: Int,
            position: Int
        ) {
            Handler(Looper.getMainLooper()).post {
                /** update mic status and name for remote user **/
                if (participant.info.userName?.isNotEmpty() == true) {
                    val remoteUserName = participant.info.userName ?: "Guest"
                    itemBinding.userDetailsTV.text = decodeText(remoteUserName)
                    itemBinding.remoteCameraMaskView.text = decodeText(participant.info.userName)
                }

                when (participant.media?.camera?.state) {
                    MediaState.playable -> {
                        Handler(Looper.getMainLooper()).postDelayed({

                            itemBinding.remoteVideoView.track = participant.media?.camera?.track
                            itemBinding.remoteVideoView.videoScaleMode =
                                VideoView.VideoScaleMode.FIT

                            itemBinding.userDetailsTV.compoundDrawablePadding =
                                mActivity.resources.getDimensionPixelSize(R.dimen.dimen_7dp)
                            val remoteUserName = participant.info.userName ?: "Guest"
                            itemBinding.userDetailsTV.text = decodeText(remoteUserName)
                            itemBinding.remoteVideoView.visibility = View.VISIBLE
                            itemBinding.remoteCameraMaskView.visibility = View.GONE

                            val params = if (noOfParticipant > 1) {
                                if (mActivity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                                    RelativeLayout.LayoutParams(
                                        RelativeLayout.LayoutParams.MATCH_PARENT,
                                        RelativeLayout.LayoutParams.WRAP_CONTENT
                                    )
                                } else {
                                    RelativeLayout.LayoutParams(
                                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                                        RelativeLayout.LayoutParams.WRAP_CONTENT
                                    )
                                }
                            } else {
                                RelativeLayout.LayoutParams(
                                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                                    RelativeLayout.LayoutParams.WRAP_CONTENT
                                )
                            }
                            itemBinding.remoteCardView.layoutParams = params

                            val height: Int = mActivity.resources.displayMetrics.heightPixels
                            Log.d(TAG, "heightPixels $height")
                            if (height < 1920) {
                                if (noOfParticipant > 1) {
                                    itemBinding.remoteCardView.layoutParams.width = 926
                                }
                            }

                            itemBinding.mainLayout.gravity = Gravity.CENTER
                            if (mActivity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                                if (noOfParticipant == 3) {
                                    itemBinding.mainLayout.gravity = when (position) {
                                        0 -> {
                                            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                                        }

                                        2 -> {
                                            Gravity.TOP or Gravity.CENTER_HORIZONTAL
                                        }

                                        else -> {
                                            Gravity.CENTER
                                        }
                                    }
                                }
                            }

                        }, 300)
                    }

                    else -> {
                        itemBinding.userDetailsTV.compoundDrawablePadding = 0
                        itemBinding.userDetailsTV.text = ""
                        itemBinding.remoteVideoView.visibility = View.INVISIBLE
                        itemBinding.remoteCameraMaskView.visibility = View.VISIBLE

                        val params = RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.MATCH_PARENT,
                            RelativeLayout.LayoutParams.MATCH_PARENT
                        )
                        itemBinding.remoteCardView.layoutParams = params

                        val height: Int = mActivity.resources.displayMetrics.heightPixels
                        if (height < 1920) {
                            if (noOfParticipant > 1) {
                                itemBinding.remoteCardView.layoutParams.width = 926
                            }
                        }
                    }
                }

                when (participant.media?.microphone?.state) {
                    MediaState.playable -> {
                        itemBinding.userDetailsTV.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_microphone_white, 0, 0, 0
                        )
                    }

                    else -> {
                        itemBinding.userDetailsTV.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_microphone_off, 0, 0, 0
                        )
                    }
                }

                /** update text message for remote user **/
                val remoteUserName = participant.info.userName ?: "Guest"
                itemBinding.remoteCameraMaskView.text = decodeText(remoteUserName)
            }
        }

        /** for converting html to text **/
        private fun decodeText(html: String?): CharSequence {
            var text = ""
            if (html != null) {
                text = URLDecoder.decode(html, "UTF-8")
            }
            return text
        }
    }

    override fun getItemId(position: Int): Long {
        return super.getItemId(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(newItems: ArrayList<Participant>) {
        val callback = MainAdapterDiffUtil(participantList, newItems)
        val diff = DiffUtil.calculateDiff(callback)
        if (newItems.size != participantList.size) {
            notifyDataSetChanged()
        }
        participantList.clear()
        participantList.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }
}