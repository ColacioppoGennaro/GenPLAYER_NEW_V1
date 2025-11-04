package com.genaro.radiomp3

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class RadioApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("RadioApp", "Application onCreate called")
    }

    override fun newImageLoader(): ImageLoader {
        return try {
            val client = OkHttpClient.Builder()
                .cache(Cache(File(cacheDir, "img_cache"), 50L * 1024L * 1024L))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("User-Agent", "GenRadio/1.0 (Android)")
                        .build()
                    chain.proceed(req)
                }
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            ImageLoader.Builder(this)
                .okHttpClient(client)
                .components {
                    add(SvgDecoder.Factory())
                }
                .crossfade(true)
                .build()
        } catch (e: Exception) {
            android.util.Log.e("RadioApp", "Error creating ImageLoader", e)
            // Fallback to basic ImageLoader if there's an error
            ImageLoader.Builder(this).build()
        }
    }
}
