# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY src/main/resources/application.yaml .
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/siamois.jar .
COPY --from=build /app/application.yaml .
ENTRYPOINT ["java", "-jar", "siamois.jar"]