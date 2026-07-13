package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Bảng partner_contracts (SDD > Cơ sở vật chất & Điểm trường, migration V2). UC-36b: Quản lý hợp đồng liên kết trường (FR-FAC-01). */
@Getter
@Setter
@Entity
@Table(name = "partner_contracts")
public class PartnerContract {

    public enum ContractType { INITIAL, RENEWAL, AMENDMENT }

    public enum Status { DRAFT, ACTIVE, EXPIRED, TERMINATED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "contract_number", nullable = false, unique = true, length = 100)
    private String contractNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 30)
    private ContractType contractType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_contract_id")
    private PartnerContract parentContract;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "terms_summary", columnDefinition = "TEXT")
    private String termsSummary;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "signed_at")
    private LocalDate signedAt;

    @Column(name = "signed_by_center", length = 200)
    private String signedByCenter;

    @Column(name = "signed_by_partner", length = 200)
    private String signedByPartner;

    @Column(name = "revenue_share_notes", columnDefinition = "TEXT")
    private String revenueShareNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
