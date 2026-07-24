# Bước 1: Build ứng dụng từ source code
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
# Tải trước các dependencies để lưu cache lớp Docker (tăng tốc độ build)
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Bước 2: Khởi chạy ứng dụng bằng JDK Eclipse Temurin trên nền Ubuntu Jammy ổn định
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/cardio-doctor-portal-1.0.0.jar app.jar
EXPOSE 8080

# Cấu hình tối ưu hóa DNS và mạng IPv4
# Sử dụng SerialGC và TieredStopAtLevel=1 để tối ưu bộ nhớ & CPU trên container nhỏ (giảm RAM và giảm độ trễ phản hồi)
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-Dsun.net.inetaddr.ttl=30", "-Dsun.net.inetaddr.negative.ttl=0", "-Xmx350m", "-Xms350m", "-jar", "app.jar"]
