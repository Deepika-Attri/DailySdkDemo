package com.dailysdkdemo.ui.adapters.diffutilsclasses

import androidx.recyclerview.widget.DiffUtil
import com.dailysdkdemo.data.modelclasses.AllParticipants

class PagerDiffUtil(
    private val oldList: ArrayList<AllParticipants>,
    private val newList: ArrayList<AllParticipants>
) : DiffUtil.Callback() {

    enum class PayloadKey {
        VALUE
    }

    override fun getOldListSize() = oldList.size

    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].participant == newList[newItemPosition].participant
    }

    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any {
        return listOf(PayloadKey.VALUE)
    }
}