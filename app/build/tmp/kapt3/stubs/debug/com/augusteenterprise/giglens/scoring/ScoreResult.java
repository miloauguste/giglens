package com.augusteenterprise.giglens.scoring;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0002\b\b\n\u0002\u0010\u000b\n\u0002\b#\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001Be\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\b\u0010\b\u001a\u0004\u0018\u00010\u0007\u0012\b\u0010\t\u001a\u0004\u0018\u00010\u0007\u0012\b\u0010\n\u001a\u0004\u0018\u00010\u0007\u0012\u0006\u0010\u000b\u001a\u00020\u0007\u0012\u0006\u0010\f\u001a\u00020\u0007\u0012\u0006\u0010\r\u001a\u00020\u0007\u0012\b\u0010\u000e\u001a\u0004\u0018\u00010\u0007\u0012\u0006\u0010\u000f\u001a\u00020\u0010\u00a2\u0006\u0002\u0010\u0011J\t\u0010#\u001a\u00020\u0003H\u00c6\u0003J\u0010\u0010$\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0019J\t\u0010%\u001a\u00020\u0010H\u00c6\u0003J\t\u0010&\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\'\u001a\u00020\u0007H\u00c6\u0003J\u0010\u0010(\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0019J\u0010\u0010)\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0019J\u0010\u0010*\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0019J\t\u0010+\u001a\u00020\u0007H\u00c6\u0003J\t\u0010,\u001a\u00020\u0007H\u00c6\u0003J\t\u0010-\u001a\u00020\u0007H\u00c6\u0003J\u0084\u0001\u0010.\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\t\u001a\u0004\u0018\u00010\u00072\n\b\u0002\u0010\n\u001a\u0004\u0018\u00010\u00072\b\b\u0002\u0010\u000b\u001a\u00020\u00072\b\b\u0002\u0010\f\u001a\u00020\u00072\b\b\u0002\u0010\r\u001a\u00020\u00072\n\b\u0002\u0010\u000e\u001a\u0004\u0018\u00010\u00072\b\b\u0002\u0010\u000f\u001a\u00020\u0010H\u00c6\u0001\u00a2\u0006\u0002\u0010/J\u0013\u00100\u001a\u00020\u00102\b\u00101\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u00102\u001a\u00020\u0003H\u00d6\u0001J\t\u00103\u001a\u000204H\u00d6\u0001R\u0011\u0010\r\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u0011\u0010\u000f\u001a\u00020\u0010\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0011\u0010\f\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0013R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u0013R\u0015\u0010\t\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\n\n\u0002\u0010\u001a\u001a\u0004\b\u0018\u0010\u0019R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u001cR\u0015\u0010\n\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\n\n\u0002\u0010\u001a\u001a\u0004\b\u001d\u0010\u0019R\u0015\u0010\b\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\n\n\u0002\u0010\u001a\u001a\u0004\b\u001e\u0010\u0019R\u0011\u0010\u000b\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001f\u0010\u0013R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b \u0010!R\u0015\u0010\u000e\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\n\n\u0002\u0010\u001a\u001a\u0004\b\"\u0010\u0019\u00a8\u00065"}, d2 = {"Lcom/augusteenterprise/giglens/scoring/ScoreResult;", "", "score", "", "verdict", "Lcom/augusteenterprise/giglens/scoring/Verdict;", "payPerMile", "", "truePayPerMile", "pickupDistance", "totalDistance", "vehicleCost", "netValue", "costPerMileUsed", "vsPersonalAvg", "failedFloor", "", "(ILcom/augusteenterprise/giglens/scoring/Verdict;DLjava/lang/Double;Ljava/lang/Double;Ljava/lang/Double;DDDLjava/lang/Double;Z)V", "getCostPerMileUsed", "()D", "getFailedFloor", "()Z", "getNetValue", "getPayPerMile", "getPickupDistance", "()Ljava/lang/Double;", "Ljava/lang/Double;", "getScore", "()I", "getTotalDistance", "getTruePayPerMile", "getVehicleCost", "getVerdict", "()Lcom/augusteenterprise/giglens/scoring/Verdict;", "getVsPersonalAvg", "component1", "component10", "component11", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "component9", "copy", "(ILcom/augusteenterprise/giglens/scoring/Verdict;DLjava/lang/Double;Ljava/lang/Double;Ljava/lang/Double;DDDLjava/lang/Double;Z)Lcom/augusteenterprise/giglens/scoring/ScoreResult;", "equals", "other", "hashCode", "toString", "", "app_debug"})
public final class ScoreResult {
    private final int score = 0;
    @org.jetbrains.annotations.NotNull()
    private final com.augusteenterprise.giglens.scoring.Verdict verdict = null;
    private final double payPerMile = 0.0;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Double truePayPerMile = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Double pickupDistance = null;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Double totalDistance = null;
    private final double vehicleCost = 0.0;
    private final double netValue = 0.0;
    private final double costPerMileUsed = 0.0;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Double vsPersonalAvg = null;
    private final boolean failedFloor = false;
    
    public ScoreResult(int score, @org.jetbrains.annotations.NotNull()
    com.augusteenterprise.giglens.scoring.Verdict verdict, double payPerMile, @org.jetbrains.annotations.Nullable()
    java.lang.Double truePayPerMile, @org.jetbrains.annotations.Nullable()
    java.lang.Double pickupDistance, @org.jetbrains.annotations.Nullable()
    java.lang.Double totalDistance, double vehicleCost, double netValue, double costPerMileUsed, @org.jetbrains.annotations.Nullable()
    java.lang.Double vsPersonalAvg, boolean failedFloor) {
        super();
    }
    
    public final int getScore() {
        return 0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.augusteenterprise.giglens.scoring.Verdict getVerdict() {
        return null;
    }
    
    public final double getPayPerMile() {
        return 0.0;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Double getTruePayPerMile() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Double getPickupDistance() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Double getTotalDistance() {
        return null;
    }
    
    public final double getVehicleCost() {
        return 0.0;
    }
    
    public final double getNetValue() {
        return 0.0;
    }
    
    public final double getCostPerMileUsed() {
        return 0.0;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Double getVsPersonalAvg() {
        return null;
    }
    
    public final boolean getFailedFloor() {
        return false;
    }
    
    public final int component1() {
        return 0;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Double component10() {
        return null;
    }
    
    public final boolean component11() {
        return false;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.augusteenterprise.giglens.scoring.Verdict component2() {
        return null;
    }
    
    public final double component3() {
        return 0.0;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Double component4() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Double component5() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Double component6() {
        return null;
    }
    
    public final double component7() {
        return 0.0;
    }
    
    public final double component8() {
        return 0.0;
    }
    
    public final double component9() {
        return 0.0;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.augusteenterprise.giglens.scoring.ScoreResult copy(int score, @org.jetbrains.annotations.NotNull()
    com.augusteenterprise.giglens.scoring.Verdict verdict, double payPerMile, @org.jetbrains.annotations.Nullable()
    java.lang.Double truePayPerMile, @org.jetbrains.annotations.Nullable()
    java.lang.Double pickupDistance, @org.jetbrains.annotations.Nullable()
    java.lang.Double totalDistance, double vehicleCost, double netValue, double costPerMileUsed, @org.jetbrains.annotations.Nullable()
    java.lang.Double vsPersonalAvg, boolean failedFloor) {
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