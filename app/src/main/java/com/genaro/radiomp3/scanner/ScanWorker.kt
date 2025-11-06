package com.genaro.radiomp3.scanner

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.genaro.radiomp3.data.local.AppDatabase

class ScanWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getInstance(applicationContext)

        try {
            // Step A: MediaStore Scan - DISABILITATO per evitare di scansionare tutto il telefono
            // Usa solo le cartelle scelte dall'utente tramite SAF
            // val mediaStoreScanner = MediaStoreScanner(applicationContext, database.trackDao())
            // mediaStoreScanner.scan()

            // Step B: SAF Scan - Scansiona SOLO le cartelle scelte dall'utente
            val safScanner = SAFScanner(applicationContext, database.trackDao(), database.safRootDao())
            safScanner.scan()

            // Step C: Tag Enrichment
            val tracksToEnrich = database.trackDao().getTracksMissingTags()
            val tagEnrichment = TagEnrichment(
                applicationContext,
                database.trackDao(),
                database.artworkDao()
            )
            tagEnrichment.enrich(tracksToEnrich)

            // TODO: Step D - Update Album/Artist statistics

        } catch (e: Exception) {
            android.util.Log.e("ScanWorker", "Error during scan", e)
            return Result.failure()
        }

        return Result.success()
    }
}