package com.genaro.radiomp3.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.genaro.radiomp3.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Universal adapter for browsing music by folders, albums, artists, or all tracks
 */
class MusicBrowserAdapter(
    private val onItemClick: (BrowserItem) -> Unit
) : RecyclerView.Adapter<MusicBrowserAdapter.ItemViewHolder>() {

    private var items = listOf<BrowserItem>()

    fun submitList(newItems: List<BrowserItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(items[position], onItemClick)
    }

    override fun getItemCount() = items.size

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgCover: ImageView = itemView.findViewById(R.id.imgCover)
        private val txtFolderName: TextView = itemView.findViewById(R.id.txtFolderName)
        private val txtTrackCount: TextView = itemView.findViewById(R.id.txtTrackCount)
        private val imgArrow: ImageView = itemView.findViewById(R.id.imgArrow)

        fun bind(item: BrowserItem, onClick: (BrowserItem) -> Unit) {
            txtFolderName.text = item.title
            txtTrackCount.text = item.subtitle

            // Simple click listener
            itemView.setOnClickListener { onClick(item) }

            // Handle different item types
            when (item) {
                is BrowserItem.FolderItem -> {
                    // Show arrow for folders with content
                    imgArrow.visibility = if (item.hasSubfolders || item.trackCount > 0)
                        View.VISIBLE else View.GONE
                    // Try to load cover art, but show folder icon if not available
                    loadCoverArtOrFolderIcon(item.coverArtUri, isFolder = true)
                }
                is BrowserItem.AlbumItem -> {
                    // Always show arrow for albums (they have tracks)
                    imgArrow.visibility = View.VISIBLE
                    loadCoverArt(item.coverArtUri)
                }
                is BrowserItem.ArtistItem -> {
                    // Show arrow for artists (they have albums)
                    imgArrow.visibility = View.VISIBLE
                    // For artists, show a placeholder or first album cover
                    loadCoverArt(item.coverArtUri)
                }
                is BrowserItem.TrackItem -> {
                    // No arrow for tracks (end of navigation)
                    imgArrow.visibility = View.GONE
                    loadCoverArt(item.coverArtUri)
                }
            }
        }

        private fun loadCoverArtOrFolderIcon(coverUri: String?, isFolder: Boolean = false) {
            if (coverUri.isNullOrBlank()) {
                // Show folder icon for folders, default icon for others
                if (isFolder) {
                    showFolderIcon()
                } else {
                    showDefaultIcon()
                }
                return
            }

            // Try to load cover art, fallback to folder icon if failed
            loadCoverArtWithFolderFallback(coverUri, isFolder)
        }

        private fun loadCoverArtWithFolderFallback(coverUri: String, isFolder: Boolean) {
            // Show skeleton placeholder while loading
            imgCover.setImageResource(R.drawable.ic_image_placeholder)
            imgCover.setColorFilter(null)

            // Try to load embedded artwork if it's a file URI
            if (coverUri.startsWith("content://")) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(itemView.context, Uri.parse(coverUri))
                        val artBytes = retriever.embeddedPicture
                        retriever.release()

                        withContext(Dispatchers.Main) {
                            if (artBytes != null) {
                                val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                                // Smooth crossfade transition
                                imgCover.animate()
                                    .alpha(0f)
                                    .setDuration(100)
                                    .withEndAction {
                                        imgCover.setImageBitmap(bitmap)
                                        imgCover.setColorFilter(null)
                                        imgCover.alpha = 0f
                                        imgCover.animate()
                                            .alpha(1f)
                                            .setDuration(200)
                                            .start()
                                    }
                                    .start()
                            } else {
                                // No embedded art - show folder icon if it's a folder
                                if (isFolder) showFolderIcon() else showDefaultIcon()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MusicBrowserAdapter", "Error loading cover art", e)
                        withContext(Dispatchers.Main) {
                            if (isFolder) showFolderIcon() else showDefaultIcon()
                        }
                    }
                }
            } else if (coverUri.startsWith("http")) {
                // Load from internet URL using Glide with transitions
                Glide.with(itemView.context)
                    .load(coverUri)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(if (isFolder) R.drawable.ic_folder else android.R.drawable.ic_menu_gallery)
                    .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(200))
                    .into(imgCover)
            } else {
                if (isFolder) showFolderIcon() else showDefaultIcon()
            }
        }

        private fun loadCoverArt(coverUri: String?) {
            if (coverUri.isNullOrBlank()) {
                // Show default folder/music icon
                showDefaultIcon()
                return
            }

            // Show skeleton placeholder while loading
            imgCover.setImageResource(R.drawable.ic_image_placeholder)
            imgCover.setColorFilter(null)

            // Try to load embedded artwork if it's a file URI
            if (coverUri.startsWith("content://")) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(itemView.context, Uri.parse(coverUri))
                        val artBytes = retriever.embeddedPicture
                        retriever.release()

                        withContext(Dispatchers.Main) {
                            if (artBytes != null) {
                                val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                                // Smooth crossfade transition
                                imgCover.animate()
                                    .alpha(0f)
                                    .setDuration(100)
                                    .withEndAction {
                                        imgCover.setImageBitmap(bitmap)
                                        imgCover.setColorFilter(null)
                                        imgCover.alpha = 0f
                                        imgCover.animate()
                                            .alpha(1f)
                                            .setDuration(200)
                                            .start()
                                    }
                                    .start()
                            } else {
                                showDefaultIcon()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MusicBrowserAdapter", "Error loading cover art", e)
                        withContext(Dispatchers.Main) {
                            showDefaultIcon()
                        }
                    }
                }
            } else if (coverUri.startsWith("http")) {
                // Load from internet URL using Glide with transitions
                Glide.with(itemView.context)
                    .load(coverUri)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(android.R.drawable.ic_menu_gallery)
                    .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(200))
                    .into(imgCover)
            } else {
                showDefaultIcon()
            }
        }

        private fun showDefaultIcon() {
            imgCover.setImageResource(android.R.drawable.ic_menu_gallery)
            imgCover.setColorFilter(Color.parseColor("#666666"))
        }

        private fun showFolderIcon() {
            // Show custom folder icon in orange
            imgCover.setImageResource(R.drawable.ic_folder)
            imgCover.setColorFilter(Color.parseColor("#FFA500"))  // Orange color for folders
        }
    }
}
