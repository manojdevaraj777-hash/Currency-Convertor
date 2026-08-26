package com.currencyconverter.service;

import com.currencyconverter.model.Currency;
import java.math.BigDecimal;

public interface ExchangeRateProvider {
    BigDecimal getRate(Currency fromCurrency, Currency toCurrency);
}
