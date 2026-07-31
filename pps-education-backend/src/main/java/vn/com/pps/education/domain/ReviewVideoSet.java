package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng review_video_sets (SDD > LMS & Portal > Kho Video Ôn tập > a) —
 * "bộ" video ôn tập dùng chung theo khung chương trình (curriculum_id)
 * hoặc riêng theo 1 lớp (class_id), không cả hai (UC-23 FR-LMS-01).
 * Tái cấu trúc 2026-07-27 từ "lessons" (Kho bài giảng) — đã xác nhận với
 * người dùng: bỏ hẳn PDF/Slide/Word, chỉ còn video/audio ôn tập.
 */
@Getter
@Setter
@Entity
@Table(name = "review_video_sets")
public class ReviewVideoSet extends BaseAuditEntity {

    public enum VideoType { CONNECTION, REFLEX }

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

    @Enumerated(EnumType.STRING)
    @Column(name = "video_type", nullable = false, length = 20)
    private VideoType videoType;

    /** Bộ chung — dùng cho mọi lớp theo khung này. Loại trừ với classScope (CHECK chk_review_video_set_scope). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_id")
    private Curriculum curriculum;

    /** Bộ riêng — chỉ 1 lớp cụ thể xem được. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private SchoolClass schoolClass;

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
}
