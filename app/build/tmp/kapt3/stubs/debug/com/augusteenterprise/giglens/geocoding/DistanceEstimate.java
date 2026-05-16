package com.augusteenterprise.giglens.geocoding;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00002\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0012\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B5\u0012\b\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\u0004\u001a\u0004\u0018\u00010\u0003\u0012\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u0012\b\u0010\u0007\u001a\u0004\u0018\u00010\u0006\u0012\u0006\u0010\b\u001a\u00020\t\u00a2\u0006\u0002\u0010\nJ\u000b\u0010\u0014\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u000b\u0010\u0015\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0010\u0010\u0016\u001a\u0004\u0018\u00010\u0006H\u00c6\u0003\u00a2\u0006\u0002\u0010\u000eJ\u0010\u0010\u0017\u001a\u0004\u0018\u00010\u0006H\u00c6\u0003\u00a2\u0006\u0002\u0010\u000eJ\t\u0010\u0018\u001a\u00020\tH\u00c6\u0003JH\u0010\u0019\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u00032\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u00062\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\u00062\b\b\u0002\u0010\b\u001a\u00020\tH\u00c6\u0001\u00a2\u0006\u0002\u0010\u001aJ\u0013\u0010\u001b\u001a\u00020\u001c2\b\u0010\u001d\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u001e\u001a\u00020\u001fH\u00d6\u0001J\t\u0010 \u001a\u00020\tH\u00d6\u0001R\u0013\u0010\u0004\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0015\u0010\u0007\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\n\n\u0002\u0010\u000f\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u0013\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\fR\u0015\u0010\u0005\u001a\u0004\u0018\u00010\u0006\u00a2\u0006\n\n\u0002\u0010\u000f\u001a\u0004\b\u0013\u0010\u000e\u00a8\u0006!"}, d2 = {"Lcom/augusteenterprise/giglens/geocoding/DistanceEstimate;", "", "pickupPoint", "Lcom/augusteenterprise/giglens/geocoding/GeoPoint;", "dropoffPoint", "straightLineMiles", "", "estimatedRoadMiles", "method", "", "(Lcom/augusteenterprise/giglens/geocoding/GeoPoint;Lcom/augusteenterprise/giglens/geocoding/GeoPoint;Ljava/lang/Double;Ljava/lang/Double;Ljava/lang/String;)V", "getDropoffPoint", "()Lcom/augusteenterprise/giglens/geocoding/GeoPoint;", "getEstimatedRoadMiles", "()Ljava/lang/Double;", "Ljava/lang/Double;", "getMethod", "()Ljava/lang/String;", "getPickupPoint", "getStraightLineMiles", "component1", "component2", "component3", "component4", "component5", "copy", "(Lcom/augusteenterprise/giglens/geocoding/GeoPoint;Lcom/augusteenterprise/giglens/geocoding/GeoPoint;Ljava/lang/Double;Ljava/lang/Double;Ljava/lang/String;)Lcom/augusteenterprise/giglens/geocoding/DistanceEstimate;", "equals", "", "other", "hashCode", "", "toString", "app_debug"})
public final class DistanceEstimate {
    @org.jetbrains.annotations.Nullable()
    private final com.augusteenterprise.giglens.geocoding.GeoPoint pickupPoint = null;
    @org.jetbrains.annotations.Nullable()
    private final com.augusteenterprise.giglens.geocoding.GeoPoint dropoffPoint = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Double straightLineMiles = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Double estimatedRoadMiles = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String method = null;
    
    public DistanceEstimate(@org.jetbrains.annotations.Nullable()
    com.augusteenterprise.giglens.geocoding.GeoPoint pickupPoint, @org.jetbrains.annotations.Nullable()
    com.augusteenterprise.giglens.geocoding.GeoPoint dropoffPoint, @org.jetbrains.annotations.Nullable()
    java.lang.Double straightLineMiles, @org.jetbrains.annotations.Nullable()
    java.lang.Double estimatedRoadMiles, @org.jetbrains.annotations.NotNull()
    java.lang.String method) {
        super();
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.augusteenterprise.giglens.geocoding.GeoPoint getPickupPoint() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.augusteenterprise.giglens.geocoding.GeoPoint getDropoffPoint() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Double getStraightLineMiles() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Double getEstimatedRoadMiles() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String getMethod() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.augusteenterprise.giglens.geocoding.GeoPoint component1() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final com.augusteenterprise.giglens.geocoding.GeoPoint component2() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Double component3() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Double component4() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.lang.String component5() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.augusteenterprise.giglens.geocoding.DistanceEstimate copy(@org.jetbrains.annotations.Nullable()
    com.augusteenterprise.giglens.geocoding.GeoPoint pickupPoint, @org.jetbrains.annotations.Nullable()
    com.augusteenterprise.giglens.geocoding.GeoPoint dropoffPoint, @org.jetbrains.annotations.Nullable()
    java.lang.Double straightLineMiles, @org.jetbrains.annotations.Nullable()
    java.lang.Double estimatedRoadMiles, @org.jetbrains.annotations.NotNull()
    java.lang.String method) {
        return null;
    }
    
    @java.lang.Override()
    public boolean equals(@org.jetbrains.annotations.Nullable()
    java.lang.Object other) {
        return false;
    }
    
    @java.lang.Override()
    public int hashCode() {
        return 0;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public java.lang.String toString() {
        return null;
    }
}