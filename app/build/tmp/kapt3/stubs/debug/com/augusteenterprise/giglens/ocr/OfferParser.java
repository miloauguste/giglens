package com.augusteenterprise.giglens.ocr;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0010\u0006\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0017\u0010\u000b\u001a\u0004\u0018\u00010\f2\u0006\u0010\r\u001a\u00020\u0007H\u0002\u00a2\u0006\u0002\u0010\u000eJ\u0017\u0010\u000f\u001a\u0004\u0018\u00010\f2\u0006\u0010\r\u001a\u00020\u0007H\u0002\u00a2\u0006\u0002\u0010\u000eJ\u0012\u0010\u0010\u001a\u0004\u0018\u00010\u00072\u0006\u0010\r\u001a\u00020\u0007H\u0002J\u000e\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0007J\u000e\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0013\u001a\u00020\u0007R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0007X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0016"}, d2 = {"Lcom/augusteenterprise/giglens/ocr/OfferParser;", "", "()V", "DISTANCE_REGEX", "Lkotlin/text/Regex;", "OFFER_KEYWORDS", "", "", "PAY_REGEX", "PAY_REGEX_OCR", "TAG", "extractDistance", "", "text", "(Ljava/lang/String;)Ljava/lang/Double;", "extractPay", "extractRestaurant", "isOfferScreen", "", "ocrText", "parse", "Lcom/augusteenterprise/giglens/ocr/ParsedOffer;", "app_debug"})
public final class OfferParser {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "OfferParser";
    @org.jetbrains.annotations.NotNull()
    private static final java.util.List<java.lang.String> OFFER_KEYWORDS = null;
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.text.Regex PAY_REGEX = null;
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.text.Regex PAY_REGEX_OCR = null;
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.text.Regex DISTANCE_REGEX = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.augusteenterprise.giglens.ocr.OfferParser INSTANCE = null;
    
    private OfferParser() {
        super();
    }
    
    /**
     * Determines if the OCR text looks like a delivery offer screen.
     * Requires at least 2 offer keywords AND a dollar amount.
     */
    public final boolean isOfferScreen(@org.jetbrains.annotations.NotNull()
    java.lang.String ocrText) {
        return false;
    }
    
    /**
     * Extracts structured offer data from raw OCR text.
     */
    @org.jetbrains.annotations.NotNull()
    public final com.augusteenterprise.giglens.ocr.ParsedOffer parse(@org.jetbrains.annotations.NotNull()
    java.lang.String ocrText) {
        return null;
    }
    
    /**
     * Extracts the primary pay amount.
     * Takes the largest dollar value as the offer pay.
     * Handles OCR misreading $ as S.
     */
    private final java.lang.Double extractPay(java.lang.String text) {
        return null;
    }
    
    /**
     * Extracts distance in miles.
     */
    private final java.lang.Double extractDistance(java.lang.String text) {
        return null;
    }
    
    /**
     * Extracts restaurant name.
     * Looks for the line after "Pickup" or "Pick up from",
     * or a capitalized line that isn't a known UI element.
     */
    private final java.lang.String extractRestaurant(java.lang.String text) {
        return null;
    }
}