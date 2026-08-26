package com.currencyconverter.service;

import com.currencyconverter.model.Currency;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class CurrencyConverterService {
    private static final int OUTPUT_SCALE = 2;
    private final ExchangeRateProvider exchangeRateProvider;

    public CurrencyConverterService(ExchangeRateProvider exchangeRateProvider) {
        if (exchangeRateProvider == null) {
            throw new IllegalArgumentException("ExchangeRateProvider is required.");
        }
        this.exchangeRateProvider = exchangeRateProvider;
    }

    public BigDecimal convert(Currency fromCurrency, Currency toCurrency, BigDecimal amount) {
        if (fromCurrency == null || toCurrency == null) {
            throw new IllegalArgumentException("From currency and to currency are required.");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required.");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amount cannot be negative.");
        }

        BigDecimal rate = exchangeRateProvider.getRate(fromCurrency, toCurrency);
        return amount.multiply(rate).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }
}
