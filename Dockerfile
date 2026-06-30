# Bước 1: Build ứng dụng từ source code
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Bước 2: Khởi chạy ứng dụng bằng JDK Eclipse Temurin trên nền Ubuntu Jammy ổn định
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/cardio-doctor-portal-1.0.0.jar app.jar
EXPOSE 8080

# Cấu hình tối ưu hóa DNS (không lưu cache DNS lỗi) và mạng IPv4 để tránh UnknownHostException
# Giới hạn bộ nhớ tối đa 350MB RAM để phù hợp với gói Free của Render
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-Dsun.net.inetaddr.ttl=30", "-Dsun.net.inetaddr.negative.ttl=0", "-Xmx350m", "-Xms350m", "-jar", "app.jar"]
