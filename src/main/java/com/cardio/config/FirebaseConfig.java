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
            InputStream serviceAccount = null;

            // 1. Ưu tiên đọc từ Biến môi trường (Dành cho Render/Production)
            String firebaseEnv = System.getenv("FIREBASE_CREDENTIALS");
            if (firebaseEnv != null && !firebaseEnv.trim().isEmpty()) {
                serviceAccount = new java.io.ByteArrayInputStream(firebaseEnv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                System.out.println(">>> ĐANG KHỞI TẠO FIREBASE ADMIN SDK TỪ BIẾN MÔI TRƯỜNG <<<");
            } else {
                // 2. Dự phòng đọc từ file local (Dành cho Local Dev)
                ClassPathResource resource = new ClassPathResource("firebase-service-account.json");
                if (resource.exists()) {
                    serviceAccount = resource.getInputStream();
                    System.out.println(">>> ĐANG KHỞI TẠO FIREBASE ADMIN SDK TỪ FILE LOCAL <<<");
                }
            }

            if (serviceAccount != null) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp(options);
                    System.out.println(">>> KHỞI TẠO FIREBASE ADMIN SDK THÀNH CÔNG <<<");
                }
            } else {
                System.err.println(">>> WARNING: Không tìm thấy FIREBASE_CREDENTIALS trong biến môi trường và file firebase-service-account.json không tồn tại. Xác thực Firebase sẽ bị lỗi!");
            }
        } catch (IOException e) {
            System.err.println("Lỗi khởi tạo Firebase SDK: " + e.getMessage());
        }
    }
}
