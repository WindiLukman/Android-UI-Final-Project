package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.finalproject.data.UserSession
import com.example.finalproject.databinding.ActivityProfileBinding
import com.example.finalproject.navigation.BottomNavDestination
import com.example.finalproject.navigation.BottomNavHelper

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindUser()
        BottomNavHelper.bind(this, binding.bottomNavContainer, BottomNavDestination.PROFILE)

        binding.profileReviewedGames.setOnClickListener {
            // TODO: wire to reviewed games screen
        }

        binding.profileLogout.setOnClickListener {
            UserSession.clear(this)
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
            finish()
        }
    }

    private fun bindUser() {
        val username = UserSession.getUsername(this) ?: getString(R.string.profile_username_placeholder)
        binding.profileUsername.text = username
    }
}
