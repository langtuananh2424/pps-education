package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassHistory;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.EmployeeHistory;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.SessionPeriod;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.SitePeriodTemplate;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AcademicDashboardResponse;
import vn.com.pps.education.dto.ChangeHistoryItemResponse;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.TeacherProfileSummaryResponse;
import vn.com.pps.education.dto.TeacherTeachingStatsRow;
import vn.com.pps.education.dto.TeachingStatsResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassHistoryRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.EmployeeHistoryRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SessionPeriodRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V203 (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-09-30) — các chức năng giám sát đào tạo
 * của Trưởng phòng đào tạo: lịch sử thay đổi dữ liệu, hồ sơ giáo viên, thống kê giảng dạy theo giáo
 * viên, dashboard số liệu thật, xuất Excel tổng hợp chuyên cần.
 */
@Transactional
class HeadAcademicOversightServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired private ChangeHistoryService changeHistoryService;
    @Autowired private TeacherProfileService teacherProfileService;
    @Autowired private TeachingStatsService teachingStatsService;
    @Autowired private AcademicDashboardService academicDashboardService;
    @Autowired private AttendanceSummaryExportService attendanceSummaryExportService;
    @Autowired private ClassService classService;
    @Autowired private CurriculumService curriculumService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private SiteRepository siteRepository;
    @Autowired private SiteManagerRepository siteManagerRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private ClassHistoryRepository classHistoryRepository;
    @Autowired private ClassSessionRepository classSessionRepository;
    @Autowired private SessionPeriodRepository sessionPeriodRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EmployeeHistoryRepository employeeHistoryRepository;

    private User headAcademic;
    private Site site;
    private ClassResponse schoolClass;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");

        var curriculum = curriculumService.create(
                new CreateCurriculumRequest("CUR-" + SEQ.incrementAndGet(), "Chuẩn", "MAIN", null, null, null, null, null),
                headAcademic.getId());
        var activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());

        site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest("CLS-" + SEQ.incrementAndGet(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now().minusMonths(1), null, null), headAcademic.getId());
    }

    // ===================== Lịch sử thay đổi dữ liệu =====================

    @Test
    void search_V203_MainFlow_returnsClassChangeWithPreviousSnapshot() {
        // Trạng thái lúc tạo do ClassService tự suy theo ngày bắt đầu — đọc lại thay vì giả định PLANNED.
        String createdStatus = String.valueOf(classHistoryRepository
                .findBySchoolClassIdOrderByCreatedAtDesc(schoolClass.id()).get(0).getDetails().get("status"));
        Map<String, Object> updated = new LinkedHashMap<>();
        updated.put("classCode", schoolClass.classCode());
        updated.put("name", "8A2");
        updated.put("maxStudents", 20);
        updated.put("status", "COMPLETED");
        ClassHistory history = new ClassHistory();
        history.setSchoolClass(classEntity());
        history.setChangedBy(headAcademic);
        history.setAction(ClassHistory.Action.UPDATED);
        history.setDetails(updated);
        classHistoryRepository.save(history);

        Page<ChangeHistoryItemResponse> page = changeHistoryService.search(
                "CLASS", null, null, null, schoolClass.id(), null, null, 0, 20, headAcademic.getId());

        assertThat(page.getTotalElements()).isEqualTo(2);
        ChangeHistoryItemResponse latest = page.getContent().get(0);
        assertThat(latest.action()).isEqualTo("UPDATED");
        assertThat(latest.details()).containsEntry("status", "COMPLETED");
        assertThat(latest.previousDetails()).containsEntry("status", createdStatus);
        assertThat(latest.changedByName()).isEqualTo(headAcademic.getFullName());
        assertThat(page.getContent().get(1).action()).isEqualTo("CREATED");
        assertThat(page.getContent().get(1).previousDetails()).isNull();
    }

    @Test
    void search_V203_rejectsUnknownEntityType() {
        assertThatThrownBy(() -> changeHistoryService.search(
                "PAYROLL", null, null, null, null, null, null, 0, 20, headAcademic.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void search_V203_siteManagerOfOtherSiteSeesNoClassHistory() {
        User outsider = newUser("site.manager");
        assignRole(outsider, "SITE_MANAGER");
        saveSiteManager(outsider, newSite());

        Page<ChangeHistoryItemResponse> page = changeHistoryService.search(
                null, null, null, null, null, null, null, 0, 20, outsider.getId());

        assertThat(page.getContent()).noneMatch(i -> schoolClass.id().equals(i.classId()));
    }

    @Test
    void search_V203_hidesIdCardAndNonTeacherStaffWithoutEmployeeViewPermission() {
        Employee teacher = newEmployee("teacher", Employee.EmployeeType.TEACHER);
        Employee staff = newEmployee("staff", Employee.EmployeeType.STAFF);
        saveEmployeeHistory(teacher);
        saveEmployeeHistory(staff);

        Page<ChangeHistoryItemResponse> page = changeHistoryService.search(
                "EMPLOYEE", null, null, null, null, null, null, 0, 20, headAcademic.getId());

        assertThat(page.getContent()).extracting(ChangeHistoryItemResponse::entityId)
                .contains(teacher.getId())
                .doesNotContain(staff.getId());
        ChangeHistoryItemResponse teacherChange = page.getContent().stream()
                .filter(i -> teacher.getId().equals(i.entityId())).findFirst().orElseThrow();
        assertThat(teacherChange.details()).doesNotContainKey("idCardNumber").containsKey("employeeCode");
    }

    // ===================== Hồ sơ giáo viên =====================

    @Test
    void searchTeachers_V203_MainFlow_listsOnlyTeachers() {
        Employee teacher = newEmployee("teacher", Employee.EmployeeType.TEACHER);
        Employee staff = newEmployee("staff", Employee.EmployeeType.STAFF);

        List<TeacherProfileSummaryResponse> result = teacherProfileService.search(null, null, headAcademic.getId());

        assertThat(result).extracting(TeacherProfileSummaryResponse::employeeId)
                .contains(teacher.getId())
                .doesNotContain(staff.getId());
    }

    @Test
    void getTeacherDetail_V203_notFoundForNonTeacherEmployee() {
        Employee staff = newEmployee("staff", Employee.EmployeeType.STAFF);

        assertThatThrownBy(() -> teacherProfileService.getDetail(staff.getId(), headAcademic.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===================== Thống kê giảng dạy theo giáo viên =====================

    @Test
    void getTeachingStats_V203_MainFlow_countsHeldCancelledMissingAndPeriods() {
        User teacher = newUser("teacher");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        ClassSession held = newSession(teacher, yesterday, ClassSession.Status.SCHEDULED);
        newPeriod(held, 1);
        newPeriod(held, 2);
        newSession(teacher, yesterday, ClassSession.Status.CANCELLED);

        TeachingStatsResponse stats = teachingStatsService.getStats(
                site.getId(), yesterday.minusDays(1), yesterday, headAcademic.getId());

        TeacherTeachingStatsRow row = stats.teachers().stream()
                .filter(r -> teacher.getId().equals(r.teacherUserId())).findFirst().orElseThrow();
        assertThat(row.scheduledSessions()).isEqualTo(2);
        assertThat(row.cancelledSessions()).isEqualTo(1);
        assertThat(row.heldSessions()).isEqualTo(1);
        assertThat(row.missingCheckIns()).isEqualTo(1);
        assertThat(row.taughtPeriods()).isEqualTo(2);
        assertThat(row.onTimeRate()).isEqualTo(0.0);
        assertThat(stats.totals().heldSessions()).isEqualTo(1);
    }

    @Test
    void getTeachingStats_V203_rejectsFromDateAfterToDate() {
        assertThatThrownBy(() -> teachingStatsService.getStats(
                null, LocalDate.now(), LocalDate.now().minusDays(1), headAcademic.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exportTeachingStats_V203_MainFlow_producesXlsx() {
        byte[] content = teachingStatsService.exportStatsExcel(
                null, LocalDate.now().minusDays(7), LocalDate.now(), headAcademic.getId());

        assertThat(content).isNotEmpty();
        assertThat(new String(content, 0, 2)).isEqualTo("PK");
    }

    // ===================== Dashboard =====================

    @Test
    void getAcademicOverview_V203_MainFlow_countsClassesInScope() {
        AcademicDashboardResponse overview = academicDashboardService.getOverview(site.getId(), headAcademic.getId());

        assertThat(overview.plannedClasses() + overview.openEnrollmentClasses() + overview.inProgressClasses()).isEqualTo(1);
        assertThat(overview.siteName()).isEqualTo(site.getName());
        assertThat(overview.attendanceRate()).isNull();
    }

    // ===================== Tổng hợp chuyên cần =====================

    @Test
    void exportAttendanceSummary_V203_MainFlow_producesXlsx() {
        byte[] content = attendanceSummaryExportService.exportClassSummary(schoolClass.id(), null, null, headAcademic.getId());

        assertThat(content).isNotEmpty();
        assertThat(new String(content, 0, 2)).isEqualTo("PK");
    }

    @Test
    void exportAttendanceSummary_V203_notFoundForClassOutsideScope() {
        User outsider = newUser("site.manager");
        assignRole(outsider, "SITE_MANAGER");
        saveSiteManager(outsider, newSite());

        assertThatThrownBy(() -> attendanceSummaryExportService.exportClassSummary(schoolClass.id(), null, null, outsider.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===================== Helpers =====================

    private SchoolClass classEntity() {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(schoolClass.id()).orElseThrow();
    }

    private ClassSession newSession(User teacher, LocalDate date, ClassSession.Status status) {
        ClassSession session = new ClassSession();
        session.setSchoolClass(classEntity());
        session.setSessionDate(date);
        session.setStartTime(LocalTime.of(8, 0));
        session.setEndTime(LocalTime.of(9, 30));
        session.setPrimaryTeacher(teacher);
        session.setStatus(status);
        session.setCreatedBy(headAcademic);
        return classSessionRepository.save(session);
    }

    private void newPeriod(ClassSession session, int periodNumber) {
        SessionPeriod period = new SessionPeriod();
        period.setClassSession(session);
        period.setDayPart(SitePeriodTemplate.DayPart.MORNING);
        period.setPeriodNumber(periodNumber);
        period.setStartTime(LocalTime.of(8, 0).plusMinutes(45L * (periodNumber - 1)));
        period.setEndTime(LocalTime.of(8, 45).plusMinutes(45L * (periodNumber - 1)));
        sessionPeriodRepository.save(period);
    }

    private Employee newEmployee(String prefix, Employee.EmployeeType type) {
        Employee employee = new Employee();
        employee.setUser(newUser(prefix));
        employee.setEmployeeCode("NV-TEST-" + SEQ.incrementAndGet());
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setIdCardNumber("0790" + SEQ.incrementAndGet());
        employee.setEmployeeType(type);
        employee.setHireDate(LocalDate.now().minusYears(1));
        return employeeRepository.save(employee);
    }

    private void saveEmployeeHistory(Employee employee) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeCode", employee.getEmployeeCode());
        snapshot.put("idCardNumber", employee.getIdCardNumber());
        snapshot.put("employeeType", employee.getEmployeeType().name());
        EmployeeHistory history = new EmployeeHistory();
        history.setEmployee(employee);
        history.setChangedBy(headAcademic);
        history.setAction(EmployeeHistory.Action.CREATED);
        history.setDetails(snapshot);
        employeeHistoryRepository.save(history);
    }

    private void saveSiteManager(User user, Site targetSite) {
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(targetSite);
        siteManager.setUser(user);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(user);
        siteManagerRepository.save(siteManager);
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private Site newSite() {
        Site s = new Site();
        s.setCode("SITE-" + SEQ.incrementAndGet());
        s.setName("Test Site " + SEQ.get());
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
