package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.ClassSessionHistory;
import vn.com.pps.education.domain.Room;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.SessionPeriod;
import vn.com.pps.education.domain.SessionPeriodHistory;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CancelClassSessionRequest;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateClassSessionRequest;
import vn.com.pps.education.dto.RescheduleClassSessionRequest;
import vn.com.pps.education.dto.SessionPeriodResponse;
import vn.com.pps.education.exception.InvalidClassSessionStatusTransitionException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.RoomConflictException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassSessionHistoryRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.RoomRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SessionPeriodHistoryRepository;
import vn.com.pps.education.repository.SessionPeriodRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteTeacherRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.SystemSettingRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
<<<<<<< HEAD
=======
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
>>>>>>> develop

/**
 * UC-48: Xếp lịch buổi học (FR-ACA-05, docs/uc/phan-he-06-hoc-thuat.md).
 * Trước đây UC-15/UC-37 đều trỏ nhầm về "UC-18" cho việc này — UC-18 thực
 * tế chỉ tạo `classes`/`class_teachers`/`class_enrollments` (xem Javadoc
 * cũ trong git history); UC-48 đã lấp đúng khoảng trống tài liệu này.
 *
 * session_periods tự sinh theo system_settings.academic.default_periods_per_session
 * (key mới, xác nhận với user — SDD chỉ nói "mặc định 2 tiết/buổi theo
 * system_settings" không nêu setting_key cụ thể).
 *
 * Authorization qua @PreAuthorize("hasPermission(null,'academic.class.manage')")
 * ở ClassSessionController (Hybrid PBAC — V28, dùng chung permission với
 * UC-18 vì cùng tập role HEAD_ACADEMIC/STAFF).
 */
@Service
public class ClassSessionService {

    private static final String DEFAULT_PERIODS_SETTING_KEY = "academic.default_periods_per_session";

    private final ClassSessionRepository classSessionRepository;
    private final SessionPeriodRepository sessionPeriodRepository;
    private final ClassSessionHistoryRepository classSessionHistoryRepository;
    private final SessionPeriodHistoryRepository sessionPeriodHistoryRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final RoomRepository roomRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;
    private final SiteTeacherRepository siteTeacherRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final PermissionEvaluationService permissionEvaluationService;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final StudentRepository studentRepository;

    public ClassSessionService(ClassSessionRepository classSessionRepository,
                                SessionPeriodRepository sessionPeriodRepository,
                                ClassSessionHistoryRepository classSessionHistoryRepository,
                                SessionPeriodHistoryRepository sessionPeriodHistoryRepository,
                                SchoolClassRepository schoolClassRepository,
                                RoomRepository roomRepository,
                                SystemSettingRepository systemSettingRepository,
                                UserRepository userRepository,
                                SiteTeacherRepository siteTeacherRepository,
                                SiteManagerRepository siteManagerRepository,
                                PermissionEvaluationService permissionEvaluationService,
                                ClassEnrollmentRepository classEnrollmentRepository,
                                StudentRepository studentRepository) {
        this.classSessionRepository = classSessionRepository;
        this.sessionPeriodRepository = sessionPeriodRepository;
        this.classSessionHistoryRepository = classSessionHistoryRepository;
        this.sessionPeriodHistoryRepository = sessionPeriodHistoryRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.roomRepository = roomRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.userRepository = userRepository;
        this.siteTeacherRepository = siteTeacherRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.permissionEvaluationService = permissionEvaluationService;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.studentRepository = studentRepository;
    }

    /** Giáo viên (không có academic.class.manage) chỉ thấy buổi học thuộc site được gán — xem ClassService.resolveAllowedSiteIds. */
    @Transactional(readOnly = true)
    public List<ClassSessionResponse> listSessions(Long classId, Long actorUserId) {
        List<Long> allowedSiteIds = resolveAllowedSiteIds(actorUserId);
        return classSessionRepository.findBySchoolClassIdOrderBySessionDateAsc(classId).stream()
                .filter(s -> isSiteAllowed(s.getSchoolClass().getSite().getId(), allowedSiteIds))
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SessionPeriodResponse> listPeriods(Long classSessionId, Long actorUserId) {
        List<Long> allowedSiteIds = resolveAllowedSiteIds(actorUserId);
        return sessionPeriodRepository.findByClassSessionIdOrderByPeriodNumber(classSessionId).stream()
                .filter(p -> isSiteAllowed(p.getClassSession().getSchoolClass().getSite().getId(), allowedSiteIds))
                .map(this::toResponse).toList();
    }

    /**
     * null = không giới hạn (actor có academic.class.manage); danh sách rỗng
     * = không thấy buổi/tiết học nào. Hợp nhất site_teachers VÀ site_managers
     * (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — cùng lý do như
     * ClassService.resolveAllowedSiteIds).
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

    private boolean isSiteAllowed(Long siteId, List<Long> allowedSiteIds) {
        return allowedSiteIds == null || allowedSiteIds.contains(siteId);
    }

    /** Tạo 1 buổi học + tự sinh session_periods. FR-FAC-03: kiểm tra trùng phòng (chỉ phòng is_flexible=FALSE). */
    @Transactional
    public ClassSessionResponse createSession(Long classId, CreateClassSessionRequest request, Long actorUserId) {
        SchoolClass schoolClass = schoolClassRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId));
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("endTime phải sau startTime.");
        }
        User teacher = userRepository.findById(request.primaryTeacherId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + request.primaryTeacherId()));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

<<<<<<< HEAD
        Room room = null;
        if (request.roomId() != null) {
            room = getRoomOrThrow(request.roomId());
            checkRoomConflict(room, request.sessionDate(), request.startTime(), request.endTime(), null);
=======
        ClassSession session = createSessionEntity(schoolClass, request.sessionDate(), request.startTime(), request.endTime(),
                room, teacher, ClassSession.SessionType.valueOf(request.sessionType()), actor);

        return toResponse(session);
    }

    /**
     * UC-56: Sinh lịch học hàng loạt theo mẫu lặp (FR-ACA-05, bổ sung
     * ngoài SDD gốc, đã xác nhận với người dùng). Với mỗi ngày trong
     * [startDate, endDate] khớp 1 trong các daysOfWeek yêu cầu, thử tạo 1
     * buổi — tái dùng đúng createSessionEntity (room-conflict-check +
     * sinh session_periods) của createSession/rescheduleSession, không
     * viết lại logic. Ngày nào trùng phòng bị bỏ qua (ghi lý do), các
     * ngày khác trong lô vẫn tiếp tục tạo bình thường (giống pattern
     * lỗi-từng-dòng của batch import UC-35/50/51/53 — không có preview
     * phòng trống trước, chỉ báo lỗi/bỏ qua sau khi thử tạo).
     */
    @Transactional
    public BulkCreateClassSessionResponse bulkCreateSessions(Long classId, BulkCreateClassSessionRequest request, Long actorUserId) {
        SchoolClass schoolClass = getSchoolClassOrThrow(classId);
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("endTime phải sau startTime.");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("endDate phải sau hoặc bằng startDate.");
        }
        Set<DayOfWeek> daysOfWeek = request.daysOfWeek().stream().map(DayOfWeek::valueOf).collect(Collectors.toSet());
        User teacher = getUserOrThrow(request.primaryTeacherId());
        User actor = getUserOrThrow(actorUserId);
        Room room = request.roomId() == null ? null : getRoomOrThrow(request.roomId());
        ClassSession.SessionType sessionType = ClassSession.SessionType.valueOf(request.sessionType());

        int totalDates = 0;
        List<ClassSessionResponse> created = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (LocalDate date = request.startDate(); !date.isAfter(request.endDate()); date = date.plusDays(1)) {
            if (!daysOfWeek.contains(date.getDayOfWeek())) {
                continue;
            }
            totalDates++;
            try {
                ClassSession session = createSessionEntity(schoolClass, date, request.startTime(), request.endTime(),
                        room, teacher, sessionType, actor);
                created.add(toResponse(session));
            } catch (RoomConflictException ex) {
                Map<String, Object> reason = new LinkedHashMap<>();
                reason.put("date", date.toString());
                reason.put("reason", ex.getMessage());
                skipped.add(reason);
            }
        }
        return new BulkCreateClassSessionResponse(totalDates, created.size(), skipped.size(), created, skipped);
    }

    /**
     * UC-57: helper dùng lại y hệt logic tạo 1 buổi của UC-48/UC-56, nhận
     * thẳng ID để ClassScheduleImportService (khác Service, cùng package)
     * gọi theo từng dòng Excel. KHÔNG @Transactional — luôn chạy trong
     * transaction bao ngoài của ClassScheduleImportService.importSchedule;
     * nếu ném RoomConflictException/ResourceNotFoundException cho 1 dòng,
     * caller bắt lỗi rồi tiếp tục dòng khác mà KHÔNG khiến Spring đánh dấu
     * rollbackOnly cho cả giao dịch (tránh bẫy: 1 method @Transactional
     * khác bean ném lỗi dù caller có bắt lại vẫn làm rollback cả giao
     * dịch bao ngoài).
     */
    ClassSessionResponse createSessionForImport(Long classId, LocalDate sessionDate, LocalTime startTime, LocalTime endTime,
                                                 Long roomId, Long teacherId, String sessionType, Long actorUserId) {
        SchoolClass schoolClass = getSchoolClassOrThrow(classId);
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime phải sau startTime.");
        }
        User teacher = getUserOrThrow(teacherId);
        User actor = getUserOrThrow(actorUserId);
        Room room = roomId == null ? null : getRoomOrThrow(roomId);

        ClassSession session = createSessionEntity(schoolClass, sessionDate, startTime, endTime, room, teacher,
                ClassSession.SessionType.valueOf(sessionType), actor);
        return toResponse(session);
    }

    /** UC-58: "Lịch của tôi" — self-service GV, không cần permission đặc biệt, trả đúng buổi của actor qua MỌI lớp. */
    @Transactional(readOnly = true)
    public List<ClassSessionResponse> listMySessions(Long actorUserId, LocalDate fromDate, LocalDate toDate) {
        return classSessionRepository.findByPrimaryTeacherAndDateRange(actorUserId, fromDate, toDate).stream()
                .map(this::toResponse).toList();
    }

    /**
     * UC-59: "Lịch học của tôi" (Học sinh, bổ sung ngoài SDD gốc, đã xác
     * nhận với người dùng) — self-service, không cần permission đặc
     * biệt, trả đúng buổi học của mọi lớp học sinh đang ghi danh ACTIVE.
     * classIdFilter tùy chọn (ngữ cảnh "lớp đang xem" — UC-42) để thu hẹp
     * về đúng 1 lớp khi học sinh học nhiều lớp cùng lúc.
     */
    @Transactional(readOnly = true)
    public List<ClassSessionResponse> listMySessionsForStudent(Long actorUserId, LocalDate fromDate, LocalDate toDate,
                                                                 Long classIdFilter) {
        var student = studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản id=" + actorUserId + " không có hồ sơ học sinh."));
        List<Long> classIds = classEnrollmentRepository.findByStudentIdAndStatus(student.getId(), ClassEnrollment.Status.ACTIVE)
                .stream()
                .map(e -> e.getSchoolClass().getId())
                .filter(id -> classIdFilter == null || id.equals(classIdFilter))
                .toList();
        if (classIds.isEmpty()) {
            return List.of();
        }
        return classSessionRepository.findBySchoolClassIdInAndDateRange(classIds, fromDate, toDate).stream()
                .map(this::toResponse).toList();
    }

    /** Lõi dùng chung: đã resolve đủ entity, chỉ check trùng phòng + save + history + sinh session_periods. */
    private ClassSession createSessionEntity(SchoolClass schoolClass, LocalDate sessionDate, LocalTime startTime, LocalTime endTime,
                                              Room room, User teacher, ClassSession.SessionType sessionType, User actor) {
        if (room != null) {
            checkRoomConflict(room, sessionDate, startTime, endTime, null);
>>>>>>> develop
        }

        ClassSession session = new ClassSession();
        session.setSchoolClass(schoolClass);
        session.setSessionDate(request.sessionDate());
        session.setStartTime(request.startTime());
        session.setEndTime(request.endTime());
        session.setRoom(room);
        session.setPrimaryTeacher(teacher);
        session.setSessionType(ClassSession.SessionType.valueOf(request.sessionType()));
        session.setCreatedBy(actor);
        session = classSessionRepository.save(session);

        writeClassSessionHistory(session, actor, ClassSessionHistory.Action.CREATED);
        generateDefaultPeriods(session, actor);

        return toResponse(session);
    }

    /** UC-48 A2: hủy 1 buổi đang SCHEDULED, giải phóng phòng khỏi ràng buộc trùng lịch. */
    @Transactional
    public ClassSessionResponse cancelSession(Long classId, Long sessionId, CancelClassSessionRequest request, Long actorUserId) {
        ClassSession session = getSessionOrThrow(classId, sessionId);
        requireScheduled(session);
        User actor = getUserOrThrow(actorUserId);

        session.setStatus(ClassSession.Status.CANCELLED);
        session.setCancellationReason(request.reason());
        session = classSessionRepository.save(session);

        writeClassSessionHistory(session, actor, ClassSessionHistory.Action.UPDATED);
        return toResponse(session);
    }

    /** UC-48 A3: dời 1 buổi đang SCHEDULED sang buổi mới; buổi cũ chuyển RESCHEDULED và liên kết sang buổi mới. */
    @Transactional
    public ClassSessionResponse rescheduleSession(Long classId, Long sessionId, RescheduleClassSessionRequest request, Long actorUserId) {
        ClassSession oldSession = getSessionOrThrow(classId, sessionId);
        requireScheduled(oldSession);
        if (!request.newEndTime().isAfter(request.newStartTime())) {
            throw new IllegalArgumentException("newEndTime phải sau newStartTime.");
        }
        User newTeacher = getUserOrThrow(request.newPrimaryTeacherId());
        User actor = getUserOrThrow(actorUserId);

        Room newRoom = null;
        if (request.newRoomId() != null) {
            newRoom = getRoomOrThrow(request.newRoomId());
            checkRoomConflict(newRoom, request.newSessionDate(), request.newStartTime(), request.newEndTime(), oldSession.getId());
        }

        ClassSession newSession = new ClassSession();
        newSession.setSchoolClass(oldSession.getSchoolClass());
        newSession.setSessionDate(request.newSessionDate());
        newSession.setStartTime(request.newStartTime());
        newSession.setEndTime(request.newEndTime());
        newSession.setRoom(newRoom);
        newSession.setPrimaryTeacher(newTeacher);
        newSession.setSessionType(oldSession.getSessionType());
        newSession.setCreatedBy(actor);
        newSession = classSessionRepository.save(newSession);
        writeClassSessionHistory(newSession, actor, ClassSessionHistory.Action.CREATED);
        generateDefaultPeriods(newSession, actor);

        oldSession.setStatus(ClassSession.Status.RESCHEDULED);
        oldSession.setCancellationReason(request.reason());
        oldSession.setRescheduledToSession(newSession);
        oldSession = classSessionRepository.save(oldSession);
        writeClassSessionHistory(oldSession, actor, ClassSessionHistory.Action.UPDATED);

        return toResponse(newSession);
    }

    private void checkRoomConflict(Room room, LocalDate date, LocalTime startTime, LocalTime endTime, Long editingSessionId) {
        if (room.isFlexible()) {
            return;
        }
        List<ClassSession> overlapping = classSessionRepository.findOverlappingInRoom(
                room.getId(), date, startTime, endTime, editingSessionId,
                List.of(ClassSession.Status.CANCELLED, ClassSession.Status.RESCHEDULED));
        if (!overlapping.isEmpty()) {
            throw new RoomConflictException("Phòng id=" + room.getId() + " đã có buổi học khác trùng khung giờ ngày " + date + ".");
        }
    }

    private void requireScheduled(ClassSession session) {
        if (session.getStatus() != ClassSession.Status.SCHEDULED) {
            throw new InvalidClassSessionStatusTransitionException(
                    "Chỉ có thể hủy/dời lịch buổi học đang ở trạng thái SCHEDULED (hiện tại: " + session.getStatus() + ").");
        }
    }

    private ClassSession getSessionOrThrow(Long classId, Long sessionId) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi học id=" + sessionId));
        if (!session.getSchoolClass().getId().equals(classId)) {
            throw new ResourceNotFoundException("Không tìm thấy buổi học id=" + sessionId + " thuộc lớp id=" + classId);
        }
        return session;
    }

    private Room getRoomOrThrow(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng học id=" + roomId));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + userId));
    }

    private void generateDefaultPeriods(ClassSession session, User actor) {
        int count = systemSettingRepository.findBySettingKey(DEFAULT_PERIODS_SETTING_KEY)
                .map(s -> s.getSettingValue().asInt())
                .orElseThrow(() -> new ResourceNotFoundException("Thiếu cấu hình system_settings: " + DEFAULT_PERIODS_SETTING_KEY));

        long totalMinutes = ChronoUnit.MINUTES.between(session.getStartTime(), session.getEndTime());
        long minutesPerPeriod = totalMinutes / count;
        LocalTime cursor = session.getStartTime();
        for (int i = 1; i <= count; i++) {
            LocalTime periodEnd = (i == count) ? session.getEndTime() : cursor.plusMinutes(minutesPerPeriod);
            SessionPeriod period = new SessionPeriod();
            period.setClassSession(session);
            period.setPeriodNumber(i);
            period.setStartTime(cursor);
            period.setEndTime(periodEnd);
            period = sessionPeriodRepository.save(period);
            writeSessionPeriodHistory(period, actor, SessionPeriodHistory.Action.CREATED);
            cursor = periodEnd;
        }
    }

    private void writeClassSessionHistory(ClassSession session, User actor, ClassSessionHistory.Action action) {
        ClassSessionHistory history = new ClassSessionHistory();
        history.setClassSession(session);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sessionDate", String.valueOf(session.getSessionDate()));
        snapshot.put("startTime", String.valueOf(session.getStartTime()));
        snapshot.put("endTime", String.valueOf(session.getEndTime()));
        snapshot.put("status", session.getStatus().name());
        history.setDetails(snapshot);
        classSessionHistoryRepository.save(history);
    }

    private void writeSessionPeriodHistory(SessionPeriod period, User actor, SessionPeriodHistory.Action action) {
        SessionPeriodHistory history = new SessionPeriodHistory();
        history.setSessionPeriod(period);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("periodNumber", period.getPeriodNumber());
        snapshot.put("startTime", String.valueOf(period.getStartTime()));
        snapshot.put("endTime", String.valueOf(period.getEndTime()));
        history.setDetails(snapshot);
        sessionPeriodHistoryRepository.save(history);
    }

    private ClassSessionResponse toResponse(ClassSession s) {
        return new ClassSessionResponse(
                s.getId(), s.getSchoolClass().getId(), s.getSessionDate(), s.getStartTime(), s.getEndTime(),
                s.getRoom() == null ? null : s.getRoom().getId(), s.getRoom() == null ? null : s.getRoom().getName(),
                s.getPrimaryTeacher().getId(), s.getPrimaryTeacher().getFullName(),
                s.getSessionType().name(), s.getStatus().name(),
                s.getCancellationReason(), s.getRescheduledToSession() == null ? null : s.getRescheduledToSession().getId());
    }

    private SessionPeriodResponse toResponse(SessionPeriod p) {
        return new SessionPeriodResponse(
                p.getId(), p.getClassSession().getId(), p.getPeriodNumber(), p.getStartTime(), p.getEndTime(),
                p.getTeacher() == null ? null : p.getTeacher().getId(), p.getSubject() == null ? null : p.getSubject().getId(),
                p.getContentNote());
    }
}
