package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.util.UUID;

/**
 * Bảng roles (SDD > Nền tảng > c) — 11 vai trò hệ thống (is_system = TRUE),
 * xem V4__seed_roles_and_permissions.sql.
 */
@Getter
@Setter
@Entity
@Table(name = "roles")
public class Role extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_system", nullable = false)
    private boolean system = false;

    /**
     * V202 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-30) —
     * vai trò được xem dữ liệu tới đâu: tất cả điểm trường / chỉ điểm trường
     * mình phụ trách (site_managers, site_teachers) / chỉ lớp mình dạy
     * (class_teachers). SELF dành cho 3 vai trò Portal, dữ liệu theo quan hệ
     * học sinh–phụ huynh–trường liên kết. Thay cho việc code tự đoán phạm vi
     * theo mã vai trò, để vai trò tự tạo trên màn "Nhóm vai trò" cũng dùng được.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "data_scope", nullable = false, length = 10)
    private DataScope dataScope = DataScope.ALL;

    /** Thứ tự từ rộng nhất tới hẹp nhất — tài khoản nhiều vai trò lấy phạm vi rộng nhất. */
    public enum DataScope { ALL, SITE, CLASS, SELF }
}
