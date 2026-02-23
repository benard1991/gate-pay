# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /app

COPY . .

RUN mvn clean package -pl notification-service -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/notification-service/target/*.jar app.jar

EXPOSE 8084

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]