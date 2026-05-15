package com.augusteenterprise.giglens.scoring;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001e\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\u0006H\u0082@\u00a2\u0006\u0002\u0010\nJ \u0010\u000b\u001a\u00020\u00062\u0006\u0010\f\u001a\u00020\u00062\u0006\u0010\r\u001a\u00020\u00062\u0006\u0010\u000e\u001a\u00020\u0006H\u0002J<\u0010\u000f\u001a\u0004\u0018\u00010\u00102\b\u0010\u0011\u001a\u0004\u0018\u00010\u00062\b\u0010\u0012\u001a\u0004\u0018\u00010\u00062\n\b\u0002\u0010\u0013\u001a\u0004\u0018\u00010\u00062\n\b\u0002\u0010\u0014\u001a\u0004\u0018\u00010\u0006H\u0086@\u00a2\u0006\u0002\u0010\u0015R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0016"}, d2 = {"Lcom/augusteenterprise/giglens/scoring/OfferScorer;", "", "configDao", "Lcom/augusteenterprise/giglens/data/ScorerConfigDao;", "(Lcom/augusteenterprise/giglens/data/ScorerConfigDao;)V", "cfg", "", "key", "", "default", "(Ljava/lang/String;DLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "normalize", "value", "min", "max", "score", "Lcom/augusteenterprise/giglens/scoring/ScoreResult;", "payAmount", "deliveryDistance", "pickupDistance", "personalAvgScore", "(Ljava/lang/Double;Ljava/lang/Double;Ljava/lang/Double;Ljava/lang/Double;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "app_debug"})
public final class OfferScorer {
    @org.jetbrains.annotations.NotNull()
    private final com.augusteenterprise.giglens.data.ScorerConfigDao configDao = null;
    
    public OfferScorer(@org.jetbrains.annotations.NotNull()
    com.augusteenterprise.giglens.data.ScorerConfigDao configDao) {
        super();
    }
    
    private final java.lang.Object cfg(java.lang.String key, double p1_772401952, kotlin.coroutines.Continuation<? super java.lang.Double> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object score(@org.jetbrains.annotations.Nullable()
    java.lang.Double payAmount, @org.jetbrains.annotations.Nullable()
    java.lang.Double deliveryDistance, @org.jetbrains.annotations.Nullable()
    java.lang.Double pickupDistance, @org.jetbrains.annotations.Nullable()
    java.lang.Double personalAvgScore, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.augusteenterprise.giglens.scoring.ScoreResult> $completion) {
        return null;
    }
    
    private final double normalize(double value, double min, double max) {
        return 0.0;
    }
}