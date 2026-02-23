# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the entire project so Maven can resolve parent POM and dependencies
COPY . .

# Build only kyc-service module and its dependencies, skip tests
RUN mvn clean package -pl kyc-service -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the KYC service JAR
COPY --from=build /app/kyc-service/target/*.jar app.jar

# Optional: environment variables
ENV JAVA_OPTS=""

# Expose the service port
EXPOSE 8085

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
