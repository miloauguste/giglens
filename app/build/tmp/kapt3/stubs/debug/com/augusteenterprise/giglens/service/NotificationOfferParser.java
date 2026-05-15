package com.augusteenterprise.giglens.service;

/**
 * Parses DoorDash notification text to extract offer details.
 * DoorDash notifications typically look like:
 *  Title: "New delivery opportunity"
 *  Text: "$7.50 - 3.2 mi - McDonald's"
 * or similar formats.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0001\u000eB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\t\u001a\u0004\u0018\u00010\u00072\u0006\u0010\n\u001a\u00020\u0007H\u0002J\u000e\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u0007R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000f"}, d2 = {"Lcom/augusteenterprise/giglens/service/NotificationOfferParser;", "", "()V", "DISTANCE_REGEX", "Lkotlin/text/Regex;", "OFFER_KEYWORDS", "", "", "PAY_REGEX", "extractRestaurant", "text", "parse", "Lcom/augusteenterprise/giglens/service/NotificationOfferParser$NotificationOffer;", "notificationText", "NotificationOffer", "app_debug"})
public final class NotificationOfferParser {
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.text.Regex PAY_REGEX = null;
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.text.Regex DISTANCE_REGEX = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.util.List<java.lang.String> OFFER_KEYWORDS = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.augusteenterprise.giglens.service.NotificationOfferParser INSTANCE = null;
    
    private NotificationOfferParser() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.augusteenterprise.giglens.service.NotificationOfferParser.NotificationOffer parse(@org.jetbrains.annotations.NotNull()
    java.lang.String notificationText) {
        return null;
    }
    
    private final java.lang.String extractRestaurant(java.lang.String text) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0011\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B3\u0012\b\b\u0002\u0010\u0002\u001a\u00020\u0003\u0012\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u0012\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u0012\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\b\u00a2\u0006\u0002\u0010\tJ\t\u0010\u0011\u001a\u00020\u0003H\u00c6\u0003J\u0010\u0010\u0012\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003\u00a2\u0006\u0002\u0010\u000bJ\u0010\u0010\u0013\u001a\u0004\u0018\u00010\u0005H\u00c6\u0003\u00a2\u0006\u0002\u0010\u000bJ\u000b\u0010\u0014\u001a\u0004\u0018\u00010\bH\u00c6\u0003J<\u0010\u0015\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u00052\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\bH\u00c6\u0001\u00a2\u0006\u0002\u0010\u0016J\u0013\u0010\u0017\u001a\u00020\u00032\b\u0010\u0018\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0019\u001a\u00020\u001aH\u00d6\u0001J\t\u0010\u001b\u001a\u00020\bH\u00d6\u0001R\u0015\u0010\u0006\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\n\n\u0002\u0010\f\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0002\u0010\rR\u0015\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\n\n\u0002\u0010\f\u001a\u0004\b\u000e\u0010\u000bR\u0013\u0010\u0007\u001a\u0004\u0018\u00010\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010\u00a8\u0006\u001c"}, d2 = {"Lcom/augusteenterprise/giglens/service/NotificationOfferParser$NotificationOffer;", "", "isOffer", "", "payAmount", "", "distance", "restaurant", "", "(ZLjava/lang/Double;Ljava/lang/Double;Ljava/lang/String;)V", "getDistance", "()Ljava/lang/Double;", "Ljava/lang/Double;", "()Z", "getPayAmount", "getRestaurant", "()Ljava/lang/String;", "component1", "component2", "component3", "component4", "copy", "(ZLjava/lang/Double;Ljava/lang/Double;Ljava/lang/String;)Lcom/augusteenterprise/giglens/service/NotificationOfferParser$NotificationOffer;", "equals", "other", "hashCode", "", "toString", "app_debug"})
    public static final class NotificationOffer {
        private final boolean isOffer = false;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Double payAmount = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Double distance = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String restaurant = null;
        
        public NotificationOffer(boolean isOffer, @org.jetbrains.annotations.Nullable()
        java.lang.Double payAmount, @org.jetbrains.annotations.Nullable()
        java.lang.Double distance, @org.jetbrains.annotations.Nullable()
        java.lang.String restaurant) {
            super();
        }
        
        public final boolean isOffer() {
            return false;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Double getPayAmount() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Double getDistance() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getRestaurant() {
            return null;
        }
        
        public NotificationOffer() {
            super();
        }
        
        public final boolean component1() {
            return false;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Double component2() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Double component3() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.augusteenterprise.giglens.service.NotificationOfferParser.NotificationOffer copy(boolean isOffer, @org.jetbrains.annotations.Nullable()
        java.lang.Double payAmount, @org.jetbrains.annotations.Nullable()
        java.lang.Double distance, @org.jetbrains.annotations.Nullable()
        java.lang.String restaurant) {
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
}