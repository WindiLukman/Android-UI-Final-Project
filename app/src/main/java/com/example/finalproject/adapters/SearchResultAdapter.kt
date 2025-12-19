package com.example.finalproject.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.finalproject.R
import com.example.finalproject.models.Game
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.Locale

class SearchResultAdapter(
    private val onClick: (Game) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.SearchViewHolder>() {

    private val items = mutableListOf<Game>()

    fun submitList(list: List<Game>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
        return SearchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val game = items[position]
        holder.bind(game)
    }

    override fun getItemCount(): Int = items.size

    inner class SearchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cover: ImageView = itemView.findViewById(R.id.searchGameCover)
        private val title: TextView = itemView.findViewById(R.id.searchGameTitle)
        private val developer: TextView = itemView.findViewById(R.id.searchGameDeveloper)
        private val rating: TextView = itemView.findViewById(R.id.searchGameRating)
        private val tags: ChipGroup = itemView.findViewById(R.id.searchGameTags)

        fun bind(game: Game) {
            title.text = game.title
            developer.text = game.developer ?: itemView.context.getString(R.string.not_available)
            rating.text = game.rating?.let { String.format(Locale.US, "%.1f", it) }
                ?: itemView.context.getString(R.string.not_rated_yet)

            Glide.with(itemView.context)
                .load(game.imageUrl)
                .placeholder(R.drawable.placeholder)
                .error(R.drawable.placeholder)
                .into(cover)

            tags.removeAllViews()
            game.tags.take(3).forEach { tag ->
                val chip = Chip(itemView.context).apply {
                    text = tag
                    isCheckable = false
                    isClickable = false
                    setChipBackgroundColorResource(R.color.home_card)
                    setTextColor(ContextCompat.getColor(itemView.context, R.color.home_text_primary))
                    chipStrokeWidth = 0f
                    textSize = 12f
                }
                tags.addView(chip)
            }

            itemView.setOnClickListener { onClick(game) }
        }
    }
}
