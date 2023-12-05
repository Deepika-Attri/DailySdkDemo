package com.dailysdkdemo.data.utils

interface DemoStateListener {
    fun onStateChanged(newState: DemoState)
    fun onError(msg: String)
}
