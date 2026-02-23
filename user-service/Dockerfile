# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-17 AS build

WORKDIR /app

COPY . .

RUN mvn clean package -pl user-service -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/user-service/target/*.jar user-service.jar

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

EXPOSE 8082

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar user-service.jar"]