package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Bảng lead_sources (SDD > Tuyển sinh & CRM) — danh mục nguồn lead, không history. */
@Getter
@Setter
@Entity
@Table(name = "lead_sources")
public class LeadSource {

    public enum ChannelType { WEBSITE, SOCIAL, HOTLINE, MESSAGING, PARTNER_FORM, OFFLINE, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 30)
    private ChannelType channelType;

    /** Chỉ set nếu channelType=PARTNER_FORM (SDD CHECK constraint, V26). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_site_id")
    private Site referrerSite;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
