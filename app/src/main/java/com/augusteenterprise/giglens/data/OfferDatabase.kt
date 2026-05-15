package com.augusteenterprise.giglens.data

// Author: Claude (Anthropic)
// Room database singleton for GigLens

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [OfferCapture::class], version = 1, exportSchema = false)
abstract class OfferDatabase : RoomDatabase() {

    abstract fun offerCaptureDao(): OfferCaptureDao

    companion object {
        @Volatile
        private var INSTANCE: OfferDatabase? = null

        fun getInstance(context: Context): OfferDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfferDatabase::class.java,
                    "giglens_offers.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
