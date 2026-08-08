package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Bảng review_video_set_class_assignments (V98, bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng 2026-08-06) — gán 1 {@link ReviewVideoSet} ("Bộ")
 * cho 1 lớp, nhiều-nhiều. Là điều kiện hiển thị DUY NHẤT cho học sinh của
 * lớp đó xem được các video trong Bộ — khung chương trình trên Bộ chỉ dùng
 * lọc/tìm kiếm, không phải điều kiện thứ 2 (mirror {@link ExamClassAssignment}).
 * Join thuần — không phải "bản giao" cần lưu lịch sử như
 * {@link ReviewVideoAssignment}, nên KHÔNG có uuid/status; gỡ lớp = xóa
 * cứng dòng này.
 */
@Getter
@Setter
@Entity
@Table(name = "review_video_set_class_assignments")
public class ReviewVideoSetClassAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_video_set_id", nullable = false)
    private ReviewVideoSet reviewVideoSet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt = OffsetDateTime.now();
}
