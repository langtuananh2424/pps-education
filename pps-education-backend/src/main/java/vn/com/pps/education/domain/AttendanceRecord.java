package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Bảng attendance_records (SDD > Nhân sự > Chấm công > d) — bản ghi chấm
 * công (UC-09). 1 record/nhân sự/ngày (UNIQUE employee_id+work_date) — check
 * in và check out cùng ghi vào 1 row.
 *
 * Cột gps_location (PostGIS GEOGRAPHY) không map qua JPA (tránh thêm
 * dependency hibernate-spatial) — ghi qua native query trong
 * AttendanceRecordRepository.updateGpsLocation(...).
 */
@Getter
@Setter
@Entity
@Table(name = "attendance_records")
public class AttendanceRecord extends BaseAuditEntity {

    public enum CheckMethod { FINGERPRINT, FACE, GPS, MANUAL }

    public enum MatchedSource { SHIFT, TEACHING_SCHEDULE, MANUAL_OVERRIDE }

    public enum Status { NORMAL, LATE, EARLY_LEAVE, MISSING }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "check_in_at")
    private OffsetDateTime checkInAt;

    @Column(name = "check_out_at")
    private OffsetDateTime checkOutAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_method", length = 20)
    private CheckMethod checkInMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_out_method", length = 20)
    private CheckMethod checkOutMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_matched_source", length = 30)
    private MatchedSource checkInMatchedSource;

    @Column(name = "check_in_matched_reference_id")
    private Long checkInMatchedReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.NORMAL;
}
