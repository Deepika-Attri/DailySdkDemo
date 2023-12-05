package com.dailysdkdemo.ui.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.adapter.FragmentViewHolder
import com.dailysdkdemo.ui.fragments.PagerFragment
import com.dailysdkdemo.data.modelclasses.AllParticipants
import com.dailysdkdemo.ui.callbacks.ClickCallBack
import com.dailysdkdemo.ui.adapters.diffutilsclasses.PagerDiffUtil

class PagerAdapter(
    private val activity: FragmentActivity, private val clickCallBack: ClickCallBack
) : FragmentStateAdapter(activity) {

    private val items: ArrayList<AllParticipants> = arrayListOf()

    override fun createFragment(position: Int): Fragment =
        PagerFragment.newInstance(items, position, clickCallBack)

    override fun getItemCount() = items.size

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return super.containsItem(itemId)
    }

    override fun onBindViewHolder(
        holder: FragmentViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty()) {
            val tag = "f" + holder.itemId
            val fragment = activity.supportFragmentManager.findFragmentByTag(tag)
            // safe check ,but fragment should not be null
            if (fragment != null) {
                (fragment as PagerFragment).setValue(items[position].participant)
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    fun setItems(newItems: ArrayList<AllParticipants>) {
        val callback = PagerDiffUtil(items, newItems)
        val diff = DiffUtil.calculateDiff(callback)
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }
}