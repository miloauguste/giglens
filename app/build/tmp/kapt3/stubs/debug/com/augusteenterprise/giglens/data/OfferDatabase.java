package com.augusteenterprise.giglens.data;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\'\u0018\u0000 \u00072\u00020\u0001:\u0001\u0007B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H&J\b\u0010\u0005\u001a\u00020\u0006H&\u00a8\u0006\b"}, d2 = {"Lcom/augusteenterprise/giglens/data/OfferDatabase;", "Landroidx/room/RoomDatabase;", "()V", "offerCaptureDao", "Lcom/augusteenterprise/giglens/data/OfferCaptureDao;", "scorerConfigDao", "Lcom/augusteenterprise/giglens/data/ScorerConfigDao;", "Companion", "app_debug"})
@androidx.room.Database(entities = {com.augusteenterprise.giglens.data.OfferCapture.class, com.augusteenterprise.giglens.data.ScorerConfig.class}, version = 2, exportSchema = false)
public abstract class OfferDatabase extends androidx.room.RoomDatabase {
    @kotlin.jvm.Volatile()
    @org.jetbrains.annotations.Nullable()
    private static volatile com.augusteenterprise.giglens.data.OfferDatabase INSTANCE;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.room.migration.Migration MIGRATION_1_2 = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.augusteenterprise.giglens.data.OfferDatabase.Companion Companion = null;
    
    public OfferDatabase() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.augusteenterprise.giglens.data.OfferCaptureDao offerCaptureDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.augusteenterprise.giglens.data.ScorerConfigDao scorerConfigDao();
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\t\u001a\u00020\u00042\u0006\u0010\n\u001a\u00020\u000bR\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\b\u00a8\u0006\f"}, d2 = {"Lcom/augusteenterprise/giglens/data/OfferDatabase$Companion;", "", "()V", "INSTANCE", "Lcom/augusteenterprise/giglens/data/OfferDatabase;", "MIGRATION_1_2", "Landroidx/room/migration/Migration;", "getMIGRATION_1_2", "()Landroidx/room/migration/Migration;", "getInstance", "context", "Landroid/content/Context;", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final androidx.room.migration.Migration getMIGRATION_1_2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.augusteenterprise.giglens.data.OfferDatabase getInstance(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
            return null;
        }
    }
}