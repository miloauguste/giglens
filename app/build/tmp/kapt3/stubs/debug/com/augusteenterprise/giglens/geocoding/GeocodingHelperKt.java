package com.augusteenterprise.giglens.geocoding;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000\u0018\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\"\u000e\u0010\u0000\u001a\u00020\u0001X\u0082T\u00a2\u0006\u0002\n\u0000\"\u000e\u0010\u0002\u001a\u00020\u0003X\u0082T\u00a2\u0006\u0002\n\u0000\"\u000e\u0010\u0004\u001a\u00020\u0001X\u0082T\u00a2\u0006\u0002\n\u0000\"\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000\"\u000e\u0010\u0007\u001a\u00020\u0001X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\b"}, d2 = {"NOMINATIM_URL", "", "ROAD_FACTOR", "", "TAG", "TIMEOUT_MS", "", "USER_AGENT", "app_debug"})
public final class GeocodingHelperKt {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "GeocodingHelper";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String USER_AGENT = "GigLens/1.0 (android; giglens@augusteenterprise.com)";
    private static final int TIMEOUT_MS = 5000;
    private static final double ROAD_FACTOR = 1.3;
}