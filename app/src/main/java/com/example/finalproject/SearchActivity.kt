package com.example.finalproject

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finalproject.adapters.SearchResultAdapter
import com.example.finalproject.databinding.ActivitySearchBinding
import com.example.finalproject.models.Game
import com.example.finalproject.navigation.BottomNavDestination
import com.example.finalproject.navigation.BottomNavHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val allGames = mutableListOf<Game>()
    private val allTags = mutableListOf<String>()
    private lateinit var adapter: SearchResultAdapter

    private var minRating = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SearchResultAdapter { openGameDetail(it) }
        binding.searchResults.layoutManager = LinearLayoutManager(this)
        binding.searchResults.adapter = adapter

        setupFilters()

        BottomNavHelper.bind(this, binding.bottomNavContainer, BottomNavDestination.SEARCH)

        loadData()
    }

    private fun setupFilters() {
        val watcher = { _: CharSequence? -> applyFilters() }

        binding.searchName.addTextChangedListener(afterTextChanged = watcher)
        binding.searchTags.addTextChangedListener(afterTextChanged = watcher)
        binding.searchDeveloper.addTextChangedListener(afterTextChanged = watcher)

        binding.ratingSeek.max = 50 // 0.1 increments up to 5.0
        binding.ratingSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                minRating = progress / 10.0
                binding.ratingValue.text = if (progress == 0) {
                    getString(R.string.search_rating_any)
                } else {
                    String.format(Locale.US, "%.1f+", minRating)
                }
                applyFilters()
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    private fun loadData() {
        setLoading(true)
        lifecycleScope.launch {
            val gamesResult = runCatching { fetchGames() }
            val tagsResult = runCatching { fetchTags() }
            setLoading(false)

            gamesResult.onSuccess { games ->
                allGames.clear()
                allGames.addAll(games)
                applyFilters()
            }.onFailure {
                showError(getString(R.string.home_error_generic))
            }

            tagsResult.onSuccess { tags ->
                allTags.clear()
                allTags.addAll(tags)
                val adapter = ArrayAdapter(this@SearchActivity, android.R.layout.simple_dropdown_item_1line, allTags)
                binding.searchTags.setAdapter(adapter)
            }
        }
    }

    private fun applyFilters() {
        val nameQuery = binding.searchName.text?.toString().orEmpty().trim()
        val tagQuery = binding.searchTags.text?.toString().orEmpty().trim()
        val devQuery = binding.searchDeveloper.text?.toString().orEmpty().trim()

        val filtered = allGames.filter { game ->
            val matchesName = nameQuery.isBlank() || game.title.contains(nameQuery, ignoreCase = true)
            val matchesTag = tagQuery.isBlank() || game.tags.any { it.contains(tagQuery, ignoreCase = true) }
            val matchesDev = devQuery.isBlank() || (game.developer?.contains(devQuery, ignoreCase = true) == true)
            val matchesRating = minRating == 0.0 || (game.rating ?: 0.0) >= minRating
            matchesName && matchesTag && matchesDev && matchesRating
        }

        adapter.submitList(filtered)
        binding.searchEmpty.isVisible = filtered.isEmpty()
    }

    private suspend fun fetchGames(): List<Game> = withContext(Dispatchers.IO) {
        val gamesArray = fetchJsonArray(GAMES_URL)
        val tagsArray = fetchJsonArray(TAGS_URL)

        val tagsByGame = mutableMapOf<String, MutableList<String>>()
        for (i in 0 until tagsArray.length()) {
            val tagObject = tagsArray.optJSONObject(i) ?: continue
            val gameId = tagObject.optString("game_id").ifBlank { tagObject.optString("id") }
            val tag = tagObject.optString("tag")
            if (gameId.isNotBlank() && tag.isNotBlank()) {
                tagsByGame.getOrPut(gameId) { mutableListOf() }.add(tag)
            }
        }

        val list = mutableListOf<Game>()
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
                ?: gameObject.optString("rating").toDoubleOrNull()
            val tags = tagsByGame[gameId].orEmpty()

            list.add(
                Game(
                    id = gameId,
                    title = title,
                    imageUrl = imageUrl,
                    description = description,
                    developer = developer,
                    publisher = publisher,
                    rating = rating,
                    tags = tags
                )
            )
        }
        list
    }

    private suspend fun fetchTags(): List<String> = withContext(Dispatchers.IO) {
        val tagsArray = fetchJsonArray(TAGS_URL)
        val set = linkedSetOf<String>()
        for (i in 0 until tagsArray.length()) {
            val obj = tagsArray.optJSONObject(i) ?: continue
            val tag = obj.optString("tag")
            if (tag.isNotBlank()) set.add(tag)
        }
        set.toList()
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
                throw Exception(response.ifBlank { getString(R.string.home_error_generic) })
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

    private fun setLoading(loading: Boolean) {
        binding.searchProgress.isVisible = loading
        binding.searchResults.isVisible = !loading
    }

    private fun showError(message: String) {
        binding.searchEmpty.text = message
        binding.searchEmpty.isVisible = true
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
    }
}
