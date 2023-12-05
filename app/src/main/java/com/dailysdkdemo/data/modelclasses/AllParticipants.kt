package com.dailysdkdemo.data.modelclasses

import co.daily.model.Participant
import java.io.Serializable

data class AllParticipants(
    val id: Int, val participant: ArrayList<Participant>
) : Serializable