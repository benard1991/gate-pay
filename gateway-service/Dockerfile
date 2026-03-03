# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /app

# Copy root pom and ALL module pom files
COPY pom.xml .
COPY auth-service/pom.xml auth-service/
COPY user-service/pom.xml user-service/
COPY notification-service/pom.xml notification-service/
COPY discovery-service/pom.xml discovery-service/
COPY kyc-service/pom.xml kyc-service/
COPY payment-service/pom.xml payment-service/
COPY gateway-service/pom.xml gateway-service/

# Download dependencies with retry loop + BuildKit cache
RUN --mount=type=cache,target=/root/.m2 \
    for i in 1 2 3 4 5; do \
      mvn dependency:go-offline -pl gateway-service -am \
        -Dmaven.wagon.http.retryHandler.count=5 \
        -Dmaven.wagon.http.pool=false \
        -Djdk.tls.client.protocols=TLSv1.2 \
        -Dhttps.protocols=TLSv1.2 && break || \
      echo "Attempt $i failed, retrying in 20s..." && sleep 20; \
    done

# Copy the rest of the source code
COPY . .

# Build only the gateway-service module and its dependencies, skip tests
RUN --mount=type=cache,target=/root/.m2 \
    for i in 1 2 3 4 5; do \
      mvn clean package -pl gateway-service -am -DskipTests \
        -Dmaven.wagon.httpconnectionManager.ttlSeconds=25 \
        -Dmaven.wagon.http.retryHandler.count=5 \
        -Dmaven.wagon.http.pool=false \
        -Djdk.tls.client.protocols=TLSv1.2 \
        -Dhttps.protocols=TLSv1.2 && break || \
      echo "Attempt $i failed, retrying in 20s..." && sleep 20; \
    done

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/gateway-service/target/*.jar gateway-service.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar gateway-service.jar"]