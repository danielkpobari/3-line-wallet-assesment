# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 wallet
COPY --from=build /app/target/three-line-wallet-*.jar app.jar
USER wallet
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
