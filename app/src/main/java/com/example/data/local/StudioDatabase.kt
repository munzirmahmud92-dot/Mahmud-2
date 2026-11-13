package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AudioClipEntity::class,
        VoiceProfileEntity::class,
        ApiUsageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun audioClipDao(): AudioClipDao
    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun apiUsageDao(): ApiUsageDao

    companion object {
        @Volatile
        private var INSTANCE: StudioDatabase? = null

        fun getInstance(context: Context): StudioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StudioDatabase::class.java,
                    "munzir_voice_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
