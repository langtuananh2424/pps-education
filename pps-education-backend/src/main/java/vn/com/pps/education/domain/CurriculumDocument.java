package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.util.UUID;

/**
 * Bảng curriculum_documents (UC-60, FR-LMS-13 — bổ sung ngoài SDD gốc,
 * đã xác nhận với người dùng). Kho tài liệu tham khảo độc lập với
 * lessons/lesson_materials (UC-23) — gắn theo curriculum, không gắn 1
 * bài giảng cụ thể nào. GV/Admin upload, Học sinh xem theo curriculum
 * của (các) lớp đang ghi danh ACTIVE.
 */
@Getter
@Setter
@Entity
@Table(name = "curriculum_documents")
public class CurriculumDocument extends BaseAuditEntity {

    public enum DocumentType { VIDEO, PDF, AUDIO, SLIDE, IMAGE, OTHER }

    public enum Status { DRAFT, PUBLISHED, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng (2026-07-23, V48) — ảnh bìa hiển thị khi liệt kê kho tài liệu, độc lập với fileUrl. */
    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
