package com.currencyconverter.service;

import java.math.BigDecimal;

/**
 * Represents a saved currency conversion log entry.
 */
public record ConversionHistoryEntry(
        String fromCode,
        String fromFlag,
        String fromCountry,
        String toCode,
        String toFlag,
        String toCountry,
        BigDecimal amount,
        BigDecimal result,
        BigDecimal rate,
        long timestamp
) {}
