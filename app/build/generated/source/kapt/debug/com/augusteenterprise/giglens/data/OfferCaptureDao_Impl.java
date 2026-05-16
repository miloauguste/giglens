package com.augusteenterprise.giglens.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Boolean;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class OfferCaptureDao_Impl implements OfferCaptureDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<OfferCapture> __insertionAdapterOfOfferCapture;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public OfferCaptureDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfOfferCapture = new EntityInsertionAdapter<OfferCapture>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `offer_captures` (`id`,`timestamp`,`platform`,`payAmount`,`distance`,`distanceUnit`,`restaurant`,`screenshotPath`,`rawOcrText`,`accepted`,`score`,`verdict`,`payPerMile`,`vsPersonalAvg`,`driverLat`,`driverLon`,`pickupDistance`,`deliveryDistance`,`totalDistance`,`truePayPerMile`,`vehicleCost`,`netValue`,`estimatedMinutes`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final OfferCapture entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getTimestamp());
        if (entity.getPlatform() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getPlatform());
        }
        if (entity.getPayAmount() == null) {
          statement.bindNull(4);
        } else {
          statement.bindDouble(4, entity.getPayAmount());
        }
        if (entity.getDistance() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getDistance());
        }
        if (entity.getDistanceUnit() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getDistanceUnit());
        }
        if (entity.getRestaurant() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getRestaurant());
        }
        if (entity.getScreenshotPath() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getScreenshotPath());
        }
        if (entity.getRawOcrText() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getRawOcrText());
        }
        final Integer _tmp = entity.getAccepted() == null ? null : (entity.getAccepted() ? 1 : 0);
        if (_tmp == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, _tmp);
        }
        if (entity.getScore() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getScore());
        }
        if (entity.getVerdict() == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, entity.getVerdict());
        }
        if (entity.getPayPerMile() == null) {
          statement.bindNull(13);
        } else {
          statement.bindDouble(13, entity.getPayPerMile());
        }
        if (entity.getVsPersonalAvg() == null) {
          statement.bindNull(14);
        } else {
          statement.bindDouble(14, entity.getVsPersonalAvg());
        }
        if (entity.getDriverLat() == null) {
          statement.bindNull(15);
        } else {
          statement.bindDouble(15, entity.getDriverLat());
        }
        if (entity.getDriverLon() == null) {
          statement.bindNull(16);
        } else {
          statement.bindDouble(16, entity.getDriverLon());
        }
        if (entity.getPickupDistance() == null) {
          statement.bindNull(17);
        } else {
          statement.bindDouble(17, entity.getPickupDistance());
        }
        if (entity.getDeliveryDistance() == null) {
          statement.bindNull(18);
        } else {
          statement.bindDouble(18, entity.getDeliveryDistance());
        }
        if (entity.getTotalDistance() == null) {
          statement.bindNull(19);
        } else {
          statement.bindDouble(19, entity.getTotalDistance());
        }
        if (entity.getTruePayPerMile() == null) {
          statement.bindNull(20);
        } else {
          statement.bindDouble(20, entity.getTruePayPerMile());
        }
        if (entity.getVehicleCost() == null) {
          statement.bindNull(21);
        } else {
          statement.bindDouble(21, entity.getVehicleCost());
        }
        if (entity.getNetValue() == null) {
          statement.bindNull(22);
        } else {
          statement.bindDouble(22, entity.getNetValue());
        }
        if (entity.getEstimatedMinutes() == null) {
          statement.bindNull(23);
        } else {
          statement.bindLong(23, entity.getEstimatedMinutes());
        }
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM offer_captures WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM offer_captures";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final OfferCapture capture, final Continuation<? super Long> arg1) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfOfferCapture.insertAndReturnId(capture);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, arg1);
  }

  @Override
  public Object deleteById(final long id, final Continuation<? super Unit> arg1) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, arg1);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> arg0) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, arg0);
  }

  @Override
  public Object getAll(final Continuation<? super List<OfferCapture>> arg0) {
    final String _sql = "SELECT * FROM offer_captures ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<OfferCapture>>() {
      @Override
      @NonNull
      public List<OfferCapture> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfPayAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "payAmount");
          final int _cursorIndexOfDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "distance");
          final int _cursorIndexOfDistanceUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "distanceUnit");
          final int _cursorIndexOfRestaurant = CursorUtil.getColumnIndexOrThrow(_cursor, "restaurant");
          final int _cursorIndexOfScreenshotPath = CursorUtil.getColumnIndexOrThrow(_cursor, "screenshotPath");
          final int _cursorIndexOfRawOcrText = CursorUtil.getColumnIndexOrThrow(_cursor, "rawOcrText");
          final int _cursorIndexOfAccepted = CursorUtil.getColumnIndexOrThrow(_cursor, "accepted");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfVerdict = CursorUtil.getColumnIndexOrThrow(_cursor, "verdict");
          final int _cursorIndexOfPayPerMile = CursorUtil.getColumnIndexOrThrow(_cursor, "payPerMile");
          final int _cursorIndexOfVsPersonalAvg = CursorUtil.getColumnIndexOrThrow(_cursor, "vsPersonalAvg");
          final int _cursorIndexOfDriverLat = CursorUtil.getColumnIndexOrThrow(_cursor, "driverLat");
          final int _cursorIndexOfDriverLon = CursorUtil.getColumnIndexOrThrow(_cursor, "driverLon");
          final int _cursorIndexOfPickupDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupDistance");
          final int _cursorIndexOfDeliveryDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "deliveryDistance");
          final int _cursorIndexOfTotalDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "totalDistance");
          final int _cursorIndexOfTruePayPerMile = CursorUtil.getColumnIndexOrThrow(_cursor, "truePayPerMile");
          final int _cursorIndexOfVehicleCost = CursorUtil.getColumnIndexOrThrow(_cursor, "vehicleCost");
          final int _cursorIndexOfNetValue = CursorUtil.getColumnIndexOrThrow(_cursor, "netValue");
          final int _cursorIndexOfEstimatedMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "estimatedMinutes");
          final List<OfferCapture> _result = new ArrayList<OfferCapture>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final OfferCapture _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpPlatform;
            if (_cursor.isNull(_cursorIndexOfPlatform)) {
              _tmpPlatform = null;
            } else {
              _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            }
            final Double _tmpPayAmount;
            if (_cursor.isNull(_cursorIndexOfPayAmount)) {
              _tmpPayAmount = null;
            } else {
              _tmpPayAmount = _cursor.getDouble(_cursorIndexOfPayAmount);
            }
            final Double _tmpDistance;
            if (_cursor.isNull(_cursorIndexOfDistance)) {
              _tmpDistance = null;
            } else {
              _tmpDistance = _cursor.getDouble(_cursorIndexOfDistance);
            }
            final String _tmpDistanceUnit;
            if (_cursor.isNull(_cursorIndexOfDistanceUnit)) {
              _tmpDistanceUnit = null;
            } else {
              _tmpDistanceUnit = _cursor.getString(_cursorIndexOfDistanceUnit);
            }
            final String _tmpRestaurant;
            if (_cursor.isNull(_cursorIndexOfRestaurant)) {
              _tmpRestaurant = null;
            } else {
              _tmpRestaurant = _cursor.getString(_cursorIndexOfRestaurant);
            }
            final String _tmpScreenshotPath;
            if (_cursor.isNull(_cursorIndexOfScreenshotPath)) {
              _tmpScreenshotPath = null;
            } else {
              _tmpScreenshotPath = _cursor.getString(_cursorIndexOfScreenshotPath);
            }
            final String _tmpRawOcrText;
            if (_cursor.isNull(_cursorIndexOfRawOcrText)) {
              _tmpRawOcrText = null;
            } else {
              _tmpRawOcrText = _cursor.getString(_cursorIndexOfRawOcrText);
            }
            final Boolean _tmpAccepted;
            final Integer _tmp;
            if (_cursor.isNull(_cursorIndexOfAccepted)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(_cursorIndexOfAccepted);
            }
            _tmpAccepted = _tmp == null ? null : _tmp != 0;
            final Integer _tmpScore;
            if (_cursor.isNull(_cursorIndexOfScore)) {
              _tmpScore = null;
            } else {
              _tmpScore = _cursor.getInt(_cursorIndexOfScore);
            }
            final String _tmpVerdict;
            if (_cursor.isNull(_cursorIndexOfVerdict)) {
              _tmpVerdict = null;
            } else {
              _tmpVerdict = _cursor.getString(_cursorIndexOfVerdict);
            }
            final Double _tmpPayPerMile;
            if (_cursor.isNull(_cursorIndexOfPayPerMile)) {
              _tmpPayPerMile = null;
            } else {
              _tmpPayPerMile = _cursor.getDouble(_cursorIndexOfPayPerMile);
            }
            final Double _tmpVsPersonalAvg;
            if (_cursor.isNull(_cursorIndexOfVsPersonalAvg)) {
              _tmpVsPersonalAvg = null;
            } else {
              _tmpVsPersonalAvg = _cursor.getDouble(_cursorIndexOfVsPersonalAvg);
            }
            final Double _tmpDriverLat;
            if (_cursor.isNull(_cursorIndexOfDriverLat)) {
              _tmpDriverLat = null;
            } else {
              _tmpDriverLat = _cursor.getDouble(_cursorIndexOfDriverLat);
            }
            final Double _tmpDriverLon;
            if (_cursor.isNull(_cursorIndexOfDriverLon)) {
              _tmpDriverLon = null;
            } else {
              _tmpDriverLon = _cursor.getDouble(_cursorIndexOfDriverLon);
            }
            final Double _tmpPickupDistance;
            if (_cursor.isNull(_cursorIndexOfPickupDistance)) {
              _tmpPickupDistance = null;
            } else {
              _tmpPickupDistance = _cursor.getDouble(_cursorIndexOfPickupDistance);
            }
            final Double _tmpDeliveryDistance;
            if (_cursor.isNull(_cursorIndexOfDeliveryDistance)) {
              _tmpDeliveryDistance = null;
            } else {
              _tmpDeliveryDistance = _cursor.getDouble(_cursorIndexOfDeliveryDistance);
            }
            final Double _tmpTotalDistance;
            if (_cursor.isNull(_cursorIndexOfTotalDistance)) {
              _tmpTotalDistance = null;
            } else {
              _tmpTotalDistance = _cursor.getDouble(_cursorIndexOfTotalDistance);
            }
            final Double _tmpTruePayPerMile;
            if (_cursor.isNull(_cursorIndexOfTruePayPerMile)) {
              _tmpTruePayPerMile = null;
            } else {
              _tmpTruePayPerMile = _cursor.getDouble(_cursorIndexOfTruePayPerMile);
            }
            final Double _tmpVehicleCost;
            if (_cursor.isNull(_cursorIndexOfVehicleCost)) {
              _tmpVehicleCost = null;
            } else {
              _tmpVehicleCost = _cursor.getDouble(_cursorIndexOfVehicleCost);
            }
            final Double _tmpNetValue;
            if (_cursor.isNull(_cursorIndexOfNetValue)) {
              _tmpNetValue = null;
            } else {
              _tmpNetValue = _cursor.getDouble(_cursorIndexOfNetValue);
            }
            final Integer _tmpEstimatedMinutes;
            if (_cursor.isNull(_cursorIndexOfEstimatedMinutes)) {
              _tmpEstimatedMinutes = null;
            } else {
              _tmpEstimatedMinutes = _cursor.getInt(_cursorIndexOfEstimatedMinutes);
            }
            _item = new OfferCapture(_tmpId,_tmpTimestamp,_tmpPlatform,_tmpPayAmount,_tmpDistance,_tmpDistanceUnit,_tmpRestaurant,_tmpScreenshotPath,_tmpRawOcrText,_tmpAccepted,_tmpScore,_tmpVerdict,_tmpPayPerMile,_tmpVsPersonalAvg,_tmpDriverLat,_tmpDriverLon,_tmpPickupDistance,_tmpDeliveryDistance,_tmpTotalDistance,_tmpTruePayPerMile,_tmpVehicleCost,_tmpNetValue,_tmpEstimatedMinutes);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, arg0);
  }

  @Override
  public Object getRecent(final int limit, final Continuation<? super List<OfferCapture>> arg1) {
    final String _sql = "SELECT * FROM offer_captures ORDER BY timestamp DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<OfferCapture>>() {
      @Override
      @NonNull
      public List<OfferCapture> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfPayAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "payAmount");
          final int _cursorIndexOfDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "distance");
          final int _cursorIndexOfDistanceUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "distanceUnit");
          final int _cursorIndexOfRestaurant = CursorUtil.getColumnIndexOrThrow(_cursor, "restaurant");
          final int _cursorIndexOfScreenshotPath = CursorUtil.getColumnIndexOrThrow(_cursor, "screenshotPath");
          final int _cursorIndexOfRawOcrText = CursorUtil.getColumnIndexOrThrow(_cursor, "rawOcrText");
          final int _cursorIndexOfAccepted = CursorUtil.getColumnIndexOrThrow(_cursor, "accepted");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfVerdict = CursorUtil.getColumnIndexOrThrow(_cursor, "verdict");
          final int _cursorIndexOfPayPerMile = CursorUtil.getColumnIndexOrThrow(_cursor, "payPerMile");
          final int _cursorIndexOfVsPersonalAvg = CursorUtil.getColumnIndexOrThrow(_cursor, "vsPersonalAvg");
          final int _cursorIndexOfDriverLat = CursorUtil.getColumnIndexOrThrow(_cursor, "driverLat");
          final int _cursorIndexOfDriverLon = CursorUtil.getColumnIndexOrThrow(_cursor, "driverLon");
          final int _cursorIndexOfPickupDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupDistance");
          final int _cursorIndexOfDeliveryDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "deliveryDistance");
          final int _cursorIndexOfTotalDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "totalDistance");
          final int _cursorIndexOfTruePayPerMile = CursorUtil.getColumnIndexOrThrow(_cursor, "truePayPerMile");
          final int _cursorIndexOfVehicleCost = CursorUtil.getColumnIndexOrThrow(_cursor, "vehicleCost");
          final int _cursorIndexOfNetValue = CursorUtil.getColumnIndexOrThrow(_cursor, "netValue");
          final int _cursorIndexOfEstimatedMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "estimatedMinutes");
          final List<OfferCapture> _result = new ArrayList<OfferCapture>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final OfferCapture _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpPlatform;
            if (_cursor.isNull(_cursorIndexOfPlatform)) {
              _tmpPlatform = null;
            } else {
              _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            }
            final Double _tmpPayAmount;
            if (_cursor.isNull(_cursorIndexOfPayAmount)) {
              _tmpPayAmount = null;
            } else {
              _tmpPayAmount = _cursor.getDouble(_cursorIndexOfPayAmount);
            }
            final Double _tmpDistance;
            if (_cursor.isNull(_cursorIndexOfDistance)) {
              _tmpDistance = null;
            } else {
              _tmpDistance = _cursor.getDouble(_cursorIndexOfDistance);
            }
            final String _tmpDistanceUnit;
            if (_cursor.isNull(_cursorIndexOfDistanceUnit)) {
              _tmpDistanceUnit = null;
            } else {
              _tmpDistanceUnit = _cursor.getString(_cursorIndexOfDistanceUnit);
            }
            final String _tmpRestaurant;
            if (_cursor.isNull(_cursorIndexOfRestaurant)) {
              _tmpRestaurant = null;
            } else {
              _tmpRestaurant = _cursor.getString(_cursorIndexOfRestaurant);
            }
            final String _tmpScreenshotPath;
            if (_cursor.isNull(_cursorIndexOfScreenshotPath)) {
              _tmpScreenshotPath = null;
            } else {
              _tmpScreenshotPath = _cursor.getString(_cursorIndexOfScreenshotPath);
            }
            final String _tmpRawOcrText;
            if (_cursor.isNull(_cursorIndexOfRawOcrText)) {
              _tmpRawOcrText = null;
            } else {
              _tmpRawOcrText = _cursor.getString(_cursorIndexOfRawOcrText);
            }
            final Boolean _tmpAccepted;
            final Integer _tmp;
            if (_cursor.isNull(_cursorIndexOfAccepted)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(_cursorIndexOfAccepted);
            }
            _tmpAccepted = _tmp == null ? null : _tmp != 0;
            final Integer _tmpScore;
            if (_cursor.isNull(_cursorIndexOfScore)) {
              _tmpScore = null;
            } else {
              _tmpScore = _cursor.getInt(_cursorIndexOfScore);
            }
            final String _tmpVerdict;
            if (_cursor.isNull(_cursorIndexOfVerdict)) {
              _tmpVerdict = null;
            } else {
              _tmpVerdict = _cursor.getString(_cursorIndexOfVerdict);
            }
            final Double _tmpPayPerMile;
            if (_cursor.isNull(_cursorIndexOfPayPerMile)) {
              _tmpPayPerMile = null;
            } else {
              _tmpPayPerMile = _cursor.getDouble(_cursorIndexOfPayPerMile);
            }
            final Double _tmpVsPersonalAvg;
            if (_cursor.isNull(_cursorIndexOfVsPersonalAvg)) {
              _tmpVsPersonalAvg = null;
            } else {
              _tmpVsPersonalAvg = _cursor.getDouble(_cursorIndexOfVsPersonalAvg);
            }
            final Double _tmpDriverLat;
            if (_cursor.isNull(_cursorIndexOfDriverLat)) {
              _tmpDriverLat = null;
            } else {
              _tmpDriverLat = _cursor.getDouble(_cursorIndexOfDriverLat);
            }
            final Double _tmpDriverLon;
            if (_cursor.isNull(_cursorIndexOfDriverLon)) {
              _tmpDriverLon = null;
            } else {
              _tmpDriverLon = _cursor.getDouble(_cursorIndexOfDriverLon);
            }
            final Double _tmpPickupDistance;
            if (_cursor.isNull(_cursorIndexOfPickupDistance)) {
              _tmpPickupDistance = null;
            } else {
              _tmpPickupDistance = _cursor.getDouble(_cursorIndexOfPickupDistance);
            }
            final Double _tmpDeliveryDistance;
            if (_cursor.isNull(_cursorIndexOfDeliveryDistance)) {
              _tmpDeliveryDistance = null;
            } else {
              _tmpDeliveryDistance = _cursor.getDouble(_cursorIndexOfDeliveryDistance);
            }
            final Double _tmpTotalDistance;
            if (_cursor.isNull(_cursorIndexOfTotalDistance)) {
              _tmpTotalDistance = null;
            } else {
              _tmpTotalDistance = _cursor.getDouble(_cursorIndexOfTotalDistance);
            }
            final Double _tmpTruePayPerMile;
            if (_cursor.isNull(_cursorIndexOfTruePayPerMile)) {
              _tmpTruePayPerMile = null;
            } else {
              _tmpTruePayPerMile = _cursor.getDouble(_cursorIndexOfTruePayPerMile);
            }
            final Double _tmpVehicleCost;
            if (_cursor.isNull(_cursorIndexOfVehicleCost)) {
              _tmpVehicleCost = null;
            } else {
              _tmpVehicleCost = _cursor.getDouble(_cursorIndexOfVehicleCost);
            }
            final Double _tmpNetValue;
            if (_cursor.isNull(_cursorIndexOfNetValue)) {
              _tmpNetValue = null;
            } else {
              _tmpNetValue = _cursor.getDouble(_cursorIndexOfNetValue);
            }
            final Integer _tmpEstimatedMinutes;
            if (_cursor.isNull(_cursorIndexOfEstimatedMinutes)) {
              _tmpEstimatedMinutes = null;
            } else {
              _tmpEstimatedMinutes = _cursor.getInt(_cursorIndexOfEstimatedMinutes);
            }
            _item = new OfferCapture(_tmpId,_tmpTimestamp,_tmpPlatform,_tmpPayAmount,_tmpDistance,_tmpDistanceUnit,_tmpRestaurant,_tmpScreenshotPath,_tmpRawOcrText,_tmpAccepted,_tmpScore,_tmpVerdict,_tmpPayPerMile,_tmpVsPersonalAvg,_tmpDriverLat,_tmpDriverLon,_tmpPickupDistance,_tmpDeliveryDistance,_tmpTotalDistance,_tmpTruePayPerMile,_tmpVehicleCost,_tmpNetValue,_tmpEstimatedMinutes);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, arg1);
  }

  @Override
  public Object getByDateRange(final long startTime, final long endTime,
      final Continuation<? super List<OfferCapture>> arg2) {
    final String _sql = "SELECT * FROM offer_captures WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startTime);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endTime);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<OfferCapture>>() {
      @Override
      @NonNull
      public List<OfferCapture> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfPlatform = CursorUtil.getColumnIndexOrThrow(_cursor, "platform");
          final int _cursorIndexOfPayAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "payAmount");
          final int _cursorIndexOfDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "distance");
          final int _cursorIndexOfDistanceUnit = CursorUtil.getColumnIndexOrThrow(_cursor, "distanceUnit");
          final int _cursorIndexOfRestaurant = CursorUtil.getColumnIndexOrThrow(_cursor, "restaurant");
          final int _cursorIndexOfScreenshotPath = CursorUtil.getColumnIndexOrThrow(_cursor, "screenshotPath");
          final int _cursorIndexOfRawOcrText = CursorUtil.getColumnIndexOrThrow(_cursor, "rawOcrText");
          final int _cursorIndexOfAccepted = CursorUtil.getColumnIndexOrThrow(_cursor, "accepted");
          final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
          final int _cursorIndexOfVerdict = CursorUtil.getColumnIndexOrThrow(_cursor, "verdict");
          final int _cursorIndexOfPayPerMile = CursorUtil.getColumnIndexOrThrow(_cursor, "payPerMile");
          final int _cursorIndexOfVsPersonalAvg = CursorUtil.getColumnIndexOrThrow(_cursor, "vsPersonalAvg");
          final int _cursorIndexOfDriverLat = CursorUtil.getColumnIndexOrThrow(_cursor, "driverLat");
          final int _cursorIndexOfDriverLon = CursorUtil.getColumnIndexOrThrow(_cursor, "driverLon");
          final int _cursorIndexOfPickupDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "pickupDistance");
          final int _cursorIndexOfDeliveryDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "deliveryDistance");
          final int _cursorIndexOfTotalDistance = CursorUtil.getColumnIndexOrThrow(_cursor, "totalDistance");
          final int _cursorIndexOfTruePayPerMile = CursorUtil.getColumnIndexOrThrow(_cursor, "truePayPerMile");
          final int _cursorIndexOfVehicleCost = CursorUtil.getColumnIndexOrThrow(_cursor, "vehicleCost");
          final int _cursorIndexOfNetValue = CursorUtil.getColumnIndexOrThrow(_cursor, "netValue");
          final int _cursorIndexOfEstimatedMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "estimatedMinutes");
          final List<OfferCapture> _result = new ArrayList<OfferCapture>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final OfferCapture _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpPlatform;
            if (_cursor.isNull(_cursorIndexOfPlatform)) {
              _tmpPlatform = null;
            } else {
              _tmpPlatform = _cursor.getString(_cursorIndexOfPlatform);
            }
            final Double _tmpPayAmount;
            if (_cursor.isNull(_cursorIndexOfPayAmount)) {
              _tmpPayAmount = null;
            } else {
              _tmpPayAmount = _cursor.getDouble(_cursorIndexOfPayAmount);
            }
            final Double _tmpDistance;
            if (_cursor.isNull(_cursorIndexOfDistance)) {
              _tmpDistance = null;
            } else {
              _tmpDistance = _cursor.getDouble(_cursorIndexOfDistance);
            }
            final String _tmpDistanceUnit;
            if (_cursor.isNull(_cursorIndexOfDistanceUnit)) {
              _tmpDistanceUnit = null;
            } else {
              _tmpDistanceUnit = _cursor.getString(_cursorIndexOfDistanceUnit);
            }
            final String _tmpRestaurant;
            if (_cursor.isNull(_cursorIndexOfRestaurant)) {
              _tmpRestaurant = null;
            } else {
              _tmpRestaurant = _cursor.getString(_cursorIndexOfRestaurant);
            }
            final String _tmpScreenshotPath;
            if (_cursor.isNull(_cursorIndexOfScreenshotPath)) {
              _tmpScreenshotPath = null;
            } else {
              _tmpScreenshotPath = _cursor.getString(_cursorIndexOfScreenshotPath);
            }
            final String _tmpRawOcrText;
            if (_cursor.isNull(_cursorIndexOfRawOcrText)) {
              _tmpRawOcrText = null;
            } else {
              _tmpRawOcrText = _cursor.getString(_cursorIndexOfRawOcrText);
            }
            final Boolean _tmpAccepted;
            final Integer _tmp;
            if (_cursor.isNull(_cursorIndexOfAccepted)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(_cursorIndexOfAccepted);
            }
            _tmpAccepted = _tmp == null ? null : _tmp != 0;
            final Integer _tmpScore;
            if (_cursor.isNull(_cursorIndexOfScore)) {
              _tmpScore = null;
            } else {
              _tmpScore = _cursor.getInt(_cursorIndexOfScore);
            }
            final String _tmpVerdict;
            if (_cursor.isNull(_cursorIndexOfVerdict)) {
              _tmpVerdict = null;
            } else {
              _tmpVerdict = _cursor.getString(_cursorIndexOfVerdict);
            }
            final Double _tmpPayPerMile;
            if (_cursor.isNull(_cursorIndexOfPayPerMile)) {
              _tmpPayPerMile = null;
            } else {
              _tmpPayPerMile = _cursor.getDouble(_cursorIndexOfPayPerMile);
            }
            final Double _tmpVsPersonalAvg;
            if (_cursor.isNull(_cursorIndexOfVsPersonalAvg)) {
              _tmpVsPersonalAvg = null;
            } else {
              _tmpVsPersonalAvg = _cursor.getDouble(_cursorIndexOfVsPersonalAvg);
            }
            final Double _tmpDriverLat;
            if (_cursor.isNull(_cursorIndexOfDriverLat)) {
              _tmpDriverLat = null;
            } else {
              _tmpDriverLat = _cursor.getDouble(_cursorIndexOfDriverLat);
            }
            final Double _tmpDriverLon;
            if (_cursor.isNull(_cursorIndexOfDriverLon)) {
              _tmpDriverLon = null;
            } else {
              _tmpDriverLon = _cursor.getDouble(_cursorIndexOfDriverLon);
            }
            final Double _tmpPickupDistance;
            if (_cursor.isNull(_cursorIndexOfPickupDistance)) {
              _tmpPickupDistance = null;
            } else {
              _tmpPickupDistance = _cursor.getDouble(_cursorIndexOfPickupDistance);
            }
            final Double _tmpDeliveryDistance;
            if (_cursor.isNull(_cursorIndexOfDeliveryDistance)) {
              _tmpDeliveryDistance = null;
            } else {
              _tmpDeliveryDistance = _cursor.getDouble(_cursorIndexOfDeliveryDistance);
            }
            final Double _tmpTotalDistance;
            if (_cursor.isNull(_cursorIndexOfTotalDistance)) {
              _tmpTotalDistance = null;
            } else {
              _tmpTotalDistance = _cursor.getDouble(_cursorIndexOfTotalDistance);
            }
            final Double _tmpTruePayPerMile;
            if (_cursor.isNull(_cursorIndexOfTruePayPerMile)) {
              _tmpTruePayPerMile = null;
            } else {
              _tmpTruePayPerMile = _cursor.getDouble(_cursorIndexOfTruePayPerMile);
            }
            final Double _tmpVehicleCost;
            if (_cursor.isNull(_cursorIndexOfVehicleCost)) {
              _tmpVehicleCost = null;
            } else {
              _tmpVehicleCost = _cursor.getDouble(_cursorIndexOfVehicleCost);
            }
            final Double _tmpNetValue;
            if (_cursor.isNull(_cursorIndexOfNetValue)) {
              _tmpNetValue = null;
            } else {
              _tmpNetValue = _cursor.getDouble(_cursorIndexOfNetValue);
            }
            final Integer _tmpEstimatedMinutes;
            if (_cursor.isNull(_cursorIndexOfEstimatedMinutes)) {
              _tmpEstimatedMinutes = null;
            } else {
              _tmpEstimatedMinutes = _cursor.getInt(_cursorIndexOfEstimatedMinutes);
            }
            _item = new OfferCapture(_tmpId,_tmpTimestamp,_tmpPlatform,_tmpPayAmount,_tmpDistance,_tmpDistanceUnit,_tmpRestaurant,_tmpScreenshotPath,_tmpRawOcrText,_tmpAccepted,_tmpScore,_tmpVerdict,_tmpPayPerMile,_tmpVsPersonalAvg,_tmpDriverLat,_tmpDriverLon,_tmpPickupDistance,_tmpDeliveryDistance,_tmpTotalDistance,_tmpTruePayPerMile,_tmpVehicleCost,_tmpNetValue,_tmpEstimatedMinutes);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, arg2);
  }

  @Override
  public Object getCount(final Continuation<? super Integer> arg0) {
    final String _sql = "SELECT COUNT(*) FROM offer_captures";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, arg0);
  }

  @Override
  public Object getAveragePay(final Continuation<? super Double> arg0) {
    final String _sql = "SELECT AVG(payAmount) FROM offer_captures WHERE payAmount IS NOT NULL";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Double>() {
      @Override
      @Nullable
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if (_cursor.moveToFirst()) {
            final Double _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getDouble(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, arg0);
  }

  @Override
  public Object getAveragePayPerMile(final Continuation<? super Double> arg0) {
    final String _sql = "SELECT AVG(CASE WHEN distance > 0 THEN payAmount / distance ELSE NULL END) FROM offer_captures WHERE payAmount IS NOT NULL AND distance IS NOT NULL AND distance > 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Double>() {
      @Override
      @Nullable
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if (_cursor.moveToFirst()) {
            final Double _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getDouble(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, arg0);
  }

  @Override
  public Object getAverageScore(final Continuation<? super Double> arg0) {
    final String _sql = "SELECT AVG(score) FROM (SELECT score FROM offer_captures WHERE score IS NOT NULL ORDER BY timestamp DESC LIMIT 30)";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Double>() {
      @Override
      @Nullable
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if (_cursor.moveToFirst()) {
            final Double _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getDouble(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, arg0);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
