package com.augusteenterprise.giglens.service;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000f\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\u0018\u0000 (2\u00020\u0001:\u0001(B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0015\u001a\u00020\u0016H\u0002J\b\u0010\u0017\u001a\u00020\u0018H\u0002J\u0014\u0010\u0019\u001a\u0004\u0018\u00010\u001a2\b\u0010\u001b\u001a\u0004\u0018\u00010\u001cH\u0016J\b\u0010\u001d\u001a\u00020\u0018H\u0016J\b\u0010\u001e\u001a\u00020\u0018H\u0016J\"\u0010\u001f\u001a\u00020\n2\b\u0010\u001b\u001a\u0004\u0018\u00010\u001c2\u0006\u0010 \u001a\u00020\n2\u0006\u0010!\u001a\u00020\nH\u0016J\u001a\u0010\"\u001a\u00020\u00182\u0006\u0010#\u001a\u00020$2\b\u0010%\u001a\u0004\u0018\u00010&H\u0002J\u0012\u0010\'\u001a\u0004\u0018\u00010&2\u0006\u0010#\u001a\u00020$H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\nX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0013\u001a\u0004\u0018\u00010\u0014X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006)"}, d2 = {"Lcom/augusteenterprise/giglens/service/ScreenCaptureService;", "Landroid/app/Service;", "()V", "captureReceiver", "Landroid/content/BroadcastReceiver;", "imageReader", "Landroid/media/ImageReader;", "mediaProjection", "Landroid/media/projection/MediaProjection;", "screenDensity", "", "screenHeight", "screenWidth", "serviceJob", "Lkotlinx/coroutines/CompletableJob;", "serviceScope", "Lkotlinx/coroutines/CoroutineScope;", "textRecognizer", "Lcom/google/mlkit/vision/text/TextRecognizer;", "virtualDisplay", "Landroid/hardware/display/VirtualDisplay;", "buildNotification", "Landroid/app/Notification;", "captureScreen", "", "onBind", "Landroid/os/IBinder;", "intent", "Landroid/content/Intent;", "onCreate", "onDestroy", "onStartCommand", "flags", "startId", "runOcr", "bitmap", "Landroid/graphics/Bitmap;", "screenshotPath", "", "saveScreenshot", "Companion", "app_debug"})
public final class ScreenCaptureService extends android.app.Service {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "ScreenCapture";
    private static final int NOTIFICATION_ID = 1001;
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_RESULT_CODE = "result_code";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String EXTRA_RESULT_DATA = "result_data";
    private static boolean isRunning = false;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CompletableJob serviceJob = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.CoroutineScope serviceScope = null;
    @org.jetbrains.annotations.Nullable()
    private android.media.projection.MediaProjection mediaProjection;
    @org.jetbrains.annotations.Nullable()
    private android.hardware.display.VirtualDisplay virtualDisplay;
    @org.jetbrains.annotations.Nullable()
    private android.media.ImageReader imageReader;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private int screenDensity = 0;
    @org.jetbrains.annotations.NotNull()
    private final com.google.mlkit.vision.text.TextRecognizer textRecognizer = null;
    @org.jetbrains.annotations.NotNull()
    private final android.content.BroadcastReceiver captureReceiver = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.augusteenterprise.giglens.service.ScreenCaptureService.Companion Companion = null;
    
    public ScreenCaptureService() {
        super();
    }
    
    @java.lang.Override()
    public void onCreate() {
    }
    
    @java.lang.Override()
    public int onStartCommand(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent, int flags, int startId) {
        return 0;
    }
    
    /**
     * Captures the current screen, saves the screenshot, and runs OCR.
     */
    private final void captureScreen() {
    }
    
    /**
     * Saves bitmap to app-specific storage.
     */
    private final java.lang.String saveScreenshot(android.graphics.Bitmap bitmap) {
        return null;
    }
    
    /**
     * Runs ML Kit OCR on the bitmap and stores parsed results in Room DB.
     */
    private final void runOcr(android.graphics.Bitmap bitmap, java.lang.String screenshotPath) {
    }
    
    private final android.app.Notification buildNotification() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public android.os.IBinder onBind(@org.jetbrains.annotations.Nullable()
    android.content.Intent intent) {
        return null;
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u001e\u0010\u000b\u001a\u00020\n2\u0006\u0010\t\u001a\u00020\n@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\r"}, d2 = {"Lcom/augusteenterprise/giglens/service/ScreenCaptureService$Companion;", "", "()V", "EXTRA_RESULT_CODE", "", "EXTRA_RESULT_DATA", "NOTIFICATION_ID", "", "TAG", "<set-?>", "", "isRunning", "()Z", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        public final boolean isRunning() {
            return false;
        }
    }
}