package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.DeviceToken;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findByUserIdAndActiveTrue(Long userId);

    /** Bổ sung ngoài SDD gốc: dedupe token cũ cùng thiết bị khi đăng ký token mới — xem NotificationService.registerDeviceToken. */
    List<DeviceToken> findByUserIdAndDeviceIdAndActiveTrue(Long userId, String deviceId);

    Optional<DeviceToken> findByToken(String token);
}
