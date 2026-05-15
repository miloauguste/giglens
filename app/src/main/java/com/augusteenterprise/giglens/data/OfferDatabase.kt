package com.augusteenterprise.giglens.data
// Author: Claude (Anthropic)
// Room database singleton for GigLens — v2 adds scorer_config and score fields

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [OfferCapture::class, ScorerConfig::class],
    version = 2,
    exportSchema = false
)
abstract class OfferDatabase : RoomDatabase() {
    abstract fun offerCaptureDao(): OfferCaptureDao
    abstract fun scorerConfigDao(): ScorerConfigDao

    companion object {
        @Volatile
        private var INSTANCE: OfferDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN score INTEGER")
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN verdict TEXT")
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN payPerMile REAL")
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN vsPersonalAvg REAL")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS scorer_config (
                        `key` TEXT NOT NULL PRIMARY KEY,
                        value REAL NOT NULL,
                        description TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): OfferDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfferDatabase::class.java,
                    "giglens_offers.db"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance

                // Seed after INSTANCE is assigned — safe to call DAO now
	CoroutineScope(Dispatchers.IO).launch {
    	try {
        	val dao = instance.scorerConfigDao()
        	val count = dao.count()
        	android.util.Log.d("OfferDatabase", "Seed check: scorer_config count = $count")
        	if (count == 0) {
            	dao.insertAll(defaultScorerConfig())
            	android.util.Log.d("OfferDatabase", "Seed complete: inserted ${defaultScorerConfig().size} rows")
        	} else {
            	android.util.Log.d("OfferDatabase", "Seed skipped: already has $count rows")
        	}
    	} catch (e: Exception) {
       		 android.util.Log.e("OfferDatabase", "Seed failed: ${e.message}", e)
    		}
	}
                instance
            }
        }
    }
}
