package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.util.UUID;

/**
 * Bảng departments (SDD > Nền tảng > a). Dùng cho FR-TSK-01 (giao việc theo
 * phòng ban) và FR-HRM-03 (duyệt đơn 2 cấp).
 */
@Getter
@Setter
@Entity
@Table(name = "departments")
public class Department extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    // Trưởng phòng - NULL khi phòng chưa có trưởng phòng (FR-HRM-03: bỏ qua bước duyệt cấp phòng ban)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_user_id")
    private User headUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_department_id")
    private Department parentDepartment;
}
