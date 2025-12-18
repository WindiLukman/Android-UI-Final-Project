package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.example.finalproject.databinding.ActivityGameDetailBinding
import com.example.finalproject.models.Game
import com.google.android.material.chip.Chip
import java.util.Locale
import kotlin.math.floor

class GameDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val game = intent.getParcelableExtra(EXTRA_GAME, Game::class.java) ?: legacyParcelable()
        if (game == null) {
            finish()
            return
        }

        binding.navHomeItem.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            finish()
        }

        bindGame(game)
    }

    private fun bindGame(game: Game) {
        binding.gameTitle.text = game.title

        Glide.with(this)
            .load(game.imageUrl)
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.placeholder)
            .into(binding.gameImage)

        binding.developerValue.text = game.developer ?: getString(R.string.not_available)
        binding.publisherValue.text = game.publisher ?: getString(R.string.not_available)

        binding.description.text = game.description?.takeIf { it.isNotBlank() }
            ?: getString(R.string.not_available)

        renderTags(game.tags)
        renderRating(game.rating ?: 4.5)
    }

    private fun renderTags(tags: List<String>) {
        binding.tagGroup.removeAllViews()
        if (tags.isEmpty()) {
            binding.tagGroup.isVisible = false
            return
        }
        binding.tagGroup.isVisible = true
        tags.forEach { tag ->
            val chip = Chip(this).apply {
                text = tag
                isCheckable = false
                setTextColor(getColor(R.color.home_text_primary))
                setChipBackgroundColorResource(R.color.home_card)
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(50f)
                    .build()
            }
            binding.tagGroup.addView(chip)
        }
    }

    private fun renderRating(rating: Double) {
        val stars = listOf(binding.star1, binding.star2, binding.star3, binding.star4, binding.star5)
        val filledCount = floor(rating).toInt().coerceIn(0, 5)
        val hasHalf = (rating - filledCount) >= 0.5 && filledCount < 5

        stars.forEachIndexed { index, imageView ->
            when {
                index < filledCount -> imageView.setImageResource(R.drawable.ic_star_filled)
                index == filledCount && hasHalf -> imageView.setImageResource(R.drawable.ic_star_border)
                else -> imageView.setImageResource(R.drawable.ic_star_border)
            }
        }

        binding.ratingValue.text = String.format(Locale.US, "%.1f", rating)
        binding.ratingLabel.text = getString(R.string.rating_peak_label)
    }

    companion object {
        const val EXTRA_GAME = "extra_game"
    }

    @Suppress("DEPRECATION")
    private fun legacyParcelable(): Game? = intent.getParcelableExtra(EXTRA_GAME)
}
