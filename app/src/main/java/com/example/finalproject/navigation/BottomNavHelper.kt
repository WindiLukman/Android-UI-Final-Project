package com.example.finalproject.navigation

import android.app.Activity
import android.content.Intent
import com.example.finalproject.HomeActivity
import com.example.finalproject.ProfileActivity
import com.example.finalproject.SearchActivity
import com.example.finalproject.MyGamesActivity
import com.example.finalproject.databinding.LayoutBottomNavBinding

enum class BottomNavDestination {
    HOME, SEARCH, MY_GAMES, PROFILE, NONE
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

        binding.navMyGamesItem.setOnClickListener {
            if (active != BottomNavDestination.MY_GAMES) {
                activity.startActivity(
                    Intent(activity, MyGamesActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
        binding.navSearchItem.setOnClickListener {
            if (active != BottomNavDestination.SEARCH) {
                activity.startActivity(
                    Intent(activity, SearchActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                )
                activity.finish()
            }
        }
        binding.navSettingsItem.setOnClickListener { /* TODO: wire settings */ }
    }

    private fun setSelected(binding: LayoutBottomNavBinding, active: BottomNavDestination) {
        binding.navHomeItem.isSelected = active == BottomNavDestination.HOME
        binding.navSearchItem.isSelected = active == BottomNavDestination.SEARCH
        binding.navMyGamesItem.isSelected = active == BottomNavDestination.MY_GAMES
        binding.navProfileItem.isSelected = active == BottomNavDestination.PROFILE
        binding.navSettingsItem.isSelected = false
    }
}
