package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng site_period_templates (V127, bổ sung ngoài SDD gốc, xác nhận với
 * người dùng 2026-08-19) — "Tiết học" cố định, cấu hình riêng theo từng
 * điểm trường (site). Thay cho cơ chế chia đều theo phút cũ
 * (system_settings.academic.default_periods_per_session) — xem
 * ClassSessionService#generatePeriodsFromTemplate.
 */
@Getter
@Setter
@Entity
@Table(name = "site_period_templates")
public class SitePeriodTemplate {

    /** Buổi trong ngày (V129, bổ sung ngoài SDD gốc, xác nhận 2026-08-20) — mỗi buổi đánh số tiết RIÊNG (VD Tiết 1 sáng khác Tiết 1 chiều), khớp thời khóa biểu giấy thực tế. */
    public enum DayPart { MORNING, AFTERNOON, EVENING }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_part", nullable = false, length = 20)
    private DayPart dayPart;

    @Column(name = "period_number", nullable = false)
    private int periodNumber;

    @Column(length = 50)
    private String label;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
