package com.augusteenterprise.giglens.geocoding;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0006\n\u0002\b\b\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J.\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u00062\b\u0010\u0007\u001a\u0004\u0018\u00010\u00062\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u0006H\u0086@\u00a2\u0006\u0002\u0010\tJ$\u0010\n\u001a\u0004\u0018\u00010\u000b2\u0006\u0010\f\u001a\u00020\u00062\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u0006H\u0086@\u00a2\u0006\u0002\u0010\rJ \u0010\u000e\u001a\u0004\u0018\u00010\u00062\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0010H\u0086@\u00a2\u0006\u0002\u0010\u0012J&\u0010\u0013\u001a\u00020\u00102\u0006\u0010\u0014\u001a\u00020\u00102\u0006\u0010\u0015\u001a\u00020\u00102\u0006\u0010\u0016\u001a\u00020\u00102\u0006\u0010\u0017\u001a\u00020\u0010\u00a8\u0006\u0018"}, d2 = {"Lcom/augusteenterprise/giglens/geocoding/GeocodingHelper;", "", "()V", "estimateDeliveryDistance", "Lcom/augusteenterprise/giglens/geocoding/DistanceEstimate;", "pickupStreet", "", "dropoffStreet", "regionHint", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "resolveAddress", "Lcom/augusteenterprise/giglens/geocoding/GeoPoint;", "street", "(Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "reverseGeocode", "lat", "", "lon", "(DDLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "straightLineMiles", "lat1", "lon1", "lat2", "lon2", "app_debug"})
public final class GeocodingHelper {
    @org.jetbrains.annotations.NotNull()
    public static final com.augusteenterprise.giglens.geocoding.GeocodingHelper INSTANCE = null;
    
    private GeocodingHelper() {
        super();
    }
    
    /**
     * Geocodes pickup and dropoff streets and returns estimated road distance.
     * Returns null distances if geocoding fails — caller must handle gracefully.
     *
     * @param pickupStreet   Street name near restaurant (from OCR)
     * @param dropoffStreet  Street name near customer (from OCR)
     * @param regionHint     City/region hint to improve accuracy (from driver GPS)
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object estimateDeliveryDistance(@org.jetbrains.annotations.Nullable()
    java.lang.String pickupStreet, @org.jetbrains.annotations.Nullable()
    java.lang.String dropoffStreet, @org.jetbrains.annotations.Nullable()
    java.lang.String regionHint, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.augusteenterprise.giglens.geocoding.DistanceEstimate> $completion) {
        return null;
    }
    
    /**
     * Calls Nominatim to resolve a street name to lat/lon.
     * Returns null if not found or network error.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object resolveAddress(@org.jetbrains.annotations.NotNull()
    java.lang.String street, @org.jetbrains.annotations.Nullable()
    java.lang.String regionHint, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.augusteenterprise.giglens.geocoding.GeoPoint> $completion) {
        return null;
    }
    
    /**
     * Reverse geocodes a lat/lon to extract state/region for use as geocoding hint.
     * Returns format like "New Jersey, USA" or null if unavailable.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object reverseGeocode(double lat, double lon, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.String> $completion) {
        return null;
    }
    
    /**
     * Haversine straight-line distance in miles between two lat/lon points.
     */
    public final double straightLineMiles(double lat1, double lon1, double lat2, double lon2) {
        return 0.0;
    }
}