package vn.com.pps.education.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.com.pps.education.common.BaseAuditEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Bảng class_sessions (SDD > Học thuật > Lịch dạy & Điểm danh > a) —
 * 1 buổi học vật lý tại 1 thời điểm cụ thể. UC-48: Xếp lịch buổi học
 * (FR-ACA-05, docs/uc/phan-he-06-hoc-thuat.md) — xem Javadoc ClassSessionService.
 */
@Getter
@Setter
@Entity
@Table(name = "class_sessions")
public class ClassSession extends BaseAuditEntity {

    public enum SessionType { REGULAR, MAKEUP, EXAM, SPECIAL }

    public enum Status { SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED, RESCHEDULED }

    public enum TeacherType { VIETNAMESE, FOREIGN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    /**
     * Chọn tay riêng từng buổi (bổ sung ngoài SDD gốc, xác nhận
     * 2026-08-19 — ĐẢO NGƯỢC quyết định 2026-08-13/V121: trước đây tự
     * động suy ra từ class_teachers PRIMARY của lớp, giờ người xếp lịch
     * chọn trực tiếp bất kỳ tài khoản TEACHER nào, không còn ràng buộc
     * phải là PRIMARY của lớp).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_teacher_id", nullable = false)
    private User primaryTeacher;

    /** Giáo viên phụ của buổi này (tuỳ chọn) — gán riêng theo buổi, khác class_teachers ASSISTANT (cấp lớp). V128, xác nhận 2026-08-19. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assistant_teacher_id")
    private User assistantTeacher;

    /** CM (Class Manager) của buổi này (tuỳ chọn) — gán riêng theo buổi, khác class_teachers CM (cấp lớp). V128, xác nhận 2026-08-19. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cm_teacher_id")
    private User cmTeacher;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 20)
    private SessionType sessionType = SessionType.REGULAR;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SCHEDULED;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    /**
     * Loại giáo viên (Việt Nam/nước ngoài) dạy buổi này, hiển thị cho Học
     * sinh/Phụ huynh (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-07-29) — tùy chọn (nullable), buổi cũ để trống. Chỉ ở cấp buổi
     * học, KHÔNG liên quan hồ sơ nhân sự (1 giáo viên có thể dạy cả 2 loại
     * buổi tùy lịch).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "teacher_type", length = 20)
    private TeacherType teacherType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rescheduled_to_session_id")
    private ClassSession rescheduledToSession;

    /**
     * Chỉ có ý nghĩa khi sessionType=MAKEUP — buổi CANCELLED mà buổi này
     * bù cho (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-07-29). UNIQUE ở DB (V61) đảm bảo 1 buổi hủy chỉ có đúng 1
     * buổi bù.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "makeup_for_session_id")
    private ClassSession makeupForSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /**
     * "Bài học hôm nay" (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-07-24) — 1 giá trị dùng chung cho cả lớp trong buổi này, Giáo
     * viên điền cùng lúc điểm danh (xem StudentAttendanceService.updateLessonContent).
     */
    @Column(name = "lesson_content", columnDefinition = "TEXT")
    private String lessonContent;

    /**
     * Tên GV thực tế dạy buổi này (bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng 2026-08-06) — KHÁC {@link #primaryTeacher} (FK tài khoản
     * hệ thống): text nhập tay, dùng khi GV nước ngoài không tự thao tác
     * hệ thống, nhân sự chăm sóc lớp nhập hộ qua Excel/UI để quản lý theo
     * dõi buổi đó thực tế ai dạy.
     */
    @Column(name = "actual_teacher_name")
    private String actualTeacherName;
}
