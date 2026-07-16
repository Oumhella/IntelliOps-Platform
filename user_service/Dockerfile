# Étape 1 : Build du JAR avec Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copier et installer la librairie commune en premier
COPY common_lib/pom.xml ./common_lib/pom.xml
COPY common_lib/src ./common_lib/src
RUN mvn -f common_lib/pom.xml clean install -DskipTests

# Copier et builder le microservice
COPY user_service/pom.xml ./pom.xml
COPY user_service/src ./src
RUN mvn clean package -DskipTests

# Étape 2 : Exécution du JAR
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]