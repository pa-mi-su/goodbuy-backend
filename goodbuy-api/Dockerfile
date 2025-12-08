# ---- build stage ----
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy parent + module POMs first for layer caching & multi-module awareness
COPY pom.xml .
COPY goodbuy-core/pom.xml                 goodbuy-core/pom.xml
COPY goodbuy-adapters-catalog/pom.xml     goodbuy-adapters-catalog/pom.xml
COPY goodbuy-adapters-core/pom.xml        goodbuy-adapters-core/pom.xml
COPY goodbuy-api/pom.xml                  goodbuy-api/pom.xml

# Pre-fetch dependencies for all modules defined in parent pom
RUN mvn -q -B -U -DskipTests dependency:go-offline

# Now copy module sources
COPY goodbuy-core/                 goodbuy-core/
COPY goodbuy-adapters-catalog/     goodbuy-adapters-catalog/
COPY goodbuy-adapters-core/        goodbuy-adapters-core/
COPY goodbuy-api/                  goodbuy-api/

# Build API (and required modules via -am)
RUN mvn -q -B -DskipTests -pl goodbuy-api -am package

# ---- run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Install curl for healthchecks
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Copy the fat jar built in the previous stage
COPY --from=build /workspace/goodbuy-api/target/goodbuy-api-*.jar /app/app.jar

# Default port (overridable)
ENV SERVER_PORT=8080
EXPOSE 8080

# Container-aware JVM settings; profile overridable at runtime
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
ENV SPRING_PROFILES_ACTIVE=dev

ENTRYPOINT ["java","-jar","/app/app.jar"]
