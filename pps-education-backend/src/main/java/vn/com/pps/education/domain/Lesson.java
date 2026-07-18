package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng lessons (SDD > LMS & Portal > Bài giảng & Kho học liệu > a) — bài
 * giảng dùng chung theo khung chương trình (curriculum_id) hoặc riêng
 * theo 1 lớp (class_id), không cả hai (UC-23 FR-LMS-01).
 */
@Getter
@Setter
@Entity
@Table(name = "lessons")
public class Lesson {

    public enum LessonType { VIDEO_LECTURE, PDF_DOCUMENT, MIXED, LIVE_RECORDING }

    public enum Status { DRAFT, PUBLISHED, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 500)
    private String title;

    /** Bài chung — dùng cho mọi lớp theo khung này. Loại trừ với classScope (CHECK chk_lesson_scope). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id")
    private Curriculum curriculum;

    /** Bài riêng — chỉ 1 lớp cụ thể xem được. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private CurriculumSubject subject;

    @Column(name = "lesson_order")
    private Integer lessonOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "lesson_type", nullable = false, length = 30)
    private LessonType lessonType;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
