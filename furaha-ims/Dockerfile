# Multi-stage Docker build for Furaha Inventory & Sales Management System
# STU 3101 · KCA University · Mitsanze Lee Kazungu

FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# Lean Runtime Container
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /build/target/furaha-ims.jar app.jar
COPY db ./db

ENV PORT=8080 \
    DB_URL=jdbc:mysql://mysql:3306/furaha_ims?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Africa/Nairobi&characterEncoding=utf8 \
    DB_USER=furaha_ims \
    DB_PASSWORD=furaha_ims

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
