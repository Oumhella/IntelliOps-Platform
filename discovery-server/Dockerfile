# Étape 1 : Utiliser une image de build temporaire pour compiler l'application
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Copier le fichier pom.xml du parent et du module pour mettre en cache les dépendances
COPY pom.xml /app/
COPY discovery-server/pom.xml /app/discovery-server/

# Copier le code source du discovery-server
COPY discovery-server/src /app/discovery-server/src

# Compiler et packager l'application en ignorant les tests pour accélérer le build de l'image
WORKDIR /app/discovery-server
RUN mvn clean package -DskipTests

# Étape 2 : Image finale d'exécution (Production-ready)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Créer un groupe et un utilisateur "spring" pour ne pas exécuter l'application en tant que root
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copier le fichier JAR compilé depuis l'étape précédente
COPY --from=builder /app/discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar app.jar

# Exposer le port par défaut d'Eureka Server
EXPOSE 8761

# Lancement de l'application
ENTRYPOINT ["java", "-jar", "app.jar"]