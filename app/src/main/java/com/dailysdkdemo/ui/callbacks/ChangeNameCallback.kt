package com.dailysdkdemo.ui.callbacks

import co.daily.model.Participant

interface ChangeNameCallback {
    fun onChangeName(name: String)
    fun onUpdateMic(participant: Participant)
    fun onUpdateCam(participant: Participant)
}