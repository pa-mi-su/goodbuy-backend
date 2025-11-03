# ---- build stage ----
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy POMs first for layer caching
COPY pom.xml .
COPY goodbuy-core/pom.xml goodbuy-core/pom.xml
COPY goodbuy-api/pom.xml  goodbuy-api/pom.xml

# Pre-fetch deps for all modules defined in the parent pom
RUN mvn -q -B -U -DskipTests dependency:go-offline

# Now copy sources
COPY goodbuy-core/ goodbuy-core/
COPY goodbuy-api/  goodbuy-api/

# Build API (and any required modules via -am)
RUN mvn -q -B -DskipTests -pl goodbuy-api -am package

# ---- run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Install curl for container healthchecks
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Copy the fat jar
COPY --from=build /workspace/goodbuy-api/target/goodbuy-api-*.jar /app/app.jar

# Default port (overridable)
ENV SERVER_PORT=8080
EXPOSE 8080

# Use container-aware JVM settings; profile can be overridden at runtime
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
ENV SPRING_PROFILES_ACTIVE=default

ENTRYPOINT ["java","-jar","/app/app.jar"]
