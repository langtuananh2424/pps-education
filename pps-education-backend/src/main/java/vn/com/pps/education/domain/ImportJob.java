package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bảng import_jobs (SDD > Nền tảng > l, tạo sẵn từ V1 làm hạ tầng import
 * Excel dùng chung). UC-35 là luồng ĐẦU TIÊN thực sự dùng bảng này
 * (import_type=STUDENTS) — chưa có luồng import nào khác (SHIFTS/
 * EMPLOYEE_SHIFTS/WORK_CALENDAR/ATTENDANCE/TEACHING_SCHEDULE) code trước đó.
 */
@Getter
@Setter
@Entity
@Table(name = "import_jobs")
public class ImportJob {

    public enum ImportType { SHIFTS, EMPLOYEE_SHIFTS, WORK_CALENDAR, STUDENTS, ATTENDANCE, TEACHING_SCHEDULE }

    public enum Status { PENDING, PROCESSING, COMPLETED, FAILED, PARTIAL_SUCCESS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(name = "import_type", nullable = false, length = 50)
    private ImportType importType;

    @Column(name = "source_file_name", nullable = false, length = 500)
    private String sourceFileName;

    @Column(name = "source_file_url", nullable = false, length = 500)
    private String sourceFileUrl;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "success_rows", nullable = false)
    private int successRows = 0;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    /** Danh sách lỗi từng dòng (UC-35 A1/A2) — List<Map<String,Object>> (rowIndex/reason). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_summary", columnDefinition = "jsonb")
    private List<Map<String, Object>> errorSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_details", columnDefinition = "jsonb")
    private Map<String, Object> resultDetails;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
