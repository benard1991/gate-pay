# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the entire project for multi-module resolution
COPY . .

# Build only the auth-service module and its dependencies, skip tests
RUN mvn clean package -pl auth-service -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the auth-service JAR
COPY --from=build /app/auth-service/target/*.jar app.jar

# Expose port
EXPOSE 8081

# JVM options (optional)
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
