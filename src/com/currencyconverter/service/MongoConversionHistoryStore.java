package com.currencyconverter.service;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Persists currency conversion logs in MongoDB (database: currency_db, collection: history).
 */
public class MongoConversionHistoryStore implements ConversionHistoryStore {

    private static final String DATABASE = "currency_db";
    private static final String COLLECTION = "history";

    private final MongoClient client;
    private final MongoCollection<Document> collection;

    public MongoConversionHistoryStore(String connectionString) {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .applyToClusterSettings(builder -> builder
                        .serverSelectionTimeout(2, TimeUnit.SECONDS))
                .build();

        client = MongoClients.create(settings);
        MongoDatabase database = client.getDatabase(DATABASE);
        collection = database.getCollection(COLLECTION);

        // Warm up and test database presence
        collection.countDocuments();
    }

    @Override
    public void add(ConversionHistoryEntry entry) {
        try {
            collection.insertOne(new Document()
                    .append("fromCode", entry.fromCode())
                    .append("fromFlag", entry.fromFlag())
                    .append("fromCountry", entry.fromCountry())
                    .append("toCode", entry.toCode())
                    .append("toFlag", entry.toFlag())
                    .append("toCountry", entry.toCountry())
                    .append("amount", entry.amount().toPlainString())
                    .append("result", entry.result().toPlainString())
                    .append("rate", entry.rate().toPlainString())
                    .append("timestamp", entry.timestamp()));
        } catch (MongoException ex) {
            System.out.println("[warn] Could not save currency log to MongoDB: " + ex.getMessage());
        }
    }

    @Override
    public List<ConversionHistoryEntry> list(int limit) {
        List<ConversionHistoryEntry> entries = new ArrayList<>();
        try {
            for (Document doc : collection.find()
                    .sort(Sorts.descending("timestamp"))
                    .limit(limit)) {
                entries.add(new ConversionHistoryEntry(
                        doc.getString("fromCode"),
                        doc.getString("fromFlag"),
                        doc.getString("fromCountry"),
                        doc.getString("toCode"),
                        doc.getString("toFlag"),
                        doc.getString("toCountry"),
                        new BigDecimal(doc.getString("amount")),
                        new BigDecimal(doc.getString("result")),
                        new BigDecimal(doc.getString("rate")),
                        doc.getLong("timestamp")));
            }
        } catch (MongoException ex) {
            System.out.println("[warn] Could not read currency logs from MongoDB: " + ex.getMessage());
        }
        return entries;
    }

    @Override
    public void clear() {
        try {
            collection.deleteMany(new Document());
        } catch (MongoException ex) {
            System.out.println("[warn] Could not clear MongoDB currency history: " + ex.getMessage());
        }
    }

    @Override
    public String describe() {
        return "MongoDB (" + DATABASE + "." + COLLECTION + ")";
    }
}
