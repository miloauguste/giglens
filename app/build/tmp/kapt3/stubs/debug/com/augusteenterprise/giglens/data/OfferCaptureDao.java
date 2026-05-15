package com.augusteenterprise.giglens.data;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0006\n\u0002\u0010\b\n\u0002\b\u0007\bg\u0018\u00002\u00020\u0001J\u000e\u0010\u0002\u001a\u00020\u0003H\u00a7@\u00a2\u0006\u0002\u0010\u0004J\u0016\u0010\u0005\u001a\u00020\u00032\u0006\u0010\u0006\u001a\u00020\u0007H\u00a7@\u00a2\u0006\u0002\u0010\bJ\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nH\u00a7@\u00a2\u0006\u0002\u0010\u0004J\u0010\u0010\f\u001a\u0004\u0018\u00010\rH\u00a7@\u00a2\u0006\u0002\u0010\u0004J\u0010\u0010\u000e\u001a\u0004\u0018\u00010\rH\u00a7@\u00a2\u0006\u0002\u0010\u0004J$\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u000b0\n2\u0006\u0010\u0010\u001a\u00020\u00072\u0006\u0010\u0011\u001a\u00020\u0007H\u00a7@\u00a2\u0006\u0002\u0010\u0012J\u000e\u0010\u0013\u001a\u00020\u0014H\u00a7@\u00a2\u0006\u0002\u0010\u0004J\u001e\u0010\u0015\u001a\b\u0012\u0004\u0012\u00020\u000b0\n2\b\b\u0002\u0010\u0016\u001a\u00020\u0014H\u00a7@\u00a2\u0006\u0002\u0010\u0017J\u0016\u0010\u0018\u001a\u00020\u00072\u0006\u0010\u0019\u001a\u00020\u000bH\u00a7@\u00a2\u0006\u0002\u0010\u001a\u00a8\u0006\u001b"}, d2 = {"Lcom/augusteenterprise/giglens/data/OfferCaptureDao;", "", "deleteAll", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "deleteById", "id", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getAll", "", "Lcom/augusteenterprise/giglens/data/OfferCapture;", "getAveragePay", "", "getAveragePayPerMile", "getByDateRange", "startTime", "endTime", "(JJLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getCount", "", "getRecent", "limit", "(ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "insert", "capture", "(Lcom/augusteenterprise/giglens/data/OfferCapture;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
@androidx.room.Dao()
public abstract interface OfferCaptureDao {
    
    @androidx.room.Insert()
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object insert(@org.jetbrains.annotations.NotNull()
    com.augusteenterprise.giglens.data.OfferCapture capture, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Long> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM offer_captures ORDER BY timestamp DESC")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getAll(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.augusteenterprise.giglens.data.OfferCapture>> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM offer_captures ORDER BY timestamp DESC LIMIT :limit")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getRecent(int limit, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.augusteenterprise.giglens.data.OfferCapture>> $completion);
    
    @androidx.room.Query(value = "SELECT * FROM offer_captures WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getByDateRange(long startTime, long endTime, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.augusteenterprise.giglens.data.OfferCapture>> $completion);
    
    @androidx.room.Query(value = "SELECT COUNT(*) FROM offer_captures")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getCount(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Integer> $completion);
    
    @androidx.room.Query(value = "SELECT AVG(payAmount) FROM offer_captures WHERE payAmount IS NOT NULL")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getAveragePay(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Double> $completion);
    
    @androidx.room.Query(value = "SELECT AVG(CASE WHEN distance > 0 THEN payAmount / distance ELSE NULL END) FROM offer_captures WHERE payAmount IS NOT NULL AND distance IS NOT NULL AND distance > 0")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object getAveragePayPerMile(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Double> $completion);
    
    @androidx.room.Query(value = "DELETE FROM offer_captures WHERE id = :id")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object deleteById(long id, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Query(value = "DELETE FROM offer_captures")
    @org.jetbrains.annotations.Nullable()
    public abstract java.lang.Object deleteAll(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 3, xi = 48)
    public static final class DefaultImpls {
    }
}