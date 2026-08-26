package com.currencyconverter.web;

import com.currencyconverter.model.Currency;
import com.currencyconverter.service.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.util.List;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Web entry point. Serves the browser UI from webroot/ and exposes a small
 * JSON API on top of the same service layer used by the console app:
 *
 *   GET /api/currencies                      -> ["AED","AFN",...]
 *   GET /api/convert?from=USD&to=INR&amount=100 -> {"from":..,"to":..,"result":..}
 */
public class CurrencyWebServer {

    private static final Path WEBROOT = Path.of("webroot");
    private static final int DEFAULT_PORT = 8080;

    private final CurrencyConverterService converterService;
    private final ExchangeRateProvider rateProvider;
    private final String sourceDescription;
    private final ConversionHistoryStore historyStore;

    public CurrencyWebServer(CurrencyConverterService converterService,
                             ExchangeRateProvider rateProvider,
                             String sourceDescription,
                             ConversionHistoryStore historyStore) {
        this.converterService = converterService;
        this.rateProvider = rateProvider;
        this.sourceDescription = sourceDescription;
        this.historyStore = historyStore;
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.net.preferIPv4Stack", "true");
        java.util.logging.Logger.getLogger("org.mongodb.driver").setLevel(java.util.logging.Level.OFF);

        int port = DEFAULT_PORT;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {
                System.out.println("[info] Invalid env PORT '" + envPort + "', using fallback check");
            }
        }
        if (port == DEFAULT_PORT && args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignoredPortArg) {
                System.out.println("[info] Invalid port '" + args[0] + "', using " + DEFAULT_PORT);
            }
        }

        LiveExchangeRateProvider rateProvider = new LiveExchangeRateProvider();
        CurrencyConverterService service = new CurrencyConverterService(rateProvider);

        ConversionHistoryStore historyStore;
        String mongoUri = System.getenv("MONGODB_URI");
        if (mongoUri == null || mongoUri.isBlank()) {
            mongoUri = "mongodb://127.0.0.1:27017";
        }
        try {
            historyStore = new MongoConversionHistoryStore(mongoUri);
        } catch (Exception mongoEx) {
            historyStore = new FileConversionHistoryStore();
        }

        CurrencyWebServer app =
                new CurrencyWebServer(service, rateProvider, rateProvider.getSourceDescription(), historyStore);

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (java.net.BindException bindEx) {
            System.out.println("Port " + port + " is already in use.");
            System.out.println("  - The app may already be running: try http://localhost:" + port);
            System.out.println("    (stop it with:  taskkill /f /im java.exe)");
            System.out.println("  - Or start on another port:  java -cp out com.currencyconverter.web.CurrencyWebServer "
                    + (port == DEFAULT_PORT ? 9090 : port + 1));
            return;
        }
        server.createContext("/", app::handleStatic);
        server.createContext("/api/convert", app::handleConvert);
        server.createContext("/api/currencies", app::handleCurrencies);
        server.createContext("/api/history", app::handleHistory);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        System.out.println("==============================================");
        System.out.println("   Currency Converter - Web App");
        System.out.println("   Rates: " + app.sourceDescription);
        System.out.println("   History: " + historyStore.describe());
        System.out.println("   Open http://localhost:" + port + " in your browser");
        System.out.println("==============================================");
    }

    private void handleCurrencies(HttpExchange exchange) throws IOException {
        if (!isGet(exchange)) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed.\"}");
            return;
        }
        StringBuilder json = new StringBuilder("[");
        Currency[] values = Currency.values();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"code\":\"").append(values[i].name())
                    .append("\",\"flag\":\"").append(values[i].getFlagEmoji())
                    .append("\",\"country\":\"").append(values[i].getCountryCode().toLowerCase())
                    .append("\"}");
        }
        json.append(']');
        sendJson(exchange, 200, json.toString());
    }

    private void handleConvert(HttpExchange exchange) throws IOException {
        if (!isGet(exchange)) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed.\"}");
            return;
        }

        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        try {
            Currency from = Currency.fromCode(params.getOrDefault("from", ""));
            Currency to = Currency.fromCode(params.getOrDefault("to", ""));
            BigDecimal amount = parseAmount(params.getOrDefault("amount", ""));

            BigDecimal result = converterService.convert(from, to, amount);
            String rate = rateProvider.getRate(from, to)
                    .setScale(6, RoundingMode.HALF_UP)
                    .toPlainString();

            // Log to database/fallback history store
            historyStore.add(new ConversionHistoryEntry(
                    from.name(), from.getFlagEmoji(), from.getCountryCode().toLowerCase(),
                    to.name(), to.getFlagEmoji(), to.getCountryCode().toLowerCase(),
                    amount, result, new BigDecimal(rate), System.currentTimeMillis()
            ));

            String json = "{\"from\":\"" + from.name()
                    + "\",\"fromFlag\":\"" + from.getFlagEmoji()
                    + "\",\"fromCountry\":\"" + from.getCountryCode().toLowerCase()
                    + "\",\"to\":\"" + to.name()
                    + "\",\"toFlag\":\"" + to.getFlagEmoji()
                    + "\",\"toCountry\":\"" + to.getCountryCode().toLowerCase()
                    + "\",\"amount\":" + amount.toPlainString()
                    + ",\"rate\":" + rate
                    + ",\"result\":" + result.toPlainString()
                    + ",\"source\":\"" + escape(sourceDescription) + "\"}";
            sendJson(exchange, 200, json);
        } catch (IllegalArgumentException | CurrencyConversionException ex) {
            sendJson(exchange, 400, "{\"error\":\"" + escape(ex.getMessage()) + "\"}");
        }
    }

    private void handleHistory(HttpExchange exchange) throws IOException {
        if (isGet(exchange)) {
            List<ConversionHistoryEntry> list = historyStore.list(10);
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) json.append(",");
                ConversionHistoryEntry entry = list.get(i);
                json.append("{\"from\":\"").append(escape(entry.fromCode()))
                    .append("\",\"fromFlag\":\"").append(escape(entry.fromFlag()))
                    .append("\",\"fromCountry\":\"").append(escape(entry.fromCountry()))
                    .append("\",\"to\":\"").append(escape(entry.toCode()))
                    .append("\",\"toFlag\":\"").append(escape(entry.toFlag()))
                    .append("\",\"toCountry\":\"").append(escape(entry.toCountry()))
                    .append("\",\"amount\":").append(entry.amount().toPlainString())
                    .append(",\"result\":").append(entry.result().toPlainString())
                    .append(",\"rate\":").append(entry.rate().toPlainString())
                    .append(",\"timestamp\":").append(entry.timestamp())
                    .append("}");
            }
            json.append("]");
            sendJson(exchange, 200, json.toString());
        } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            historyStore.clear();
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        } else {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed.\"}");
        }
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (!isGet(exchange)) {
            sendText(exchange, 405, "Method not allowed.");
            return;
        }

        String urlPath = exchange.getRequestURI().getPath();
        if (urlPath.equals("/") || urlPath.isEmpty()) {
            urlPath = "/index.html";
        }

        Path resolved = WEBROOT.resolve(urlPath.substring(1)).normalize();
        if (!resolved.startsWith(WEBROOT) || !Files.isRegularFile(resolved)) {
            sendText(exchange, 404, "404 - Not Found");
            return;
        }

        byte[] body = Files.readAllBytes(resolved);
        exchange.getResponseHeaders().set("Content-Type", contentTypeOf(resolved));
        exchange.sendResponseHeaders(200, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private boolean isGet(HttpExchange exchange) {
        return "GET".equalsIgnoreCase(exchange.getRequestMethod());
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    private BigDecimal parseAmount(String rawAmount) {
        if (rawAmount == null || rawAmount.isBlank()) {
            throw new IllegalArgumentException("Amount is required.");
        }
        try {
            BigDecimal amount = new BigDecimal(rawAmount.trim());
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("Amount cannot be negative.");
            }
            return amount;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Amount must be a valid number.");
        }
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private String contentTypeOf(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}
