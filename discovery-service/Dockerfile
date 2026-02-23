# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /app

COPY . .

RUN mvn clean package -pl discovery-service -am -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/discovery-service/target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=default
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8761

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]