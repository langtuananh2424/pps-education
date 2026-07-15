package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.OffsetDateTime;

/**
 * Bảng users (SDD > Nền tảng > b) — bảng trung tâm, mọi tác nhân (UC-01..UC-41)
 * đều là 1 record ở đây. Học sinh/Phụ huynh/Nhân sự có bảng mở rộng riêng
 * (students, parents, employees) liên kết 1-1 qua user_id.
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseAuditEntity {

    public enum Status { ACTIVE, INACTIVE, SUSPENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash (NFR-SEC-01). NULL nếu chỉ đăng nhập Google. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;
}
