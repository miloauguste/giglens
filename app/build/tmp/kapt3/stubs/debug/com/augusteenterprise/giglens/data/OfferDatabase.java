package com.augusteenterprise.giglens.data;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\'\u0018\u0000 \t2\u00020\u0001:\u0001\tB\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H&J\b\u0010\u0005\u001a\u00020\u0006H&J\b\u0010\u0007\u001a\u00020\bH&\u00a8\u0006\n"}, d2 = {"Lcom/augusteenterprise/giglens/data/OfferDatabase;", "Landroidx/room/RoomDatabase;", "()V", "appConfigDao", "Lcom/augusteenterprise/giglens/data/AppConfigDao;", "offerCaptureDao", "Lcom/augusteenterprise/giglens/data/OfferCaptureDao;", "scorerConfigDao", "Lcom/augusteenterprise/giglens/data/ScorerConfigDao;", "Companion", "app_debug"})
@androidx.room.Database(entities = {com.augusteenterprise.giglens.data.OfferCapture.class, com.augusteenterprise.giglens.data.ScorerConfig.class, com.augusteenterprise.giglens.data.AppConfig.class}, version = 4, exportSchema = false)
public abstract class OfferDatabase extends androidx.room.RoomDatabase {
    @kotlin.jvm.Volatile()
    @org.jetbrains.annotations.Nullable()
    private static volatile com.augusteenterprise.giglens.data.OfferDatabase INSTANCE;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.room.migration.Migration MIGRATION_1_2 = null;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.room.migration.Migration MIGRATION_2_3 = null;
    @org.jetbrains.annotations.NotNull()
    private static final androidx.room.migration.Migration MIGRATION_3_4 = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.augusteenterprise.giglens.data.OfferDatabase.Companion Companion = null;
    
    public OfferDatabase() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.augusteenterprise.giglens.data.OfferCaptureDao offerCaptureDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.augusteenterprise.giglens.data.ScorerConfigDao scorerConfigDao();
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.augusteenterprise.giglens.data.AppConfigDao appConfigDao();
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\r\u001a\u00020\u00042\u0006\u0010\u000e\u001a\u00020\u000fR\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\t\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\bR\u0011\u0010\u000b\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\b\u00a8\u0006\u0010"}, d2 = {"Lcom/augusteenterprise/giglens/data/OfferDatabase$Companion;", "", "()V", "INSTANCE", "Lcom/augusteenterprise/giglens/data/OfferDatabase;", "MIGRATION_1_2", "Landroidx/room/migration/Migration;", "getMIGRATION_1_2", "()Landroidx/room/migration/Migration;", "MIGRATION_2_3", "getMIGRATION_2_3", "MIGRATION_3_4", "getMIGRATION_3_4", "getInstance", "context", "Landroid/content/Context;", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final androidx.room.migration.Migration getMIGRATION_1_2() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final androidx.room.migration.Migration getMIGRATION_2_3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final androidx.room.migration.Migration getMIGRATION_3_4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.augusteenterprise.giglens.data.OfferDatabase getInstance(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
            return null;
        }
    }
}