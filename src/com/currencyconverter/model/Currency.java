package com.currencyconverter.model;

import java.util.Arrays;

/**
 * ISO 4217 currency codes supported by the converter, each mapped to the
 * ISO 3166-1 alpha-2 country/region code whose flag represents it.
 */
public enum Currency {
    AED("AE"), AFN("AF"), ALL("AL"), AMD("AM"), ANG("CW"), AOA("AO"), ARS("AR"),
    AUD("AU"), AWG("AW"), AZN("AZ"),
    BAM("BA"), BBD("BB"), BDT("BD"), BGN("BG"), BHD("BH"), BIF("BI"), BMD("BM"),
    BND("BN"), BOB("BO"), BRL("BR"), BSD("BS"), BTN("BT"), BWP("BW"), BYN("BY"),
    BZD("BZ"),
    CAD("CA"), CDF("CD"), CHF("CH"), CLP("CL"), CNY("CN"), COP("CO"), CRC("CR"),
    CUP("CU"), CVE("CV"), CZK("CZ"),
    DJF("DJ"), DKK("DK"), DOP("DO"), DZD("DZ"),
    EGP("EG"), ERN("ER"), ETB("ET"), EUR("EU"),
    FJD("FJ"), FKP("FK"),
    GBP("GB"), GEL("GE"), GHS("GH"), GIP("GI"), GMD("GM"), GNF("GN"), GTQ("GT"),
    GYD("GY"),
    HKD("HK"), HNL("HN"), HTG("HT"), HUF("HU"),
    IDR("ID"), ILS("IL"), INR("IN"), IQD("IQ"), IRR("IR"), ISK("IS"),
    JMD("JM"), JOD("JO"), JPY("JP"),
    KES("KE"), KGS("KG"), KHR("KH"), KMF("KM"), KPW("KP"), KRW("KR"), KWD("KW"),
    KYD("KY"), KZT("KZ"),
    LAK("LA"), LBP("LB"), LKR("LK"), LRD("LR"), LSL("LS"), LYD("LY"),
    MAD("MA"), MDL("MD"), MGA("MG"), MKD("MK"), MMK("MM"), MNT("MN"), MOP("MO"),
    MRU("MR"), MUR("MU"), MVR("MV"), MWK("MW"), MXN("MX"), MYR("MY"), MZN("MZ"),
    NAD("NA"), NGN("NG"), NIO("NI"), NOK("NO"), NPR("NP"), NZD("NZ"),
    OMR("OM"),
    PAB("PA"), PEN("PE"), PGK("PG"), PHP("PH"), PKR("PK"), PLN("PL"), PYG("PY"),
    QAR("QA"),
    RON("RO"), RSD("RS"), RUB("RU"), RWF("RW"),
    SAR("SA"), SBD("SB"), SCR("SC"), SDG("SD"), SEK("SE"), SGD("SG"), SHP("SH"),
    SLE("SL"), SOS("SO"), SRD("SR"), SSP("SS"), STN("ST"), SVC("SV"), SYP("SY"),
    SZL("SZ"),
    THB("TH"), TJS("TJ"), TMT("TM"), TND("TN"), TOP("TO"), TRY("TR"), TTD("TT"),
    TWD("TW"), TZS("TZ"),
    UAH("UA"), UGX("UG"), USD("US"), UYU("UY"), UZS("UZ"),
    VES("VE"), VND("VN"), VUV("VU"),
    WST("WS"),
    XAF("CM"), XCD("KN"), XOF("SN"), XPF("PF"),
    YER("YE"),
    ZAR("ZA"), ZMW("ZM"), ZWL("ZW");

    private static final int REGIONAL_INDICATOR_A = 0x1F1E6;
    private static final String FALLBACK_FLAG = "\uD83C\uDDF3\uD83C\uDDF1";

    private final String countryCode;

    Currency(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    /**
     * Builds the regional-indicator flag emoji (e.g., "US" -> the USA flag)
     * from the two-letter country code.
     */
    public String getFlagEmoji() {
        if (countryCode == null || countryCode.length() != 2) {
            return FALLBACK_FLAG;
        }
        int first = REGIONAL_INDICATOR_A + (countryCode.charAt(0) - 'A');
        int second = REGIONAL_INDICATOR_A + (countryCode.charAt(1) - 'A');
        return new String(new int[]{first, second}, 0, 2);
    }

    public static Currency fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Currency code cannot be null.");
        }

        String normalized = code.trim().toUpperCase();
        for (Currency currency : values()) {
            if (currency.name().equals(normalized)) {
                return currency;
            }
        }
        throw new IllegalArgumentException(
                "Unsupported currency code: " + code + ". Supported codes: " + Arrays.toString(values()));
    }

    public static int count() {
        return values().length;
    }
}
