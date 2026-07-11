package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.LocalDate;

/**
 * Bảng site_managers (SDD > Cơ sở vật chất & Điểm trường > d, migration
 * V2) — gán Quản lý điểm trường phụ trách 1 điểm trường (UC-36). Entity
 * tối thiểu — chỉ đủ để UC-16b kiểm tra "Quản lý điểm trường được gán
 * phụ trách điểm trường liên quan" (Precondition).
 */
@Getter
@Setter
@Entity
@Table(name = "site_managers")
public class SiteManager extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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
