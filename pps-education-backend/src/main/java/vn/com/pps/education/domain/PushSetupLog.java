package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Bảng push_setup_logs (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-09-07 — xem V164__push_setup_logs.sql) — nhật ký kết quả từng lần
 * chạy setupPushNotifications() phía client (thành công/thất bại + lý do),
 * phục vụ debug qua SQL khi push không hoạt động (không có Safari Web
 * Inspector để remote-debug). Bảng ghi đơn, không sửa/xoá.
 */
@Getter
@Setter
@Entity
@Table(name = "push_setup_logs")
public class PushSetupLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(length = 20)
    private String platform;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
