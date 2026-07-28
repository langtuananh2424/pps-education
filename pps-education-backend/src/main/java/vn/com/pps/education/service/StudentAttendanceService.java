package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AttendanceMark;
import vn.com.pps.education.domain.AttendanceMarkHistory;
import vn.com.pps.education.domain.AttendancePeriodMark;
import vn.com.pps.education.domain.AttendanceSession;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.SessionPeriod;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AttendanceMarkResponse;
import vn.com.pps.education.dto.AttendanceSessionResponse;
import vn.com.pps.education.dto.ClassSessionLessonContentResponse;
import vn.com.pps.education.dto.EnterAttendanceMarkRequest;
import vn.com.pps.education.dto.MarkAttendanceRequest;
import vn.com.pps.education.dto.PartnerAttendanceSummaryResponse;
import vn.com.pps.education.dto.UpdatePeriodMarkRequest;
import vn.com.pps.education.exception.AttendanceSessionNotEditableException;
import vn.com.pps.education.exception.NotAssignedTeacherForSessionException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AttendanceMarkHistoryRepository;
import vn.com.pps.education.repository.AttendanceMarkRepository;
import vn.com.pps.education.repository.AttendancePeriodMarkRepository;
import vn.com.pps.education.repository.AttendanceSessionRepository;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SessionPeriodRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteTeacherRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * UC-15: Điểm danh học sinh (FR-STU-03).
 * Xem docs/uc/phan-he-05-hoc-sinh.md — Main Flow bước 1-6, A1 (gửi thông
 * báo thất bại vượt retry), A2 (toàn bộ có mặt).
 *
 * Precondition ("Buổi học/tiết học đã được xếp lịch — UC-18") thực ra phụ
 * thuộc ClassSessionService (xem Javadoc ở đó — không có UC nào mô tả
 * việc xếp lịch, đã xác nhận với user).
 *
 * A1 KHÔNG tự implement lại cơ chế retry — dùng lại nguyên
 * NotificationService.notify() + NotificationDispatchService (module
 * Notification, PR #14) đã có sẵn cơ chế retry/đánh dấu FAILED, tránh
 * trùng lặp logic (xem Javadoc NotificationService).
 */
@Service
public class StudentAttendanceService {

    /** Quyền quản trị vượt rào Giáo viên (migration V45) — điểm danh/sửa buổi bất kỳ, ngày bất kỳ. */
    private static final String PERM_ATTENDANCE_CREATE = "academic.attendance.create";
    private static final String PERM_ATTENDANCE_UPDATE = "academic.attendance.update";

    private final ClassSessionRepository classSessionRepository;
    private final SessionPeriodRepository sessionPeriodRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceMarkRepository attendanceMarkRepository;
    private final AttendanceMarkHistoryRepository attendanceMarkHistoryRepository;
    private final AttendancePeriodMarkRepository attendancePeriodMarkRepository;
    private final StudentRepository studentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SiteTeacherRepository siteTeacherRepository;
    private final PermissionEvaluationService permissionEvaluationService;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final SiteManagerRepository siteManagerRepository;

    public StudentAttendanceService(ClassSessionRepository classSessionRepository,
                                     SessionPeriodRepository sessionPeriodRepository,
                                     AttendanceSessionRepository attendanceSessionRepository,
                                     AttendanceMarkRepository attendanceMarkRepository,
                                     AttendanceMarkHistoryRepository attendanceMarkHistoryRepository,
                                     AttendancePeriodMarkRepository attendancePeriodMarkRepository,
                                     StudentRepository studentRepository,
                                     ParentStudentRepository parentStudentRepository,
                                     UserRepository userRepository,
                                     NotificationService notificationService,
                                     SiteTeacherRepository siteTeacherRepository,
                                     PermissionEvaluationService permissionEvaluationService,
                                     SchoolClassRepository schoolClassRepository,
                                     ClassEnrollmentRepository classEnrollmentRepository,
                                     SiteManagerRepository siteManagerRepository) {
        this.classSessionRepository = classSessionRepository;
        this.sessionPeriodRepository = sessionPeriodRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.attendanceMarkRepository = attendanceMarkRepository;
        this.attendanceMarkHistoryRepository = attendanceMarkHistoryRepository;
        this.attendancePeriodMarkRepository = attendancePeriodMarkRepository;
        this.studentRepository = studentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.siteTeacherRepository = siteTeacherRepository;
        this.permissionEvaluationService = permissionEvaluationService;
        this.schoolClassRepository = schoolClassRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.siteManagerRepository = siteManagerRepository;
    }

    /**
     * Main Flow bước 1-2: điểm danh cả lớp. Giáo viên chỉ điểm danh buổi mình
     * được phân công dạy và trong ngày diễn ra buổi học; được gọi lại nhiều
     * lần để SỬA (kể cả sau khi submit) cho tới hết ngày hôm đó (xem
     * requireCanWriteAttendance). Quyền quản trị academic.attendance.create
     * vượt cả 2 ràng buộc này.
     */
    @Transactional
    public AttendanceSessionResponse markAttendance(Long classSessionId, MarkAttendanceRequest request, Long actorUserId) {
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        requireCanWriteAttendance(classSession, actorUserId, PERM_ATTENDANCE_CREATE);
        User actor = getUserOrThrow(actorUserId);

        AttendanceSession attendanceSession = attendanceSessionRepository.findByClassSessionId(classSessionId)
                .orElseGet(() -> {
                    AttendanceSession created = new AttendanceSession();
                    created.setClassSession(classSession);
                    created.setMarkedBy(actor);
                    return created;
                });
        attendanceSession.setMode(AttendanceSession.Mode.valueOf(request.mode()));
        attendanceSession.setMarkedBy(actor);
        attendanceSession.setMarkedAt(OffsetDateTime.now());
        attendanceSession = attendanceSessionRepository.save(attendanceSession);

        List<SessionPeriod> periods = sessionPeriodRepository.findByClassSessionIdOrderByPeriodNumber(classSessionId);
        for (EnterAttendanceMarkRequest markRequest : request.marks()) {
            upsertMark(attendanceSession, markRequest, periods, actor);
        }

        return toResponse(attendanceSession);
    }

    private void upsertMark(AttendanceSession attendanceSession, EnterAttendanceMarkRequest request,
                             List<SessionPeriod> periods, User actor) {
        Student student = studentRepository.findByIdAndDeletedAtIsNull(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh id=" + request.studentId()));
        AttendanceMark mark = attendanceMarkRepository
                .findByAttendanceSessionIdAndStudentId(attendanceSession.getId(), request.studentId())
                .orElse(null);
        AttendanceMarkHistory.Action action = AttendanceMarkHistory.Action.CREATED;
        if (mark == null) {
            mark = new AttendanceMark();
            mark.setAttendanceSession(attendanceSession);
            mark.setStudent(student);
        } else {
            action = AttendanceMarkHistory.Action.UPDATED;
        }
        AttendanceMark.Status status = AttendanceMark.Status.valueOf(request.status());
        mark.setStatus(status);
        mark.setMinutesLate(request.minutesLate());
        mark.setMinutesEarlyLeave(request.minutesEarlyLeave());
        mark.setAbsenceReason(request.absenceReason());
        mark = attendanceMarkRepository.save(mark);
        writeAttendanceMarkHistory(mark, actor, action);

        // SDD: điểm danh SESSION_LEVEL tự tạo attendance_period_marks cho từng tiết
        // với cùng status. AttendancePeriodMark.Status không có EARLY_LEAVE (chỉ
        // PRESENT/ABSENT/EXCUSED/LATE) -- bỏ qua tự sinh khi status=EARLY_LEAVE,
        // để Giáo viên tự set qua updatePeriodMark (Main Flow bước 3) nếu cần.
        AttendancePeriodMark.Status periodStatus;
        try {
            periodStatus = AttendancePeriodMark.Status.valueOf(status.name());
        } catch (IllegalArgumentException ex) {
            return;
        }
        for (SessionPeriod period : periods) {
            AttendancePeriodMark periodMark = attendancePeriodMarkRepository
                    .findByAttendanceMarkIdAndSessionPeriodId(mark.getId(), period.getId())
                    .orElseGet(AttendancePeriodMark::new);
            periodMark.setAttendanceMark(mark);
            periodMark.setSessionPeriod(period);
            periodMark.setStatus(periodStatus);
            attendancePeriodMarkRepository.save(periodMark);
        }
    }

    /** Main Flow bước 3: sửa chi tiết điểm danh 1 tiết cho 1 học sinh cụ thể. */
    @Transactional
    public AttendanceMarkResponse updatePeriodMark(Long classSessionId, Long studentId, Long sessionPeriodId,
                                                     UpdatePeriodMarkRequest request, Long actorUserId) {
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        requireCanWriteAttendance(classSession, actorUserId, PERM_ATTENDANCE_UPDATE);
        User actor = getUserOrThrow(actorUserId);

        AttendanceSession attendanceSession = attendanceSessionRepository.findByClassSessionId(classSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chưa có bản ghi điểm danh cho buổi id=" + classSessionId));
        AttendanceMark mark = attendanceMarkRepository
                .findByAttendanceSessionIdAndStudentId(attendanceSession.getId(), studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Chưa điểm danh nhanh cho học sinh id=" + studentId + " ở buổi id=" + classSessionId));
        SessionPeriod period = sessionPeriodRepository.findById(sessionPeriodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tiết học id=" + sessionPeriodId));

        AttendancePeriodMark periodMark = attendancePeriodMarkRepository
                .findByAttendanceMarkIdAndSessionPeriodId(mark.getId(), sessionPeriodId)
                .orElseGet(AttendancePeriodMark::new);
        periodMark.setAttendanceMark(mark);
        periodMark.setSessionPeriod(period);
        periodMark.setStatus(AttendancePeriodMark.Status.valueOf(request.status()));
        periodMark.setNote(request.note());
        attendancePeriodMarkRepository.save(periodMark);

        writeAttendanceMarkHistory(mark, actor, AttendanceMarkHistory.Action.UPDATED);
        return toResponse(mark);
    }

    /**
     * Main Flow bước 4-6, A2: xác nhận Lưu điểm danh. Nếu có ABSENT/LATE,
     * kích hoạt thông báo cho tất cả phụ huynh liên kết (bất đồng bộ, dùng
     * lại NotificationService/NotificationDispatchService — xem Javadoc lớp).
     */
    @Transactional
    public AttendanceSessionResponse submitAttendance(Long classSessionId, Long actorUserId) {
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        requireCanWriteAttendance(classSession, actorUserId, PERM_ATTENDANCE_CREATE);
        User actor = getUserOrThrow(actorUserId);

        AttendanceSession attendanceSession = attendanceSessionRepository.findByClassSessionId(classSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chưa có bản ghi điểm danh cho buổi id=" + classSessionId));
        attendanceSession.setStatus(AttendanceSession.Status.SUBMITTED);
        if (attendanceSession.getSubmittedAt() == null) {
            attendanceSession.setSubmittedAt(OffsetDateTime.now());
        }
        attendanceSession = attendanceSessionRepository.save(attendanceSession);

        List<AttendanceMark> marks = attendanceMarkRepository.findByAttendanceSessionId(attendanceSession.getId());
        for (AttendanceMark mark : marks) {
            // Main Flow bước 5 / A2 -- chỉ ABSENT/LATE CHƯA gửi mới kích hoạt thông báo.
            // Guard notifiedParentAt: submit lại trong ngày (sau khi sửa) không gửi trùng cho PH.
            if ((mark.getStatus() == AttendanceMark.Status.ABSENT || mark.getStatus() == AttendanceMark.Status.LATE)
                    && mark.getNotifiedParentAt() == null) {
                notifyParents(mark, classSession, actor);
            }
        }

        return toResponse(attendanceSession);
    }

    /**
     * "Bài học hôm nay" (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-07-24) — cùng rào với điểm danh (GV được phân công buổi + trong
     * ngày diễn ra, hoặc quyền quản trị điểm danh), nên đặt ở đây thay vì
     * ClassSessionService để tái dùng nguyên requireCanWriteAttendance,
     * không viết lại rào ở chỗ khác.
     */
    @Transactional
    public ClassSessionLessonContentResponse updateLessonContent(Long classSessionId, String lessonContent, Long actorUserId) {
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        requireCanWriteAttendance(classSession, actorUserId, PERM_ATTENDANCE_UPDATE);
        classSession.setLessonContent(lessonContent);
        classSession = classSessionRepository.save(classSession);
        return new ClassSessionLessonContentResponse(classSession.getId(), classSession.getLessonContent());
    }

    /**
     * Quyền quản trị academic.attendance.delete (vượt rào Giáo viên): xóa
     * toàn bộ bản ghi điểm danh của 1 buổi. Xóa theo đúng thứ tự khóa ngoại:
     * attendance_period_marks → attendance_marks_history → attendance_marks →
     * attendance_sessions. Dùng để gỡ bỏ điểm danh nhầm/sai buổi. Gating quyền
     * đặt ở Controller (@PreAuthorize) — service không giới hạn GV/ngày.
     */
    @Transactional
    public void deleteAttendance(Long classSessionId, Long actorUserId) {
        getClassSessionOrThrow(classSessionId);
        AttendanceSession attendanceSession = attendanceSessionRepository.findByClassSessionId(classSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chưa có bản ghi điểm danh cho buổi id=" + classSessionId));
        Long attendanceSessionId = attendanceSession.getId();
        attendancePeriodMarkRepository.deleteByAttendanceSessionId(attendanceSessionId);
        attendanceMarkHistoryRepository.deleteByAttendanceSessionId(attendanceSessionId);
        attendanceMarkRepository.deleteByAttendanceSessionId(attendanceSessionId);
        // clearAutomatically ở các bulk delete trên đã detach attendanceSession —
        // xóa lại qua id (deleteById tự nạp mới) để tránh thao tác trên entity detached.
        attendanceSessionRepository.deleteById(attendanceSessionId);
    }

    private void notifyParents(AttendanceMark mark, ClassSession classSession, User actor) {
        List<ParentStudent> links = parentStudentRepository.findByStudentId(mark.getStudent().getId());
        if (links.isEmpty()) {
            return;
        }
        String title = "Học sinh " + mark.getStudent().getUser().getFullName()
                + (mark.getStatus() == AttendanceMark.Status.ABSENT ? " vắng học" : " đi muộn");
        String content = "Buổi học ngày " + classSession.getSessionDate() + " (" + classSession.getStartTime()
                + " - " + classSession.getEndTime() + "): " + mark.getStudent().getUser().getFullName()
                + " được ghi nhận " + mark.getStatus() + ".";
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classSessionId", classSession.getId());
        metadata.put("studentId", mark.getStudent().getId());
        metadata.put("status", mark.getStatus().name());

        for (ParentStudent link : links) {
            notificationService.notify(link.getParent().getUser().getId(), Notification.NotificationType.ATTENDANCE_ABSENT,
                    title, content, metadata, "ATTENDANCE_MARK", mark.getId(), Notification.Priority.HIGH, actor.getId());
        }
        mark.setNotifiedParentAt(OffsetDateTime.now());
        attendanceMarkRepository.save(mark);
    }

    /** Giáo viên (không có academic.class.manage) chỉ xem được điểm danh buổi thuộc site được gán — xem ClassService.resolveAllowedSiteIds. */
    @Transactional(readOnly = true)
    public AttendanceSessionResponse getAttendanceSession(Long classSessionId, Long actorUserId) {
        AttendanceSession attendanceSession = attendanceSessionRepository.findByClassSessionId(classSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chưa có bản ghi điểm danh cho buổi id=" + classSessionId));
        List<Long> allowedSiteIds = resolveAllowedSiteIds(actorUserId);
        Long siteId = attendanceSession.getClassSession().getSchoolClass().getSite().getId();
        if (allowedSiteIds != null && !allowedSiteIds.contains(siteId)) {
            throw new ResourceNotFoundException("Chưa có bản ghi điểm danh cho buổi id=" + classSessionId);
        }
        return toResponse(attendanceSession);
    }

    /**
     * null = không giới hạn (actor có academic.class.manage); danh sách rỗng
     * = không thấy buổi điểm danh nào. Hợp nhất site_teachers VÀ
     * site_managers (bổ sung ngoài SDD gốc, đã xác nhận với người dùng —
     * cùng lý do như ClassService.resolveAllowedSiteIds; trước đây Quản lý
     * điểm trường không kiêm giáo viên vẫn xem được báo cáo tổng hợp qua
     * getSiteSummary nhưng lại không xem được chi tiết 1 buổi điểm danh ở
     * đây, không nhất quán).
     */
    private List<Long> resolveAllowedSiteIds(Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, "academic.class.manage")) {
            return null;
        }
        return Stream.concat(
                siteTeacherRepository.findByTeacherIdAndAssignedToIsNull(actorUserId).stream()
                        .map(st -> st.getSite().getId()),
                siteManagerRepository.findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.SITE_MANAGER).stream()
                        .map(sm -> sm.getSite().getId()))
                .distinct().toList();
    }

    /**
     * UC-15b (bổ sung ngoài SRS gốc, xác nhận 2026-07-16): báo cáo chuyên
     * cần cho Quản lý điểm trường, giới hạn đúng (các) điểm trường đang
     * phụ trách qua site_managers role_type=SITE_MANAGER — chỉ xem, không
     * đổi quyền chỉnh sửa điểm danh (vẫn của Giáo viên, UC-15). Logic tổng
     * hợp mirror PartnerPortalService.getAttendanceSummary/summarize
     * (UC-29) — theo tiền lệ mỗi Service tự có bản tổng hợp/mapper riêng
     * cho việc đọc dữ liệu, không gọi chéo Service khác (xem
     * GradeService.toResponse vs PartnerPortalService.toResponse(GradeEntry)).
     */
    @Transactional(readOnly = true)
    public List<PartnerAttendanceSummaryResponse> getSiteSummary(Long siteId, Long actorUserId) {
        if (!siteManagerRepository.existsBySiteIdAndUserIdAndRoleTypeAndAssignedToIsNull(
                siteId, actorUserId, SiteManager.RoleType.SITE_MANAGER)) {
            throw new NotSiteManagerForSiteException(
                    "Tài khoản id=" + actorUserId + " không được gán phụ trách điểm trường id=" + siteId + ".");
        }
        List<SchoolClass> classes = schoolClassRepository.findBySiteIdAndDeletedAtIsNull(siteId);
        return classes.stream()
                .flatMap(schoolClass -> classEnrollmentRepository
                        .findBySchoolClassIdAndStatus(schoolClass.getId(), ClassEnrollment.Status.ACTIVE).stream()
                        .map(enrollment -> summarizeAttendance(schoolClass, enrollment)))
                .filter(summary -> summary.totalMarks() > 0)
                .toList();
    }

    private PartnerAttendanceSummaryResponse summarizeAttendance(SchoolClass schoolClass, ClassEnrollment enrollment) {
        List<AttendanceMark> marks = attendanceMarkRepository
                .findByStudentIdAndClassId(enrollment.getStudent().getId(), schoolClass.getId());
        long present = marks.stream().filter(m -> m.getStatus() == AttendanceMark.Status.PRESENT).count();
        long absent = marks.stream().filter(m -> m.getStatus() == AttendanceMark.Status.ABSENT).count();
        long excused = marks.stream().filter(m -> m.getStatus() == AttendanceMark.Status.EXCUSED).count();
        long late = marks.stream().filter(m -> m.getStatus() == AttendanceMark.Status.LATE).count();
        long earlyLeave = marks.stream().filter(m -> m.getStatus() == AttendanceMark.Status.EARLY_LEAVE).count();
        long total = marks.size();
        BigDecimal rate = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(present).multiply(new BigDecimal("100"))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        return new PartnerAttendanceSummaryResponse(
                enrollment.getStudent().getId(), enrollment.getStudent().getUser().getFullName(),
                schoolClass.getId(), schoolClass.getName(), present, absent, excused, late, earlyLeave, total, rate);
    }

    private void requireAssignedTeacher(ClassSession classSession, Long actorUserId) {
        if (!classSession.getPrimaryTeacher().getId().equals(actorUserId)) {
            throw new NotAssignedTeacherForSessionException(
                    "Tài khoản id=" + actorUserId + " không được phân công giảng dạy buổi id=" + classSession.getId() + ".");
        }
    }

    /**
     * UC-15 (quy tắc bổ sung, xác nhận với người dùng 2026-07-22): Giáo viên
     * tự điểm danh chỉ được thao tác buổi MÌNH được phân công dạy
     * (primary_teacher) và CHỈ trong ngày diễn ra buổi học — được sửa lại (kể
     * cả sau khi Lưu/submit) cho tới hết ngày hôm đó, sang ngày sau thì khóa.
     * Tài khoản có quyền quản trị tương ứng (adminOverridePermission —
     * academic.attendance.create/update) vượt cả 2 ràng buộc: thao tác buổi
     * bất kỳ, ngày bất kỳ (bổ sung/khắc phục sai sót).
     */
    private void requireCanWriteAttendance(ClassSession classSession, Long actorUserId, String adminOverridePermission) {
        if (permissionEvaluationService.hasPermission(actorUserId, adminOverridePermission)) {
            return;
        }
        requireAssignedTeacher(classSession, actorUserId);
        LocalDate today = LocalDate.now();
        if (!today.equals(classSession.getSessionDate())) {
            throw new AttendanceSessionNotEditableException(
                    "Chỉ điểm danh/sửa được trong ngày diễn ra buổi học (" + classSession.getSessionDate()
                            + "); hôm nay là " + today + ". Cần quyền quản trị điểm danh để thao tác buổi khác ngày.");
        }
    }

    /**
     * Kiểm tra TRƯỚC (không throw) actor có ghi được điểm danh buổi này
     * không — dùng cho StudentCommentService.importComments() để tránh gọi
     * markAttendance() (bean @Transactional khác) rồi bắt exception: exception
     * xuyên qua ranh giới @Transactional của lời gọi lồng nhau đánh dấu
     * transaction NGOÀI rollback-only ngay tại proxy, dù caller có catch
     * cũng không "gỡ" được — commit sau đó ném UnexpectedRollbackException.
     * Cùng đúng rào với requireCanWriteAttendance, chỉ khác không throw.
     */
    @Transactional(readOnly = true)
    public boolean canWriteAttendance(Long classSessionId, Long actorUserId) {
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        if (permissionEvaluationService.hasPermission(actorUserId, PERM_ATTENDANCE_CREATE)) {
            return true;
        }
        if (!classSession.getPrimaryTeacher().getId().equals(actorUserId)) {
            return false;
        }
        return LocalDate.now().equals(classSession.getSessionDate());
    }

    private ClassSession getClassSessionOrThrow(Long id) {
        return classSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi học id=" + id));
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    private void writeAttendanceMarkHistory(AttendanceMark mark, User actor, AttendanceMarkHistory.Action action) {
        AttendanceMarkHistory history = new AttendanceMarkHistory();
        history.setAttendanceMark(mark);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("studentId", mark.getStudent().getId());
        snapshot.put("status", mark.getStatus().name());
        history.setDetails(snapshot);
        attendanceMarkHistoryRepository.save(history);
    }

    private AttendanceSessionResponse toResponse(AttendanceSession s) {
        List<AttendanceMarkResponse> marks = attendanceMarkRepository.findByAttendanceSessionId(s.getId()).stream()
                .map(this::toResponse).toList();
        return new AttendanceSessionResponse(
                s.getId(), s.getClassSession().getId(), s.getMode().name(), s.getMarkedBy().getId(), s.getMarkedAt(),
                s.getStatus().name(), s.getSubmittedAt(), marks);
    }

    private AttendanceMarkResponse toResponse(AttendanceMark m) {
        return new AttendanceMarkResponse(
                m.getId(), m.getAttendanceSession().getId(), m.getStudent().getId(), m.getStudent().getUser().getFullName(),
                m.getStudent().getStudentCode(), m.getStatus().name(), m.getMinutesLate(), m.getMinutesEarlyLeave(),
                m.getAbsenceReason(), m.getNotifiedParentAt());
    }
}
