# Bước 1: Build ứng dụng từ source code
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Bước 2: Khởi chạy ứng dụng bằng JDK Eclipse Temurin siêu nhẹ, chính thức và ổn định
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/cardio-doctor-portal-1.0.0.jar app.jar
EXPOSE 8080

# Cấu hình ưu tiên kết nối IPv4 để sửa lỗi kết nối Supabase trên Render
# Giới hạn bộ nhớ tối đa 350MB RAM để phù hợp với gói Free của Render
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-Xmx350m", "-Xms350m", "-jar", "app.jar"]
