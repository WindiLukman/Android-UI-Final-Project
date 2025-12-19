package com.example.finalproject.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.core.content.edit

object UserSession {
    private const val KEY_USERNAME = "session_username"
    private const val KEY_USER_ID = "session_user_id"

    private fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun saveUsername(context: Context, username: String) {
        prefs(context).edit { putString(KEY_USERNAME, username) }
    }

    fun saveUserId(context: Context, userId: String) {
        prefs(context).edit { putString(KEY_USER_ID, userId) }
    }

    fun getUsername(context: Context): String? =
        prefs(context).getString(KEY_USERNAME, null)

    fun getUserId(context: Context): String? =
        prefs(context).getString(KEY_USER_ID, null)

    fun clear(context: Context) {
        prefs(context).edit {
            remove(KEY_USERNAME)
            remove(KEY_USER_ID)
        }
    }
}
