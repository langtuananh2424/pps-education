package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Bảng grade_period_edit_windows (SDD > Học thuật > Sổ điểm & Điểm tổng
 * kết, V39 — bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — mốc
 * "lần đầu nhập điểm" cho 1 (lớp, kỳ đánh giá), làm gốc tính hạn X ngày
 * GV toàn quyền sửa điểm (UC-19). Ghi 1 lần duy nhất (UNIQUE class_id +
 * grade_period_id), không cập nhật lại sau đó.
 */
@Getter
@Setter
@Entity
@Table(name = "grade_period_edit_windows")
public class GradePeriodEditWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_period_id", nullable = false)
    private GradePeriod gradePeriod;

    @Column(name = "first_entered_at", nullable = false)
    private OffsetDateTime firstEnteredAt = OffsetDateTime.now();
}
