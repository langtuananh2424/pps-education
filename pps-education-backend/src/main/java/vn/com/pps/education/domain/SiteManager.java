package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.LocalDate;

/**
 * Bảng site_managers (SDD > Cơ sở vật chất & Điểm trường > d, migration
 * V2) — gán người phụ trách 1 điểm trường. Ban đầu chỉ cho Quản lý điểm
 * trường (UC-36) — UC-29 tái dùng cho Đại diện trường liên kết
 * (PARTNER_REP), phân biệt qua roleType (migration V22, đã xác nhận với
 * user không có bảng nào khác trong SDD cho quan hệ này). unique index
 * (site_id, role_type) WHERE assigned_to IS NULL — 1 site có thể có đồng
 * thời 1 SITE_MANAGER active + 1 PARTNER_REP active.
 */
@Getter
@Setter
@Entity
@Table(name = "site_managers")
public class SiteManager extends BaseAuditEntity {

    public enum RoleType { SITE_MANAGER, PARTNER_REP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 20)
    private RoleType roleType = RoleType.SITE_MANAGER;

    @Column(name = "assigned_from", nullable = false)
    private LocalDate assignedFrom;

    /** NULL = đang phụ trách (SDD). */
    @Column(name = "assigned_to")
    private LocalDate assignedTo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
