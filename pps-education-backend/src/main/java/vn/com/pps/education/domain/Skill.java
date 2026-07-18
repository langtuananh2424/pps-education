package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

/**
 * Bảng skills (SDD > Học thuật > Khung chương trình & Lớp học > f, V37 —
 * bổ sung ngoài SDD gốc, đã xác nhận với người dùng) — danh mục kỹ năng
 * thi dùng chung toàn hệ thống (UC-54). Không thay thế enum
 * SubjectCode/ComponentCode hiện có — chỉ là danh mục tham chiếu bổ sung
 * qua skill_id (nullable) trên curriculum_subjects/grade_components.
 */
@Getter
@Setter
@Entity
@Table(name = "skills")
public class Skill extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
