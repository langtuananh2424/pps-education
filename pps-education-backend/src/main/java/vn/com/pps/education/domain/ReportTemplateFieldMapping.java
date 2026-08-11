package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Bảng report_template_field_mappings (UC-67 bước 3) — ánh xạ mỗi
 * placeholder phát hiện được trong mẫu tới nguồn dữ liệu thực tế
 * (VD {@code [STUDENT_NAME]} -> {@code students.user.fullName}), dùng làm
 * cơ sở resolve dữ liệu khi xuất báo cáo ở UC-68.
 */
@Getter
@Setter
@Entity
@Table(name = "report_template_field_mappings")
public class ReportTemplateFieldMapping {

    public enum FieldType { FIELD, FORMULA, TABLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ReportTemplate template;

    /** VD "[STUDENT_NAME]", hoặc nguyên văn biểu thức formula, hoặc "[[TABLE:STUDENTS]]". */
    @Column(name = "placeholder_key", nullable = false, length = 200)
    private String placeholderKey;

    /** NULL nếu fieldType = FORMULA/TABLE (không cấu hình data_path riêng — xem UC-67 bước 3). */
    @Column(name = "data_path", length = 500)
    private String dataPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 20)
    private FieldType fieldType;

    @Column(columnDefinition = "TEXT")
    private String description;
}
