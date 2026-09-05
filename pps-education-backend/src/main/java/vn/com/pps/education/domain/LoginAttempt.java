package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Bảng login_attempts (SDD > Nền tảng > j) — nhật ký đăng nhập, dùng cho
 * chống Brute-Force (FR-AUT-02) và audit (UC-01 Main Flow bước 6, A1-A3).
 */
@Getter
@Setter
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    public enum FailureReason { WRONG_PASSWORD, USER_NOT_FOUND, USER_LOCKED, USER_INACTIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username_or_email", nullable = false)
    private String usernameOrEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "ip_address", nullable = false)
    @JdbcTypeCode(SqlTypes.INET)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(nullable = false)
    private boolean success;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 50)
    private FailureReason failureReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    /** Bo sung ngoai SDD goc (da xac nhan voi nguoi dung 2026-09-05) - metadata thiet bi client tu gui. */
    @Column(name = "screen_resolution", length = 20)
    private String screenResolution;

    @Column(name = "browser_language", length = 20)
    private String browserLanguage;

    @Column(name = "timezone", length = 100)
    private String timezone;
}
