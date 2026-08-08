package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng review_video_sets (SDD > LMS & Portal > Kho Video Ôn tập > a) —
 * "bộ" video ôn tập (UC-23 FR-LMS-01). Tái cấu trúc 2026-07-27 từ
 * "lessons" (Kho bài giảng) — đã xác nhận với người dùng: bỏ hẳn
 * PDF/Slide/Word, chỉ còn video/audio ôn tập.
 *
 * V98 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06) —
 * đổi mô hình gán lớp giống hệt Kho đề (mirror {@link Exam}): curriculum
 * CHỈ dùng lọc/tìm kiếm trong Kho Video (không còn "bộ dùng chung theo
 * khung" tự động hiển thị mọi lớp), điều kiện hiển thị DUY NHẤT cho học
 * sinh của 1 lớp là {@link ReviewVideoSetClassAssignment} (gán tường
 * minh, nhiều-nhiều).
 */
@Getter
@Setter
@Entity
@Table(name = "review_video_sets")
public class ReviewVideoSet extends BaseAuditEntity {

    public enum VideoType { CONNECTION, REFLEX }

    public enum Status { DRAFT, PUBLISHED, ARCHIVED }

    /** Bộ dành cho GV Việt Nam hay GV nước ngoài — dùng để lọc khi giao bài (V98, mirror Exam.TeacherType). */
    public enum TeacherType { VIETNAMESE, FOREIGN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "video_type", nullable = false, length = 20)
    private VideoType videoType;

    /** V98: CHỈ dùng lọc/tìm kiếm trong Kho Video — không còn là điều kiện hiển thị (xem Javadoc lớp). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private CurriculumSubject subject;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /** Bắt buộc chọn 1 trong 2 khi tạo Bộ — sửa được cùng title (V98). */
    @Enumerated(EnumType.STRING)
    @Column(name = "teacher_type", nullable = false, length = 20)
    private TeacherType teacherType;
}
