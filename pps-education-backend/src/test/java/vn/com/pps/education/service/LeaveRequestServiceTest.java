package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.ClassTeacher;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Room;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.CreateClassSessionRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateLeaveRequestRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.DecideLeaveRequestRequest;
import vn.com.pps.education.dto.LeaveRequestResponse;
import vn.com.pps.education.dto.SubstituteAssignmentRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.ExecutiveExemptFromLeaveRequestException;
import vn.com.pps.education.exception.NotCurrentApproverException;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.LeaveSubstitutionRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.RoomRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-10: Nộp đơn từ + UC-11: Duyệt đơn từ — Main Flow, A1 (UC-10: Ban giám
 * đốc miễn trừ), A2 (UC-10: phòng ban không có trưởng phòng), A3/A4 (UC-10:
 * giáo viên dạy thay), A1 (UC-11: từ chối giữa chừng), A2 (UC-11: thu hồi
 * dạy thay khi từ chối). Xem docs/uc/phan-he-04-nhan-su.md.
 */
@Transactional
class LeaveRequestServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private ClassSessionService classSessionService;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ClassSessionRepository classSessionRepository;

    @Autowired
    private ClassTeacherRepository classTeacherRepository;

    @Autowired
    private LeaveSubstitutionRepository leaveSubstitutionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void submit_UC10_MainFlow_departmentWithHead_createsTwoStepWorkflow() {
        User head = newUser("dept.head");
        Department department = newDepartment(head);
        User staffUser = newUser("staff.with.head");
        newEmployee(staffUser, department);

        LeaveRequestResponse response = leaveRequestService.submit(staffUser.getId(),
                new CreateLeaveRequestRequest("ANNUAL", LocalDate.now().plusDays(5), LocalDate.now().plusDays(7),
                        null, null, "Nghỉ phép năm", null, null));

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.currentStep()).isEqualTo(1);
        assertThat(response.currentApproverUserId()).isEqualTo(head.getId());
        assertThat(response.totalDays()).isEqualByComparingTo("3.00");
    }

    @Test
    void submit_UC10_A2_departmentWithoutHead_skipsDirectlyToOperationsManager() {
        Department department = newDepartment(null);
        User staffUser = newUser("staff.no.head");
        newEmployee(staffUser, department);

        LeaveRequestResponse response = leaveRequestService.submit(staffUser.getId(),
                new CreateLeaveRequestRequest("SICK", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                        null, null, "Ốm", null, null));

        assertThat(response.currentApproverUserId()).isNull(); // role-based (OPERATIONS_MANAGER), chưa xác định người cụ thể
    }

    @Test
    void submit_UC10_managementEmployee_singleStepExecutiveWorkflow() {
        User siteManagerUser = newUser("site.manager");
        assignRole(siteManagerUser, "SITE_MANAGER");
        newEmployee(siteManagerUser);

        LeaveRequestResponse response = leaveRequestService.submit(siteManagerUser.getId(),
                new CreateLeaveRequestRequest("PERSONAL", LocalDate.now().plusDays(2), LocalDate.now().plusDays(2),
                        null, null, "Việc riêng", null, null));

        assertThat(response.currentStep()).isEqualTo(1);
        assertThat(response.currentApproverUserId()).isNull(); // EXECUTIVE -- role-based
    }

    @Test
    void submit_UC10_A1_rejectsExecutiveRole() {
        User executiveUser = newUser("executive");
        assignRole(executiveUser, "EXECUTIVE");
        newEmployee(executiveUser);

        assertThatThrownBy(() -> leaveRequestService.submit(executiveUser.getId(),
                new CreateLeaveRequestRequest("ANNUAL", LocalDate.now(), LocalDate.now(), null, null, "x", null, null)))
                .isInstanceOf(ExecutiveExemptFromLeaveRequestException.class);
    }

    @Test
    void decide_UC11_MainFlow_bothStepsApproved_finalizesApproved() {
        User head = newUser("decide.head");
        Department department = newDepartment(head);
        User staffUser = newUser("decide.staff");
        newEmployee(staffUser, department);
        User opsManagerUser = newUser("decide.ops");
        assignRole(opsManagerUser, "OPS_MANAGER");

        LeaveRequestResponse submitted = leaveRequestService.submit(staffUser.getId(),
                new CreateLeaveRequestRequest("ANNUAL", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                        null, null, "Nghỉ 1 ngày", null, null));

        LeaveRequestResponse afterStep1 = leaveRequestService.decide(head.getId(), submitted.id(),
                new DecideLeaveRequestRequest("APPROVED", "OK"));
        assertThat(afterStep1.status()).isEqualTo("PENDING");
        assertThat(afterStep1.currentStep()).isEqualTo(2);

        LeaveRequestResponse afterStep2 = leaveRequestService.decide(opsManagerUser.getId(), submitted.id(),
                new DecideLeaveRequestRequest("APPROVED", "Duyệt"));
        assertThat(afterStep2.status()).isEqualTo("APPROVED");
        assertThat(afterStep2.finalizedAt()).isNotNull();
    }

    @Test
    void decide_UC11_A1_departmentHeadRejects_finalizesImmediatelyWithoutSecondStep() {
        User head = newUser("reject.head");
        Department department = newDepartment(head);
        User staffUser = newUser("reject.staff");
        newEmployee(staffUser, department);

        LeaveRequestResponse submitted = leaveRequestService.submit(staffUser.getId(),
                new CreateLeaveRequestRequest("UNPAID", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                        null, null, "Nghỉ không lương", null, null));

        LeaveRequestResponse afterReject = leaveRequestService.decide(head.getId(), submitted.id(),
                new DecideLeaveRequestRequest("REJECTED", "Không đủ điều kiện"));

        assertThat(afterReject.status()).isEqualTo("REJECTED");
        assertThat(afterReject.currentStep()).isEqualTo(1); // dừng ngay, không sang bước 2
        assertThat(afterReject.finalizedAt()).isNotNull();
    }

    @Test
    void decide_rejectsWhenActorIsNotCurrentApprover() {
        User head = newUser("wrongapprover.head");
        Department department = newDepartment(head);
        User staffUser = newUser("wrongapprover.staff");
        newEmployee(staffUser, department);
        User randomUser = newUser("wrongapprover.random");

        LeaveRequestResponse submitted = leaveRequestService.submit(staffUser.getId(),
                new CreateLeaveRequestRequest("ANNUAL", LocalDate.now().plusDays(1), LocalDate.now().plusDays(1),
                        null, null, "x", null, null));

        assertThatThrownBy(() -> leaveRequestService.decide(randomUser.getId(), submitted.id(),
                new DecideLeaveRequestRequest("APPROVED", null)))
                .isInstanceOf(NotCurrentApproverException.class);
    }

    @Test
    void submit_UC10_Buoc5_teacherWithSubstitute_appliesImmediatelyWithoutWaitingForApproval() {
        User head = newUser("sub.head");
        Department department = newDepartment(head);
        User teacherUser = newUser("sub.teacher");
        newTeacherEmployee(teacherUser, department);
        User substituteUser = newUser("sub.substitute");

        LocalDate sessionDate = LocalDate.now().plusDays(3);
        ClassSessionResponse session = newClassSessionTaughtBy(teacherUser, sessionDate);

        LeaveRequestResponse submitted = leaveRequestService.submit(teacherUser.getId(),
                new CreateLeaveRequestRequest("ANNUAL", sessionDate, sessionDate, null, null, "Nghỉ có dạy thay", null,
                        List.of(new SubstituteAssignmentRequest(session.id(), substituteUser.getId()))));

        assertThat(submitted.status()).isEqualTo("PENDING"); // chưa duyệt

        // Postcondition UC-10 bước 5: áp dụng NGAY, không đợi duyệt.
        ClassSession updatedSession = classSessionRepository.findById(session.id()).orElseThrow();
        assertThat(updatedSession.getPrimaryTeacher().getId()).isEqualTo(substituteUser.getId());

        List<ClassTeacher> substituteRoster = classTeacherRepository.findBySchoolClassIdAndAssignedToIsNull(session.classId());
        assertThat(substituteRoster).anySatisfy(ct -> {
            assertThat(ct.getTeacher().getId()).isEqualTo(substituteUser.getId());
            assertThat(ct.getTeacherRole()).isEqualTo(ClassTeacher.TeacherRole.SUBSTITUTE);
        });

        assertThat(leaveSubstitutionRepository.findByClassSessionIdAndRevokedAtIsNull(session.id()))
                .isPresent()
                .get().satisfies(ls -> {
                    assertThat(ls.getSubstituteTeacher().getId()).isEqualTo(substituteUser.getId());
                    assertThat(ls.getOriginalTeacher().getId()).isEqualTo(teacherUser.getId());
                    assertThat(ls.getRevokedAt()).isNull();
                });
    }

    @Test
    void submit_UC10_teacherWithTeachingSessionsButNoSubstitutes_throws() {
        User head = newUser("nosub.head");
        Department department = newDepartment(head);
        User teacherUser = newUser("nosub.teacher");
        newTeacherEmployee(teacherUser, department);

        LocalDate sessionDate = LocalDate.now().plusDays(3);
        newClassSessionTaughtBy(teacherUser, sessionDate);

        assertThatThrownBy(() -> leaveRequestService.submit(teacherUser.getId(),
                new CreateLeaveRequestRequest("ANNUAL", sessionDate, sessionDate, null, null, "Nghỉ thiếu dạy thay", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submit_UC10_A3_teacherSubstitutesAcrossMultipleClasses_rejectsMixedSelection() {
        User head = newUser("a3.head");
        Department department = newDepartment(head);
        User teacherUser = newUser("a3.teacher");
        newTeacherEmployee(teacherUser, department);
        User substituteUser = newUser("a3.substitute");

        LocalDate sessionDate = LocalDate.now().plusDays(3);
        ClassSessionResponse sessionClass1 = newClassSessionTaughtBy(teacherUser, sessionDate, LocalTime.of(8, 0));
        ClassSessionResponse sessionClass2 = newClassSessionTaughtBy(teacherUser, sessionDate, LocalTime.of(10, 0));

        assertThatThrownBy(() -> leaveRequestService.submit(teacherUser.getId(),
                new CreateLeaveRequestRequest("ANNUAL", sessionDate, sessionDate, null, null, "Nghỉ trùng nhiều lớp", null,
                        List.of(new SubstituteAssignmentRequest(sessionClass1.id(), substituteUser.getId()),
                                new SubstituteAssignmentRequest(sessionClass2.id(), substituteUser.getId())))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submit_UC10_A4_teacherWithoutTeachingSessions_skipsSubstituteStep() {
        User head = newUser("a4.head");
        Department department = newDepartment(head);
        User teacherUser = newUser("a4.teacher");
        newTeacherEmployee(teacherUser, department);

        LeaveRequestResponse submitted = leaveRequestService.submit(teacherUser.getId(),
                new CreateLeaveRequestRequest("ANNUAL", LocalDate.now().plusDays(30), LocalDate.now().plusDays(30),
                        null, null, "Nghỉ không có lịch dạy", null, null));

        assertThat(submitted.status()).isEqualTo("PENDING");
    }

    @Test
    void decide_UC11_A2_rejectRevokesSubstituteImmediately() {
        User head = newUser("revoke.head");
        Department department = newDepartment(head);
        User teacherUser = newUser("revoke.teacher");
        newTeacherEmployee(teacherUser, department);
        User substituteUser = newUser("revoke.substitute");

        LocalDate sessionDate = LocalDate.now().plusDays(3);
        ClassSessionResponse session = newClassSessionTaughtBy(teacherUser, sessionDate);

        LeaveRequestResponse submitted = leaveRequestService.submit(teacherUser.getId(),
                new CreateLeaveRequestRequest("ANNUAL", sessionDate, sessionDate, null, null, "Nghỉ bị từ chối", null,
                        List.of(new SubstituteAssignmentRequest(session.id(), substituteUser.getId()))));

        // Trước khi từ chối: đã dạy thay.
        assertThat(classSessionRepository.findById(session.id()).orElseThrow().getPrimaryTeacher().getId())
                .isEqualTo(substituteUser.getId());

        leaveRequestService.decide(head.getId(), submitted.id(), new DecideLeaveRequestRequest("REJECTED", "Không đủ điều kiện"));

        // A2: thu hồi ngay -- trả về đúng GV chính ban đầu, đóng class_teachers, ghi revoked_at.
        ClassSession revertedSession = classSessionRepository.findById(session.id()).orElseThrow();
        assertThat(revertedSession.getPrimaryTeacher().getId()).isEqualTo(teacherUser.getId());

        assertThat(leaveSubstitutionRepository.findByClassSessionIdAndRevokedAtIsNull(session.id())).isEmpty();

        List<ClassTeacher> substituteRoster = classTeacherRepository.findBySchoolClassId(session.classId()).stream()
                .filter(ct -> ct.getTeacherRole() == ClassTeacher.TeacherRole.SUBSTITUTE)
                .toList();
        assertThat(substituteRoster).allSatisfy(ct -> assertThat(ct.getAssignedTo()).isNotNull());
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private Department newDepartment(User head) {
        Department department = new Department();
        department.setCode("DEPT-" + SEQ.incrementAndGet());
        department.setName("Test Department");
        department.setHeadUser(head);
        return departmentRepository.save(department);
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + SEQ.incrementAndGet());
        user.setEmail(prefix + "." + SEQ.incrementAndGet() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }

    private Employee newEmployee(User forUser) {
        return newEmployee(forUser, null);
    }

    private Employee newEmployee(User forUser, Department department) {
        Employee employee = new Employee();
        employee.setUser(forUser);
        employee.setEmployeeCode("NVLR" + SEQ.incrementAndGet());
        employee.setDateOfBirth(LocalDate.of(1995, 1, 1));
        employee.setEmployeeType(Employee.EmployeeType.STAFF);
        employee.setDepartment(department);
        employee.setDefaultShiftRequired(true);
        employee.setHireDate(LocalDate.of(2024, 1, 1));
        return employeeRepository.save(employee);
    }

    private Employee newTeacherEmployee(User forUser, Department department) {
        assignRole(forUser, "TEACHER");
        Employee employee = newEmployee(forUser, department);
        employee.setEmployeeType(Employee.EmployeeType.TEACHER);
        return employeeRepository.save(employee);
    }

    /** Dựng 1 lớp (curriculum + class + site + room) mới với 1 buổi học do teacherUser dạy, để test UC-10 bước 3. */
    private ClassSessionResponse newClassSessionTaughtBy(User teacherUser, LocalDate sessionDate) {
        return newClassSessionTaughtBy(teacherUser, sessionDate, LocalTime.of(8, 0));
    }

    private ClassSessionResponse newClassSessionTaughtBy(User teacherUser, LocalDate sessionDate, LocalTime startTime) {
        User headAcademic = newUser("headacademic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        CurriculumResponse activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());
        Site site = newSite();
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "Test Class", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        Room room = newRoom(site);
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacherUser.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());

        return classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(sessionDate, startTime, startTime.plusMinutes(100), room.getId(), "REGULAR", "VIETNAMESE", null), headAcademic.getId());
    }

    private String curriculumCode() {
        return "CUR-LR-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-LR-" + SEQ.incrementAndGet();
    }

    private Site newSite() {
        Site s = new Site();
        s.setCode("SITE-LR-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private Room newRoom(Site site) {
        Room r = new Room();
        r.setSite(site);
        r.setCode("ROOM-LR-" + SEQ.incrementAndGet());
        r.setName("Test Room");
        r.setRoomType(Room.RoomType.THEORY);
        r.setCapacity(30);
        r.setFlexible(false);
        return roomRepository.save(r);
    }
}
