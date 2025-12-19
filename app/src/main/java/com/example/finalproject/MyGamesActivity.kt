package com.example.finalproject

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finalproject.data.UserSession
import com.example.finalproject.databinding.ActivityMyGamesBinding
import com.example.finalproject.models.Game
import com.example.finalproject.navigation.BottomNavDestination
import com.example.finalproject.navigation.BottomNavHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MyGamesActivity : AppCompatActivity(), MyGamesAdapter.StatusListener {

    private lateinit var binding: ActivityMyGamesBinding
    private val adapter = MyGamesAdapter(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyGamesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.myGamesRecycler.layoutManager = LinearLayoutManager(this)
        binding.myGamesRecycler.adapter = adapter

        BottomNavHelper.bind(this, binding.bottomNavContainer, BottomNavDestination.MY_GAMES)

        loadFavorites()
    }

    private fun loadFavorites() {
        val userId = UserSession.getUserId(this)
        if (userId.isNullOrBlank()) {
            showError(getString(R.string.missing_user))
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val gamesDeferred = async { fetchGamesMap() }
            val ratingsDeferred = async { fetchUserRatings(userId) }
            val favoritesResult = runCatching { fetchFavorites(userId, gamesDeferred.await()) }
            setLoading(false)
            favoritesResult.onSuccess { items ->
                val ratings = ratingsDeferred.await()
                val merged = items.map { it.copy(userRating = ratings[it.game.id]) }
                adapter.submitList(merged)
                binding.emptyState.visibility = if (merged.isEmpty()) View.VISIBLE else View.GONE
            }.onFailure { showError(it.message ?: getString(R.string.home_error_generic)) }
        }
    }

    private suspend fun fetchGamesMap(): Map<String, Game> = withContext(Dispatchers.IO) {
        val url = URL("$BASE_API_URL/games")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.let { readStream(it) }.orEmpty()
            if (code !in 200..299) throw IOException(response.ifBlank { "Error loading games" })

            val array = JSONArray(response)
            val map = mutableMapOf<String, Game>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("game_id").ifBlank { obj.optString("id") }
                if (id.isBlank()) continue
                val title = obj.optString("title")
                    .ifBlank { obj.optString("name") }
                    .ifBlank { "Untitled" }
                val imageUrl = obj.optString("image").takeIf { it.isNotBlank() }?.let { buildImageUrl(it) }
                val tags = mutableListOf<String>()
                obj.optJSONArray("tags")?.let { tagsArray ->
                    for (t in 0 until tagsArray.length()) tags.add(tagsArray.optString(t))
                }
                val rating = obj.optDouble("rating", Double.NaN).takeIf { !it.isNaN() }
                val game = Game(
                    id = id,
                    title = title,
                    imageUrl = imageUrl,
                    description = obj.optString("description"),
                    developer = obj.optString("developer"),
                    publisher = obj.optString("publisher"),
                    tags = tags,
                    rating = rating
                )
                map[id] = game
            }
            map
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun fetchFavorites(userId: String, gamesMap: Map<String, Game>): List<MyGameItem> = withContext(Dispatchers.IO) {
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
            val response = stream?.let { readStream(it) }.orEmpty()
            if (code !in 200..299) throw IOException(response.ifBlank { "Error loading favorites" })

            val array = JSONArray(response)
            val list = mutableListOf<MyGameItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val gameObj = obj.optJSONObject("game") ?: obj
                val gameId = gameObj.optString("game_id")
                    .ifBlank { gameObj.optString("id") }
                if (gameId.isBlank()) continue

                val baseGame = gamesMap[gameId]
                val title = gameObj.optString("title")
                    .ifBlank { gameObj.optString("name") }
                    .ifBlank { baseGame?.title.orEmpty() }
                    .ifBlank { "Untitled" }
                val imageUrl = gameObj.optString("image")
                    .takeIf { it.isNotBlank() }
                    ?.let { buildImageUrl(it) }
                    ?: baseGame?.imageUrl

                val tags = mutableListOf<String>().apply {
                    gameObj.optJSONArray("tags")?.let { tagsArray ->
                        for (t in 0 until tagsArray.length()) add(tagsArray.optString(t))
                    }
                    if (isEmpty()) addAll(baseGame?.tags.orEmpty())
                }
                val rating = gameObj.optDouble("rating", Double.NaN).takeIf { !it.isNaN() } ?: baseGame?.rating
                val progress = obj.optString("progress").ifBlank { "want" }

                val game = Game(
                    id = gameId,
                    title = title,
                    imageUrl = imageUrl,
                    description = gameObj.optString("description").ifBlank { baseGame?.description },
                    developer = gameObj.optString("developer").ifBlank { baseGame?.developer },
                    publisher = gameObj.optString("publisher").ifBlank { baseGame?.publisher },
                    tags = tags,
                    rating = rating
                )
                list.add(MyGameItem(game, progress))
            }
            list
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun fetchUserRatings(userId: String): Map<String, Double?> = withContext(Dispatchers.IO) {
        val url = URL(REVIEWS_URL)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.let { readStream(it) }.orEmpty()
            if (code !in 200..299) throw IOException(response.ifBlank { "Error loading reviews" })

            val array = JSONArray(response)
            val map = mutableMapOf<String, Double?>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val uid = obj.optString("user_id").ifBlank { obj.optString("userId") }
                if (uid != userId) continue
                val gameId = obj.optString("game_id").ifBlank { obj.optString("gameId") }
                if (gameId.isBlank()) continue
                val rating = obj.optDouble("rating", Double.NaN).takeIf { !it.isNaN() }
                map[gameId] = rating
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

    private fun readStream(stream: java.io.InputStream): String {
        val builder = StringBuilder()
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                builder.append(line)
            }
        }
        return builder.toString()
    }

    private fun setLoading(loading: Boolean) {
        binding.myGamesProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.myGamesRecycler.visibility = if (loading) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        binding.emptyState.visibility = View.VISIBLE
    }

    override fun onStatusSelected(item: MyGameItem, status: String) {
        val userId = UserSession.getUserId(this) ?: return
        lifecycleScope.launch {
            val result = runCatching { updateFavorite(userId, item.game.id, status) }
            result.onSuccess {
                adapter.updateStatus(item.game.id, status)
            }.onFailure {
                Toast.makeText(this@MyGamesActivity, getString(R.string.bookmark_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun updateFavorite(userId: String, gameId: String, progress: String) = withContext(Dispatchers.IO) {
        val patchUrl = URL("$FAVORITES_URL/$userId/$gameId")
        val patchPayload = JSONObject().apply {
            put("progress", progress)
        }.toString()
        val patchCode = sendJson(patchUrl, "PATCH", patchPayload, treat409AsSuccess = false)
        if (patchCode == 404) {
            val createPayload = JSONObject().apply {
                put("user_id", userId)
                put("game_id", gameId)
                put("progress", progress)
                put("liked", false)
                put("added_at", formatNowUtc())
            }.toString()
            val createCode = sendJson(URL(FAVORITES_URL), "POST", createPayload, treat409AsSuccess = true)
            if (createCode !in 200..299 && createCode != 409) {
                throw IOException("Update failed ($createCode)")
            }
        } else if (patchCode !in 200..299) {
            throw IOException("Update failed ($patchCode)")
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

    private fun formatNowUtc(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    companion object {
        private const val BASE_API_URL = "http://10.0.2.2:3000"
        private const val FAVORITES_URL = "$BASE_API_URL/favorites"
        private const val REVIEWS_URL = "$BASE_API_URL/reviews"
    }
}
