# syntax=docker/dockerfile:1
#
# One Dockerfile for every service; docker-compose passes the module name as
# the SERVICE build arg. The build context is the repo root because Maven's
# reactor needs the parent pom.xml AND every module's pom.xml listed in it,
# even when building just one module with -pl.

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /app

# POMs first: dependency resolution is cached until a POM changes.
COPY pom.xml .
COPY url-service/pom.xml url-service/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY analytics-service/pom.xml analytics-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
# sharing=locked: compose builds all five images in parallel, and Maven's
# local repository isn't safe for concurrent writers.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q -pl ${SERVICE} -am dependency:go-offline

COPY ${SERVICE}/src ${SERVICE}/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q -pl ${SERVICE} -am package -DskipTests \
 && cp ${SERVICE}/target/${SERVICE}-*.jar /app/app.jar

# ---- Run stage ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/app.jar app.jar
# Never run the JVM as root inside the container.
USER app
# Size the heap from the container's memory limit, and restart (via the
# orchestrator) on OOM instead of limping along.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
