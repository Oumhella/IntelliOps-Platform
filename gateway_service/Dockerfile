# Étape 1 : Build du JAR avec Maven
# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# On copie le pom.xml parent et le pom.xml du sous-module
COPY pom.xml .
COPY gateway_service/pom.xml ./gateway_service/
COPY gateway_service/src ./gateway_service/src

# On compile spécifiquement le sous-module
WORKDIR /app/gateway_service
RUN --mount=type=cache,id=intelliops-maven,target=/root/.m2,sharing=locked \
    mvn clean package -DskipTests -Dmaven.wagon.http.retryHandler.count=5

# Étape 2 : Exécution du JAR
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Sécurité : Éviter de tourner en ROOT
RUN for attempt in 1 2 3; do \
      apk upgrade --no-cache && break; \
      if [ "$attempt" = "3" ]; then exit 1; fi; \
      sleep $((attempt * 5)); \
    done \
    && addgroup -S spring \
    && adduser -S spring -G spring
USER spring:spring

# On récupère le JAR compilé dans le dossier cible du sous-module
COPY --from=build /app/gateway_service/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
