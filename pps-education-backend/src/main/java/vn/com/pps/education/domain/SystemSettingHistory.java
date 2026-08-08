package vn.com.pps.education.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Bảng system_settings_history (bổ sung ngoài SDD gốc, đã xác nhận với
 * người dùng 2026-08-08 — xem V105) — SDD (02-nen-tang.md > k) đã nhắc tới
 * bảng này nhưng chưa từng được build. Mỗi lần Quản trị viên sửa giá trị 1
 * setting qua UI (SystemSettingService#update) ghi 1 dòng, chỉ có UPDATED
 * (không CREATED/DELETED — UI hiện tại chỉ cho sửa key có sẵn).
 */
@Getter
@Setter
@Entity
@Table(name = "system_settings_history")
public class SystemSettingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "system_setting_id", nullable = false)
    private SystemSetting systemSetting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", nullable = false, columnDefinition = "jsonb")
    private JsonNode oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", nullable = false, columnDefinition = "jsonb")
    private JsonNode newValue;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
