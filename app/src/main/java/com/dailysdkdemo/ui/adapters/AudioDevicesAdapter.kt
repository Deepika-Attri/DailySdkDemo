package com.dailysdkdemo.ui.adapters

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.daily.model.MediaDeviceInfo
import com.dailysdkdemo.data.utils.TAG
import com.dailysdkdemo.ui.callbacks.AudioDeviceIdCallback
import com.dailysdkdemo.R
import com.dailysdkdemo.databinding.AdapterAudioDevicesBinding

private var row_index = 0

class AudioDevicesAdapter(
    private val mActivity: Activity,
    private val list: List<MediaDeviceInfo>,
    private val audioDeviceIdCallback: AudioDeviceIdCallback
) : RecyclerView.Adapter<AudioDevicesAdapter.DeviceHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceHolder {
        val itemBinding =
            AdapterAudioDevicesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: DeviceHolder, position: Int) {
        holder.bind(list, position, audioDeviceIdCallback, this, mActivity)
    }

    override fun getItemCount(): Int = list.size

    class DeviceHolder(private val itemBinding: AdapterAudioDevicesBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {
        @SuppressLint("NotifyDataSetChanged")
        fun bind(
            list: List<MediaDeviceInfo>,
            position: Int,
            audioDeviceIdCallback: AudioDeviceIdCallback,
            audioDevicesAdapter: AudioDevicesAdapter,
            mActivity: Activity
        ) {
            val mediaDeviceInfo: MediaDeviceInfo = list[position]

            itemBinding.labelTV.text = mediaDeviceInfo.deviceId

            when {
                mediaDeviceInfo.label.contains("Speaker") -> {
                    itemBinding.labelTV.text = mActivity.resources.getString(R.string.speaker)
                }
                mediaDeviceInfo.label.contains("Bluetooth") -> {
                    itemBinding.labelTV.text = mActivity.resources.getString(R.string.bluetooth)
                }
                mediaDeviceInfo.label.contains("Wired") -> {
                    itemBinding.labelTV.text = mActivity.resources.getString(R.string.microphone)
                }
                else -> {
                    itemBinding.labelTV.text = mActivity.resources.getString(R.string.phone)
                }
            }

            if (position == (list.size - 1)) {
                itemBinding.dividerView.visibility = View.GONE
            }
            itemBinding.root.setOnClickListener {
                row_index = position
                audioDevicesAdapter.notifyDataSetChanged()
                audioDeviceIdCallback.onClick(mediaDeviceInfo.deviceId)
                Log.e(TAG, "deviceId: ${mediaDeviceInfo.deviceId}")
            }
            if (row_index == position) {
                itemBinding.checkIV.visibility = View.VISIBLE
            } else {
                itemBinding.checkIV.visibility = View.GONE
            }
        }
    }
}