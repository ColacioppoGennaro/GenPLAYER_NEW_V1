package com.genaro.radiomp3.work

import android.content.Context
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScanWorker(appContext: Context, params: WorkerParameters)
    : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // TODO: leggere da MediaStore e dalle SafRoot salvate
        // per ora prova “dummy” (solo contare quante tracce vede MediaStore):
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val count = applicationContext.contentResolver
            .query(uri, projection, null, null, null)
            ?.use { it.count } ?: 0

        android.util.Log.i("ScanWorker", "Trovate $count tracce (MediaStore).")
        Result.success()
    }
}