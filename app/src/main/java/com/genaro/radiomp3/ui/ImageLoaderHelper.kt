package com.genaro.radiomp3.ui

import android.widget.ImageView
import coil.ImageLoader
import coil.load
import com.genaro.radiomp3.R

/**
 * Helper per lazy-load immagini con skeleton placeholder
 */
object ImageLoaderHelper {

    /**
     * Carica immagine da URI con skeleton placeholder
     *
     * @param imageView Target ImageView
     * @param uri URI della traccia audio
     * @param placeholder Drawable placeholder (default: skeleton gray)
     */
    fun loadArtwork(
        imageView: ImageView,
        uri: String?,
        placeholder: Int = R.drawable.ic_image_placeholder
    ) {
        if (uri.isNullOrEmpty()) {
            imageView.setImageResource(placeholder)
            return
        }

        imageView.load(uri) {
            // Placeholder mentre carica
            placeholder(placeholder)

            // Errore
            error(placeholder)

            // Cache in memory
            memoryCacheKey(uri)

            // Crossfade per smooth transition
            crossfade(true)
        }
    }

    /**
     * Cancella cache immagini (memoria)
     */
    fun clearImageCache(imageLoader: ImageLoader) {
        imageLoader.memoryCache?.clear()
    }
}
