package com.example.finalproject

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.finalproject.databinding.ActivityGameDetailBinding
import com.example.finalproject.models.Game
import com.example.finalproject.data.UserSession
import com.google.android.material.chip.Chip
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.example.finalproject.databinding.BottomSheetGameActionsBinding
import java.util.Locale
import kotlin.math.floor
import com.example.finalproject.navigation.BottomNavHelper
import com.example.finalproject.navigation.BottomNavDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class GameDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameDetailBinding
    private var isBookmarked = false
    private var isLiked = false
    private var activeReply: DiscussionItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val game = legacyParcelable()
        if (game == null) {
            finish()
            return
        }

        BottomNavHelper.bind(this, binding.bottomNavContainer, BottomNavDestination.NONE)

        bindGame(game)
        loadAudienceRating(game)
        loadAudienceReviews(game)
        loadDiscussions(game)
        loadBookmarkState(game)
        setupBookmark(game)

        binding.audienceReviews.setOnClickListener { showReviewsTab() }
        binding.gameDiscussions.setOnClickListener { showDiscussionsTab(game) }
        binding.discussionSend.setOnClickListener { submitDiscussion(game) }

        showReviewsTab()
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
        renderRating(null)
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

    private fun renderRating(summary: RatingSummary?) {
        val stars = listOf(binding.star1, binding.star2, binding.star3, binding.star4, binding.star5)
        val rating = summary?.average
        val count = summary?.count ?: 0
        binding.ratingCount.text = count.toString()

        if (rating == null) {
            stars.forEach { it.setImageResource(R.drawable.ic_star_border) }
            binding.ratingValue.text = getString(R.string.not_available)
            return
        }

        val filledCount = floor(rating).toInt().coerceIn(0, 5)
        val hasHalf = (rating - filledCount) >= 0.5 && filledCount < 5

        stars.forEachIndexed { index, imageView ->
            when {
                index < filledCount -> imageView.setImageResource(R.drawable.ic_star_filled)
                index == filledCount && hasHalf -> imageView.setImageResource(R.drawable.ic_star_half)
                else -> imageView.setImageResource(R.drawable.ic_star_border)
            }
        }

        binding.ratingValue.text = String.format(Locale.US, "%.1f", rating)
    }

    private fun loadAudienceRating(game: Game) {
        lifecycleScope.launch {
            val summary = runCatching { fetchRatingSummary(game.id) }.getOrNull()
            renderRating(summary)
        }
    }

    private fun loadAudienceReviews(game: Game) {
        lifecycleScope.launch {
            val reviews = runCatching { fetchReviews(game.id) }.getOrNull().orEmpty()
            val users = runCatching { fetchUsersMap() }.getOrNull().orEmpty()
            renderReviews(reviews, users)
        }
    }

    private fun loadDiscussions(game: Game) {
        lifecycleScope.launch {
            val discussions = runCatching { fetchDiscussions(game.id) }.getOrNull().orEmpty()
            val users = runCatching { fetchUsersMap() }.getOrNull().orEmpty()
            renderDiscussions(discussions, users)
        }
    }

    private fun loadBookmarkState(game: Game) {
        val userId = UserSession.getUserId(this) ?: return
        lifecycleScope.launch {
            val state = runCatching { fetchBookmark(userId, game.id) }.getOrNull()
            if (state != null) {
                isBookmarked = true
                isLiked = state.liked
            }
        }
    }

    private fun showReviewsTab() {
        binding.audienceReviewsSection.isVisible = true
        binding.discussionsSection.isVisible = false
        binding.audienceReviews.setTextColor(getColor(R.color.home_nav_active))
        binding.gameDiscussions.setTextColor(getColor(R.color.home_text_primary))
    }

    private fun showDiscussionsTab(game: Game) {
        binding.audienceReviewsSection.isVisible = false
        binding.discussionsSection.isVisible = true
        binding.gameDiscussions.setTextColor(getColor(R.color.home_nav_active))
        binding.audienceReviews.setTextColor(getColor(R.color.home_text_primary))
        loadDiscussions(game)
    }

    private suspend fun fetchRatingSummary(gameId: String): RatingSummary? = withContext(Dispatchers.IO) {
        val url = URL("$REVIEWS_URL/game/$gameId")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
        }

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw Exception("Review load failed ($code): $body")

            val array = JSONArray(body)
            var sum = 0.0
            var count = 0
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val value = obj.optDouble("rating", Double.NaN)
                if (!value.isNaN()) {
                    sum += value
                    count++
                }
            }
            RatingSummary(average = if (count == 0) null else sum / count, count = count)
        } finally {
            connection.disconnect()
        }
    }

    private fun setupBookmark(game: Game) {
        binding.bookmarkButton.setOnClickListener {
            showActionsSheet(game)
        }
    }

    private fun showActionsSheet(game: Game) {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetGameActionsBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.bottomSheetTitle.text = game.title
        updateActionIcons(sheetBinding)

        sheetBinding.bookmarkAction.setOnClickListener {
            if (isBookmarked) {
                removeBookmark(game, sheetBinding)
            } else {
                performBookmark(game, sheetBinding)
            }
        }

        sheetBinding.likeAction.setOnClickListener {
            if (!isBookmarked) return@setOnClickListener
            toggleLike(game, sheetBinding)
        }

        sheetBinding.starRow.setOnClickListener {
            openReview(game)
            dialog.dismiss()
        }

        sheetBinding.addReviewRow.setOnClickListener {
            openReview(game)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateActionIcons(sheetBinding: BottomSheetGameActionsBinding) {
        val likeTint = if (isLiked) R.color.home_nav_active else R.color.home_icon_tint
        val bookmarkTint = if (isBookmarked) R.color.home_nav_active else R.color.home_icon_tint
        sheetBinding.likeIcon.setColorFilter(getColor(likeTint))
        sheetBinding.bookmarkIcon.setColorFilter(getColor(bookmarkTint))
    }

    private fun performBookmark(
        game: Game,
        sheetBinding: BottomSheetGameActionsBinding
    ) {
        val userId = UserSession.getUserId(this)
        if (userId.isNullOrBlank()) {
            toast(getString(R.string.missing_user))
            return
        }

        lifecycleScope.launch {
            val result = runCatching {
                postFavorite(
                    userId = userId,
                    gameId = game.id,
                    liked = false
                )
            }
            result.onSuccess { created ->
                isBookmarked = true
                if (created) {
                    isLiked = false
                }
                updateActionIcons(sheetBinding)
                toast(getString(R.string.bookmark_success))
            }.onFailure {
                toast(getString(R.string.bookmark_error))
            }
        }
    }

    private fun removeBookmark(
        game: Game,
        sheetBinding: BottomSheetGameActionsBinding
    ) {
        val userId = UserSession.getUserId(this)
        if (userId.isNullOrBlank()) {
            toast(getString(R.string.missing_user))
            return
        }
        lifecycleScope.launch {
            val result = runCatching { deleteFavorite(userId, game.id) }
            result.onSuccess {
                isBookmarked = false
                isLiked = false
                updateActionIcons(sheetBinding)
            }.onFailure {
                toast(getString(R.string.bookmark_error))
            }
        }
    }

    private fun toggleLike(game: Game, sheetBinding: BottomSheetGameActionsBinding) {
        val userId = UserSession.getUserId(this)
        if (userId.isNullOrBlank()) {
            toast(getString(R.string.missing_user))
            return
        }
        val newLiked = !isLiked
        lifecycleScope.launch {
            val result = runCatching {
                updateFavorite(
                    userId = userId,
                    gameId = game.id,
                    liked = newLiked
                )
            }
            result.onSuccess {
                isLiked = newLiked
                updateActionIcons(sheetBinding)
            }.onFailure {
                toast(getString(R.string.like_update_error))
            }
        }
    }

    private fun openReview(game: Game) {
        val intent = android.content.Intent(this, ReviewActivity::class.java).apply {
            putExtra(EXTRA_GAME, game)
        }
        startActivity(intent)
    }

    private suspend fun postFavorite(
        userId: String,
        gameId: String,
        liked: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val url = URL(FAVORITES_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val payload = JSONObject().apply {
            put("user_id", userId)
            put("game_id", gameId)
            put("progress", "want")
            put("liked", liked)
            put("added_at", Instant.now().toString())
        }.toString()

        try {
            connection.outputStream.use { os ->
                BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8)).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }
            }
            when (val responseCode = connection.responseCode) {
                in 200..299 -> true
                409 -> false // already exists, treat as success
                else -> throw Exception("Favorite update failed ($responseCode)")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun updateFavorite(
        userId: String,
        gameId: String,
        liked: Boolean
    ) = withContext(Dispatchers.IO) {
        val url = URL("$FAVORITES_URL/$userId/$gameId")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val payload = JSONObject().apply {
            put("liked", liked)
        }.toString()

        try {
            connection.outputStream.use { os ->
                BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8)).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw Exception("Favorite update failed ($code)")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun deleteFavorite(
        userId: String,
        gameId: String
    ) = withContext(Dispatchers.IO) {
        val url = URL("$FAVORITES_URL/$userId/$gameId")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw Exception("Favorite delete failed ($code)")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun postDiscussion(
        userId: String,
        gameId: String,
        text: String,
        replyId: String?
    ) = withContext(Dispatchers.IO) {
        val url = URL(DISCUSSIONS_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        val payload = JSONObject().apply {
            put("user_id", userId)
            put("game_id", gameId)
            put("discussion_text", text)
            if (!replyId.isNullOrBlank()) {
                put("reply_id", replyId)
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
            if (code !in 200..299) throw Exception("Discussion submit failed ($code)")
        } finally {
            connection.disconnect()
        }
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun renderReviews(reviews: List<ReviewItem>, users: Map<String, UserInfo>) {
        val container = binding.reviewsContainer
        val emptyView = binding.reviewsEmpty
        container.removeAllViews()

        if (reviews.isEmpty()) {
            emptyView.isVisible = true
            return
        }
        emptyView.isVisible = false

        reviews.forEach { review ->
            val itemBinding = com.example.finalproject.databinding.ItemAudienceReviewBinding.inflate(layoutInflater, container, false)
            val user = users[review.userId]
            itemBinding.reviewUserName.text = user?.name ?: getString(R.string.unknown_user)
            itemBinding.reviewComment.text = review.text.takeUnless { it.isNullOrBlank() } ?: getString(R.string.not_available)
            itemBinding.reviewRating.rating = (review.rating ?: 0.0).toFloat()

            val picture = user?.picture
            if (!picture.isNullOrBlank()) {
                Glide.with(this)
                    .load(buildImageUrl(picture))
                    .placeholder(R.drawable.placeholder)
                    .error(R.drawable.placeholder)
                    .into(itemBinding.reviewUserAvatar)
            } else {
                itemBinding.reviewUserAvatar.setImageResource(R.drawable.placeholder)
            }

            container.addView(itemBinding.root)
        }
    }

    private fun renderDiscussions(items: List<DiscussionItem>, users: Map<String, UserInfo>) {
        val container = binding.discussionsContainer
        val emptyView = binding.discussionsEmpty
        container.removeAllViews()

        val byParent = items.groupBy { it.replyId }
        val topLevel = byParent[null].orEmpty()

        if (topLevel.isEmpty()) {
            emptyView.isVisible = true
            return
        }
        emptyView.isVisible = false

        fun addItem(item: DiscussionItem, depth: Int) {
            val itemBinding = com.example.finalproject.databinding.ItemDiscussionBinding.inflate(layoutInflater, container, false)
            val user = users[item.userId]
            itemBinding.discussionUserName.text = user?.name ?: getString(R.string.unknown_user)
            itemBinding.discussionText.text = item.text ?: getString(R.string.not_available)
            itemBinding.discussionReplyLabel.isVisible = item.replyId != null
            if (item.replyId != null) {
                val parent = items.find { it.id == item.replyId }
                val parentName = parent?.let { users[it.userId]?.name } ?: getString(R.string.unknown_user)
                itemBinding.discussionReplyLabel.text = getString(R.string.replying_to, parentName)
            }

            val params = itemBinding.root.layoutParams as? android.widget.LinearLayout.LayoutParams
            val indentPx = (16 * depth * resources.displayMetrics.density).toInt()
            params?.setMargins(indentPx, params.topMargin, params.rightMargin, params.bottomMargin)
            itemBinding.root.layoutParams = params

            val picture = user?.picture
            if (!picture.isNullOrBlank()) {
                Glide.with(this)
                    .load(buildImageUrl(picture))
                    .placeholder(R.drawable.placeholder)
                    .error(R.drawable.placeholder)
                    .into(itemBinding.discussionAvatar)
            } else {
                itemBinding.discussionAvatar.setImageResource(R.drawable.placeholder)
            }

            val children = byParent[item.id].orEmpty()
            itemBinding.discussionViewReplies.isVisible = children.isNotEmpty()
            if (children.isNotEmpty()) {
                itemBinding.discussionViewReplies.text = getString(R.string.discussion_view_replies_count, children.size)
            }

            itemBinding.discussionReply.setOnClickListener {
                activeReply = item
                binding.discussionReplying.text = getString(R.string.replying_to, user?.name ?: getString(R.string.unknown_user))
                binding.discussionReplying.isVisible = true
                binding.discussionInput.hint = getString(R.string.discussion_reply_hint)
                binding.discussionInput.requestFocus()
            }

            container.addView(itemBinding.root)
            children.forEach { child -> addItem(child, depth + 1) }
        }

        topLevel.forEach { addItem(it, 0) }
    }

    private fun submitDiscussion(game: Game) {
        val userId = UserSession.getUserId(this)
        if (userId.isNullOrBlank()) {
            toast(getString(R.string.missing_user))
            return
        }
        val text = binding.discussionInput.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            toast(getString(R.string.discussion_empty_error))
            return
        }

        val replyTo = activeReply?.id

        lifecycleScope.launch {
            val result = runCatching {
                postDiscussion(
                    userId = userId,
                    gameId = game.id,
                    text = text,
                    replyId = replyTo
                )
            }
            result.onSuccess {
                binding.discussionInput.setText("")
                activeReply = null
                binding.discussionReplying.isVisible = false
                binding.discussionInput.hint = getString(R.string.discussion_new_comment)
                loadDiscussions(game)
            }.onFailure {
                toast(getString(R.string.discussion_submit_error))
            }
        }
    }

    private suspend fun fetchReviews(gameId: String): List<ReviewItem> = withContext(Dispatchers.IO) {
        val url = URL("$REVIEWS_URL/game/$gameId")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw Exception("Review load failed ($code): $body")
            val array = JSONArray(body)
            val list = mutableListOf<ReviewItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val userId = obj.optString("user_id").ifBlank { obj.optString("userId") }
                val rating = obj.optDouble("rating", Double.NaN).takeIf { !it.isNaN() }
                val text = obj.optString("review").takeIf { it.isNotBlank() }
                if (userId.isNotBlank()) {
                    list.add(ReviewItem(userId = userId, rating = rating, text = text))
                }
            }
            list
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun fetchDiscussions(gameId: String): List<DiscussionItem> = withContext(Dispatchers.IO) {
        val url = URL("$DISCUSSIONS_URL/game/$gameId")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw Exception("Discussion load failed ($code): $body")
            val array = JSONArray(body)
            val list = mutableListOf<DiscussionItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("discussion_id").ifBlank { obj.optString("discussionId") }
                val userId = obj.optString("user_id").ifBlank { obj.optString("userId") }
                if (id.isBlank() || userId.isBlank()) continue
                val text = obj.optString("discussion_text").ifBlank { obj.optString("discussionText") }
                val rawReply = obj.optString("reply_id").ifBlank { obj.optString("replyId") }
                val replyId = rawReply.takeUnless { it.isNullOrBlank() || it.equals("null", ignoreCase = true) }
                list.add(DiscussionItem(id = id, userId = userId, text = text, replyId = replyId))
            }
            list
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun fetchBookmark(userId: String, gameId: String): BookmarkState? = withContext(Dispatchers.IO) {
        val url = URL("$FAVORITES_URL/user/$userId")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
        }

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw Exception("Favorite load failed ($code): $body")

            val array = JSONArray(body)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("game_id").ifBlank { obj.optString("gameId") }
                if (id == gameId) {
                    val liked = obj.optBoolean("liked", false)
                    return@withContext BookmarkState(liked = liked)
                }
            }
            null
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun fetchUsersMap(): Map<String, UserInfo> = withContext(Dispatchers.IO) {
        val url = URL(USERS_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw Exception("User load failed ($code): $body")
            val array = JSONArray(body)
            val map = mutableMapOf<String, UserInfo>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                if (id.isBlank()) continue
                val name = obj.optString("name").ifBlank { getString(R.string.unknown_user) }
                val picture = obj.optString("picture").takeIf { it.isNotBlank() }
                map[id] = UserInfo(name = name, picture = picture)
            }
            map
        } finally {
            connection.disconnect()
        }
    }

    private fun buildImageUrl(path: String): String {
        val trimmed = path.trim()
        if (trimmed.startsWith("http", ignoreCase = true)) return trimmed
        val normalized = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return "$BASE_API_URL$normalized"
    }

    companion object {
        const val EXTRA_GAME = "extra_game"
        private const val FAVORITES_URL = "http://10.0.2.2:3000/favorites"
        private const val REVIEWS_URL = "http://10.0.2.2:3000/reviews"
        private const val DISCUSSIONS_URL = "http://10.0.2.2:3000/discussions"
        private const val USERS_URL = "http://10.0.2.2:3000/users"
        private const val BASE_API_URL = "http://10.0.2.2:3000"
    }

    @Suppress("DEPRECATION")
    private fun legacyParcelable(): Game? = intent.getParcelableExtra(EXTRA_GAME)

    private data class RatingSummary(val average: Double?, val count: Int)
    private data class ReviewItem(val userId: String, val rating: Double?, val text: String?)
    private data class UserInfo(val name: String, val picture: String?)
    private data class DiscussionItem(
        val id: String,
        val userId: String,
        val text: String?,
        val replyId: String?
    )
    private data class BookmarkState(val liked: Boolean)
}
