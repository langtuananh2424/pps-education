package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bảng commendations (SDD > Nhân sự > Hồ sơ nhân sự > d) — khen thưởng/kỷ
 * luật của 1 nhân sự (UC-08 Main Flow bước 4). Mỗi record là 1 sự kiện độc
 * lập, không sửa, không có lịch sử (theo SDD).
 */
@Getter
@Setter
@Entity
@Table(name = "commendations")
public class Commendation {

    public enum RecordType { COMMENDATION, DISCIPLINE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 20)
    private RecordType recordType;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(nullable = false, length = 300)
    private String title;

    /** Dùng ở tính lương (UC-12). */
    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;
}
