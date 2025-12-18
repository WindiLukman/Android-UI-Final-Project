package com.example.finalproject.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.finalproject.R
import com.example.finalproject.models.Game
import com.example.finalproject.models.Genre

class GenreAdapter(
    initialGenres: List<Genre>,
    private val onGameClick: (Game) -> Unit
) : RecyclerView.Adapter<GenreAdapter.GenreViewHolder>() {

    private var genreList: List<Genre> = initialGenres

    inner class GenreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val genreTitle: TextView = itemView.findViewById(R.id.genreTitle)
        val gameRecycler: RecyclerView = itemView.findViewById(R.id.gameRecycler)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenreViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_genre_row, parent, false)
        return GenreViewHolder(view)
    }

    override fun onBindViewHolder(holder: GenreViewHolder, position: Int) {
        val genre = genreList[position]
        holder.genreTitle.text = genre.name

        holder.gameRecycler.layoutManager =
            LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
        holder.gameRecycler.adapter = GameAdapter(genre.games, onGameClick)
    }

    override fun getItemCount(): Int = genreList.size

    fun updateGenres(genres: List<Genre>) {
        genreList = genres
        notifyDataSetChanged()
    }
}
