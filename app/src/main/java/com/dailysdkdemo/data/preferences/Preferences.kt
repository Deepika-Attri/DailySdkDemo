package com.dailysdkdemo.data.preferences

import android.content.Context
import android.content.SharedPreferences

open class Preferences {

    companion object {
        val PREF_NAME = "App_PREF"
        val MODE = Context.MODE_PRIVATE
        val LAST_URL = "pref_last_url"
        val NAME = "pref_last_name"

        /** Return the SharedPreference with @Prefreces Name & Prefreces = MODE **/
        private fun getPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREF_NAME, MODE)
        }

        /** Return the SharedPreferences Editor**/
        private fun getEditor(context: Context): SharedPreferences.Editor? {
            return getPreferences(context).edit()
        }

        /** Write the String Value**/
        fun writeString(context: Context?, key: String?, value: String?) {
            getEditor(context!!)!!.putString(key, value).apply()
        }

        /** Read the String Value**/
        fun readString(context: Context?, key: String?, defValue: String?): String? {
            return getPreferences(context!!).getString(key, defValue)
        }
    }
}
