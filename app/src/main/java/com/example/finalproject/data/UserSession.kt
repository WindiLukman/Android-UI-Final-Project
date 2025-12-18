package com.example.finalproject.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.core.content.edit

object UserSession {
    private const val KEY_USERNAME = "session_username"

    private fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun saveUsername(context: Context, username: String) {
        prefs(context).edit { putString(KEY_USERNAME, username) }
    }

    fun getUsername(context: Context): String? =
        prefs(context).getString(KEY_USERNAME, null)

    fun clear(context: Context) {
        prefs(context).edit { remove(KEY_USERNAME) }
    }
}
