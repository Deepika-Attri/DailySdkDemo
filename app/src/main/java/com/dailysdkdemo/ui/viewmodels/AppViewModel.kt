package com.dailysdkdemo.ui.viewmodels

import android.content.res.Configuration
import androidx.lifecycle.*
import co.daily.model.Participant

class AppViewModel : ViewModel() {

    private val _participantsList = MutableLiveData<MutableList<Participant>>()

    val participantsList: LiveData<MutableList<Participant>>
        get() = _participantsList

    fun setData(newData: MutableList<Participant>) {
        _participantsList.value = newData
    }

    private val _isLandscape = MutableLiveData(false)
    val isLandscape: LiveData<Boolean>
        get() = _isLandscape

    fun checkOrientation(newConfig: Configuration) {
        _isLandscape.value = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    enum class VideoTrackType {
        Camera,
        ScreenShare
    }
}