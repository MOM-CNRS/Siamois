# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY primefaces-themes ./primefaces-themes
# Le build Maven installe Node puis construit l'app React (frontend/) embarquée dans le jar
COPY frontend ./frontend
COPY src/main/resources/application.yaml .
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/siamois.jar .
COPY --from=build /app/application.yaml .
ENTRYPOINT ["java", "-jar", "siamois.jar"]
