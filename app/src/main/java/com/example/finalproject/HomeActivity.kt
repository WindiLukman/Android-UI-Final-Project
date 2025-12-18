package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finalproject.adapters.GenreAdapter
import com.example.finalproject.databinding.ActivityHomeBinding
import com.example.finalproject.models.Game
import com.example.finalproject.models.Genre
import com.example.finalproject.navigation.BottomNavDestination
import com.example.finalproject.navigation.BottomNavHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var genreAdapter: GenreAdapter
    private var allGenres: List<Genre> = emptyList()
    private var currentQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        genreAdapter = GenreAdapter(emptyList()) { game -> openGameDetail(game) }
        binding.genreRecycler.layoutManager = LinearLayoutManager(this)
        binding.genreRecycler.adapter = genreAdapter

        binding.searchInput.addTextChangedListener { text ->
            filterGenres(text?.toString().orEmpty())
        }

        BottomNavHelper.bind(this, binding.bottomNavContainer, BottomNavDestination.HOME)

        loadData()
    }

    private fun loadData() {
        setLoading(true)
        lifecycleScope.launch {
            val result = runCatching { fetchGenresFromApi() }
            setLoading(false)

            result.onSuccess { genres ->
                allGenres = genres
                filterGenres(currentQuery)
                if (genres.isEmpty()) {
                    showEmptyState(getString(R.string.home_empty_state))
                } else {
                    hideMessage()
                }
            }.onFailure { error ->
                showError(error.message ?: getString(R.string.home_error_generic))
            }
        }
    }

    private fun filterGenres(query: String) {
        currentQuery = query
        val filtered = if (query.isBlank()) {
            allGenres
        } else {
            allGenres.mapNotNull { genre ->
                val matchingGames = genre.games.filter { game ->
                    game.title.contains(query, ignoreCase = true) ||
                            genre.name.contains(query, ignoreCase = true)
                }
                if (matchingGames.isNotEmpty()) Genre(genre.name, matchingGames) else null
            }
        }

        genreAdapter.updateGenres(filtered)

        if (filtered.isEmpty()) {
            showEmptyState(if (query.isBlank()) getString(R.string.home_empty_state) else getString(R.string.home_empty_filter))
        } else {
            hideMessage()
        }
    }

    private suspend fun fetchGenresFromApi(): List<Genre> = withContext(Dispatchers.IO) {
        val gamesArray = fetchJsonArray(GAMES_URL)
        val tagsArray = fetchJsonArray(TAGS_URL)

        val gamesById = mutableMapOf<String, Game>()
        val gameTags = mutableMapOf<String, MutableList<String>>()
        for (i in 0 until gamesArray.length()) {
            val gameObject = gamesArray.optJSONObject(i) ?: continue
            val gameId = gameObject.optString("game_id").ifBlank { gameObject.optString("id") }
            if (gameId.isBlank()) continue

            val title = gameObject.optString("title")
                .ifBlank { gameObject.optString("name") }
                .ifBlank { "Untitled" }

            val imageUrl = buildImageUrl(gameObject.optString("image"))
            val description = gameObject.optString("description").ifBlank { null }
            val developer = gameObject.optString("developer").ifBlank { null }
            val publisher = gameObject.optString("publisher").ifBlank { null }
            val rating = gameObject.optDouble("rating", Double.NaN).takeIf { !it.isNaN() }

            gamesById[gameId] = Game(
                id = gameId,
                title = title,
                imageUrl = imageUrl,
                description = description,
                developer = developer,
                publisher = publisher,
                rating = rating
            )
        }

        val genreToGameIds = linkedMapOf<String, MutableList<String>>()
        val gamesUsed = mutableSetOf<String>()

        for (i in 0 until tagsArray.length()) {
            val tagObject = tagsArray.optJSONObject(i) ?: continue
            val tagName = tagObject.optString("tag").ifBlank { DEFAULT_GENRE }
            val gameId = tagObject.optString("game_id").ifBlank { tagObject.optString("id") }
            if (!gamesById.containsKey(gameId)) continue

            val tagsForGame = gameTags.getOrPut(gameId) { mutableListOf() }
            if (tagName.isNotBlank()) {
                tagsForGame.add(tagName)
            }

            genreToGameIds.getOrPut(tagName) { mutableListOf() }.add(gameId)
            gamesUsed.add(gameId)
        }

        val genreMap = linkedMapOf<String, MutableList<Game>>()
        genreToGameIds.forEach { (genreName, ids) ->
            val games = ids.mapNotNull { id ->
                val base = gamesById[id] ?: return@mapNotNull null
                base.copy(tags = gameTags[id].orEmpty())
            }
            if (games.isNotEmpty()) {
                genreMap[genreName] = games.toMutableList()
            }
        }

        // Optionally add games that have no tag into a default bucket
        gamesById.filterKeys { it !in gamesUsed }.values.takeIf { it.isNotEmpty() }?.let { leftovers ->
            leftovers.forEach { game ->
                val tagsForGame = gameTags[game.id].orEmpty()
                genreMap.getOrPut(DEFAULT_GENRE) { mutableListOf() }.add(game.copy(tags = tagsForGame))
            }
        }

        genreMap.entries
            .map { Genre(it.key, it.value) }
            .sortedBy { it.name.lowercase() }
    }

    private fun fetchJsonArray(urlString: String): JSONArray {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            doInput = true
        }

        return try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.let { readStream(it) }.orEmpty()

            if (responseCode !in 200..299) {
                throw IOException(response.ifBlank { getString(R.string.home_error_generic) })
            }

            val trimmed = response.trim()
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                JSONObject(trimmed).optJSONArray("data") ?: JSONArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildImageUrl(imagePath: String?): String? {
        if (imagePath.isNullOrBlank()) return null
        val trimmed = imagePath.trim()
        if (trimmed.startsWith("http", ignoreCase = true)) {
            return trimmed
        }
        val normalizedPath = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return "$BASE_API_URL$normalizedPath"
    }

    private fun readStream(stream: InputStream): String {
        val builder = StringBuilder()
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                builder.append(line)
            }
        }
        return builder.toString()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.homeProgress.isVisible = isLoading
        binding.genreRecycler.isVisible = !isLoading && allGenres.isNotEmpty()
        if (isLoading) {
            binding.homeError.visibility = View.GONE
        }
    }

    private fun showEmptyState(message: String) {
        binding.genreRecycler.visibility = View.GONE
        binding.homeError.visibility = View.VISIBLE
        binding.homeError.text = message
    }

    private fun showError(message: String) {
        showEmptyState(message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun hideMessage() {
        binding.homeError.visibility = View.GONE
        binding.genreRecycler.isVisible = genreAdapter.itemCount > 0
    }

    private fun openGameDetail(game: Game) {
        val intent = Intent(this, GameDetailActivity::class.java).apply {
            putExtra(GameDetailActivity.EXTRA_GAME, game)
        }
        startActivity(intent)
    }

    companion object {
        private const val BASE_API_URL = "http://10.0.2.2:3000"
        private const val GAMES_URL = "$BASE_API_URL/games"
        private const val TAGS_URL = "$BASE_API_URL/tags"
        private const val DEFAULT_GENRE = "Other"
    }
}
