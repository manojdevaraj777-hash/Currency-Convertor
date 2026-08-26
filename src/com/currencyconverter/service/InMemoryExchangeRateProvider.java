package com.currencyconverter.service;

import com.currencyconverter.model.Currency;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Provides exchange rates using USD as the base currency.
 * Cross rates (e.g., EUR -> JPY) are derived automatically:
 * rate(from -> to) = usdRate(to) / usdRate(from).
 *
 * Rates are approximate sample values for study purposes.
 */
public class InMemoryExchangeRateProvider implements ExchangeRateProvider {
    private final Map<Currency, BigDecimal> usdBaseRates;

    public InMemoryExchangeRateProvider() {
        this.usdBaseRates = new EnumMap<>(Currency.class);
        seedRates();
    }

    private void seedRates() {
        // A
        usdBaseRates.put(Currency.AED, new BigDecimal("3.6725"));
        usdBaseRates.put(Currency.AFN, new BigDecimal("71.50"));
        usdBaseRates.put(Currency.ALL, new BigDecimal("92.50"));
        usdBaseRates.put(Currency.AMD, new BigDecimal("388.00"));
        usdBaseRates.put(Currency.ANG, new BigDecimal("1.7900"));
        usdBaseRates.put(Currency.AOA, new BigDecimal("910.00"));
        usdBaseRates.put(Currency.ARS, new BigDecimal("900.00"));
        usdBaseRates.put(Currency.AUD, new BigDecimal("1.5100"));
        usdBaseRates.put(Currency.AWG, new BigDecimal("1.7900"));
        usdBaseRates.put(Currency.AZN, new BigDecimal("1.7000"));

        // B
        usdBaseRates.put(Currency.BAM, new BigDecimal("1.8000"));
        usdBaseRates.put(Currency.BBD, new BigDecimal("2.0000"));
        usdBaseRates.put(Currency.BDT, new BigDecimal("117.00"));
        usdBaseRates.put(Currency.BGN, new BigDecimal("1.8000"));
        usdBaseRates.put(Currency.BHD, new BigDecimal("0.3760"));
        usdBaseRates.put(Currency.BIF, new BigDecimal("2850.00"));
        usdBaseRates.put(Currency.BMD, new BigDecimal("1.0000"));
        usdBaseRates.put(Currency.BND, new BigDecimal("1.3400"));
        usdBaseRates.put(Currency.BOB, new BigDecimal("6.9100"));
        usdBaseRates.put(Currency.BRL, new BigDecimal("5.5500"));
        usdBaseRates.put(Currency.BSD, new BigDecimal("1.0000"));
        usdBaseRates.put(Currency.BTN, new BigDecimal("83.500"));
        usdBaseRates.put(Currency.BWP, new BigDecimal("13.700"));
        usdBaseRates.put(Currency.BYN, new BigDecimal("3.2700"));
        usdBaseRates.put(Currency.BZD, new BigDecimal("2.0100"));

        // C
        usdBaseRates.put(Currency.CAD, new BigDecimal("1.3600"));
        usdBaseRates.put(Currency.CDF, new BigDecimal("2800.00"));
        usdBaseRates.put(Currency.CHF, new BigDecimal("0.8700"));
        usdBaseRates.put(Currency.CLP, new BigDecimal("950.00"));
        usdBaseRates.put(Currency.CNY, new BigDecimal("7.1900"));
        usdBaseRates.put(Currency.COP, new BigDecimal("4100.00"));
        usdBaseRates.put(Currency.CRC, new BigDecimal("520.00"));
        usdBaseRates.put(Currency.CUP, new BigDecimal("24.000"));
        usdBaseRates.put(Currency.CVE, new BigDecimal("102.00"));
        usdBaseRates.put(Currency.CZK, new BigDecimal("23.200"));

        // D
        usdBaseRates.put(Currency.DJF, new BigDecimal("178.00"));
        usdBaseRates.put(Currency.DKK, new BigDecimal("6.8600"));
        usdBaseRates.put(Currency.DOP, new BigDecimal("60.000"));
        usdBaseRates.put(Currency.DZD, new BigDecimal("134.00"));

        // E
        usdBaseRates.put(Currency.EGP, new BigDecimal("48.500"));
        usdBaseRates.put(Currency.ERN, new BigDecimal("15.000"));
        usdBaseRates.put(Currency.ETB, new BigDecimal("57.000"));
        usdBaseRates.put(Currency.EUR, new BigDecimal("0.9200"));

        // F
        usdBaseRates.put(Currency.FJD, new BigDecimal("2.2500"));
        usdBaseRates.put(Currency.FKP, new BigDecimal("0.7800"));

        // G
        usdBaseRates.put(Currency.GBP, new BigDecimal("0.7900"));
        usdBaseRates.put(Currency.GEL, new BigDecimal("2.7200"));
        usdBaseRates.put(Currency.GHS, new BigDecimal("15.800"));
        usdBaseRates.put(Currency.GIP, new BigDecimal("0.7800"));
        usdBaseRates.put(Currency.GMD, new BigDecimal("68.000"));
        usdBaseRates.put(Currency.GNF, new BigDecimal("8600.00"));
        usdBaseRates.put(Currency.GTQ, new BigDecimal("7.7700"));
        usdBaseRates.put(Currency.GYD, new BigDecimal("209.00"));

        // H
        usdBaseRates.put(Currency.HKD, new BigDecimal("7.8100"));
        usdBaseRates.put(Currency.HNL, new BigDecimal("24.700"));
        usdBaseRates.put(Currency.HTG, new BigDecimal("132.00"));
        usdBaseRates.put(Currency.HUF, new BigDecimal("360.00"));

        // I
        usdBaseRates.put(Currency.IDR, new BigDecimal("15800.0"));
        usdBaseRates.put(Currency.ILS, new BigDecimal("3.7100"));
        usdBaseRates.put(Currency.INR, new BigDecimal("83.500"));
        usdBaseRates.put(Currency.IQD, new BigDecimal("1310.00"));
        usdBaseRates.put(Currency.IRR, new BigDecimal("42000.0"));
        usdBaseRates.put(Currency.ISK, new BigDecimal("138.00"));

        // J
        usdBaseRates.put(Currency.JMD, new BigDecimal("157.00"));
        usdBaseRates.put(Currency.JOD, new BigDecimal("0.7090"));
        usdBaseRates.put(Currency.JPY, new BigDecimal("146.20"));

        // K
        usdBaseRates.put(Currency.KES, new BigDecimal("129.00"));
        usdBaseRates.put(Currency.KGS, new BigDecimal("89.500"));
        usdBaseRates.put(Currency.KHR, new BigDecimal("4050.00"));
        usdBaseRates.put(Currency.KMF, new BigDecimal("452.00"));
        usdBaseRates.put(Currency.KPW, new BigDecimal("900.00"));
        usdBaseRates.put(Currency.KRW, new BigDecimal("1340.00"));
        usdBaseRates.put(Currency.KWD, new BigDecimal("0.3070"));
        usdBaseRates.put(Currency.KYD, new BigDecimal("0.8300"));
        usdBaseRates.put(Currency.KZT, new BigDecimal("480.00"));

        // L
        usdBaseRates.put(Currency.LAK, new BigDecimal("21800.0"));
        usdBaseRates.put(Currency.LBP, new BigDecimal("89500.0"));
        usdBaseRates.put(Currency.LKR, new BigDecimal("300.00"));
        usdBaseRates.put(Currency.LRD, new BigDecimal("195.00"));
        usdBaseRates.put(Currency.LSL, new BigDecimal("18.200"));
        usdBaseRates.put(Currency.LYD, new BigDecimal("4.8500"));

        // M
        usdBaseRates.put(Currency.MAD, new BigDecimal("9.9500"));
        usdBaseRates.put(Currency.MDL, new BigDecimal("17.600"));
        usdBaseRates.put(Currency.MGA, new BigDecimal("4600.00"));
        usdBaseRates.put(Currency.MKD, new BigDecimal("56.600"));
        usdBaseRates.put(Currency.MMK, new BigDecimal("2100.00"));
        usdBaseRates.put(Currency.MNT, new BigDecimal("3400.00"));
        usdBaseRates.put(Currency.MOP, new BigDecimal("8.0400"));
        usdBaseRates.put(Currency.MRU, new BigDecimal("39.500"));
        usdBaseRates.put(Currency.MUR, new BigDecimal("46.500"));
        usdBaseRates.put(Currency.MVR, new BigDecimal("15.400"));
        usdBaseRates.put(Currency.MWK, new BigDecimal("1735.00"));
        usdBaseRates.put(Currency.MXN, new BigDecimal("20.100"));
        usdBaseRates.put(Currency.MYR, new BigDecimal("4.4200"));
        usdBaseRates.put(Currency.MZN, new BigDecimal("63.900"));

        // N
        usdBaseRates.put(Currency.NAD, new BigDecimal("18.200"));
        usdBaseRates.put(Currency.NGN, new BigDecimal("1600.00"));
        usdBaseRates.put(Currency.NIO, new BigDecimal("36.800"));
        usdBaseRates.put(Currency.NOK, new BigDecimal("10.600"));
        usdBaseRates.put(Currency.NPR, new BigDecimal("133.60"));
        usdBaseRates.put(Currency.NZD, new BigDecimal("1.6600"));

        // O
        usdBaseRates.put(Currency.OMR, new BigDecimal("0.3845"));

        // P
        usdBaseRates.put(Currency.PAB, new BigDecimal("1.0000"));
        usdBaseRates.put(Currency.PEN, new BigDecimal("3.7600"));
        usdBaseRates.put(Currency.PGK, new BigDecimal("3.9000"));
        usdBaseRates.put(Currency.PHP, new BigDecimal("58.000"));
        usdBaseRates.put(Currency.PKR, new BigDecimal("278.00"));
        usdBaseRates.put(Currency.PLN, new BigDecimal("3.9500"));
        usdBaseRates.put(Currency.PYG, new BigDecimal("7800.00"));

        // Q
        usdBaseRates.put(Currency.QAR, new BigDecimal("3.6400"));

        // R
        usdBaseRates.put(Currency.RON, new BigDecimal("4.5800"));
        usdBaseRates.put(Currency.RSD, new BigDecimal("108.00"));
        usdBaseRates.put(Currency.RUB, new BigDecimal("92.000"));
        usdBaseRates.put(Currency.RWF, new BigDecimal("1350.00"));

        // S
        usdBaseRates.put(Currency.SAR, new BigDecimal("3.7500"));
        usdBaseRates.put(Currency.SBD, new BigDecimal("8.4200"));
        usdBaseRates.put(Currency.SCR, new BigDecimal("13.500"));
        usdBaseRates.put(Currency.SDG, new BigDecimal("601.00"));
        usdBaseRates.put(Currency.SEK, new BigDecimal("10.500"));
        usdBaseRates.put(Currency.SGD, new BigDecimal("1.3400"));
        usdBaseRates.put(Currency.SHP, new BigDecimal("0.7800"));
        usdBaseRates.put(Currency.SLE, new BigDecimal("22.700"));
        usdBaseRates.put(Currency.SOS, new BigDecimal("571.00"));
        usdBaseRates.put(Currency.SRD, new BigDecimal("35.300"));
        usdBaseRates.put(Currency.SSP, new BigDecimal("1300.00"));
        usdBaseRates.put(Currency.STN, new BigDecimal("22.600"));
        usdBaseRates.put(Currency.SVC, new BigDecimal("8.7500"));
        usdBaseRates.put(Currency.SYP, new BigDecimal("13000.0"));
        usdBaseRates.put(Currency.SZL, new BigDecimal("18.200"));

        // T
        usdBaseRates.put(Currency.THB, new BigDecimal("34.500"));
        usdBaseRates.put(Currency.TJS, new BigDecimal("10.900"));
        usdBaseRates.put(Currency.TMT, new BigDecimal("3.5000"));
        usdBaseRates.put(Currency.TND, new BigDecimal("3.1200"));
        usdBaseRates.put(Currency.TOP, new BigDecimal("2.3500"));
        usdBaseRates.put(Currency.TRY, new BigDecimal("34.200"));
        usdBaseRates.put(Currency.TTD, new BigDecimal("6.7800"));
        usdBaseRates.put(Currency.TWD, new BigDecimal("32.300"));
        usdBaseRates.put(Currency.TZS, new BigDecimal("2700.00"));

        // U
        usdBaseRates.put(Currency.UAH, new BigDecimal("41.200"));
        usdBaseRates.put(Currency.UGX, new BigDecimal("3800.00"));
        usdBaseRates.put(Currency.USD, new BigDecimal("1.0000"));
        usdBaseRates.put(Currency.UYU, new BigDecimal("40.500"));
        usdBaseRates.put(Currency.UZS, new BigDecimal("12700.0"));

        // V
        usdBaseRates.put(Currency.VES, new BigDecimal("37.200"));
        usdBaseRates.put(Currency.VND, new BigDecimal("25400.0"));
        usdBaseRates.put(Currency.VUV, new BigDecimal("119.00"));

        // W
        usdBaseRates.put(Currency.WST, new BigDecimal("2.7300"));

        // X
        usdBaseRates.put(Currency.XAF, new BigDecimal("604.00"));
        usdBaseRates.put(Currency.XCD, new BigDecimal("2.7000"));
        usdBaseRates.put(Currency.XOF, new BigDecimal("604.00"));
        usdBaseRates.put(Currency.XPF, new BigDecimal("110.00"));

        // Y
        usdBaseRates.put(Currency.YER, new BigDecimal("250.00"));

        // Z
        usdBaseRates.put(Currency.ZAR, new BigDecimal("18.200"));
        usdBaseRates.put(Currency.ZMW, new BigDecimal("26.500"));
        usdBaseRates.put(Currency.ZWL, new BigDecimal("25.00"));
    }

    @Override
    public BigDecimal getRate(Currency fromCurrency, Currency toCurrency) {
        if (fromCurrency == toCurrency) {
            return BigDecimal.ONE;
        }

        BigDecimal fromRate = usdBaseRates.get(fromCurrency);
        BigDecimal toRate = usdBaseRates.get(toCurrency);

        if (fromRate == null || toRate == null) {
            throw new CurrencyConversionException(
                    "Exchange rate not available for " + fromCurrency + " to " + toCurrency + ".");
        }

        return toRate.divide(fromRate, 10, java.math.RoundingMode.HALF_UP);
    }
}
