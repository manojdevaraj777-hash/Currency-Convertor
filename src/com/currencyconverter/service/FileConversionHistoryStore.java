package com.currencyconverter.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * File-based fallback history store (currency-history.jsonl, newest first on read).
 */
public class FileConversionHistoryStore implements ConversionHistoryStore {

    private static final Path FILE = Path.of("currency-history.jsonl");

    @Override
    public void add(ConversionHistoryEntry entry) {
        String line = "{\"fromCode\":\"" + escape(entry.fromCode()) + "\""
                + ",\"fromFlag\":\"" + escape(entry.fromFlag()) + "\""
                + ",\"fromCountry\":\"" + escape(entry.fromCountry()) + "\""
                + ",\"toCode\":\"" + escape(entry.toCode()) + "\""
                + ",\"toFlag\":\"" + escape(entry.toFlag()) + "\""
                + ",\"toCountry\":\"" + escape(entry.toCountry()) + "\""
                + ",\"amount\":" + entry.amount().toPlainString()
                + ",\"result\":" + entry.result().toPlainString()
                + ",\"rate\":" + entry.rate().toPlainString()
                + ",\"timestamp\":" + entry.timestamp()
                + "}";
        try {
            Files.writeString(FILE, line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            System.out.println("[warn] Could not write currency history file: " + ex.getMessage());
        }
    }

    @Override
    public List<ConversionHistoryEntry> list(int limit) {
        if (!Files.exists(FILE)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
            Collections.reverse(lines);

            List<ConversionHistoryEntry> entries = new ArrayList<>();
            for (String line : lines) {
                if (entries.size() >= limit) {
                    break;
                }
                String fromCode = readField(line, "fromCode");
                String fromFlag = readField(line, "fromFlag");
                String fromCountry = readField(line, "fromCountry");
                String toCode = readField(line, "toCode");
                String toFlag = readField(line, "toFlag");
                String toCountry = readField(line, "toCountry");
                String amountStr = readField(line, "amount");
                String resultStr = readField(line, "result");
                String rateStr = readField(line, "rate");
                String stampStr = readField(line, "timestamp");

                if (fromCode != null && toCode != null && amountStr != null && resultStr != null && rateStr != null && stampStr != null) {
                    entries.add(new ConversionHistoryEntry(
                            fromCode, fromFlag, fromCountry,
                            toCode, toFlag, toCountry,
                            new BigDecimal(amountStr),
                            new BigDecimal(resultStr),
                            new BigDecimal(rateStr),
                            Long.parseLong(stampStr)
                    ));
                }
            }
            return entries;
        } catch (IOException | NumberFormatException ex) {
            return List.of();
        }
    }

    @Override
    public void clear() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException ex) {
            System.out.println("[warn] Could not delete currency history file: " + ex.getMessage());
        }
    }

    @Override
    public String describe() {
        return "local file (" + FILE.getFileName() + ")";
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String readField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            // Check for numeric field (unquoted)
            String numKey = "\"" + field + "\":";
            int numStart = json.indexOf(numKey);
            if (numStart < 0) return null;
            int numEnd = json.indexOf(",", numStart);
            if (numEnd < 0) numEnd = json.indexOf("}", numStart);
            return json.substring(numStart + numKey.length(), numEnd).trim();
        }
        int end = json.indexOf("\"", start + key.length());
        return json.substring(start + key.length(), end);
    }
}
