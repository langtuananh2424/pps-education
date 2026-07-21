# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --no-create-home appuser
COPY --from=build /app/target/*.jar app.jar
# Thư mục lưu file upload (MediaStorageService) - tạo + chown trước khi đổi
# sang appuser (non-root) để tránh lỗi Permission denied khi ghi file lúc
# runtime; Docker sẽ giữ nguyên ownership này khi named volume gắn đè lên
# (xem docker-compose.yml - volume pps_media_data).
RUN mkdir -p /app/media-storage && chown -R appuser /app/media-storage
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
