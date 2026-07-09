package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

/**
 * Bảng permissions (SDD > Nền tảng > d) — danh mục quyền chi tiết (UC-02).
 * code theo format &lt;module&gt;.&lt;action&gt;, VD class.create, grade.approve.
 */
@Getter
@Setter
@Entity
@Table(name = "permissions")
public class Permission extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String module; // AUTH, USER, TASK, HRM, STUDENT, ACADEMIC, LMS, FINANCE, CRM, FACILITY

    @Column(columnDefinition = "TEXT")
    private String description;
}
