package com.gatepay.paymentservice.util;

import java.math.BigDecimal;

public class CurrencyUtils {

    /*
     * Convert amount to smallest currency unit (kobo for NGN)
     */
    public static BigDecimal toSmallestUnit(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100));
    }

    /*
     * Convert from smallest unit back to main unit
     */
    public static BigDecimal fromSmallestUnit(BigDecimal amount) {
        return amount.divide(BigDecimal.valueOf(100));
    }

    /*
     * Check if currency is supported
     */
    public static boolean isCurrencySupported(String currency) {
        return currency != null &&
                ("NGN".equals(currency) || "USD".equals(currency) ||
                        "GHS".equals(currency) || "ZAR".equals(currency));
    }
}
