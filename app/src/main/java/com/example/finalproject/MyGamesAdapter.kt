package com.example.finalproject

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.finalproject.models.Game

class MyGamesAdapter(
    private val statusListener: StatusListener
) : RecyclerView.Adapter<MyGamesAdapter.MyGameViewHolder>() {

    private val items = mutableListOf<MyGameItem>()

    interface StatusListener {
        fun onStatusSelected(item: MyGameItem, status: String)
    }

    fun submitList(newItems: List<MyGameItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun updateStatus(gameId: String, status: String) {
        val idx = items.indexOfFirst { it.game.id == gameId }
        if (idx != -1) {
            items[idx] = items[idx].copy(progress = status)
            notifyItemChanged(idx)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyGameViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_game, parent, false)
        return MyGameViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyGameViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, statusListener)
    }

    override fun getItemCount(): Int = items.size

    class MyGameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cover: ImageView = itemView.findViewById(R.id.myGameCover)
        private val title: TextView = itemView.findViewById(R.id.myGameTitle)
        private val tagsContainer: ViewGroup = itemView.findViewById(R.id.myGameTags)
        private val rating: TextView = itemView.findViewById(R.id.myGameRating)
        private val statusWant: TextView = itemView.findViewById(R.id.statusWant)
        private val statusPlayed: TextView = itemView.findViewById(R.id.statusPlayed)
        private val statusCompleted: TextView = itemView.findViewById(R.id.statusCompleted)

        fun bind(item: MyGameItem, listener: StatusListener) {
            val game: Game = item.game
            title.text = game.title
            val userRating = item.userRating
            rating.text = userRating?.let { String.format("%.1f", it) }
                ?: itemView.context.getString(R.string.not_rated_yet)

            Glide.with(itemView)
                .load(game.imageUrl)
                .placeholder(R.drawable.placeholder)
                .error(R.drawable.placeholder)
                .into(cover)

            bindTags(game.tags)
            bindStatuses(item.progress)

            statusWant.setOnClickListener { listener.onStatusSelected(item, "want") }
            statusPlayed.setOnClickListener { listener.onStatusSelected(item, "played") }
            statusCompleted.setOnClickListener { listener.onStatusSelected(item, "completed") }
        }

        private fun bindTags(tags: List<String>) {
            tagsContainer.removeAllViews()
            val inflater = LayoutInflater.from(itemView.context)
            tags.take(3).forEach { tag ->
                val chip = inflater.inflate(R.layout.view_tag_pill, tagsContainer, false) as TextView
                chip.text = tag
                tagsContainer.addView(chip)
            }
        }

        private fun bindStatuses(progress: String) {
            val active = itemView.context.getColor(R.color.home_nav_active)
            val inactive = itemView.context.getColor(R.color.home_text_secondary)

            val map = mapOf(
                "want" to statusWant,
                "played" to statusPlayed,
                "completed" to statusCompleted
            )
            map.forEach { (key, view) ->
                val isActive = progress.equals(key, ignoreCase = true)
                view.setTextColor(if (isActive) active else inactive)
                view.paint.isUnderlineText = isActive
            }
        }
    }
}
