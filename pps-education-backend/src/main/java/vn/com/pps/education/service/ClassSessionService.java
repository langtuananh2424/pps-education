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
import vn.com.pps.education.domain.SitePeriodTemplate;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.BulkCreateClassSessionRequest;
import vn.com.pps.education.dto.BulkCreateClassSessionResponse;
import vn.com.pps.education.dto.CancelClassSessionRequest;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateClassSessionRequest;
import vn.com.pps.education.dto.RescheduleClassSessionRequest;
import vn.com.pps.education.dto.SessionPeriodResponse;
import vn.com.pps.education.dto.UpdateSessionAssignmentRequest;
import vn.com.pps.education.exception.ClassScheduleConflictException;
import vn.com.pps.education.exception.ClassSessionOutsideClassPeriodException;
import vn.com.pps.education.exception.InvalidClassSessionStatusTransitionException;
import vn.com.pps.education.exception.MakeupSessionAlreadyLinkedException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.RoomConflictException;
import vn.com.pps.education.exception.TeacherScheduleConflictException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassSessionHistoryRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.RoomRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SessionPeriodHistoryRepository;
import vn.com.pps.education.repository.SessionPeriodRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SitePeriodTemplateRepository;
import vn.com.pps.education.repository.SiteTeacherRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * UC-48: Xếp lịch buổi học (FR-ACA-05, docs/uc/phan-he-06-hoc-thuat.md).
 * Trước đây UC-15/UC-37 đều trỏ nhầm về "UC-18" cho việc này — UC-18 thực
 * tế chỉ tạo `classes`/`class_teachers`/`class_enrollments` (xem Javadoc
 * cũ trong git history); UC-48 đã lấp đúng khoảng trống tài liệu này.
 *
 * ĐẢO NGƯỢC quyết định 2026-08-13 (xác nhận lại với người dùng
 * 2026-08-19): session_periods không còn chia đều theo phút
 * (system_settings.academic.default_periods_per_session, đã bỏ), mà sinh
 * trực tiếp từ site_period_templates theo periodNumber người dùng chọn
 * (xem generatePeriodsFromTemplate). Giáo viên chính/phụ/CM của 1 buổi
 * không còn tự động suy ra từ class_teachers PRIMARY — chọn tay riêng
 * từng buổi (primaryTeacherId bắt buộc, assistantTeacherId/cmTeacherId
 * tuỳ chọn), gán trực tiếp trên class_sessions (V128).
 *
 * Authorization qua @PreAuthorize("hasPermission(null,'academic.class.manage')")
 * ở ClassSessionController (Hybrid PBAC — V28, dùng chung permission với
 * UC-18 vì cùng tập role HEAD_ACADEMIC/STAFF).
 */
@Service
public class ClassSessionService {

    private final ClassSessionRepository classSessionRepository;
    private final SessionPeriodRepository sessionPeriodRepository;
    private final ClassSessionHistoryRepository classSessionHistoryRepository;
    private final SessionPeriodHistoryRepository sessionPeriodHistoryRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final SiteTeacherRepository siteTeacherRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final PermissionEvaluationService permissionEvaluationService;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final SitePeriodTemplateRepository sitePeriodTemplateRepository;

    public ClassSessionService(ClassSessionRepository classSessionRepository,
                                SessionPeriodRepository sessionPeriodRepository,
                                ClassSessionHistoryRepository classSessionHistoryRepository,
                                SessionPeriodHistoryRepository sessionPeriodHistoryRepository,
                                SchoolClassRepository schoolClassRepository,
                                RoomRepository roomRepository,
                                UserRepository userRepository,
                                SiteTeacherRepository siteTeacherRepository,
                                SiteManagerRepository siteManagerRepository,
                                PermissionEvaluationService permissionEvaluationService,
                                ClassEnrollmentRepository classEnrollmentRepository,
                                StudentRepository studentRepository,
                                SitePeriodTemplateRepository sitePeriodTemplateRepository) {
        this.classSessionRepository = classSessionRepository;
        this.sessionPeriodRepository = sessionPeriodRepository;
        this.classSessionHistoryRepository = classSessionHistoryRepository;
        this.sessionPeriodHistoryRepository = sessionPeriodHistoryRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.siteTeacherRepository = siteTeacherRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.permissionEvaluationService = permissionEvaluationService;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.sitePeriodTemplateRepository = sitePeriodTemplateRepository;
    }

    /** Giáo viên (không có academic.class.manage) chỉ thấy buổi học thuộc site được gán — xem ClassService.resolveAllowedSiteIds. */
    @Transactional(readOnly = true)
    public List<ClassSessionResponse> listSessions(Long classId, Long actorUserId) {
        List<Long> allowedSiteIds = resolveAllowedSiteIds(actorUserId);
        return classSessionRepository.findBySchoolClassIdOrderBySessionDateAsc(classId).stream()
                .filter(s -> isSiteAllowed(s.getSchoolClass().getSite().getId(), allowedSiteIds))
                .map(this::toResponse).toList();
    }

    /**
     * Buổi học hôm nay của 1 lớp (tab Nhận xét học viên, bổ sung ngoài SDD
     * gốc, đã xác nhận với người dùng 2026-07-29) — GV vào tab nhận xét,
     * nếu hôm nay có lịch thì FE tự chọn buổi này, không thì báo "hôm nay
     * không có buổi học" và để GV tự chọn buổi khác. Loại CANCELLED/
     * RESCHEDULED vì không còn là buổi "đang diễn ra hôm nay" nữa.
     */
    @Transactional(readOnly = true)
    public List<ClassSessionResponse> listTodaySessions(Long classId, Long actorUserId) {
        List<Long> allowedSiteIds = resolveAllowedSiteIds(actorUserId);
        return classSessionRepository.findBySchoolClassIdAndSessionDate(classId, LocalDate.now()).stream()
                .filter(s -> isSiteAllowed(s.getSchoolClass().getSite().getId(), allowedSiteIds))
                .filter(s -> s.getStatus() != ClassSession.Status.CANCELLED && s.getStatus() != ClassSession.Status.RESCHEDULED)
                .map(this::toResponse).toList();
    }

    /**
     * Danh sách buổi CANCELLED của lớp CHƯA có buổi bù nào liên kết (Case
     * 1, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29) —
     * phục vụ màn hình chọn "buổi cần bù" khi tạo buổi MAKEUP.
     */
    @Transactional(readOnly = true)
    public List<ClassSessionResponse> listCancelledSessionsPendingMakeup(Long classId, Long actorUserId) {
        List<Long> allowedSiteIds = resolveAllowedSiteIds(actorUserId);
        return classSessionRepository.findBySchoolClassIdAndStatus(classId, ClassSession.Status.CANCELLED).stream()
                .filter(s -> isSiteAllowed(s.getSchoolClass().getSite().getId(), allowedSiteIds))
                .filter(s -> !classSessionRepository.existsByMakeupForSessionId(s.getId()))
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
     * Lưới thời khóa biểu toàn điểm trường theo tuần (bổ sung ngoài SDD
     * gốc, xác nhận 2026-08-19) — mọi buổi của mọi lớp thuộc 1 site, lọc
     * theo khoảng ngày. actor phải được phép xem site này (giống các
     * list* khác) — không cần academic.class.manage (self-service xem,
     * chỉnh sửa vẫn gate riêng ở createSession/updateAssignment/...).
     */
    @Transactional(readOnly = true)
    public List<ClassSessionResponse> listSessionsForSiteTimetable(Long siteId, LocalDate fromDate, LocalDate toDate, Long actorUserId) {
        List<Long> allowedSiteIds = resolveAllowedSiteIds(actorUserId);
        if (!isSiteAllowed(siteId, allowedSiteIds)) {
            return List.of();
        }
        return classSessionRepository.findBySiteIdAndDateRange(siteId, fromDate, toDate).stream()
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
        SchoolClass schoolClass = getSchoolClassOrThrow(classId);
        ClassSession.TeacherType teacherType = parseTeacherType(request.teacherType());
        SitePeriodTemplate.DayPart dayPart = SitePeriodTemplate.DayPart.valueOf(request.dayPart());
        User primaryTeacher = getUserOrThrow(request.primaryTeacherId());
        User assistantTeacher = request.assistantTeacherId() == null ? null : getUserOrThrow(request.assistantTeacherId());
        User cmTeacher = request.cmTeacherId() == null ? null : getUserOrThrow(request.cmTeacherId());
        User actor = getUserOrThrow(actorUserId);
        Room room = request.roomId() == null ? null : getRoomOrThrow(request.roomId());
        ClassSession.SessionType sessionType = ClassSession.SessionType.valueOf(request.sessionType());
        ClassSession makeupForSession = resolveMakeupForSession(sessionType, request.makeupForSessionId(), classId);

        ClassSession session = createSessionEntity(schoolClass, request.sessionDate(), dayPart, request.periodNumbers(),
                room, primaryTeacher, assistantTeacher, cmTeacher, sessionType, actor, teacherType, makeupForSession);

        return toResponse(session);
    }

    /**
     * Liên kết buổi hủy↔bù (bổ sung ngoài SDD gốc, đã xác nhận với người
     * dùng 2026-07-29): sessionType=MAKEUP bắt buộc makeupForSessionId
     * (bù cho buổi nào), loại khác phải để trống. Buổi tham chiếu phải
     * cùng lớp, đang CANCELLED, và chưa có buổi bù nào khác (UNIQUE ở DB,
     * V61 — check tường minh ở đây để báo lỗi rõ ràng thay vì lỗi
     * constraint thô). Chỉ áp dụng UC-48 (tạo 1 buổi) — không áp dụng
     * bulk (UC-56) hay Excel import (UC-57), ngoài phạm vi yêu cầu.
     */
    private ClassSession resolveMakeupForSession(ClassSession.SessionType sessionType, Long makeupForSessionId, Long classId) {
        if (sessionType != ClassSession.SessionType.MAKEUP) {
            if (makeupForSessionId != null) {
                throw new IllegalArgumentException("makeupForSessionId chỉ áp dụng khi sessionType=MAKEUP.");
            }
            return null;
        }
        if (makeupForSessionId == null) {
            throw new IllegalArgumentException("sessionType=MAKEUP phải có makeupForSessionId (bù cho buổi nào).");
        }
        ClassSession cancelledSession = classSessionRepository.findById(makeupForSessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.classSession.sessionNotFound", new Object[]{makeupForSessionId},
                        "Không tìm thấy buổi học id=" + makeupForSessionId));
        if (!cancelledSession.getSchoolClass().getId().equals(classId)) {
            throw new ResourceNotFoundException(
                    "error.classSession.sessionNotFoundInClass", new Object[]{makeupForSessionId, classId},
                    "Không tìm thấy buổi học id=" + makeupForSessionId + " thuộc lớp id=" + classId);
        }
        if (cancelledSession.getStatus() != ClassSession.Status.CANCELLED) {
            throw new InvalidClassSessionStatusTransitionException("error.invalidClassSessionStatusTransition.notCancelledForMakeup",
                    new Object[]{makeupForSessionId, cancelledSession.getStatus()},
                    "Chỉ có thể chọn buổi đang CANCELLED để bù (buổi id="
                    + makeupForSessionId + " hiện tại: " + cancelledSession.getStatus() + ").");
        }
        if (classSessionRepository.existsByMakeupForSessionId(makeupForSessionId)) {
            throw new MakeupSessionAlreadyLinkedException("error.makeupSessionAlreadyLinked.default", new Object[]{},
                    "Buổi học đã hủy này đã có buổi bù khác liên kết rồi.");
        }
        return cancelledSession;
    }

    /**
     * UC-56: Sinh lịch học hàng loạt theo mẫu lặp (FR-ACA-05, bổ sung
     * ngoài SDD gốc, đã xác nhận với người dùng). Với mỗi ngày trong
     * [startDate, endDate] khớp 1 trong các daysOfWeek yêu cầu, thử tạo 1
     * buổi — tái dùng đúng createSessionEntity (room/teacher/class-conflict-
     * check + sinh session_periods) của createSession/rescheduleSession,
     * không viết lại logic. Ngày nào trùng phòng/trùng giờ GV/trùng giờ
     * lớp/vượt quá classes.end_date (checkWithinClassPeriod, bổ sung ngoài
     * SDD gốc, xác nhận 2026-08-27) bị bỏ qua (ghi lý do), các ngày khác
     * trong lô vẫn tiếp tục tạo bình thường.
     */
    @Transactional
    public BulkCreateClassSessionResponse bulkCreateSessions(Long classId, BulkCreateClassSessionRequest request, Long actorUserId) {
        SchoolClass schoolClass = getSchoolClassOrThrow(classId);
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("endDate phải sau hoặc bằng startDate.");
        }
        Set<DayOfWeek> daysOfWeek = request.daysOfWeek().stream().map(DayOfWeek::valueOf).collect(Collectors.toSet());
        ClassSession.TeacherType teacherType = parseTeacherType(request.teacherType());
        SitePeriodTemplate.DayPart dayPart = SitePeriodTemplate.DayPart.valueOf(request.dayPart());
        User primaryTeacher = getUserOrThrow(request.primaryTeacherId());
        User assistantTeacher = request.assistantTeacherId() == null ? null : getUserOrThrow(request.assistantTeacherId());
        User cmTeacher = request.cmTeacherId() == null ? null : getUserOrThrow(request.cmTeacherId());
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
                // makeupForSessionId không áp dụng cho sinh lịch hàng loạt — chỉ có nghĩa cho 1 buổi tạo lẻ (UC-48), ngoài phạm vi UC-56.
                ClassSession session = createSessionEntity(schoolClass, date, dayPart, request.periodNumbers(),
                        room, primaryTeacher, assistantTeacher, cmTeacher, sessionType, actor, teacherType, null);
                created.add(toResponse(session));
            } catch (RoomConflictException | TeacherScheduleConflictException | ClassScheduleConflictException
                    | ClassSessionOutsideClassPeriodException ex) {
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
     * nếu ném RoomConflictException/TeacherScheduleConflictException/
     * ClassScheduleConflictException/ResourceNotFoundException cho 1 dòng,
     * caller bắt lỗi (catch RuntimeException chung, không cần liệt kê từng
     * loại) rồi tiếp tục dòng khác mà KHÔNG khiến Spring đánh dấu
     * rollbackOnly cho cả giao dịch (tránh bẫy: 1 method @Transactional
     * khác bean ném lỗi dù caller có bắt lại vẫn làm rollback cả giao
     * dịch bao ngoài).
     */
    ClassSessionResponse createSessionForImport(Long classId, LocalDate sessionDate, String dayPart, List<Integer> periodNumbers,
                                                 Long roomId, String teacherType, String sessionType,
                                                 Long primaryTeacherId, Long assistantTeacherId, Long cmTeacherId, Long actorUserId) {
        SchoolClass schoolClass = getSchoolClassOrThrow(classId);
        ClassSession.TeacherType parsedTeacherType = parseTeacherType(teacherType);
        SitePeriodTemplate.DayPart parsedDayPart = SitePeriodTemplate.DayPart.valueOf(dayPart);
        User primaryTeacher = getUserOrThrow(primaryTeacherId);
        User assistantTeacher = assistantTeacherId == null ? null : getUserOrThrow(assistantTeacherId);
        User cmTeacher = cmTeacherId == null ? null : getUserOrThrow(cmTeacherId);
        User actor = getUserOrThrow(actorUserId);
        Room room = roomId == null ? null : getRoomOrThrow(roomId);

        // makeupForSessionId chưa có trong luồng Excel import (UC-57) — ngoài phạm vi yêu cầu bổ sung này.
        ClassSession session = createSessionEntity(schoolClass, sessionDate, parsedDayPart, periodNumbers, room, primaryTeacher, assistantTeacher, cmTeacher,
                ClassSession.SessionType.valueOf(sessionType), actor, parsedTeacherType, null);
        return toResponse(session);
    }

    /** UC-58: "Lịch của tôi" — self-service GV, không cần permission đặc biệt, trả đúng buổi của actor qua MỌI lớp. */
    @Transactional(readOnly = true)
    public List<ClassSessionResponse> listMySessions(Long actorUserId, LocalDate fromDate, LocalDate toDate) {
        return classSessionRepository.findByPrimaryTeacherAndDateRange(actorUserId, fromDate, toDate).stream()
                .map(this::toResponse).toList();
    }

    /**
     * Bổ sung ngoài SDD gốc, xác nhận 2026-08-17: HR/Điều hành xem lịch dạy
     * của 1 giáo viên bất kỳ (khác UC-58 self-service). Gate quyền
     * hrm.employee-schedule.view ở Controller — Service chỉ nhận thẳng
     * teacherUserId đã resolve. Xem docs/uc/phan-he-04-nhan-su.md (UC-70).
     */
    @Transactional(readOnly = true)
    public List<ClassSessionResponse> listSessionsByTeacher(Long teacherUserId, LocalDate fromDate, LocalDate toDate) {
        return classSessionRepository.findByPrimaryTeacherAndDateRange(teacherUserId, fromDate, toDate).stream()
                .map(this::toResponse).toList();
    }

    /**
     * Bổ sung ngoài SDD gốc, xác nhận 2026-08-17: nguồn dữ liệu lịch dạy cho
     * trang roster "Lịch làm việc" toàn công ty (EmployeeScheduleService).
     * teacherUserIds rỗng nghĩa là "không có Giáo viên nào trong phạm vi lọc
     * hiện tại" — trả về rỗng ngay, không gọi query. Xem
     * docs/uc/phan-he-04-nhan-su.md (UC-70).
     */
    @Transactional(readOnly = true)
    public List<ClassSessionResponse> listForScheduleOverview(List<Long> teacherUserIds, List<Long> siteIds, Long classId,
                                                                LocalDate fromDate, LocalDate toDate) {
        if (teacherUserIds.isEmpty()) {
            return List.of();
        }
        return classSessionRepository.findByPrimaryTeacherIdInAndFiltersAndDateRange(teacherUserIds, siteIds, classId, fromDate, toDate)
                .stream().map(this::toResponse).toList();
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
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.classSession.studentProfileNotFound", new Object[]{actorUserId},
                        "Tài khoản id=" + actorUserId + " không có hồ sơ học sinh."));
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

    /**
     * Lõi dùng chung: đã resolve đủ entity, chỉ check trùng phòng + trùng
     * giờ Giáo viên + trùng giờ trong cùng Lớp (2 chặn cuối bổ sung ngoài
     * SDD gốc, đã xác nhận với người dùng 2026-07-30) + save + history +
     * sinh session_periods từ site_period_templates (thay vì chia đều
     * theo phút — đảo ngược 2026-08-13, xác nhận lại 2026-08-19).
     */
    private ClassSession createSessionEntity(SchoolClass schoolClass, LocalDate sessionDate, SitePeriodTemplate.DayPart dayPart, List<Integer> periodNumbers,
                                              Room room, User primaryTeacher, User assistantTeacher, User cmTeacher,
                                              ClassSession.SessionType sessionType, User actor,
                                              ClassSession.TeacherType teacherType, ClassSession makeupForSession) {
        checkWithinClassPeriod(schoolClass, sessionDate);
        List<SitePeriodTemplate> templates = resolvePeriodTemplates(schoolClass.getSite().getId(), dayPart, periodNumbers);
        LocalTime startTime = templates.get(0).getStartTime();
        LocalTime endTime = templates.get(templates.size() - 1).getEndTime();

        if (room != null) {
            checkRoomConflict(room, sessionDate, startTime, endTime, null);
        }
        checkTeacherConflict(primaryTeacher, sessionDate, startTime, endTime, null);
        checkClassConflict(schoolClass.getId(), sessionDate, startTime, endTime, null);

        ClassSession session = new ClassSession();
        session.setSchoolClass(schoolClass);
        session.setSessionDate(sessionDate);
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        session.setRoom(room);
        session.setPrimaryTeacher(primaryTeacher);
        session.setAssistantTeacher(assistantTeacher);
        session.setCmTeacher(cmTeacher);
        session.setSessionType(sessionType);
        session.setTeacherType(teacherType);
        session.setMakeupForSession(makeupForSession);
        session.setCreatedBy(actor);
        session = classSessionRepository.save(session);

        writeClassSessionHistory(session, actor, ClassSessionHistory.Action.CREATED);
        generatePeriodsFromTemplate(session, templates, actor);
        return session;
    }

    private ClassSession.TeacherType parseTeacherType(String teacherType) {
        return teacherType == null ? null : ClassSession.TeacherType.valueOf(teacherType);
    }

    private SchoolClass getSchoolClassOrThrow(Long classId) {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.classSession.classNotFound", new Object[]{classId},
                        "Không tìm thấy lớp học id=" + classId));
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

    /**
     * UC-48 A3: dời 1 buổi đang SCHEDULED sang buổi mới; buổi cũ chuyển
     * RESCHEDULED và liên kết sang buổi mới. Đảo ngược 2026-08-13 (xác
     * nhận lại 2026-08-19): KHÔNG re-derive giáo viên nữa — copy nguyên
     * primaryTeacher/assistantTeacher/cmTeacher/teacherType từ buổi cũ
     * (sửa GV thì dùng updateAssignment riêng, tách bạch 2 thao tác).
     */
    @Transactional
    public ClassSessionResponse rescheduleSession(Long classId, Long sessionId, RescheduleClassSessionRequest request, Long actorUserId) {
        ClassSession oldSession = getSessionOrThrow(classId, sessionId);
        requireScheduled(oldSession);
        User actor = getUserOrThrow(actorUserId);
        checkWithinClassPeriod(oldSession.getSchoolClass(), request.newSessionDate());

        SitePeriodTemplate.DayPart newDayPart = SitePeriodTemplate.DayPart.valueOf(request.newDayPart());
        List<SitePeriodTemplate> templates = resolvePeriodTemplates(oldSession.getSchoolClass().getSite().getId(), newDayPart, request.newPeriodNumbers());
        LocalTime newStartTime = templates.get(0).getStartTime();
        LocalTime newEndTime = templates.get(templates.size() - 1).getEndTime();

        Room newRoom = null;
        if (request.newRoomId() != null) {
            newRoom = getRoomOrThrow(request.newRoomId());
            checkRoomConflict(newRoom, request.newSessionDate(), newStartTime, newEndTime, oldSession.getId());
        }
        checkTeacherConflict(oldSession.getPrimaryTeacher(), request.newSessionDate(), newStartTime, newEndTime, oldSession.getId());
        checkClassConflict(oldSession.getSchoolClass().getId(), request.newSessionDate(), newStartTime, newEndTime, oldSession.getId());

        // Chuyển liên kết bù (nếu buổi đang dời lịch chính là 1 buổi MAKEUP đã liên kết) sang buổi
        // mới — phải gỡ khỏi buổi cũ VÀ flush ngay, vì UNIQUE constraint (V61) không cho 2 buổi
        // cùng trỏ 1 buổi hủy, và ClassSession dùng IDENTITY nên save(newSession) insert ngay
        // lập tức chứ không đợi flush cuối transaction.
        ClassSession makeupForSession = oldSession.getMakeupForSession();
        if (makeupForSession != null) {
            oldSession.setMakeupForSession(null);
            classSessionRepository.saveAndFlush(oldSession);
        }

        ClassSession newSession = new ClassSession();
        newSession.setSchoolClass(oldSession.getSchoolClass());
        newSession.setSessionDate(request.newSessionDate());
        newSession.setStartTime(newStartTime);
        newSession.setEndTime(newEndTime);
        newSession.setRoom(newRoom);
        newSession.setPrimaryTeacher(oldSession.getPrimaryTeacher());
        newSession.setAssistantTeacher(oldSession.getAssistantTeacher());
        newSession.setCmTeacher(oldSession.getCmTeacher());
        newSession.setSessionType(oldSession.getSessionType());
        newSession.setTeacherType(oldSession.getTeacherType());
        newSession.setMakeupForSession(makeupForSession);
        newSession.setCreatedBy(actor);
        newSession = classSessionRepository.save(newSession);
        writeClassSessionHistory(newSession, actor, ClassSessionHistory.Action.CREATED);
        generatePeriodsFromTemplate(newSession, templates, actor);

        oldSession.setStatus(ClassSession.Status.RESCHEDULED);
        oldSession.setCancellationReason(request.reason());
        oldSession.setRescheduledToSession(newSession);
        oldSession = classSessionRepository.save(oldSession);
        writeClassSessionHistory(oldSession, actor, ClassSessionHistory.Action.UPDATED);

        return toResponse(newSession);
    }

    /**
     * Sửa nhanh tại chỗ (bổ sung ngoài SDD gốc, xác nhận 2026-08-19, phục
     * vụ click-thẻ trên lưới thời khóa biểu) — sửa phòng/loại GV/GV chính-
     * phụ-CM/tiết CÙNG NGÀY cho 1 buổi đang SCHEDULED, không tạo buổi mới
     * (khác reschedule — đổi ngày phải đi qua reschedule để giữ đúng ngữ
     * nghĩa RESCHEDULED + audit trail).
     */
    @Transactional
    public ClassSessionResponse updateAssignment(Long classId, Long sessionId, UpdateSessionAssignmentRequest request, Long actorUserId) {
        ClassSession session = getSessionOrThrow(classId, sessionId);
        requireScheduled(session);
        User actor = getUserOrThrow(actorUserId);

        SitePeriodTemplate.DayPart dayPart = SitePeriodTemplate.DayPart.valueOf(request.dayPart());
        List<SitePeriodTemplate> templates = resolvePeriodTemplates(session.getSchoolClass().getSite().getId(), dayPart, request.periodNumbers());
        LocalTime newStartTime = templates.get(0).getStartTime();
        LocalTime newEndTime = templates.get(templates.size() - 1).getEndTime();

        Room newRoom = request.roomId() == null ? null : getRoomOrThrow(request.roomId());
        User primaryTeacher = getUserOrThrow(request.primaryTeacherId());
        User assistantTeacher = request.assistantTeacherId() == null ? null : getUserOrThrow(request.assistantTeacherId());
        User cmTeacher = request.cmTeacherId() == null ? null : getUserOrThrow(request.cmTeacherId());

        if (newRoom != null) {
            checkRoomConflict(newRoom, session.getSessionDate(), newStartTime, newEndTime, session.getId());
        }
        checkTeacherConflict(primaryTeacher, session.getSessionDate(), newStartTime, newEndTime, session.getId());
        checkClassConflict(session.getSchoolClass().getId(), session.getSessionDate(), newStartTime, newEndTime, session.getId());

        session.setStartTime(newStartTime);
        session.setEndTime(newEndTime);
        session.setRoom(newRoom);
        session.setPrimaryTeacher(primaryTeacher);
        session.setAssistantTeacher(assistantTeacher);
        session.setCmTeacher(cmTeacher);
        session.setTeacherType(parseTeacherType(request.teacherType()));
        session = classSessionRepository.save(session);

        // Phải xoá session_periods_history TRƯỚC (FK NOT NULL không cascade — V14), rồi mới xoá
        // session_periods, rồi flush() để tránh 2 lỗi: (1) vi phạm FK khi period còn lịch sử trỏ
        // tới, (2) Hibernate mặc định chạy INSERT trước DELETE trong cùng 1 flush nên periodNumber
        // mới trùng periodNumber cũ (VD đổi [2,3] -> [3,4]) sẽ vi phạm UNIQUE nếu không flush DELETE
        // trước khi INSERT. Chấp nhận mất lịch sử cấp-tiết của các tiết bị thay — audit cấp buổi học
        // vẫn còn nguyên ở class_sessions_history (UPDATED, ghi ngay dưới đây).
        sessionPeriodHistoryRepository.deleteBySessionPeriodClassSessionId(session.getId());
        sessionPeriodRepository.deleteByClassSessionId(session.getId());
        sessionPeriodRepository.flush();
        generatePeriodsFromTemplate(session, templates, actor);

        writeClassSessionHistory(session, actor, ClassSessionHistory.Action.UPDATED);
        return toResponse(session);
    }

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-27) — chặn
     * xếp/dời buổi học sang ngày sau classes.end_date (Ngày kết thúc dự
     * kiến). end_date NULL (lớp chưa xác định ngày kết thúc) thì không
     * chặn. Muốn xếp buổi học sau ngày này phải cập nhật end_date của lớp
     * trước (UC-18).
     */
    private void checkWithinClassPeriod(SchoolClass schoolClass, LocalDate sessionDate) {
        LocalDate endDate = schoolClass.getEndDate();
        if (endDate != null && sessionDate.isAfter(endDate)) {
            throw new ClassSessionOutsideClassPeriodException("error.classSessionOutsideClassPeriod.default",
                    new Object[]{sessionDate, endDate},
                    "Ngày " + sessionDate + " vượt quá ngày kết thúc dự kiến (" + endDate + ") của lớp học. "
                    + "Vui lòng cập nhật ngày kết thúc của lớp trước khi xếp buổi học ở ngày này.");
        }
    }

    private void checkRoomConflict(Room room, LocalDate date, LocalTime startTime, LocalTime endTime, Long editingSessionId) {
        if (room.isFlexible()) {
            return;
        }
        List<ClassSession> overlapping = classSessionRepository.findOverlappingInRoom(
                room.getId(), date, startTime, endTime, editingSessionId,
                List.of(ClassSession.Status.CANCELLED, ClassSession.Status.RESCHEDULED));
        if (!overlapping.isEmpty()) {
            throw new RoomConflictException("error.roomConflict.default", new Object[]{date},
                    "Phòng học này đã có buổi học khác trùng khung giờ ngày " + date + ".");
        }
    }

    /**
     * Chặn trùng giờ Giáo viên (bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng 2026-07-30) — 1 giáo viên không thể bị xếp 2 buổi chồng
     * giờ dù khác lớp/phòng. Chỉ áp dụng cho primaryTeacher — assistant/CM
     * không trực tiếp đứng lớp nên không chặn trùng giờ (bổ sung ngoài SDD
     * gốc, xác nhận 2026-08-19).
     */
    private void checkTeacherConflict(User teacher, LocalDate date, LocalTime startTime, LocalTime endTime, Long editingSessionId) {
        List<ClassSession> overlapping = classSessionRepository.findOverlappingForTeacher(
                teacher.getId(), date, startTime, endTime, editingSessionId,
                List.of(ClassSession.Status.CANCELLED, ClassSession.Status.RESCHEDULED));
        if (!overlapping.isEmpty()) {
            throw new TeacherScheduleConflictException("error.teacherScheduleConflict.default", new Object[]{date},
                    "Giáo viên này đã có buổi dạy khác trùng khung giờ ngày " + date + ".");
        }
    }

    /**
     * Chặn trùng giờ trong cùng 1 Lớp (bổ sung ngoài SDD gốc, đã xác nhận
     * với người dùng 2026-07-30) — VD lỡ tạo 2 buổi chồng giờ cho cùng 1
     * lớp, kể cả khi không gán phòng/phòng khác nhau (room-check không
     * bắt được trường hợp này).
     */
    private void checkClassConflict(Long classId, LocalDate date, LocalTime startTime, LocalTime endTime, Long editingSessionId) {
        List<ClassSession> overlapping = classSessionRepository.findOverlappingForClass(
                classId, date, startTime, endTime, editingSessionId,
                List.of(ClassSession.Status.CANCELLED, ClassSession.Status.RESCHEDULED));
        if (!overlapping.isEmpty()) {
            throw new ClassScheduleConflictException("error.classScheduleConflict.default", new Object[]{date},
                    "Lớp này đã có buổi học khác trùng khung giờ ngày " + date + ".");
        }
    }

    private void requireScheduled(ClassSession session) {
        if (session.getStatus() != ClassSession.Status.SCHEDULED) {
            throw new InvalidClassSessionStatusTransitionException("error.invalidClassSessionStatusTransition.notScheduled",
                    new Object[]{session.getStatus()},
                    "Chỉ có thể hủy/dời lịch/sửa buổi học đang ở trạng thái SCHEDULED (hiện tại: " + session.getStatus() + ").");
        }
    }

    private ClassSession getSessionOrThrow(Long classId, Long sessionId) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.classSession.sessionNotFound", new Object[]{sessionId},
                        "Không tìm thấy buổi học id=" + sessionId));
        if (!session.getSchoolClass().getId().equals(classId)) {
            throw new ResourceNotFoundException(
                    "error.classSession.sessionNotFoundInClass", new Object[]{sessionId, classId},
                    "Không tìm thấy buổi học id=" + sessionId + " thuộc lớp id=" + classId);
        }
        return session;
    }

    private Room getRoomOrThrow(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.classSession.roomNotFound", new Object[]{roomId},
                        "Không tìm thấy phòng học id=" + roomId));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.classSession.actorNotFound", new Object[]{userId},
                        "Không tìm thấy tài khoản id=" + userId));
    }

    /** Tra site_period_templates theo dayPart + từng periodNumber được chọn, sắp xếp tăng dần, ném lỗi rõ ràng nếu site chưa cấu hình tiết đó. */
    private List<SitePeriodTemplate> resolvePeriodTemplates(Long siteId, SitePeriodTemplate.DayPart dayPart, List<Integer> periodNumbers) {
        return periodNumbers.stream()
                .sorted()
                .map(periodNumber -> sitePeriodTemplateRepository.findBySiteIdAndDayPartAndPeriodNumberAndDeletedAtIsNull(siteId, dayPart, periodNumber)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "error.classSession.sitePeriodTemplateNotFound", new Object[]{periodNumber, dayPart},
                                "Điểm trường chưa cấu hình Tiết " + periodNumber + " buổi " + dayPart
                                        + " — vào Cơ sở vật chất & Đối tác > Điểm trường > Tiết học để thêm.")))
                .toList();
    }

    private void generatePeriodsFromTemplate(ClassSession session, List<SitePeriodTemplate> templates, User actor) {
        for (SitePeriodTemplate template : templates) {
            SessionPeriod period = new SessionPeriod();
            period.setClassSession(session);
            period.setDayPart(template.getDayPart());
            period.setPeriodNumber(template.getPeriodNumber());
            period.setStartTime(template.getStartTime());
            period.setEndTime(template.getEndTime());
            period = sessionPeriodRepository.save(period);
            writeSessionPeriodHistory(period, actor, SessionPeriodHistory.Action.CREATED);
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
        int sessionNumber = (int) classSessionRepository.countEarlierSessions(
                s.getSchoolClass().getId(), s.getSessionDate(), s.getId()) + 1;
        List<SessionPeriod> periods = sessionPeriodRepository.findByClassSessionIdOrderByPeriodNumber(s.getId());
        List<Integer> periodNumbers = periods.stream()
                .map(SessionPeriod::getPeriodNumber).sorted(Comparator.naturalOrder()).toList();
        String dayPart = periods.isEmpty() ? null : periods.get(0).getDayPart().name();
        return new ClassSessionResponse(
                s.getId(), s.getSchoolClass().getId(), s.getSchoolClass().getName(), s.getSessionDate(), s.getStartTime(), s.getEndTime(),
                dayPart, periodNumbers,
                s.getRoom() == null ? null : s.getRoom().getId(), s.getRoom() == null ? null : s.getRoom().getName(),
                s.getPrimaryTeacher().getId(), s.getPrimaryTeacher().getFullName(),
                s.getAssistantTeacher() == null ? null : s.getAssistantTeacher().getId(),
                s.getAssistantTeacher() == null ? null : s.getAssistantTeacher().getFullName(),
                s.getCmTeacher() == null ? null : s.getCmTeacher().getId(),
                s.getCmTeacher() == null ? null : s.getCmTeacher().getFullName(),
                s.getSessionType().name(), s.getStatus().name(),
                s.getCancellationReason(), s.getRescheduledToSession() == null ? null : s.getRescheduledToSession().getId(),
                s.getLessonContent(), s.getTeacherType() == null ? null : s.getTeacherType().name(),
                s.getActualTeacherName(), sessionNumber,
                s.getMakeupForSession() == null ? null : s.getMakeupForSession().getId(),
                s.getSchoolClass().getColor());
    }

    private SessionPeriodResponse toResponse(SessionPeriod p) {
        return new SessionPeriodResponse(
                p.getId(), p.getClassSession().getId(), p.getDayPart().name(), p.getPeriodNumber(), p.getStartTime(), p.getEndTime(),
                p.getTeacher() == null ? null : p.getTeacher().getId(), p.getSubject() == null ? null : p.getSubject().getId(),
                p.getContentNote());
    }
}
