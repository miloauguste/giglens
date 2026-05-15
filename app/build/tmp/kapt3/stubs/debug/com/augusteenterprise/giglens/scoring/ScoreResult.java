package com.augusteenterprise.giglens.scoring;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0017\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B/\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\b\u0010\b\u001a\u0004\u0018\u00010\u0007\u0012\u0006\u0010\t\u001a\u00020\n\u00a2\u0006\u0002\u0010\u000bJ\t\u0010\u0017\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0018\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u0019\u001a\u00020\u0007H\u00c6\u0003J\u0010\u0010\u001a\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003\u00a2\u0006\u0002\u0010\u0015J\t\u0010\u001b\u001a\u00020\nH\u00c6\u0003JB\u0010\u001c\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\u00072\b\b\u0002\u0010\t\u001a\u00020\nH\u00c6\u0001\u00a2\u0006\u0002\u0010\u001dJ\u0013\u0010\u001e\u001a\u00020\n2\b\u0010\u001f\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010 \u001a\u00020\u0003H\u00d6\u0001J\t\u0010!\u001a\u00020\"H\u00d6\u0001R\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u0015\u0010\b\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\n\n\u0002\u0010\u0016\u001a\u0004\b\u0014\u0010\u0015\u00a8\u0006#"}, d2 = {"Lcom/augusteenterprise/giglens/scoring/ScoreResult;", "", "score", "", "verdict", "Lcom/augusteenterprise/giglens/scoring/Verdict;", "payPerMile", "", "vsPersonalAvg", "failedFloor", "", "(ILcom/augusteenterprise/giglens/scoring/Verdict;DLjava/lang/Double;Z)V", "getFailedFloor", "()Z", "getPayPerMile", "()D", "getScore", "()I", "getVerdict", "()Lcom/augusteenterprise/giglens/scoring/Verdict;", "getVsPersonalAvg", "()Ljava/lang/Double;", "Ljava/lang/Double;", "component1", "component2", "component3", "component4", "component5", "copy", "(ILcom/augusteenterprise/giglens/scoring/Verdict;DLjava/lang/Double;Z)Lcom/augusteenterprise/giglens/scoring/ScoreResult;", "equals", "other", "hashCode", "toString", "", "app_debug"})
public final class ScoreResult {
    private final int score = 0;
    @org.jetbrains.annotations.NotNull()
    private final com.augusteenterprise.giglens.scoring.Verdict verdict = null;
    private final double payPerMile = 0.0;
    @org.jetbrains.annotations.Nullable()
    private final java.lang.Double vsPersonalAvg = null;
    private final boolean failedFloor = false;
    
    public ScoreResult(int score, @org.jetbrains.annotations.NotNull()
    com.augusteenterprise.giglens.scoring.Verdict verdict, double payPerMile, @org.jetbrains.annotations.Nullable()
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
    public final java.lang.Double getVsPersonalAvg() {
        return null;
    }
    
    public final boolean getFailedFloor() {
        return false;
    }
    
    public final int component1() {
        return 0;
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
    
    public final boolean component5() {
        return false;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.augusteenterprise.giglens.scoring.ScoreResult copy(int score, @org.jetbrains.annotations.NotNull()
    com.augusteenterprise.giglens.scoring.Verdict verdict, double payPerMile, @org.jetbrains.annotations.Nullable()
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