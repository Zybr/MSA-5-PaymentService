FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /workspace/target/payment-orchestrator-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "--add-modules", "jdk.httpserver", "-jar", "/app/app.jar"]
