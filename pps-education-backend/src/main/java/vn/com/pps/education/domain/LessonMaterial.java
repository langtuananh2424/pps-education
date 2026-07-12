package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bảng lesson_materials (SDD > LMS & Portal > Bài giảng & Kho học liệu >
 * b) — tệp đính kèm (video/PDF/audio...) đã upload lên CDN (URL, không
 * lưu file trực tiếp — NFR-TECH-07). Không có history, sửa tài liệu tạo
 * bản ghi mới (SDD).
 */
@Getter
@Setter
@Entity
@Table(name = "lesson_materials")
public class LessonMaterial {

    public enum MaterialType { VIDEO, PDF, AUDIO, SLIDE, IMAGE, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 30)
    private MaterialType materialType;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "is_downloadable", nullable = false)
    private boolean downloadable = false;
}
