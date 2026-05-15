package com.augusteenterprise.giglens;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0002\b\u0003\u0018\u0000 \u000b2\u00020\u0001:\u0001\u000bB\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\b\u001a\u00020\tH\u0002J\b\u0010\n\u001a\u00020\tH\u0016R\u001e\u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0003\u001a\u00020\u0004@BX\u0086.\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007\u00a8\u0006\f"}, d2 = {"Lcom/augusteenterprise/giglens/GigLensApp;", "Landroid/app/Application;", "()V", "<set-?>", "Lcom/augusteenterprise/giglens/data/OfferDatabase;", "database", "getDatabase", "()Lcom/augusteenterprise/giglens/data/OfferDatabase;", "createNotificationChannel", "", "onCreate", "Companion", "app_debug"})
public final class GigLensApp extends android.app.Application {
    private com.augusteenterprise.giglens.data.OfferDatabase database;
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String NOTIFICATION_CHANNEL_ID = "giglens_capture";
    private static com.augusteenterprise.giglens.GigLensApp instance;
    @org.jetbrains.annotations.NotNull()
    public static final com.augusteenterprise.giglens.GigLensApp.Companion Companion = null;
    
    public GigLensApp() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.augusteenterprise.giglens.data.OfferDatabase getDatabase() {
        return null;
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    private final void createNotificationChannel() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u001e\u0010\u0007\u001a\u00020\u00062\u0006\u0010\u0005\u001a\u00020\u0006@BX\u0086.\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u00a8\u0006\n"}, d2 = {"Lcom/augusteenterprise/giglens/GigLensApp$Companion;", "", "()V", "NOTIFICATION_CHANNEL_ID", "", "<set-?>", "Lcom/augusteenterprise/giglens/GigLensApp;", "instance", "getInstance", "()Lcom/augusteenterprise/giglens/GigLensApp;", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.augusteenterprise.giglens.GigLensApp getInstance() {
            return null;
        }
    }
}