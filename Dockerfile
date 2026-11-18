# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- run ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd -r -u 1001 app && mkdir -p /app/data && chown app /app/data
COPY --from=build /app/target/smart-watchlist-2.0.0.jar app.jar
USER app
ENV PORT=8080 JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=70 -XX:TieredStopAtLevel=1"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:${PORT}/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
