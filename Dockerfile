FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/currency-converter-1.0.jar app.jar
COPY webroot ./webroot
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
