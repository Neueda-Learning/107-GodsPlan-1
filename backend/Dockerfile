# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -q package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S payments && adduser -S payments -G payments
USER payments
WORKDIR /app
COPY --from=build /app/target/payments-api-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
