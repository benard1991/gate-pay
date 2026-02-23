# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the full project (parent POM + all modules)
COPY . .

# Build only the gateway-service module and its dependencies, skip tests
RUN mvn clean package -pl gateway-service -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the gateway-service JAR
COPY --from=build /app/gateway-service/target/*.jar gateway-service.jar

# Expose the port
EXPOSE 8080

# JVM options (optional)
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar gateway-service.jar"]