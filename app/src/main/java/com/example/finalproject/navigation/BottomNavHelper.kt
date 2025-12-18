package com.example.finalproject.navigation

import android.app.Activity
import android.content.Intent
import com.example.finalproject.HomeActivity
import com.example.finalproject.ProfileActivity
import com.example.finalproject.databinding.LayoutBottomNavBinding

enum class BottomNavDestination {
    HOME, PROFILE, NONE
}

object BottomNavHelper {
    fun bind(
        activity: Activity,
        binding: LayoutBottomNavBinding,
        active: BottomNavDestination
    ) {
        setSelected(binding, active)

        binding.navHomeItem.setOnClickListener {
            if (active != BottomNavDestination.HOME) {
                activity.startActivity(
                    Intent(activity, HomeActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                )
                activity.finish()
            }
        }

        binding.navProfileItem.setOnClickListener {
            if (active != BottomNavDestination.PROFILE) {
                activity.startActivity(
                    Intent(activity, ProfileActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                )
                activity.finish()
            }
        }

        // Stubs for other items
        binding.navSearchItem.setOnClickListener { /* TODO: wire search */ }
        binding.navMyGamesItem.setOnClickListener { /* TODO: wire my games */ }
        binding.navSettingsItem.setOnClickListener { /* TODO: wire settings */ }
    }

    private fun setSelected(binding: LayoutBottomNavBinding, active: BottomNavDestination) {
        binding.navHomeItem.isSelected = active == BottomNavDestination.HOME
        binding.navProfileItem.isSelected = active == BottomNavDestination.PROFILE
        binding.navSearchItem.isSelected = false
        binding.navMyGamesItem.isSelected = false
        binding.navSettingsItem.isSelected = false
    }
}
