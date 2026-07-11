package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

/**
 * Bảng parent_student (SDD > Học sinh & Phụ huynh > b) — liên kết M-N có
 * thuộc tính giữa Phụ huynh và Học sinh (UC-13 Main Flow bước 2). Không có
 * lịch sử — đổi giám hộ tạo record mới, xóa cứng record cũ (theo SDD).
 */
@Getter
@Setter
@Entity
@Table(name = "parent_student")
public class ParentStudent extends BaseAuditEntity {

    public enum Relationship { FATHER, MOTHER, GUARDIAN, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false)
    private Parent parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Relationship relationship;

    @Column(name = "is_primary_contact", nullable = false)
    private boolean primaryContact = false;

    @Column(name = "is_financial_responsible", nullable = false)
    private boolean financialResponsible = false;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
