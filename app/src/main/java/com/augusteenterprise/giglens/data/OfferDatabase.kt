package com.augusteenterprise.giglens.data

// Author: Claude (Anthropic) - Feature #8: Migration 4→5 adds auto_capture_mode + enabled_platforms
// Last modified: Claude (Anthropic) - 2026-06-15 - Migration 7→8 adds town estimation accuracy tracking
// Room database singleton — v8 adds estimatedTown, estimatedTownMethod, confirmedTown, townAccurate to offer_captures

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [OfferCapture::class, ScorerConfig::class, AppConfig::class],
    version = 8,
    exportSchema = false
)
abstract class OfferDatabase : RoomDatabase() {
    abstract fun offerCaptureDao(): OfferCaptureDao
    abstract fun scorerConfigDao(): ScorerConfigDao
    abstract fun appConfigDao(): AppConfigDao

    companion object {
        @Volatile
        private var INSTANCE: OfferDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS offer_captures (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        platform TEXT NOT NULL,
                        payAmount REAL,
                        distance REAL,
                        distanceUnit TEXT NOT NULL,
                        restaurant TEXT,
                        screenshotPath TEXT,
                        rawOcrText TEXT,
                        accepted INTEGER,
                        score INTEGER,
                        verdict TEXT,
                        payPerMile REAL,
                        vsPersonalAvg REAL,
                        driverLat REAL,
                        driverLon REAL,
                        pickupDistance REAL,
                        deliveryDistance REAL,
                        totalDistance REAL,
                        truePayPerMile REAL,
                        vehicleCost REAL,
                        netValue REAL,
                        estimatedMinutes INTEGER
                    )
                """)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add any migration logic from version 2 to 3
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add any migration logic from version 3 to 4
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_config ADD COLUMN auto_capture_mode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_config ADD COLUMN enabled_platforms TEXT NOT NULL DEFAULT '[\"DOORDASH\"]'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migration 5→6: Add estimatedMinutes column
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN estimatedMinutes INTEGER")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // CORRECT: ALTER TABLE adds nullable columns — existing rows get NULL
                // WRONG:   dropping and recreating table — destroys existing offer data
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN timeCost REAL")
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN minutesOnJob REAL")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // CORRECT: ALTER TABLE adds nullable columns — existing rows get NULL
                // WRONG:   dropping and recreating table — destroys existing offer data
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN estimatedTown TEXT")
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN estimatedTownMethod TEXT")
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN confirmedTown TEXT")
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN townAccurate INTEGER")
            }
        }

        fun getInstance(context: Context): OfferDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfferDatabase::class.java,
                    "offer_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
