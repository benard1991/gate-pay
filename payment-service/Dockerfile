# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-17 AS build

WORKDIR /app

# Copy the entire multi-module project (root POM + modules)
COPY . .

# Build only the payment-service module and its dependencies
RUN mvn clean package -pl payment-service -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/payment-service/target/*.jar payment-service.jar

# Expose the service port
EXPOSE 8083

# JVM optimization
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Health check (optional)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8083/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar payment-service.jar"]
