package com.example.finalproject

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class FinalProjectApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}