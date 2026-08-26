# Build stage
FROM openjdk:17-slim AS build
WORKDIR /app
COPY src/ /app/src/
COPY lib/ /app/lib/
RUN mkdir -p out && javac -encoding UTF-8 -cp "lib/*" -d out src/com/currencyconverter/model/Currency.java src/com/currencyconverter/service/*.java src/com/currencyconverter/web/CurrencyWebServer.java

# Run stage
FROM openjdk:17-slim
WORKDIR /app
COPY --from=build /app/out /app/out
COPY --from=build /app/lib /app/lib
COPY webroot/ /app/webroot/
EXPOSE 8080
CMD ["java", "-cp", "out:lib/*", "com.currencyconverter.web.CurrencyWebServer"]
