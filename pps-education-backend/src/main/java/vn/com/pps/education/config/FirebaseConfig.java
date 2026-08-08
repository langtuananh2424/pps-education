package vn.com.pps.education.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Kênh PUSH của module Notification (FCM) — bổ sung ngoài SDD gốc, đã xác
 * nhận với người dùng (2026-08-08). Credential service account (JSON) truyền
 * qua biến môi trường FIREBASE_CREDENTIALS_BASE64 (base64 của file JSON tải
 * từ Firebase Console). Nếu chưa cấu hình (biến rỗng — mặc định ở máy
 * dev/CI), bean trả về null thay vì crash context lúc khởi động (cùng
 * convention với SMTP chưa cấu hình ở EmailNotificationSender) —
 * PushNotificationSender tự kiểm tra null và throw lỗi có kiểm soát khi
 * thực sự có notification cần gửi qua PUSH.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseMessaging firebaseMessaging(
            @Value("${app.notification.firebase.credentials-base64}") String credentialsBase64) {
        if (credentialsBase64 == null || credentialsBase64.isBlank()) {
            log.warn("FIREBASE_CREDENTIALS_BASE64 chưa cấu hình - kênh PUSH sẽ báo lỗi có kiểm soát khi được gọi");
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(credentialsBase64);
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            return FirebaseMessaging.getInstance(app);
        } catch (Exception ex) {
            log.error("Khởi tạo Firebase thất bại - kênh PUSH sẽ báo lỗi có kiểm soát khi được gọi", ex);
            return null;
        }
    }
}
