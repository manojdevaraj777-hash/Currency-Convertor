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

/**
 * Connects RatesHub AI chatbot to Google Gemini API (gemini-3.5-flash-lite)
 * with automatic fallback to gemini-3.1-flash-lite and prompt grounding on live exchange rates.
 */
public class GeminiChatService {

    private static final String MODEL_PRIMARY = "gemini-3.5-flash-lite";
    private static final String MODEL_BACKUP = "gemini-3.1-flash-lite";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final HttpClient httpClient;

    public GeminiChatService() {
        this(resolveApiKey());
    }

    public GeminiChatService(String apiKey) {
        this.apiKey = (apiKey != null) ? apiKey.trim() : null;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String ask(String userMessage, ExchangeRateProvider rateProvider) throws IOException, InterruptedException {
        if (!isConfigured()) {
            throw new IllegalStateException("Gemini API key is not configured.");
        }

        String prompt = buildPromptWithContext(userMessage, rateProvider);
        String requestJson = buildRequestBody(prompt);

        try {
            return callModel(MODEL_PRIMARY, requestJson);
        } catch (Exception ex) {
            System.out.println("[info] Gemini primary model (" + MODEL_PRIMARY + ") failed: " + ex + ", trying backup: " + MODEL_BACKUP);
            try {
                return callModel(MODEL_BACKUP, requestJson);
            } catch (Exception backupEx) {
                System.out.println("[warn] Gemini backup model (" + MODEL_BACKUP + ") also failed: " + backupEx);
                if (backupEx instanceof IOException) throw (IOException) backupEx;
                if (backupEx instanceof InterruptedException) throw (InterruptedException) backupEx;
                throw new IOException(backupEx);
            }
        }
    }

    private String callModel(String model, String requestJson) throws IOException, InterruptedException {
        String endpoint = BASE_URL + model + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Gemini API (" + model + ") returned status " + response.statusCode() + ": " + response.body());
        }

        return extractCandidateText(response.body());
    }

    private String buildPromptWithContext(String userMessage, ExchangeRateProvider rateProvider) {
        StringBuilder sampleRates = new StringBuilder();
        Currency[] samples = {
            Currency.EUR, Currency.GBP, Currency.INR, Currency.JPY,
            Currency.CAD, Currency.AUD, Currency.CHF, Currency.CNY,
            Currency.AED, Currency.SGD
        };

        for (Currency c : samples) {
            try {
                BigDecimal r = rateProvider.getRate(Currency.USD, c);
                sampleRates.append("1 USD = ")
                        .append(r.setScale(4, RoundingMode.HALF_UP).toPlainString())
                        .append(" ")
                        .append(c.name())
                        .append(", ");
            } catch (Exception ignored) {}
        }

        return "System Instructions:\n"
                + "You are RatesHub AI, a friendly, concise, and expert financial & currency assistant embedded inside the RatesHub Currency Converter web app.\n"
                + "Today's live base exchange rates (USD base):\n"
                + sampleRates.toString() + "\n\n"
                + "Guidelines:\n"
                + "1. Answer questions about currencies, exchange rates, conversions, travel budgets, travel advice, and economics clearly.\n"
                + "2. If the user asks to convert currencies (e.g., '100 EUR in USD' or 'convert 50 bucks to yen'), perform the calculation and state the result prominently.\n"
                + "3. Always format numbers nicely and use clean markdown (bold for amounts and currency codes, bullet points for lists).\n"
                + "4. Explicitly include 3-letter ISO currency codes (like USD, EUR, INR, GBP, JPY, CAD) whenever discussing currencies so our interface can offer quick action buttons.\n"
                + "5. Keep replies concise, helpful, and under 150 words unless the user asks for detailed analysis.\n\n"
                + "User Question: " + userMessage;
    }

    private String buildRequestBody(String promptText) {
        return "{\"contents\":[{\"parts\":[{\"text\":\"" + jsonEscape(promptText) + "\"}]}]}";
    }

    private String extractCandidateText(String responseJson) {
        int textIdx = responseJson.indexOf("\"text\": \"");
        if (textIdx < 0) {
            textIdx = responseJson.indexOf("\"text\":\"");
            if (textIdx < 0) return "I received an empty response. Please try again.";
            textIdx += 8;
        } else {
            textIdx += 9;
        }

        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = textIdx; i < responseJson.length(); i++) {
            char ch = responseJson.charAt(i);
            if (escaped) {
                if (ch == 'n') sb.append('\n');
                else if (ch == 'r') {}
                else if (ch == 't') sb.append('\t');
                else if (ch == '"') sb.append('"');
                else if (ch == '\\') sb.append('\\');
                else if (ch == 'u' && i + 4 < responseJson.length()) {
                    try {
                        String hex = responseJson.substring(i + 1, i + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    } catch (Exception ignored) {
                        sb.append('u');
                    }
                }
                else sb.append(ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                break;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString().trim();
    }

    private String jsonEscape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t");
    }

    private static String resolveApiKey() {
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        Path keyFile = Path.of("gemini.key");
        if (Files.exists(keyFile)) {
            try {
                String fileKey = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
                if (!fileKey.isBlank()) return fileKey;
            } catch (IOException ignored) {}
        }
        return null;
    }
}
