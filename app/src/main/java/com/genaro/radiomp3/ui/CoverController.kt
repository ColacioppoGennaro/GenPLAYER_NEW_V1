package com.genaro.radiomp3.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.widget.ImageView
import androidx.core.graphics.applyCanvas
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlin.math.roundToInt

class CoverController(
    private val context: Context,
    private val imageView: ImageView,
    private val crossfadeMs: Int = 650
) {

    private var lastGoodDrawable: Drawable? = null
    private var stationLogoUrl: String? = null

    // Debounce/antirumore sui metadata
    private var lastMetaUrl: String? = null
    private var lastSwitchAt: Long = 0L
    private val minSwitchIntervalMs = 3000L // evita rimbalzi troppo ravvicinati

    fun setStationLogo(logoUrl: String?) {
        stationLogoUrl = logoUrl
    }

    /**
     * Chiamala quando arrivano i metadata della traccia.
     * Può essere null/empty -> in quel caso mantiene la cover esistente se presente.
     */
    fun updateCover(coverUrlFromMetadata: String?) {
        val normalized = normalizeUrl(coverUrlFromMetadata)

        // Debounce: evita rimbalzi troppo ravvicinati
        val now = System.currentTimeMillis()
        if (normalized == lastMetaUrl && (now - lastSwitchAt) < minSwitchIntervalMs) {
            // stessa URL e troppo presto per rifare qualcosa → ignora
            return
        }
        if (!normalized.isNullOrBlank() && normalized != lastMetaUrl) {
            // è una URL diversa vera → ok, consentiamo lo switch
            lastMetaUrl = normalized
            lastSwitchAt = now
        }

        // 1) Se arriva null/empty: NON toccare nulla se abbiamo già una lastGood.
        if (normalized.isNullOrBlank()) {
            if (lastGoodDrawable != null) {
                // abbiamo già qualcosa di buono a schermo → resta così
                return
            }
            // primo avvio o non abbiamo nulla: prova logo; se manca, fallback testo
            tryLoadDrawable(
                url = stationLogoUrl,
                onSuccess = { applyWithCrossfade(it) },
                onFail = { applyWithCrossfade(makeTextFallback("No cover")) }
            )
            return
        }

        // 2) Prova a caricare la nuova cover
        tryLoadDrawable(
            url = normalized,
            onSuccess = { applyWithCrossfade(it) }, // aggiorna e memorizza
            onFail = {
                // Se il load fallisce ma abbiamo già una cover buona, NON fare niente.
                if (lastGoodDrawable != null) return@tryLoadDrawable

                // Non abbiamo nulla: prova logo → poi fallback
                tryLoadDrawable(
                    url = stationLogoUrl,
                    onSuccess = { applyWithCrossfade(it) },
                    onFail = { applyWithCrossfade(makeTextFallback("No cover")) }
                )
            }
        )
    }

    // --- Internals ----------------------------------------------------------

    private fun normalizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return url
        // Rimuovi querystring volatile tipo ?ts=12345
        val idx = url.indexOf('?')
        return if (idx > 0) url.substring(0, idx) else url
    }

    private fun tryLoadDrawable(
        url: String?,
        onSuccess: (Drawable) -> Unit,
        onFail: () -> Unit
    ) {
        if (url.isNullOrBlank()) {
            onFail()
            return
        }

        Glide.with(context)
            .asBitmap()
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val d = BitmapDrawable(context.resources, resource)
                    onSuccess(d)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // non svuotare l'ImageView (evita flash/nero)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    onFail()
                }
            })
    }

    private fun applyWithCrossfade(newDrawable: Drawable) {
        val current = (imageView.drawable ?: lastGoodDrawable)

        // Se identica o praticamente uguale → non rifare transizione
        if (current is BitmapDrawable && newDrawable is BitmapDrawable) {
            if (fastSameBitmap(current.bitmap, newDrawable.bitmap)) return
        }

        if (current == null) {
            imageView.setImageDrawable(newDrawable)
            lastGoodDrawable = newDrawable
            return
        }

        val td = TransitionDrawable(arrayOf(current, newDrawable)).apply {
            isCrossFadeEnabled = true
        }
        imageView.setImageDrawable(td)
        td.startTransition(crossfadeMs)

        lastGoodDrawable = newDrawable // <<< IMPORTANTE: memorizza SEMPRE la buona
    }

    private fun fastSameBitmap(a: Bitmap?, b: Bitmap?): Boolean {
        if (a == null || b == null) return false
        if (a.width != b.width || a.height != b.height) return false
        // cheap check: sample a few pixels
        val p1 = a.getPixel(a.width / 2, a.height / 2)
        val p2 = b.getPixel(b.width / 2, b.height / 2)
        return p1 == p2
    }

    private fun makeTextFallback(text: String): Drawable {
        // Bitmap 1:1 con gradiente tenue + testo al centro
        val size = dp(220)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).applyCanvas {
            // sfondo gradiente
            val shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                Color.parseColor("#363A3F"),
                Color.parseColor("#1F2124"),
                Shader.TileMode.CLAMP
            )
            paint.shader = shader
            drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

            // testo
            paint.shader = null
            paint.isAntiAlias = true
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            paint.textSize = dp(18).toFloat()

            val y = size / 2f - (paint.descent() + paint.ascent()) / 2
            drawText(text.uppercase(), size / 2f, y, paint)
        }
        return BitmapDrawable(context.resources, bmp)
    }

    private val paint = Paint()

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).roundToInt()
}
