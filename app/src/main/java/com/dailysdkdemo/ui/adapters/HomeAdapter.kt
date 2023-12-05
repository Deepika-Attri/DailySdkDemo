package com.dailysdkdemo.ui.adapters

import android.annotation.SuppressLint
import android.app.Activity
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dailysdkdemo.data.modelclasses.AllParticipants
import com.dailysdkdemo.data.preferences.Preferences
import com.dailysdkdemo.ui.callbacks.ClickWithPositionCallBack
import com.dailysdkdemo.R
import com.dailysdkdemo.databinding.AdapterHomeBinding
import java.net.URLDecoder

class HomeAdapter(
    private val mActivity: Activity,
    private var list: List<AllParticipants>,
    private var currentPosition: Int,
    private val clickCallback: ClickWithPositionCallBack
) : RecyclerView.Adapter<HomeAdapter.HomeHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeHolder {
        val itemBinding =
            AdapterHomeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HomeHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: HomeHolder, position: Int) {
        val participant: AllParticipants = list[position]
        holder.bind(mActivity, participant, position, clickCallback)
    }

    override fun getItemCount(): Int = list.size

    fun setData(newParticipant: List<AllParticipants>) {
        list = newParticipant
    }

    class HomeHolder(private val itemBinding: AdapterHomeBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(
            mActivity: Activity,
            participant: AllParticipants,
            position: Int,
            clickCallback: ClickWithPositionCallBack
        ) {

            val namesList: ArrayList<String> = ArrayList()
            for ((index, _) in participant.participant.toList().withIndex()) {
                val remoteUserName = participant.participant[index].info.userName ?: "Guest"
                namesList.add(remoteUserName)
                val s: String = TextUtils.join(", ", namesList)
                itemBinding.participantsTV.text = decodeText(s)
            }

            if (position == 0) {
                itemBinding.pageTV.text = mActivity.resources.getText(R.string.home)
                val others = itemBinding.participantsTV.text
                val self = Preferences.readString(mActivity, Preferences.NAME, "")
                itemBinding.participantsTV.text = "$self(You), $others"
                itemBinding.homeIV.visibility = View.VISIBLE
            } else {
                val page = mActivity.resources.getText(R.string.page)
                val pageNo = position + 1
                itemBinding.pageTV.text = "$page $pageNo"
                itemBinding.homeIV.visibility = View.GONE
            }

            itemBinding.root.setOnClickListener {
                clickCallback.onClick(position)
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
}