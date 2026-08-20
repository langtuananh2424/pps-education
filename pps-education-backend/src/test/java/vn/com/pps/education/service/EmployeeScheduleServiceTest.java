package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.EmployeeShift;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Shift;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.dto.EmployeeScheduleOverviewResponse;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.EmployeeShiftRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.ShiftRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Trang "Lịch làm việc" (roster toàn công ty) — bổ sung ngoài SDD gốc, xác
 * nhận với người dùng 2026-08-17. Xem docs/uc/phan-he-04-nhan-su.md (UC-70).
 */
@Transactional
class EmployeeScheduleServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final double SITE_LAT = 21.0285;
    private static final double SITE_LNG = 105.8542;

    @Autowired
    private EmployeeScheduleService employeeScheduleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private EmployeeShiftRepository employeeShiftRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private CurriculumRepository curriculumRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    @Autowired
    private ClassSessionRepository classSessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SiteManagerRepository siteManagerRepository;

    @Test
    void getOverview_UC70_MainFlow_returnsAllEmployeesShiftsAndSessionsInRange() {
        Department department = newDepartment();
        User staffUser = newUser();
        Employee staffEmployee = newEmployee(staffUser, Employee.EmployeeType.STAFF, department);
        assignShift(staffEmployee);

        User teacherUser = newUser();
        Employee teacherEmployee = newEmployee(teacherUser, Employee.EmployeeType.TEACHER, department);
        Site site = newSite();
        SchoolClass schoolClass = newSchoolClass(site, teacherUser);
        newSession(schoolClass, teacherUser);

        EmployeeScheduleOverviewResponse response = employeeScheduleService.getOverview(
                LocalDate.now(), LocalDate.now(), null, null, null, null, staffUser.getId());

        assertThat(response.employees()).extracting("id")
                .contains(staffEmployee.getId(), teacherEmployee.getId());
        assertThat(response.employeeShifts()).extracting("employeeId").contains(staffEmployee.getId());
        assertThat(response.sessions()).extracting("primaryTeacherId").contains(teacherUser.getId());
    }

    @Test
    void getOverview_UC70_A1_filtersBySiteKeepsOnlyTeachersTeachingThere() {
        Department department = newDepartment();
        User staffUser = newUser();
        Employee staffEmployee = newEmployee(staffUser, Employee.EmployeeType.STAFF, department);

        Site siteA = newSite();
        User teacherAUser = newUser();
        Employee teacherAEmployee = newEmployee(teacherAUser, Employee.EmployeeType.TEACHER, department);
        SchoolClass classAtSiteA = newSchoolClass(siteA, teacherAUser);
        newSession(classAtSiteA, teacherAUser);

        Site siteB = newSite();
        User teacherBUser = newUser();
        newEmployee(teacherBUser, Employee.EmployeeType.TEACHER, department);
        SchoolClass classAtSiteB = newSchoolClass(siteB, teacherBUser);
        newSession(classAtSiteB, teacherBUser);

        EmployeeScheduleOverviewResponse response = employeeScheduleService.getOverview(
                LocalDate.now(), LocalDate.now(), null, siteA.getId(), null, null, staffUser.getId());

        assertThat(response.employees()).extracting("id").containsExactly(teacherAEmployee.getId());
        assertThat(response.employees()).extracting("id").doesNotContain(staffEmployee.getId());
        assertThat(response.sessions()).extracting("primaryTeacherId").containsOnly(teacherAUser.getId());
    }

    @Test
    void getOverview_UC70_filtersByDepartmentAndEmployeeCombined() {
        Department deptA = newDepartment();
        Department deptB = newDepartment();
        Employee empInDeptA1 = newEmployee(newUser(), Employee.EmployeeType.STAFF, deptA);
        Employee empInDeptA2 = newEmployee(newUser(), Employee.EmployeeType.STAFF, deptA);
        newEmployee(newUser(), Employee.EmployeeType.STAFF, deptB);
        User actorUser = newUser();

        EmployeeScheduleOverviewResponse response = employeeScheduleService.getOverview(
                LocalDate.now(), LocalDate.now(), deptA.getId(), null, null, empInDeptA1.getId(), actorUser.getId());

        assertThat(response.employees()).extracting("id").containsExactly(empInDeptA1.getId());
        assertThat(response.employees()).extracting("id").doesNotContain(empInDeptA2.getId());
    }

    /**
     * UC-71 site-scoping (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-08-18): Quản lý điểm trường chỉ thấy roster của điểm trường mình
     * phụ trách, kể cả khi KHÔNG truyền siteId — không cần biết trước ID
     * điểm trường của chính mình.
     */
    @Test
    void getOverview_UC71_siteManagerSeesOnlyManagedSiteEvenWithoutSiteIdParam() {
        Department department = newDepartment();

        Site managedSite = newSite();
        User managerUser = newUser();
        assignSiteManager(managedSite, managerUser);
        User teacherAtManagedSiteUser = newUser();
        Employee teacherAtManagedSite = newEmployee(teacherAtManagedSiteUser, Employee.EmployeeType.TEACHER, department);
        newSession(newSchoolClass(managedSite, teacherAtManagedSiteUser), teacherAtManagedSiteUser);

        Site otherSite = newSite();
        User teacherAtOtherSiteUser = newUser();
        newEmployee(teacherAtOtherSiteUser, Employee.EmployeeType.TEACHER, department);
        newSession(newSchoolClass(otherSite, teacherAtOtherSiteUser), teacherAtOtherSiteUser);

        EmployeeScheduleOverviewResponse response = employeeScheduleService.getOverview(
                LocalDate.now(), LocalDate.now(), null, null, null, null, managerUser.getId());

        assertThat(response.employees()).extracting("id").containsExactly(teacherAtManagedSite.getId());
        assertThat(response.sessions()).extracting("primaryTeacherId").containsOnly(teacherAtManagedSiteUser.getId());
    }

    /**
     * UC-71 — Quản lý điểm trường yêu cầu siteId nằm ngoài phạm vi phụ
     * trách -> không thấy gì (không throw lỗi, giữ UX report đơn giản).
     */
    @Test
    void getOverview_UC71_siteManagerRequestingOutsideSiteSeesNothing() {
        Department department = newDepartment();

        Site managedSite = newSite();
        User managerUser = newUser();
        assignSiteManager(managedSite, managerUser);

        Site otherSite = newSite();
        User teacherAtOtherSiteUser = newUser();
        newEmployee(teacherAtOtherSiteUser, Employee.EmployeeType.TEACHER, department);
        newSession(newSchoolClass(otherSite, teacherAtOtherSiteUser), teacherAtOtherSiteUser);

        EmployeeScheduleOverviewResponse response = employeeScheduleService.getOverview(
                LocalDate.now(), LocalDate.now(), null, otherSite.getId(), null, null, managerUser.getId());

        assertThat(response.employees()).isEmpty();
        assertThat(response.sessions()).isEmpty();
    }

    @Test
    void getOverview_UC70_rejectsInvalidDateRange() {
        assertThatThrownBy(() -> employeeScheduleService.getOverview(
                LocalDate.now(), LocalDate.now().minusDays(1), null, null, null, null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Department newDepartment() {
        Department department = new Department();
        department.setCode("DEPT-SCHED-" + SEQ.incrementAndGet());
        department.setName("Schedule Test Dept " + SEQ.get());
        return departmentRepository.save(department);
    }

    private User newUser() {
        User user = new User();
        user.setUsername("sched.user." + SEQ.incrementAndGet());
        user.setEmail("sched.user." + SEQ.incrementAndGet() + "@pps.edu.vn");
        user.setFullName("Schedule Test User");
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }

    private Employee newEmployee(User forUser, Employee.EmployeeType type, Department department) {
        Employee employee = new Employee();
        employee.setUser(forUser);
        employee.setEmployeeCode("NVSCHED" + SEQ.incrementAndGet());
        employee.setDateOfBirth(LocalDate.of(1995, 1, 1));
        employee.setEmployeeType(type);
        employee.setDepartment(department);
        employee.setManagement(false);
        employee.setDefaultShiftRequired(true);
        employee.setHireDate(LocalDate.of(2024, 1, 1));
        return employeeRepository.save(employee);
    }

    private Site newSite() {
        Site site = new Site();
        site.setCode("SITE-SCHED-" + SEQ.incrementAndGet());
        site.setName("Test Site");
        site.setSiteType(Site.SiteType.OWNED);
        site = siteRepository.save(site);
        jdbcTemplate.update(
                "UPDATE sites SET geo_location = ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography WHERE id = ?",
                SITE_LNG, SITE_LAT, site.getId());
        return site;
    }

    private SchoolClass newSchoolClass(Site site, User creator) {
        Curriculum curriculum = new Curriculum();
        curriculum.setCode("CUR-SCHED-" + SEQ.incrementAndGet());
        curriculum.setName("Test curriculum");
        curriculum.setClassCategory(Curriculum.ClassCategory.MAIN);
        curriculum.setCreatedBy(creator);
        curriculum = curriculumRepository.save(curriculum);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setClassCode("CLS-SCHED-" + SEQ.incrementAndGet());
        schoolClass.setName("Test class");
        schoolClass.setSite(site);
        schoolClass.setCurriculum(curriculum);
        schoolClass.setClassType(SchoolClass.ClassType.OPEN);
        schoolClass.setMaxStudents(20);
        schoolClass.setStartDate(LocalDate.now());
        schoolClass.setCreatedBy(creator);
        return schoolClassRepository.save(schoolClass);
    }

    private ClassSession newSession(SchoolClass schoolClass, User teacher) {
        ClassSession session = new ClassSession();
        session.setSchoolClass(schoolClass);
        session.setSessionDate(LocalDate.now());
        session.setStartTime(LocalTime.of(8, 0));
        session.setEndTime(LocalTime.of(9, 0));
        session.setPrimaryTeacher(teacher);
        session.setCreatedBy(teacher);
        session.setStatus(ClassSession.Status.SCHEDULED);
        return classSessionRepository.save(session);
    }

    private void assignShift(Employee forEmployee) {
        Shift shift = new Shift();
        shift.setCode("SHIFT-SCHED-" + SEQ.incrementAndGet());
        shift.setName("Test shift");
        shift.setCheckInTime(LocalTime.of(8, 0));
        shift.setCheckOutTime(LocalTime.of(17, 0));
        shift.setCheckInWindowBeforeMinutes(30);
        shift.setCheckInWindowAfterMinutes(30);
        shift.setCheckOutWindowBeforeMinutes(30);
        shift.setCheckOutWindowAfterMinutes(30);
        shift.setAppliesToWeekdays("1,2,3,4,5,6,7");
        shift.setWeekParity(Shift.WeekParity.ALL);
        shift.setActive(true);
        shift = shiftRepository.save(shift);

        EmployeeShift employeeShift = new EmployeeShift();
        employeeShift.setEmployee(forEmployee);
        employeeShift.setShift(shift);
        employeeShift.setEffectiveFrom(LocalDate.now().minusYears(1));
        employeeShiftRepository.save(employeeShift);
    }

    private void assignSiteManager(Site site, User managerUser) {
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(site);
        siteManager.setUser(managerUser);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(managerUser);
        siteManagerRepository.save(siteManager);
    }
}
