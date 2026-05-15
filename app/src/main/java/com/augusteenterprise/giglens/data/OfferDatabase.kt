package com.augusteenterprise.giglens.data
// Author: Claude (Anthropic)
// Room database singleton — v3 adds location and distance breakdown fields

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
    version = 3,
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
                // Add new scorer_config keys for 4-factor scoring
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('weight_pickup_penalty', 0.50, 'Weight: pickup leg penalty (shorter = better)')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('weight_true_pay_per_mile', 0.20, 'Weight: pay / total miles')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('weight_total_pay_v2', 0.20, 'Weight: total pay amount')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('weight_delivery_leg', 0.10, 'Weight: delivery leg (shorter = slightly better)')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('pickup_distance_max', 8.0, 'Normalization: max pickup distance (scores 0)')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('cost_per_mile', 0.90, 'Vehicle cost per mile driven (IRS standard $0.90)')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('pickup_distance_min', 0.0, 'Normalization: min pickup distance (scores 100)')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('true_pay_per_mile_min', 0.50, 'Normalization: worst true $/mile')")
                db.execSQL("INSERT OR IGNORE INTO scorer_config VALUES('true_pay_per_mile_max', 3.00, 'Normalization: best true $/mile')")
            }
        }

        fun getInstance(context: Context): OfferDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfferDatabase::class.java,
                    "giglens_offers.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance

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
