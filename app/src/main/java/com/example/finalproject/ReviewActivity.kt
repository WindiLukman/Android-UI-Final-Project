package com.example.finalproject

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.finalproject.data.UserSession
import com.example.finalproject.databinding.ActivityReviewBinding
import com.example.finalproject.models.Game
import com.example.finalproject.navigation.BottomNavDestination
import com.example.finalproject.navigation.BottomNavHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class ReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewBinding
    private var currentRating = 0.0
    private var liked = false
    private var game: Game? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        game = intent.getParcelableExtra(GameDetailActivity.EXTRA_GAME) ?: intent.getParcelableExtra("game")
        if (game == null) {
            finish()
            return
        }

        bindGame(game!!)
        setupStars()
        setupActions()
        BottomNavHelper.bind(this, binding.bottomNavContainer, BottomNavDestination.NONE)
    }

    private fun bindGame(game: Game) {
        binding.reviewTitle.text = game.title
        binding.reviewDeveloper.text = game.developer ?: getString(R.string.not_available)
        Glide.with(this)
            .load(game.imageUrl)
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.placeholder)
            .into(binding.reviewCover)
    }

    private fun setupStars() {
        val stars = listOf(
            binding.reviewStar1,
            binding.reviewStar2,
            binding.reviewStar3,
            binding.reviewStar4,
            binding.reviewStar5
        )
        stars.forEachIndexed { index, imageView ->
            imageView.setOnClickListener {
                val target = (index + 1).toDouble()
                currentRating = if (currentRating == target) target - 0.5 else target
                if (currentRating < 0.5) currentRating = target
                renderStars(stars)
            }
            imageView.setOnLongClickListener {
                currentRating = index + 0.5
                renderStars(stars)
                true
            }
        }
    }

    private fun renderStars(stars: List<ImageView>) {
        stars.forEachIndexed { idx, iv ->
            val value = idx + 1
            when {
                currentRating >= value -> iv.setImageResource(R.drawable.ic_star_filled)
                currentRating >= value - 0.5 -> iv.setImageResource(R.drawable.ic_star_half)
                else -> iv.setImageResource(R.drawable.ic_star_border)
            }
        }
    }

    private fun setupActions() {
        binding.reviewBack.setOnClickListener { finish() }
        binding.reviewLike.setOnClickListener {
            liked = !liked
            binding.reviewLike.setColorFilter(getColor(if (liked) R.color.home_nav_active else R.color.home_icon_tint))
        }
        binding.reviewSubmit.setOnClickListener { submitReview() }
    }

    private fun submitReview() {
        val userId = UserSession.getUserId(this)
        val gameId = game?.id
        if (userId.isNullOrBlank() || gameId.isNullOrBlank()) {
            toast(getString(R.string.missing_user))
            return
        }
        val reviewText = binding.reviewInput.text?.toString().orEmpty()
        val ratingToSend = if (currentRating <= 0.0) null else currentRating

        lifecycleScope.launch {
            val result = runCatching {
                postReview(userId, gameId, reviewText, ratingToSend)
                upsertFavorite(userId, gameId, liked)
            }
            result.onSuccess {
                toast(getString(R.string.review_submit_success))
                finish()
            }.onFailure {
                toast(getString(R.string.review_submit_error))
            }
        }
    }

    private suspend fun postReview(userId: String, gameId: String, review: String, rating: Double?) =
        withContext(Dispatchers.IO) {
            val url = URL(REVIEWS_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }

            val payload = JSONObject().apply {
                // Backend expects camelCase keys
                put("gameId", gameId)
                put("userId", userId)
                put("review", review)
                if (rating != null) {
                    put("rating", rating)
                }
            }.toString()

            try {
                connection.outputStream.use { os ->
                    BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8)).use { writer ->
                        writer.write(payload)
                        writer.flush()
                    }
                }
                val code = connection.responseCode
                if (code !in 200..299) throw Exception("Review submit failed ($code)")
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun upsertFavorite(userId: String, gameId: String, liked: Boolean) = withContext(Dispatchers.IO) {
        // Try update first (handles existing favorites without duplicate key errors)
        val updateUrl = URL("$FAVORITES_URL/$userId/$gameId")
        val updatePayload = JSONObject().apply {
            put("liked", liked)
        }.toString()
        val updateCode = sendJson(updateUrl, "PATCH", updatePayload, treat409AsSuccess = false)
        if (updateCode == 404) {
            // Fall back to create if the favorite does not exist yet
            val createPayload = JSONObject().apply {
                put("user_id", userId)
                put("game_id", gameId)
                put("liked", liked)
            }.toString()
            val createCode = sendJson(URL(FAVORITES_URL), "POST", createPayload, treat409AsSuccess = true)
            if (createCode !in 200..299 && createCode != 409) {
                throw Exception("Favorite create failed ($createCode)")
            }
        } else if (updateCode !in 200..299) {
            throw Exception("Favorite update failed ($updateCode)")
        }
    }

    private fun sendJson(url: URL, method: String, payload: String, treat409AsSuccess: Boolean): Int {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            connection.outputStream.use { os ->
                BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8)).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }
            }
            val code = connection.responseCode
            if (code == 409 && treat409AsSuccess) 200 else code
        } finally {
            connection.disconnect()
        }
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REVIEWS_URL = "http://10.0.2.2:3000/reviews"
        private const val FAVORITES_URL = "http://10.0.2.2:3000/favorites"
    }
}
