package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.EmployeeResponse;
import vn.com.pps.education.dto.EmployeeScheduleOverviewResponse;
import vn.com.pps.education.dto.EmployeeShiftResponse;
import vn.com.pps.education.dto.ShiftResponse;
import vn.com.pps.education.dto.WorkCalendarResponse;
import vn.com.pps.education.repository.SiteManagerRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bổ sung ngoài SDD gốc, xác nhận 2026-08-17: trang "Lịch làm việc" (roster
 * toàn công ty, gộp ca làm cố định + lịch dạy + lịch nghỉ lễ) cho HR/Điều
 * hành, gate bằng quyền hrm.employee-schedule.view (xem
 * EmployeeScheduleController). Thuần orchestration -- gọi các Service anh
 * em, không tự query Repository trực tiếp. Xem
 * docs/uc/phan-he-04-nhan-su.md (UC-70).
 *
 * UC-71 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-18):
 * gộp thêm trạng thái nhận lớp (classSessionCheckIns) + site-scoping cho
 * SITE_MANAGER (chỉ thấy roster của điểm trường mình phụ trách). Xem
 * resolveAllowedSiteIds bên dưới.
 */
@Service
public class EmployeeScheduleService {

    private static final String TEACHER_TYPE = "TEACHER";

    private final EmployeeService employeeService;
    private final ClassSessionService classSessionService;
    private final EmployeeShiftService employeeShiftService;
    private final ShiftService shiftService;
    private final WorkCalendarService workCalendarService;
    private final ClassSessionCheckInService classSessionCheckInService;
    private final SiteManagerRepository siteManagerRepository;

    public EmployeeScheduleService(EmployeeService employeeService, ClassSessionService classSessionService,
                                    EmployeeShiftService employeeShiftService, ShiftService shiftService,
                                    WorkCalendarService workCalendarService,
                                    ClassSessionCheckInService classSessionCheckInService,
                                    SiteManagerRepository siteManagerRepository) {
        this.employeeService = employeeService;
        this.classSessionService = classSessionService;
        this.employeeShiftService = employeeShiftService;
        this.shiftService = shiftService;
        this.workCalendarService = workCalendarService;
        this.classSessionCheckInService = classSessionCheckInService;
        this.siteManagerRepository = siteManagerRepository;
    }

    @Transactional(readOnly = true)
    public EmployeeScheduleOverviewResponse getOverview(LocalDate from, LocalDate to, Long departmentId,
                                                          Long siteId, Long classId, Long employeeId, Long actorUserId) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Khoảng ngày không hợp lệ: from phải trước hoặc bằng to.");
        }

        List<Long> allowedSiteIds = resolveEffectiveSiteIds(actorUserId, siteId);
        boolean siteScoped = allowedSiteIds != null;

        List<EmployeeResponse> baseEmployees = employeeService.search(null, departmentId).stream()
                .filter(e -> employeeId == null || e.id().equals(employeeId))
                .toList();

        List<Long> teacherUserIds = baseEmployees.stream()
                .filter(e -> TEACHER_TYPE.equals(e.employeeType()))
                .map(EmployeeResponse::userId)
                .toList();

        List<ClassSessionResponse> sessions = classSessionService.listForScheduleOverview(teacherUserIds, allowedSiteIds, classId, from, to);

        List<EmployeeResponse> finalEmployees = baseEmployees;
        if (siteScoped || classId != null) {
            Set<Long> sessionTeacherUserIds = sessions.stream()
                    .map(ClassSessionResponse::primaryTeacherId)
                    .collect(Collectors.toSet());
            finalEmployees = baseEmployees.stream()
                    .filter(e -> TEACHER_TYPE.equals(e.employeeType()) && sessionTeacherUserIds.contains(e.userId()))
                    .toList();
        }

        List<Long> employeeIds = finalEmployees.stream().map(EmployeeResponse::id).toList();
        List<EmployeeShiftResponse> employeeShifts = employeeShiftService.listForScheduleOverview(employeeIds, from, to);
        List<ShiftResponse> shiftDefinitions = shiftService.listShifts();
        List<WorkCalendarResponse> workCalendarOverrides = workCalendarService.listByDateRange(from, to);
        var classSessionCheckIns = classSessionCheckInService.listEffectiveStatus(sessions);

        return new EmployeeScheduleOverviewResponse(finalEmployees, shiftDefinitions, employeeShifts, sessions,
                workCalendarOverrides, classSessionCheckIns);
    }

    /**
     * UC-71 site-scoping: actor có bản ghi site_managers active (bất kể có
     * quản lý 1 hay nhiều điểm trường) → chỉ thấy roster của (các) điểm
     * trường đó, bỏ qua tham số siteId client gửi nếu nằm ngoài phạm vi;
     * actor KHÔNG có bản ghi site_managers nào (HR/Điều hành/SUPER_ADMIN/
     * SYS_ADMIN) → không giới hạn, dùng nguyên siteId client gửi (có thể
     * null = xem toàn trung tâm). Cùng tinh thần row-level scoping đã dùng
     * ở FinanceReportService#getMySiteReports/ClassSessionService#resolveAllowedSiteIds.
     * Trả về null = không lọc theo site.
     */
    private List<Long> resolveEffectiveSiteIds(Long actorUserId, Long requestedSiteId) {
        List<Long> managedSiteIds = siteManagerRepository
                .findByUserIdAndRoleTypeAndAssignedToIsNull(actorUserId, SiteManager.RoleType.SITE_MANAGER).stream()
                .map(sm -> sm.getSite().getId()).toList();

        if (managedSiteIds.isEmpty()) {
            return requestedSiteId == null ? null : List.of(requestedSiteId);
        }
        if (requestedSiteId == null || managedSiteIds.contains(requestedSiteId)) {
            return requestedSiteId == null ? managedSiteIds : List.of(requestedSiteId);
        }
        return List.of(); // yêu cầu site ngoài phạm vi phụ trách -> không thấy gì, không lỗi
    }
}
