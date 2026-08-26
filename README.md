# RatesHub — Currency Converter

A clean, professional Java **web application** for converting between 155 global currencies with live daily rates, country flags, historical trend charts, conversion history, and an AI chatbot — built with zero frameworks.

## Features

- 155 supported currencies (ISO 4217) with country flag images
- Live daily exchange rates fetched from free public APIs (no API key needed)
- Daily local cache (`rates-cache.txt`) — same-day runs are instant and offline-friendly
- Automatic fallback chain: live API → cache → built-in sample rates
- Conversion history persisted to **MongoDB Atlas** (with file fallback)
- Historical trend chart seeded from the live rate
- AI assistant chatbot that can perform conversions from natural language
- Dark glassmorphism UI — pure HTML/CSS/JS, no frameworks
- JSON REST API using Java's built-in `com.sun.net.httpserver` — no Spring, no Tomcat

## Project Structure

```
src/
  com/currencyconverter/
    model/
      Currency.java                        # 155 ISO currencies + country codes + flag emoji
    service/
      CurrencyConverterService.java        # core conversion logic (BigDecimal)
      CurrencyConversionException.java
      ExchangeRateProvider.java            # rate source interface
      InMemoryExchangeRateProvider.java    # offline fallback sample rates
      LiveExchangeRateProvider.java        # live rates + cache + fallback chain
      ConversionHistoryStore.java          # history persistence interface
      MongoConversionHistoryStore.java     # MongoDB implementation
      FileConversionHistoryStore.java      # JSONL file fallback
      ConversionHistoryEntry.java          # history record record
    web/
      CurrencyWebServer.java               # HTTP server: static files + JSON API
webroot/
  index.html                               # 5-tab SPA: Converter, Rates, Charts, History, AI Bot
  style.css                                # dark glassmorphism theme
  app.js                                   # frontend logic (~700 lines)
lib/
  bson-5.2.1.jar                           # MongoDB driver dependencies (shaded into fat JAR)
  mongodb-driver-core-5.2.1.jar
  mongodb-driver-sync-5.2.1.jar
pom.xml                                    # Maven build — produces fat JAR via shade plugin
render.yaml                                # Render deployment config
```

## How to Run (Local)

### Option A: javac (quick, no Maven)

```bash
javac -encoding UTF-8 -cp "lib/*" -d out src/com/currencyconverter/model/Currency.java src/com/currencyconverter/service/*.java src/com/currencyconverter/web/CurrencyWebServer.java
java -cp "out;lib/*" com.currencyconverter.web.CurrencyWebServer
```

Then open **http://localhost:8080**.

Optional custom port:

```bash
java -cp "out;lib/*" com.currencyconverter.web.CurrencyWebServer 9090
```

### Option B: Maven (needed for Render deployment)

```bash
mvn package -DskipTests
java -jar target/currency-converter-1.0.jar
```

## MongoDB Setup (Optional — for persistent history)

The app stores conversion history in MongoDB with automatic file fallback.

**Local:** Install [MongoDB Community](https://www.mongodb.com/try/download/community) — the app connects to `mongodb://127.0.0.1:27017` by default.

**Render / Cloud:** Set the `MONGODB_URI` environment variable to your [MongoDB Atlas](https://www.mongodb.com/atlas) connection string (free M0 tier is fine):

```
MONGODB_URI=mongodb+srv://<user>:<password>@cluster0.xxxxx.mongodb.net/currency_db?retryWrites=true&w=majority
```

Data lives in database `currency_db`, collection `history`.

## API Endpoints

```
GET  /api/currencies                          -> [{"code":"USD","flag":"🇺🇸","country":"us"}, ...]
GET  /api/convert?from=USD&to=INR&amount=100  -> {"from":"USD","result":9579.37,"rate":95.7937, ...}
GET  /api/history                             -> [{"from":"USD","to":"INR","amount":100,"result":9579.37, ...}]
DELETE /api/history                           -> clears all history
```

## Deploy to Render

1. Push this repository to GitHub
2. Log in to [render.com](https://render.com) → **New** → **Web Service**
3. Connect your GitHub repo
4. Render auto-detects `render.yaml`; confirm these settings:
   - **Build Command:** `mvn package -DskipTests`
   - **Start Command:** `java -jar target/currency-converter-1.0.jar`
   - **Java Version:** 17
5. Under **Environment**, add:
   - `MONGODB_URI` = your Atlas connection string (from above)
6. Click **Create Web Service** — Render builds and deploys automatically

The app reads the `PORT` env var provided by Render (do not hardcode a port).

## Study Notes

1. `CurrencyWebServer` serves static files from `webroot/` and the JSON API — all from a single process.
2. The server reads `PORT` and `MONGODB_URI` from environment variables (with sensible defaults for local dev).
3. `CurrencyConverterService` uses `BigDecimal` throughout — no floating-point rounding errors.
4. `LiveExchangeRateProvider` fetches rates once per day via a background daemon thread with a 20s startup timeout, keeping startup fast even on slow networks.
5. Cross-rates are calculated via a USD base:

```
convertedAmount = amount * rate(from → to)
rate(from → to) = usdRate(to) / usdRate(from)
```

6. All monetary results are rounded to 2 decimal places with `HALF_UP`.
7. Delete `rates-cache.txt` (or wait for tomorrow) to force a fresh rate fetch.
