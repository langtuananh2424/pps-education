package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bảng grade_periods (SDD > Học thuật > Sổ điểm & Điểm tổng kết > a) —
 * kỳ đánh giá của 1 khung chương trình (UC-19 Precondition: "công thức
 * tính điểm đã cấu hình trong khung chương trình").
 */
@Getter
@Setter
@Entity
@Table(name = "grade_periods")
public class GradePeriod {

    public enum Code { MID_1, END_1, MID_2, END_2, OTHER }

    public enum Status { ACTIVE, ARCHIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curriculum_id", nullable = false)
    private Curriculum curriculum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Code code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "weight_in_final", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightInFinal;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;
}
