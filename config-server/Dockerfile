# Étape 1 : Utiliser une image de build temporaire pour extraire ou compiler
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Copier le fichier pom.xml du parent et du config-server pour mettre en cache les dépendances
COPY pom.xml /app/
COPY config-server/pom.xml /app/config-server/

# Copier le code source du config-server
COPY config-server/src /app/config-server/src

# Compiler et packager l'application en ignorant les tests
WORKDIR /app/config-server
RUN mvn clean package -DskipTests

# Étape 2 : Image finale d'exécution ultra-légère (production-ready)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Créer un utilisateur non-root pour des raisons de sécurité en production
RUN addgroup -S spring && adduser -S spring -G spring

# Copier le fichier JAR compilé depuis l'étape précédente
COPY --from=builder /app/config-server/target/*.jar app.jar

# Copier et rendre exécutable le script d'entrypoint
COPY config-server/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh && chown spring:spring /docker-entrypoint.sh app.jar

USER spring:spring

EXPOSE 8888

ENTRYPOINT ["/docker-entrypoint.sh"]