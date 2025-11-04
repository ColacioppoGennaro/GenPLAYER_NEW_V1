package com.genaro.radiomp3.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.genaro.radiomp3.R
import com.genaro.radiomp3.data.local.Track

class TrackAdapter(
    private var tracks: List<Track>,
    private val onTrackClick: (Track) -> Unit
) : RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {

    class TrackViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgAlbumArt: ImageView = view.findViewById(R.id.imgAlbumArt)
        val txtTitle: TextView = view.findViewById(R.id.txtTitle)
        val txtArtist: TextView = view.findViewById(R.id.txtArtist)
        val txtAlbum: TextView = view.findViewById(R.id.txtAlbum)
        val btnPlay: ImageButton = view.findViewById(R.id.btnPlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track, parent, false)
        return TrackViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = tracks[position]

        holder.txtTitle.text = track.title ?: track.displayName
        holder.txtArtist.text = track.artistName ?: "Unknown Artist"
        holder.txtAlbum.text = track.albumTitle ?: "Unknown Album"

        // Click on whole item
        holder.itemView.setOnClickListener {
            onTrackClick(track)
        }

        // Click on play button
        holder.btnPlay.setOnClickListener {
            onTrackClick(track)
        }
    }

    override fun getItemCount() = tracks.size

    fun updateTracks(newTracks: List<Track>) {
        tracks = newTracks
        notifyDataSetChanged()
    }
}
