package vn.com.pps.education.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Bảng system_settings (SDD > Nền tảng > k) — cấu hình hệ thống dạng
 * key/value JSONB, dùng cho UC-09 đọc cờ bật/tắt phương thức chấm công
 * (attendance.gps_enabled/fingerprint_enabled/face_enabled/
 * manual_when_all_disabled/gps_radius_meters).
 */
@Getter
@Setter
@Entity
@Table(name = "system_settings")
public class SystemSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String settingKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "setting_value", nullable = false, columnDefinition = "jsonb")
    private JsonNode settingValue;

    @Column(length = 50, nullable = false)
    private String category;

    /** Bổ sung field còn thiếu (cột đã có sẵn trong DB từ V1, chỉ chưa map) — cần cho trang Cài đặt hệ thống (2026-08-08). */
    @Column(columnDefinition = "text")
    private String description;

    /** V39 — trước đây không map (chưa có API ghi nào); dùng để audit ai đã đổi setting. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
