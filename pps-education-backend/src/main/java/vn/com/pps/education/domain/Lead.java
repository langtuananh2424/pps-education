package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bảng leads (SDD > Tuyển sinh & CRM) — bảng trung tâm UC-33/34, gộp
 * thông tin liên hệ, học sinh quan tâm, trạng thái xử lý, kết quả chuyển
 * đổi. Không theo dõi lịch sử tư vấn chi tiết (SDD) — chỉ status +
 * final_note, không có bảng lead_contacts riêng.
 */
@Getter
@Setter
@Entity
@Table(name = "leads")
public class Lead {

    public enum Status { NEW, CONTACTED, QUALIFIED, WON, LOST, DUPLICATE }

    public enum ContactRelationship { SELF, FATHER, MOTHER, GUARDIAN, OTHER }

    public enum Outcome { WON_ENROLLED, LOST_PRICE, LOST_LOCATION, LOST_TIMING, LOST_NO_INTEREST, LOST_OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Column(name = "lead_code", nullable = false, unique = true, length = 50)
    private String leadCode;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_relationship", length = 30)
    private ContactRelationship contactRelationship;

    @Column(name = "student_name", length = 200)
    private String studentName;

    @Column(name = "student_dob")
    private LocalDate studentDob;

    @Column(name = "student_grade", length = 50)
    private String studentGrade;

    @Column(name = "student_current_school", length = 300)
    private String studentCurrentSchool;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_source_id", nullable = false)
    private LeadSource leadSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interested_site_id")
    private Site interestedSite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interested_curriculum_id")
    private Curriculum interestedCurriculum;

    @Column(name = "initial_message", columnDefinition = "TEXT")
    private String initialMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.NEW;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Outcome outcome;

    @Column(name = "final_note", columnDefinition = "TEXT")
    private String finalNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_student_id")
    private Student convertedStudent;

    @Column(name = "converted_at")
    private OffsetDateTime convertedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_by")
    private User convertedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
