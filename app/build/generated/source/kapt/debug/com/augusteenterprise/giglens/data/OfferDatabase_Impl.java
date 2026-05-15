package com.augusteenterprise.giglens.data;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class OfferDatabase_Impl extends OfferDatabase {
  private volatile OfferCaptureDao _offerCaptureDao;

  private volatile ScorerConfigDao _scorerConfigDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `offer_captures` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `platform` TEXT NOT NULL, `payAmount` REAL, `distance` REAL, `distanceUnit` TEXT NOT NULL, `restaurant` TEXT, `screenshotPath` TEXT, `rawOcrText` TEXT, `accepted` INTEGER, `score` INTEGER, `verdict` TEXT, `payPerMile` REAL, `vsPersonalAvg` REAL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `scorer_config` (`key` TEXT NOT NULL, `value` REAL NOT NULL, `description` TEXT NOT NULL, PRIMARY KEY(`key`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '52d9332a08dddf007e024de4f64ecae8')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `offer_captures`");
        db.execSQL("DROP TABLE IF EXISTS `scorer_config`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsOfferCaptures = new HashMap<String, TableInfo.Column>(14);
        _columnsOfferCaptures.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfferCaptures.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfferCaptures.put("platform", new TableInfo.Column("platform", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfferCaptures.put("payAmount", new TableInfo.Column("payAmount", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfferCaptures.put("distance", new TableInfo.Column("distance", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfferCaptures.put("distanceUnit", new TableInfo.Column("distanceUnit", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfferCaptures.put("restaurant", new TableInfo.Column("restaurant", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfferCaptures.put("screenshotPath", new TableInfo.Column("screenshotPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfferCaptures.put("rawOcrText", new TableInfo.Column("rawOcrText", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfferCaptures.put("accepted", new TableInfo.Column("accepted", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfferCaptures.put("score", new TableInfo.Column("score", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfferCaptures.put("verdict", new TableInfo.Column("verdict", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfferCaptures.put("payPerMile", new TableInfo.Column("payPerMile", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsOfferCaptures.put("vsPersonalAvg", new TableInfo.Column("vsPersonalAvg", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysOfferCaptures = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesOfferCaptures = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoOfferCaptures = new TableInfo("offer_captures", _columnsOfferCaptures, _foreignKeysOfferCaptures, _indicesOfferCaptures);
        final TableInfo _existingOfferCaptures = TableInfo.read(db, "offer_captures");
        if (!_infoOfferCaptures.equals(_existingOfferCaptures)) {
          return new RoomOpenHelper.ValidationResult(false, "offer_captures(com.augusteenterprise.giglens.data.OfferCapture).\n"
                  + " Expected:\n" + _infoOfferCaptures + "\n"
                  + " Found:\n" + _existingOfferCaptures);
        }
        final HashMap<String, TableInfo.Column> _columnsScorerConfig = new HashMap<String, TableInfo.Column>(3);
        _columnsScorerConfig.put("key", new TableInfo.Column("key", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScorerConfig.put("value", new TableInfo.Column("value", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsScorerConfig.put("description", new TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysScorerConfig = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesScorerConfig = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoScorerConfig = new TableInfo("scorer_config", _columnsScorerConfig, _foreignKeysScorerConfig, _indicesScorerConfig);
        final TableInfo _existingScorerConfig = TableInfo.read(db, "scorer_config");
        if (!_infoScorerConfig.equals(_existingScorerConfig)) {
          return new RoomOpenHelper.ValidationResult(false, "scorer_config(com.augusteenterprise.giglens.data.ScorerConfig).\n"
                  + " Expected:\n" + _infoScorerConfig + "\n"
                  + " Found:\n" + _existingScorerConfig);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "52d9332a08dddf007e024de4f64ecae8", "9bdfe82ec44dceb241e6760d3eff3bf4");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "offer_captures","scorer_config");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `offer_captures`");
      _db.execSQL("DELETE FROM `scorer_config`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(OfferCaptureDao.class, OfferCaptureDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ScorerConfigDao.class, ScorerConfigDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public OfferCaptureDao offerCaptureDao() {
    if (_offerCaptureDao != null) {
      return _offerCaptureDao;
    } else {
      synchronized(this) {
        if(_offerCaptureDao == null) {
          _offerCaptureDao = new OfferCaptureDao_Impl(this);
        }
        return _offerCaptureDao;
      }
    }
  }

  @Override
  public ScorerConfigDao scorerConfigDao() {
    if (_scorerConfigDao != null) {
      return _scorerConfigDao;
    } else {
      synchronized(this) {
        if(_scorerConfigDao == null) {
          _scorerConfigDao = new ScorerConfigDao_Impl(this);
        }
        return _scorerConfigDao;
      }
    }
  }
}
