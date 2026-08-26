package com.currencyconverter.service;

import java.util.List;

/**
 * Abstraction over currency conversion log persistence.
 */
public interface ConversionHistoryStore {
    void add(ConversionHistoryEntry entry);

    List<ConversionHistoryEntry> list(int limit);

    void clear();

    String describe();
}
