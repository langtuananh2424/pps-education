package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng generated_reports (UC-68, FR-ACA-08 — bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng). Lịch sử báo cáo đã xuất từ 1 report_template
 * (mail merge) — ai xuất, khi nào, cho đối tượng nào, file ở đâu trên R2.
 */
@Getter
@Setter
@Entity
@Table(name = "generated_reports")
public class GeneratedReport {

    public enum Scope { SINGLE, BULK_CLASS, CLASS_SESSION }

    public enum FileFormat { DOCX, PDF, ZIP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ReportTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Scope scope;

    /** NULL khi scope=BULK_CLASS (nhiều học sinh, gộp vào 1 file ZIP). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private SchoolClass schoolClass;

    /** Chỉ dùng khi scope=CLASS_SESSION (VD DAILY_REPORT — 1 tài liệu/buổi học). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_session_id")
    private ClassSession classSession;

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_format", nullable = false, length = 20)
    private FileFormat fileFormat;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generated_by", nullable = false)
    private User generatedBy;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt = OffsetDateTime.now();
}
