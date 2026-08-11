package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.com.pps.education.common.BaseAuditEntity;

import java.util.List;
import java.util.UUID;

/**
 * Bảng report_templates (UC-67, FR-ACA-08 — bổ sung ngoài SDD gốc, đã xác
 * nhận với người dùng). Mẫu báo cáo (DOCX/PDF) đã đánh dấu sẵn các trường
 * bằng ký hiệu {@code [FIELD_NAME]}/{@code [[formula]]}/
 * {@code [[TABLE:x]]...[[/TABLE:x]]}, dùng làm cơ sở xuất báo cáo hàng
 * loạt ở UC-68 (cơ chế Mail Merge). Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@Getter
@Setter
@Entity
@Table(name = "report_templates")
public class ReportTemplate extends BaseAuditEntity {

    public enum TemplateType { TRANSCRIPT, DAILY_REPORT, STUDENT_PROFILE, GRADE_REPORT, STUDENT_COMMENT }

    public enum FileFormat { DOCX, PDF, HTML }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 50)
    private TemplateType templateType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_format", nullable = false, length = 20)
    private FileFormat fileFormat;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    /** Danh sách [FIELD]/[[formula]]/[[TABLE:x]] hệ thống tự phát hiện được trong file (UC-67 bước 2). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "placeholder_keys", columnDefinition = "jsonb", nullable = false)
    private List<String> placeholderKeys = List.of();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
