package com.dailysdkdemo.ui.activities

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import co.daily.model.MediaState
import co.daily.model.ParticipantVideoInfo
import com.dailysdkdemo.data.preferences.Preferences
import com.dailysdkdemo.data.preferences.Preferences.Companion.LAST_URL
import com.dailysdkdemo.data.preferences.Preferences.Companion.NAME
import com.dailysdkdemo.R
import com.dailysdkdemo.data.utils.APP_PACKAGE

open class AppBaseActivity : AppCompatActivity() {

    /** to check internet connection **/
    fun isNetworkAvailable(context: Context?): Boolean {
        if (context == null) return false
        val connectivityManager =
            context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnected
    }

    /** to show toast **/
    fun showMessage(message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()
    }

    /** to show settings dialog to open App Setting's Page to Change Permission **/
    fun showSettingsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.permission_required))
        builder.setMessage(getString(R.string.we_need_permission))
        builder.setPositiveButton(R.string.ok) { dialog, _ ->
            openSettings()
            dialog.cancel()
        }
        builder.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    /** open App Setting's Page to Change Permission **/
    fun openSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val uri = Uri.fromParts("package", APP_PACKAGE, null)
        intent.data = uri
        startActivity(intent)
    }

    fun modifyJoinButton(state: Boolean, button: Button) {
        button.isEnabled = state
        if (state) {
            ViewCompat.setBackgroundTintList(
                button, ContextCompat.getColorStateList(
                    applicationContext, R.color.sea_green
                )
            )
            button.setTextColor(
                ContextCompat.getColorStateList(
                    applicationContext, R.color.eerie_black
                )
            )
        } else {
            ViewCompat.setBackgroundTintList(
                button, ContextCompat.getColorStateList(
                    applicationContext, R.color.text_hint_color
                )
            )
            button.setTextColor(
                ContextCompat.getColorStateList(
                    applicationContext, R.color.eerie_black
                )
            )
        }
    }

    /** Get URL **/
    open fun getLastURL(): String? {
        return Preferences.readString(this@AppBaseActivity, LAST_URL, "")
    }

    /** Get URL **/
    open fun getLastName(): String? {
        return Preferences.readString(this@AppBaseActivity, NAME, "")
    }

    fun isMediaAvailable(info: ParticipantVideoInfo?): Boolean {
        return when (info?.state) {
            MediaState.blocked, MediaState.off, MediaState.interrupted -> false
            MediaState.receivable, MediaState.loading, MediaState.playable -> true
            null -> false
        }
    }

    /** fade in animation **/
    open fun fadeIn(view: View) {
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.interpolator = DecelerateInterpolator() //add this
        fadeIn.duration = 300

        val animation = AnimationSet(false) //change to false
        animation.addAnimation(fadeIn)
        view.animation = animation
    }

    /** fade out animation **/
    open fun fadeOut(view: View) {
        val fadeOut = AlphaAnimation(1f, 0f)
        fadeOut.interpolator = AccelerateInterpolator() //and this
        fadeOut.startOffset = 300
        fadeOut.duration = 300

        val animation = AnimationSet(false) //change to false
        animation.addAnimation(fadeOut)
        view.animation = animation
    }
}