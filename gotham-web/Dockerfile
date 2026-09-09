# syntax=docker/dockerfile:1
# =============================================================================
#  gotham-web image — multi-stage build on Temurin 25 (matches the JDK 25 baseline).
#  Build context is the REPO ROOT (see docker-compose.yml) so the Maven reactor
#  (parent + gotham-common + gotham-web) is available to the build.
# =============================================================================

# ---- Stage 1: build the executable jar with the Maven Wrapper ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

# Copy the reactor descriptors first to leverage Docker layer caching for dependencies.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY gotham-common/pom.xml gotham-common/pom.xml
COPY gotham-web/pom.xml gotham-web/pom.xml
COPY gotham-datagen/pom.xml gotham-datagen/pom.xml
COPY elasticsearch/ elasticsearch/
RUN chmod +x mvnw && ./mvnw -q -B -pl gotham-web -am -DskipTests dependency:go-offline

# Now copy sources and package (skip tests: image builds must not require live ES/GCS).
COPY gotham-common/src gotham-common/src
COPY gotham-web/src gotham-web/src
RUN ./mvnw -q -B -pl gotham-web -am -DskipTests package

# ---- Stage 2: slim runtime ----
FROM eclipse-temurin:25-jdk AS run
WORKDIR /app
COPY --from=build /src/gotham-web/target/gotham-web-*.jar /app/gotham-web.jar

# Real secrets are provided at runtime (mounted secrets/ + env vars), never baked in.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/gotham-web.jar"]
