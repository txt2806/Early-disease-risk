package com.cardio.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() {
        try {
            // Đọc tệp dịch vụ từ thư mục resources
            ClassPathResource resource = new ClassPathResource("firebase-service-account.json");
            if (resource.exists()) {
                InputStream serviceAccount = resource.getInputStream();

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp(options);
                    System.out.println(">>> KHỞI TẠO FIREBASE ADMIN SDK THÀNH CÔNG <<<");
                }
            } else {
                System.err.println(">>> WARNING: firebase-service-account.json không tồn tại trong src/main/resources. Xác thực Firebase sẽ bị bỏ qua hoặc lỗi khi gọi API.");
            }
        } catch (IOException e) {
            System.err.println("Lỗi khởi tạo Firebase SDK: " + e.getMessage());
        }
    }
}
