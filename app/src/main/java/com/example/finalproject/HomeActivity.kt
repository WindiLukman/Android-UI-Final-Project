package com.example.finalproject

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finalproject.adapters.GenreAdapter
import com.example.finalproject.databinding.ActivityHomeBinding
import com.example.finalproject.models.Game
import com.example.finalproject.models.Genre

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val actionGames = listOf(
            Game("Expedition 33", R.drawable.expedition33),
            Game("Placeholder", R.drawable.placeholder),
            Game("Placeholder", R.drawable.placeholder)
        )

        val puzzleGames = listOf(
            Game("Puzzle Game 1", R.drawable.placeholder),
            Game("Puzzle Game 2", R.drawable.placeholder),
            Game("Puzzle Game 3", R.drawable.placeholder)
        )

        val genreList = listOf(
            Genre("Action", actionGames),
            Genre("Puzzle", puzzleGames)
        )

        binding.genreRecycler.layoutManager = LinearLayoutManager(this)
        binding.genreRecycler.adapter = GenreAdapter(genreList)
    }
}
