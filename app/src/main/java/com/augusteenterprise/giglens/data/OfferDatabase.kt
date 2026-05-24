package com.augusteenterprise.giglens.data
// Author: Claude (Anthropic) - Feature #8: Migration 4→5 adds auto_capture_mode + enabled_platforms
// Room database singleton — v5 adds auto capture + platform config keys

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
    entities = [OfferCapture::class, ScorerConfig::class, AppConfig::class],
    version = 6,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN driverLat REAL")
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN driverLon REAL")
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN pickupDistance REAL")
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN deliveryDistance REAL")
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN totalDistance REAL")
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN truePayPerMile REAL")
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN vehicleCost REAL")
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN netValue REAL")
                db.execSQL("ALTER TABLE offer_captures ADD COLUMN estimatedMinutes INTEGER")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('weight_pickup_penalty', 0.50, 'Weight: pickup leg penalty')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('weight_true_pay_per_mile', 0.20, 'Weight: pay / total miles')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('weight_total_pay_v2', 0.20, 'Weight: total pay amount')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('weight_delivery_leg', 0.10, 'Weight: delivery leg')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('pickup_distance_max', 8.0, 'Max pickup distance')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('pickup_distance_min', 0.0, 'Min pickup distance')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('true_pay_per_mile_min', 0.50, 'Worst true \$/mile')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('true_pay_per_mile_max', 3.00, 'Best true \$/mile')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('cost_per_mile', 0.90, 'Vehicle cost per mile')")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('result_display_seconds', 60.0, 'Seconds to show result pill before reverting to idle')")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT OR IGNORE INTO app_config VALUES('auto_capture_mode', 'off', 'Auto capture: off | accessibility | button | both')")
                db.execSQL("INSERT OR IGNORE INTO app_config VALUES('enabled_platforms', 'doordash', 'Enabled gig platforms comma-separated')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('hourly_rate', 15.00, 'Driver hourly rate for time cost')")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS app_config (
                        `key` TEXT NOT NULL PRIMARY KEY,
                        value TEXT NOT NULL,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
                INSTANCE = instance

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val scorerDao = instance.scorerConfigDao()
                        if (scorerDao.count() == 0) {
                            scorerDao.insertAll(defaultScorerConfig())
                            android.util.Log.d("OfferDatabase", "Scorer config seeded")
                        }
                        val appDao = instance.appConfigDao()
                        if (appDao.count() == 0) {
                            appDao.insertAll(defaultAppConfig())
                            android.util.Log.d("OfferDatabase", "App config seeded")
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
