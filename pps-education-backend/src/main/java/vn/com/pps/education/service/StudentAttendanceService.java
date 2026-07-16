package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AttendanceMark;
import vn.com.pps.education.domain.AttendanceMarkHistory;
import vn.com.pps.education.domain.AttendancePeriodMark;
import vn.com.pps.education.domain.AttendanceSession;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.SessionPeriod;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AttendanceMarkResponse;
import vn.com.pps.education.dto.AttendanceSessionResponse;
import vn.com.pps.education.dto.EnterAttendanceMarkRequest;
import vn.com.pps.education.dto.MarkAttendanceRequest;
import vn.com.pps.education.dto.UpdatePeriodMarkRequest;
import vn.com.pps.education.exception.AttendanceSessionNotEditableException;
import vn.com.pps.education.exception.NotAssignedTeacherForSessionException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AttendanceMarkHistoryRepository;
import vn.com.pps.education.repository.AttendanceMarkRepository;
import vn.com.pps.education.repository.AttendancePeriodMarkRepository;
import vn.com.pps.education.repository.AttendanceSessionRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.SessionPeriodRepository;
import vn.com.pps.education.repository.SiteTeacherRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                                     PermissionEvaluationService permissionEvaluationService) {
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
    }

    /** Main Flow bước 1-2: điểm danh cả lớp (lưu DRAFT, có thể gọi lại nhiều lần trước khi submit). */
    @Transactional
    public AttendanceSessionResponse markAttendance(Long classSessionId, MarkAttendanceRequest request, Long actorUserId) {
        ClassSession classSession = getClassSessionOrThrow(classSessionId);
        requireAssignedTeacher(classSession, actorUserId);
        User actor = getUserOrThrow(actorUserId);

        AttendanceSession attendanceSession = attendanceSessionRepository.findByClassSessionId(classSessionId)
                .orElseGet(() -> {
                    AttendanceSession created = new AttendanceSession();
                    created.setClassSession(classSession);
                    created.setMarkedBy(actor);
                    return created;
                });
        if (attendanceSession.getId() != null && attendanceSession.getStatus() != AttendanceSession.Status.DRAFT) {
            throw new AttendanceSessionNotEditableException(
                    "Điểm danh buổi id=" + classSessionId + " đang ở trạng thái " + attendanceSession.getStatus()
                            + " — chỉ sửa được khi DRAFT.");
        }
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
        requireAssignedTeacher(classSession, actorUserId);
        User actor = getUserOrThrow(actorUserId);

        AttendanceSession attendanceSession = attendanceSessionRepository.findByClassSessionId(classSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chưa có bản ghi điểm danh cho buổi id=" + classSessionId));
        if (attendanceSession.getStatus() != AttendanceSession.Status.DRAFT) {
            throw new AttendanceSessionNotEditableException(
                    "Điểm danh buổi id=" + classSessionId + " đang ở trạng thái " + attendanceSession.getStatus()
                            + " — chỉ sửa được khi DRAFT.");
        }
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
        requireAssignedTeacher(classSession, actorUserId);
        User actor = getUserOrThrow(actorUserId);

        AttendanceSession attendanceSession = attendanceSessionRepository.findByClassSessionId(classSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chưa có bản ghi điểm danh cho buổi id=" + classSessionId));
        if (attendanceSession.getStatus() != AttendanceSession.Status.DRAFT) {
            throw new AttendanceSessionNotEditableException(
                    "Điểm danh buổi id=" + classSessionId + " đang ở trạng thái " + attendanceSession.getStatus()
                            + " — chỉ submit được khi DRAFT.");
        }
        attendanceSession.setStatus(AttendanceSession.Status.SUBMITTED);
        attendanceSession.setSubmittedAt(OffsetDateTime.now());
        attendanceSession = attendanceSessionRepository.save(attendanceSession);

        List<AttendanceMark> marks = attendanceMarkRepository.findByAttendanceSessionId(attendanceSession.getId());
        for (AttendanceMark mark : marks) {
            // Main Flow bước 5 / A2 -- chỉ ABSENT/LATE mới kích hoạt thông báo.
            if (mark.getStatus() == AttendanceMark.Status.ABSENT || mark.getStatus() == AttendanceMark.Status.LATE) {
                notifyParents(mark, classSession, actor);
            }
        }

        return toResponse(attendanceSession);
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

    /** null = không giới hạn (actor có academic.class.manage); danh sách rỗng = không thấy buổi điểm danh nào. */
    private List<Long> resolveAllowedSiteIds(Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, "academic.class.manage")) {
            return null;
        }
        return siteTeacherRepository.findByTeacherIdAndAssignedToIsNull(actorUserId).stream()
                .map(st -> st.getSite().getId()).toList();
    }

    private void requireAssignedTeacher(ClassSession classSession, Long actorUserId) {
        if (!classSession.getPrimaryTeacher().getId().equals(actorUserId)) {
            throw new NotAssignedTeacherForSessionException(
                    "Tài khoản id=" + actorUserId + " không được phân công giảng dạy buổi id=" + classSession.getId() + ".");
        }
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
