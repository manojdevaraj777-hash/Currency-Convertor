package com.currencyconverter.service;

import com.currencyconverter.model.Currency;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides live exchange rates fetched once per day from a free public API,
 * with a local file cache so repeated runs on the same day stay offline-fast,
 * and an automatic fallback to built-in sample rates when no internet is available.
 */
public class LiveExchangeRateProvider implements ExchangeRateProvider {

    private static final String ER_API_URL = "https://open.er-api.com/v6/latest/USD";
    private static final String PAGES_DEV_URL = "https://latest.currency-api.pages.dev/v1/currencies/usd.json";
    private static final Path CACHE_FILE = Path.of("rates-cache.txt");
    private static final int MIN_ACCEPTED_RATES = 100;
    private static final long LOAD_TIMEOUT_MS = 20000;

    private static final Pattern ER_API_PATTERN =
            Pattern.compile("\"([A-Z]{3})\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern PAGES_DEV_PATTERN =
            Pattern.compile("\"([a-z]{3})\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

    private final Map<Currency, BigDecimal> usdBaseRates = new EnumMap<>(Currency.class);
    private final InMemoryExchangeRateProvider fallbackProvider = new InMemoryExchangeRateProvider();
    private final Object stateLock = new Object();

    private volatile boolean usingFallback;
    private volatile String sourceDescription;

    public LiveExchangeRateProvider() {
        String today = LocalDate.now().toString();

        Thread loader = new Thread(() -> loadRates(today), "rate-loader");
        loader.setDaemon(true);
        loader.start();

        try {
            loader.join(LOAD_TIMEOUT_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }

        synchronized (stateLock) {
            if (usdBaseRates.isEmpty()) {
                usingFallback = true;
                sourceDescription = "OFFLINE - using built-in sample rates (not live)";
            } else if (sourceDescription == null) {
                sourceDescription = "cached daily rates (fetched " + today + ")";
            }
        }
    }

    private void loadRates(String today) {
        if (!loadFromCache(today)) {
            fetchLiveRates(today);
        }

        synchronized (stateLock) {
            if (!usdBaseRates.isEmpty() && sourceDescription == null) {
                sourceDescription = "LIVE daily rates (fetched " + today + ")";
            }
        }
    }

    @Override
    public BigDecimal getRate(Currency fromCurrency, Currency toCurrency) {
        if (usingFallback) {
            return fallbackProvider.getRate(fromCurrency, toCurrency);
        }

        if (fromCurrency == toCurrency) {
            return BigDecimal.ONE;
        }

        BigDecimal fromRate;
        BigDecimal toRate;
        synchronized (stateLock) {
            fromRate = usdBaseRates.get(fromCurrency);
            toRate = usdBaseRates.get(toCurrency);
        }

        if (fromRate == null || toRate == null) {
            throw new CurrencyConversionException(
                    "Exchange rate not available for " + fromCurrency + " to " + toCurrency + ".");
        }

        return toRate.divide(fromRate, 10, RoundingMode.HALF_UP);
    }

    public String getSourceDescription() {
        return sourceDescription;
    }

    private void fetchLiveRates(String today) {
        String[] urls = {ER_API_URL, PAGES_DEV_URL};

        for (String url : urls) {
            try {
                String body = httpGet(url);
                Map<Currency, BigDecimal> parsed =
                        url.equals(ER_API_URL) ? parseErApi(body) : parsePagesDev(body);

                if (parsed.size() >= MIN_ACCEPTED_RATES) {
                    synchronized (stateLock) {
                        usdBaseRates.putAll(parsed);
                        sourceDescription = "LIVE daily rates via " + hostOf(url) + " (fetched " + today + ")";
                    }
                    writeCache(today);
                    return;
                }
            } catch (IOException | InterruptedException ex) {
                System.out.println("[info] Rate source failed (" + hostOf(url) + "), trying next...");
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private String httpGet(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IOException("Unexpected HTTP status: " + response.statusCode());
        }
        return response.body();
    }

    private Map<Currency, BigDecimal> parseErApi(String body) {
        String jsonSegment = extractSegment(body, "\"rates\"");
        return parseWithPattern(jsonSegment, ER_API_PATTERN);
    }

    private Map<Currency, BigDecimal> parsePagesDev(String body) {
        String jsonSegment = extractSegment(body, "\"usd\"");
        return parseWithPattern(jsonSegment, PAGES_DEV_PATTERN);
    }

    private String extractSegment(String body, String anchor) {
        int anchorIndex = body.indexOf(anchor);
        if (anchorIndex < 0) {
            return "";
        }
        int start = body.indexOf('{', anchorIndex);
        int end = body.indexOf('}', start);
        if (start < 0 || end < 0 || end <= start) {
            return "";
        }
        return body.substring(start, end);
    }

    private Map<Currency, BigDecimal> parseWithPattern(String segment, Pattern pattern) {
        Map<Currency, BigDecimal> result = new EnumMap<>(Currency.class);
        Matcher matcher = pattern.matcher(segment);
        while (matcher.find()) {
            try {
                result.put(Currency.fromCode(matcher.group(1)), new BigDecimal(matcher.group(2)));
            } catch (IllegalArgumentException ignoredCode) {
                // API returned a code outside our supported enum; skip it.
            }
        }
        return result;
    }

    private boolean loadFromCache(String expectedDate) {
        if (!Files.exists(CACHE_FILE)) {
            return false;
        }
        try {
            var lines = Files.readAllLines(CACHE_FILE, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.get(0).equals("#fetched=" + expectedDate)) {
                return false;
            }
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                try {
                    synchronized (stateLock) {
                        usdBaseRates.put(
                                Currency.fromCode(line.substring(0, separator)),
                                new BigDecimal(line.substring(separator + 1)));
                    }
                } catch (IllegalArgumentException ignoredBadLine) {
                    // Corrupt or unsupported line; skip it.
                }
            }
            return usdBaseRates.size() >= MIN_ACCEPTED_RATES;
        } catch (IOException ioEx) {
            return false;
        }
    }

    private void writeCache(String today) {
        StringBuilder builder = new StringBuilder("#fetched=").append(today).append(System.lineSeparator());
        for (Map.Entry<Currency, BigDecimal> entry : usdBaseRates.entrySet()) {
            builder.append(entry.getKey().name())
                    .append('=')
                    .append(entry.getValue().toPlainString())
                    .append(System.lineSeparator());
        }
        try {
            Files.writeString(CACHE_FILE, builder.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignoredWrite) {
            // Cache is an optimization only; live rates are still usable in memory.
        }
    }

    private String hostOf(String url) {
        return URI.create(url).getHost();
    }
}
