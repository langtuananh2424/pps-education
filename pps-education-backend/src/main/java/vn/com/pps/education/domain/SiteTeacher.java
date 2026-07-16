package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.LocalDate;

/**
 * Bảng site_teachers (migration V35) — gán giáo viên vào điểm trường, quan hệ
 * N-N (1 giáo viên nhiều site, 1 site nhiều giáo viên). Khác site_managers
 * (bảng cho vai trò quản lý, unique 1 active/site/role_type) — bảng này
 * không có khái niệm vai trò, chỉ ghi nhận "đang dạy tại site nào".
 * assignedFrom/assignedTo tự thân là lịch sử, không có bảng history riêng.
 */
@Getter
@Setter
@Entity
@Table(name = "site_teachers")
public class SiteTeacher extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_user_id", nullable = false)
    private User teacher;

    @Column(name = "assigned_from", nullable = false)
    private LocalDate assignedFrom;

    /** NULL = đang được gán tại điểm trường này. */
    @Column(name = "assigned_to")
    private LocalDate assignedTo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
