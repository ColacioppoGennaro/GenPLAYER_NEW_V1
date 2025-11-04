package com.genaro.radiomp3.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.genaro.radiomp3.data.local.dao.AlbumDao
import com.genaro.radiomp3.data.local.dao.ArtistDao
import com.genaro.radiomp3.data.local.dao.ArtworkDao
import com.genaro.radiomp3.data.local.dao.PreferenceDao
import com.genaro.radiomp3.data.local.dao.QueueItemDao
import com.genaro.radiomp3.data.local.dao.SafRootDao
import com.genaro.radiomp3.data.local.dao.TrackDao

@Database(
    entities = [
        Track::class,
        Album::class,
        Artist::class,
        ArtworkCache::class,
        SafRoot::class,
        QueueItem::class,
        PreferenceEntry::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun artworkDao(): ArtworkDao
    abstract fun safRootDao(): SafRootDao
    abstract fun queueItemDao(): QueueItemDao
    abstract fun preferenceDao(): PreferenceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "genplayer.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}