package com.dailysdkdemo.ui.adapters

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import co.daily.model.MediaState
import co.daily.model.Participant
import com.dailysdkdemo.data.preferences.Preferences
import com.dailysdkdemo.ui.callbacks.ChangeNameCallback
import com.dailysdkdemo.R
import com.dailysdkdemo.databinding.AdapterPeopleBinding
import java.net.URLDecoder

class PeopleAdapter(
    private val mActivity: Activity,
    private var list: List<Participant>,
    private var localParticipant: Participant?,
    private val changeNameCallback: ChangeNameCallback
) : RecyclerView.Adapter<PeopleAdapter.PeopleHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PeopleHolder {
        val itemBinding =
            AdapterPeopleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PeopleHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: PeopleHolder, position: Int) {
        val participant: Participant = list[position]
        holder.bind(mActivity, participant, changeNameCallback, localParticipant)
    }

    override fun getItemCount(): Int = list.size

    fun setData(newParticipant: List<Participant>) {
        list = newParticipant
    }

    class PeopleHolder(private val itemBinding: AdapterPeopleBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(
            mActivity: Activity, participant: Participant,
            changeNameCallback: ChangeNameCallback,
            localParticipant: Participant?
        ) {
            if (participant.info.isLocal) {
                val username = Preferences.readString(mActivity, Preferences.NAME, "")
                itemBinding.participantName.text = "$username ${mActivity.getString(R.string.you)}"
            } else {
                val remoteUserName = participant.info.userName ?: "Guest"
                itemBinding.participantName.text = decodeText(remoteUserName)
            }

            when (participant.media?.microphone?.state) {
                MediaState.playable -> {
                    itemBinding.micIV.setImageResource(R.drawable.ic_microphone)
                }
                else -> {
                    itemBinding.micIV.setImageResource(R.drawable.ic_microphone_red)
                    DrawableCompat.setTint(
                        DrawableCompat.wrap(itemBinding.micIV.drawable),
                        ContextCompat.getColor(mActivity, R.color.red)
                    )
                }
            }

            if (participant.info.isLocal) {
                itemBinding.moreIV.visibility = View.VISIBLE
            } else {
                itemBinding.moreIV.visibility = View.GONE
            }

            if (localParticipant?.info?.isOwner == true) {
                itemBinding.moreIV.visibility = View.VISIBLE
            }

            itemBinding.moreIV.setOnClickListener {
                showMorePopup(
                    mActivity,
                    it,
                    participant.info.isLocal,
                    changeNameCallback,
                    participant
                )
            }
        }

        /* show info dialog to copy link */
        private fun showMorePopup(
            context: Context?,
            moreView: View,
            isLocal: Boolean,
            changeNameCallback: ChangeNameCallback,
            participant: Participant
        ) {

            val customView: View =
                LayoutInflater.from(context).inflate(R.layout.more_dialog, LinearLayout(context), false)
            val popupWindow = PopupWindow(
                customView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )

            popupWindow.contentView = customView

            popupWindow.setBackgroundDrawable(BitmapDrawable())
            popupWindow.isOutsideTouchable = true
            popupWindow.showAsDropDown(moreView, 0, 0, 0)

            val changeNameTV: TextView = customView.findViewById(R.id.changeNameTV)
            val participantMoreLL: LinearLayout = customView.findViewById(R.id.participantMoreLL)
            val muteMicrophoneTV: TextView = customView.findViewById(R.id.muteMicrophoneTV)
            val turnOffCameraTV: TextView = customView.findViewById(R.id.turnOffCameraTV)

            if (isLocal) {
                changeNameTV.visibility = View.VISIBLE
                participantMoreLL.visibility = View.GONE
            } else {
                changeNameTV.visibility = View.GONE
                participantMoreLL.visibility = View.VISIBLE
            }

            changeNameTV.setOnClickListener {
                popupWindow.dismiss()
                context?.let { it1 -> changeNameDialog(it1, changeNameCallback, participant.info.userName.toString()) }
            }

            muteMicrophoneTV.setOnClickListener {
                changeNameCallback.onUpdateMic(participant)
                popupWindow.dismiss()
            }

            turnOffCameraTV.setOnClickListener {
                changeNameCallback.onUpdateCam(participant)
                popupWindow.dismiss()
            }
        }

        private fun changeNameDialog(
            activity: Context, changeNameCallback: ChangeNameCallback, name: String
        ) {
            val alertDialog = Dialog(activity)
            alertDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            alertDialog.setContentView(R.layout.change_name_dialog)
            alertDialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            alertDialog.setCanceledOnTouchOutside(true)
            alertDialog.setCancelable(true)
            alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            // set the custom dialog components - text, image and button
            val nameET: EditText = alertDialog.findViewById(R.id.nameET)
            val continueBtn: Button = alertDialog.findViewById(R.id.continueBtn)
            val cancelBtn: Button = alertDialog.findViewById(R.id.cancelBtn)

            nameET.setText(name)

            nameET.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    continueBtn.isEnabled =
                        nameET.text.toString() != ""
                    modifyJoinButton(
                        activity, continueBtn.isEnabled, continueBtn
                    )
                }

                override fun afterTextChanged(s: Editable?) {

                }
            })

            continueBtn.setOnClickListener {
                alertDialog.dismiss()
                changeNameCallback.onChangeName(nameET.text.toString())
            }

            cancelBtn.setOnClickListener {
                alertDialog.dismiss()
            }

            alertDialog.show()
        }

        /** for converting html to text **/
        private fun decodeText(html: String?): CharSequence {
            var text = ""
            if (html != null) {
                text = URLDecoder.decode(html, "UTF-8")
            }
            return text
        }

        fun modifyJoinButton(context: Context, state: Boolean, button: Button) {
            button.isEnabled = state
            if (state) {
                ViewCompat.setBackgroundTintList(
                    button, ContextCompat.getColorStateList(
                        context, R.color.sea_green
                    )
                )
                button.setTextColor(
                    ContextCompat.getColorStateList(
                        context, R.color.eerie_black
                    )
                )
            } else {
                ViewCompat.setBackgroundTintList(
                    button, ContextCompat.getColorStateList(
                        context, R.color.text_hint_color
                    )
                )
                button.setTextColor(
                    ContextCompat.getColorStateList(
                        context, R.color.eerie_black
                    )
                )
            }
        }
    }
}