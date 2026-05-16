package com.augusteenterprise.giglens.ocr;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006J\u0018\u0010\u0007\u001a\u0004\u0018\u00010\u00062\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00060\tH\u0002J \u0010\n\u001a\u0004\u0018\u00010\u00062\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00060\t2\u0006\u0010\u000b\u001a\u00020\u0006H\u0002J\u0010\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u0006H\u0002\u00a8\u0006\u000f"}, d2 = {"Lcom/augusteenterprise/giglens/ocr/StreetExtractor;", "", "()V", "extract", "Lcom/augusteenterprise/giglens/ocr/ExtractedAddresses;", "rawOcrText", "", "extractDeliverBy", "lines", "", "extractNearKeyword", "keyword", "isStreetLine", "", "line", "app_debug"})
public final class StreetExtractor {
    @org.jetbrains.annotations.NotNull()
    public static final com.augusteenterprise.giglens.ocr.StreetExtractor INSTANCE = null;
    
    private StreetExtractor() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.augusteenterprise.giglens.ocr.ExtractedAddresses extract(@org.jetbrains.annotations.NotNull()
    java.lang.String rawOcrText) {
        return null;
    }
    
    /**
     * Scans lines after a keyword for the first line that looks like a street address.
     */
    private final java.lang.String extractNearKeyword(java.util.List<java.lang.String> lines, java.lang.String keyword) {
        return null;
    }
    
    /**
     * Returns true if line looks like a street address.
     * Must contain a street suffix and not be a skip token.
     */
    private final boolean isStreetLine(java.lang.String line) {
        return false;
    }
    
    /**
     * Extracts "Deliver by X:XX PM" time from OCR text.
     */
    private final java.lang.String extractDeliverBy(java.util.List<java.lang.String> lines) {
        return null;
    }
}