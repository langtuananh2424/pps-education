package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Bảng student_transfer_history (SDD > Học sinh & Phụ huynh > d) — lịch sử
 * chuyển lớp/chuyển điểm trường (UC-13 Main Flow bước 4, A1).
 *
 * fromClassId/toClassId map thô kiểu Long (không @ManyToOne) — giữ entity
 * lịch sử nhẹ, tránh lazy-load 2 SchoolClass mỗi lần đọc list lịch sử (cùng
 * lựa chọn với ClassEnrollment.importJobId). FK constraint thật tới
 * classes(id) được bổ sung ở V33__student_transfer_history_class_fk.sql
 * (Phân hệ 6 - Học thuật đã triển khai từ V12, xem StudentService.recordTransfer).
 */
@Getter
@Setter
@Entity
@Table(name = "student_transfer_history")
public class StudentTransferHistory {

    public enum TransferType { CLASS_CHANGE, SITE_CHANGE, BOTH }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", nullable = false, length = 20)
    private TransferType transferType;

    @Column(name = "from_class_id")
    private Long fromClassId;

    @Column(name = "to_class_id")
    private Long toClassId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_site_id")
    private Site fromSite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_site_id")
    private Site toSite;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approved_by", nullable = false)
    private User approvedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
