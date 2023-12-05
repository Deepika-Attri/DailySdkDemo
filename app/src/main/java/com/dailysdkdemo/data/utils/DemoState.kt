package com.dailysdkdemo.data.utils

import co.daily.model.CallState

// As an optimization for larger calls, it would be possible to modify this to
// represent state updates rather than entire state snapshots. The MainActivity
// could then respond to only the parts of the state which have changed.

data class DemoState(
    val status: CallState,
    val inputs: StreamsState
) {
    data class StreamsState(
        val cameraEnabled: Boolean,
        val micEnabled: Boolean
    )

    fun with(
        newStatus: CallState = status,
        newInputs: StreamsState = inputs
    ) = DemoState(
        newStatus,
        newInputs
    )

    companion object {
        fun default(): DemoState = DemoState(
            status = CallState.initialized,
            inputs = StreamsState(cameraEnabled = true, micEnabled = true)
        )
    }
}
