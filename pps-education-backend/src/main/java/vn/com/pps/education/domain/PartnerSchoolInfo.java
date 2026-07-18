package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

/**
 * Bảng partner_school_info (SDD > Cơ sở vật chất & Điểm trường > b,
 * migration V2) — thông tin liên hệ đầu mối, chỉ áp dụng khi
 * sites.site_type = PARTNER (UC-36 Main Flow bước 3). Quan hệ 1-1 với
 * Site qua site_id UNIQUE.
 */
@Getter
@Setter
@Entity
@Table(name = "partner_school_info")
public class PartnerSchoolInfo extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false, unique = true)
    private Site site;

    @Column(name = "contact_person_name", length = 200)
    private String contactPersonName;

    @Column(name = "contact_person_title", length = 100)
    private String contactPersonTitle;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "additional_info", columnDefinition = "TEXT")
    private String additionalInfo;
}
