package com.augusteenterprise.giglens.service;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u0000 \u00112\u00020\u0001:\u0001\u0011B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bH\u0002J\u0012\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u0016J\b\u0010\r\u001a\u00020\nH\u0016J\b\u0010\u000e\u001a\u00020\nH\u0016J\b\u0010\u000f\u001a\u00020\nH\u0014J\b\u0010\u0010\u001a\u00020\nH\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0012"}, d2 = {"Lcom/augusteenterprise/giglens/service/OfferDetectorService;", "Landroid/accessibilityservice/AccessibilityService;", "()V", "lastCaptureTime", "", "looksLikeOfferScreen", "", "root", "Landroid/view/accessibility/AccessibilityNodeInfo;", "onAccessibilityEvent", "", "event", "Landroid/view/accessibility/AccessibilityEvent;", "onDestroy", "onInterrupt", "onServiceConnected", "signalCapture", "Companion", "app_debug"})
public final class OfferDetectorService extends android.accessibilityservice.AccessibilityService {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "OfferDetector";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_OFFER_DETECTED = "com.augusteenterprise.giglens.OFFER_DETECTED";
    private static final long CAPTURE_COOLDOWN_MS = 3000L;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String DOORDASH_PACKAGE = "com.dd.doordash";
    private static boolean isRunning = false;
    private long lastCaptureTime = 0L;
    @org.jetbrains.annotations.NotNull()
    public static final com.augusteenterprise.giglens.service.OfferDetectorService.Companion Companion = null;
    
    public OfferDetectorService() {
        super();
    }
    
    @java.lang.Override()
    protected void onServiceConnected() {
    }
    
    @java.lang.Override()
    public void onAccessibilityEvent(@org.jetbrains.annotations.Nullable()
    android.view.accessibility.AccessibilityEvent event) {
    }
    
    /**
     * Walks the accessibility node tree and checks for offer-related keywords.
     * Returns true if enough signals are found to indicate a delivery offer.
     */
    private final boolean looksLikeOfferScreen(android.view.accessibility.AccessibilityNodeInfo root) {
        return false;
    }
    
    /**
     * Sends a broadcast to ScreenCaptureService to trigger a screenshot.
     */
    private final void signalCapture() {
    }
    
    @java.lang.Override()
    public void onInterrupt() {
    }
    
    @java.lang.Override()
    public void onDestroy() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\t\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u001e\u0010\u000b\u001a\u00020\n2\u0006\u0010\t\u001a\u00020\n@BX\u0086\u000e\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\f\u00a8\u0006\r"}, d2 = {"Lcom/augusteenterprise/giglens/service/OfferDetectorService$Companion;", "", "()V", "ACTION_OFFER_DETECTED", "", "CAPTURE_COOLDOWN_MS", "", "DOORDASH_PACKAGE", "TAG", "<set-?>", "", "isRunning", "()Z", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        public final boolean isRunning() {
            return false;
        }
    }
}