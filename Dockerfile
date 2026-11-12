FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /project
COPY java/pom.xml java/pom.xml
COPY java/code java/code
COPY java/settings java/settings
COPY kotlin kotlin
COPY checks/java checks/java
COPY database/migrations database/migrations
RUN mvn -f java/pom.xml -B package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /project/java/target/dependency-hell-0.1.0.jar /app/service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/service.jar"]
