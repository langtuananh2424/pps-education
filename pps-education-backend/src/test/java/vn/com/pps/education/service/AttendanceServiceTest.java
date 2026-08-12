package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AttendanceRecord;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.EmployeeShift;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Shift;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.WorkCalendar;
import vn.com.pps.education.dto.AttendanceCheckRequest;
import vn.com.pps.education.dto.AttendanceRecordResponse;
import vn.com.pps.education.exception.AttendanceMethodNotAvailableException;
import vn.com.pps.education.exception.BiometricVerificationFailedException;
import vn.com.pps.education.exception.ManagementExemptFromAttendanceException;
import vn.com.pps.education.exception.NotAWorkingDayException;
import vn.com.pps.education.exception.OutsideAttendanceWindowException;
import vn.com.pps.education.exception.OutsideGpsRadiusException;
import vn.com.pps.education.repository.AttendanceRecordRepository;
import vn.com.pps.education.repository.ClassSessionRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.EmployeeShiftRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.ShiftRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.WorkCalendarRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-09: Chấm công — Main Flow, A1 (ngoài cửa sổ), A2 (GPS ngoài bán kính),
 * A3 (xác thực sinh trắc thất bại), + miễn trừ cấp quản lý (Main Flow bước 2).
 * Xem docs/uc/phan-he-04-nhan-su.md.
 */
@Transactional
class AttendanceServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();
    // Hà Nội (toạ độ điểm trường trong test)
    private static final double SITE_LAT = 21.0285;
    private static final double SITE_LNG = 105.8542;
    // TP.HCM -- cách xa hơn nhiều lần bán kính cho phép (200m mặc định)
    private static final double FAR_LAT = 10.8231;
    private static final double FAR_LNG = 106.6297;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

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
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private WorkCalendarRepository workCalendarRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private Employee employee;
    private Site site;

    @BeforeEach
    void setUp() {
        user = newUser();
        employee = newEmployee(user);
        site = newSite();
    }

    @Test
    void checkIn_UC09_MainFlow_recordsAttendanceWithinShiftWindow() {
        assignWideOpenShift();

        AttendanceRecordResponse response = attendanceService.checkIn(user.getId(),
                new AttendanceCheckRequest("GPS", site.getId(), SITE_LAT, SITE_LNG, null));

        assertThat(response.checkInAt()).isNotNull();
        assertThat(response.checkInMethod()).isEqualTo("GPS");
        assertThat(response.status()).isIn("NORMAL", "LATE");
    }

    @Test
    void checkInThenCheckOut_UC09_MainFlow_updatesSameRecord() {
        assignWideOpenShift();

        AttendanceRecordResponse checkedIn = attendanceService.checkIn(user.getId(),
                new AttendanceCheckRequest("GPS", site.getId(), SITE_LAT, SITE_LNG, null));
        AttendanceRecordResponse checkedOut = attendanceService.checkOut(user.getId(),
                new AttendanceCheckRequest("GPS", site.getId(), SITE_LAT, SITE_LNG, null));

        assertThat(checkedOut.id()).isEqualTo(checkedIn.id());
        assertThat(checkedOut.checkOutAt()).isNotNull();
        assertThat(checkedOut.checkOutMethod()).isEqualTo("GPS");
    }

    @Test
    void checkIn_UC09_managementExempt_rejectsWhenIsManagementTrue() {
        User manager = newUser();
        newEmployee(manager, Employee.EmployeeType.STAFF, true);

        assertThatThrownBy(() -> attendanceService.checkIn(manager.getId(),
                new AttendanceCheckRequest("GPS", site.getId(), SITE_LAT, SITE_LNG, null)))
                .isInstanceOf(ManagementExemptFromAttendanceException.class);
    }

    @Test
    void checkIn_UC09_A1_rejectsOutsideShiftWindow() {
        // Ca cố định cách xa thời điểm hiện tại, cửa sổ hẹp -- chắc chắn ngoài giờ.
        Shift shift = newShift(LocalTime.now().minusHours(6), LocalTime.now().minusHours(2), 0, 0, 0, 0);
        assignShift(shift);

        assertThatThrownBy(() -> attendanceService.checkIn(user.getId(),
                new AttendanceCheckRequest("GPS", site.getId(), SITE_LAT, SITE_LNG, null)))
                .isInstanceOf(OutsideAttendanceWindowException.class);
    }

    @Test
    void checkIn_UC09_A2_rejectsGpsOutsideRadius() {
        assignWideOpenShift();

        assertThatThrownBy(() -> attendanceService.checkIn(user.getId(),
                new AttendanceCheckRequest("GPS", site.getId(), FAR_LAT, FAR_LNG, null)))
                .isInstanceOf(OutsideGpsRadiusException.class);
    }

    @Test
    void checkIn_UC09_A3_rejectsFailedBiometricVerification() {
        assignWideOpenShift();

        assertThatThrownBy(() -> attendanceService.checkIn(user.getId(),
                new AttendanceCheckRequest("FINGERPRINT", site.getId(), null, null, false)))
                .isInstanceOf(BiometricVerificationFailedException.class);
    }

    @Test
    void checkIn_UC09_A3_rejectsManualWhenAutoMethodsStillEnabled() {
        assignWideOpenShift();

        // system_settings mặc định (V7 seed): 3 phương thức tự động đều bật
        // -> MANUAL chưa khả dụng (ActivityDiagram-ChamCong A4-A6).
        assertThatThrownBy(() -> attendanceService.checkIn(user.getId(),
                new AttendanceCheckRequest("MANUAL", null, null, null, null)))
                .isInstanceOf(AttendanceMethodNotAvailableException.class);
    }

    @Test
    void checkIn_UC09_A12A13_recordsAttendanceWithinTeachingScheduleWindowOutsideShiftWindow() {
        User teacher = newUser();
        Employee teacherEmployee = newEmployee(teacher, Employee.EmployeeType.TEACHER);
        // Ca cố định chỉ dùng để xác định D là ngày làm việc (bước 3) -- cửa sổ giờ của
        // chính ca này KHÔNG phủ thời điểm hiện tại, để cô lập rõ nhánh lịch dạy (A12/A13).
        assignShiftNotCoveringNow(teacherEmployee);
        SchoolClass schoolClass = newSchoolClass(teacher);
        LocalTime now = LocalTime.now();
        newSession(schoolClass, teacher, minusHoursClamped(now, 1), plusHoursClamped(now, 1), ClassSession.Status.SCHEDULED);

        AttendanceRecordResponse response = attendanceService.checkIn(teacher.getId(),
                new AttendanceCheckRequest("GPS", site.getId(), SITE_LAT, SITE_LNG, null));

        assertThat(response.checkInAt()).isNotNull();
        assertThat(response.status()).isIn("NORMAL", "LATE");
        var persisted = attendanceRecordRepository
                .findByEmployeeIdAndWorkDate(teacherEmployee.getId(), LocalDate.now()).orElseThrow();
        assertThat(persisted.getCheckInMatchedSource()).isEqualTo(AttendanceRecord.MatchedSource.TEACHING_SCHEDULE);
    }

    @Test
    void checkIn_UC09_A1_rejectsOutsideTeachingScheduleAndShiftWindow() {
        User teacher = newUser();
        Employee teacherEmployee = newEmployee(teacher, Employee.EmployeeType.TEACHER);
        assignShiftNotCoveringNow(teacherEmployee);
        SchoolClass schoolClass = newSchoolClass(teacher);
        // Tiết dạy đã kết thúc từ lâu, không phủ thời điểm hiện tại. Lấy mốc kết thúc đúng
        // bằng "now" đã chụp (checkIn() gọi OffsetDateTime.now() muộn hơn 1 chút nên luôn
        // > mốc này), start chỉ lùi 1 phút so với end qua đúng helper minusMinutesClamped
        // đã dùng ở các test khác trong file -- KHÔNG trừ giờ cố định (6h/5h) rồi clamp
        // riêng từng mốc như trước: khi "now" < 5h, cả 2 mốc cùng bị clamp về chung
        // LocalTime.MIN, start == end vi phạm chk_session_time (V14) -- lỗi thật gặp ở CI
        // 02:38 UTC (PR #52). Cũng KHÔNG dùng thẳng LocalTime.MIN làm mốc xa "now": tại
        // dev machine JVM +07 (Asia/Bangkok), driver ghi lệch giờ đối với cặp mốc cách xa
        // nhau qua ranh giới 7h (hibernate.jdbc.time_zone=UTC ở application.yml) -- giữ 2
        // mốc sát nhau như các test khác trong file để tránh rơi vào đúng ranh giới đó.
        LocalTime now = LocalTime.now();
        LocalTime end = now;
        LocalTime start = minusMinutesClamped(end, 1);
        newSession(schoolClass, teacher, start, end, ClassSession.Status.SCHEDULED);

        assertThatThrownBy(() -> attendanceService.checkIn(teacher.getId(),
                new AttendanceCheckRequest("GPS", site.getId(), SITE_LAT, SITE_LNG, null)))
                .isInstanceOf(OutsideAttendanceWindowException.class);
    }

    @Test
    void checkIn_UC09_A12A13_ignoresCancelledSession() {
        User teacher = newUser();
        Employee teacherEmployee = newEmployee(teacher, Employee.EmployeeType.TEACHER);
        assignShiftNotCoveringNow(teacherEmployee);
        SchoolClass schoolClass = newSchoolClass(teacher);
        // Tiết dạy phủ thời điểm hiện tại nhưng đã CANCELLED -- không được tính vào cửa sổ.
        LocalTime now = LocalTime.now();
        newSession(schoolClass, teacher, minusMinutesClamped(now, 30), plusMinutesClamped(now, 30), ClassSession.Status.CANCELLED);

        assertThatThrownBy(() -> attendanceService.checkIn(teacher.getId(),
                new AttendanceCheckRequest("GPS", site.getId(), SITE_LAT, SITE_LNG, null)))
                .isInstanceOf(OutsideAttendanceWindowException.class);
    }

    @Test
    void checkIn_UC09_MainFlow_teacherWithSessionTodayIsWorkingDayWithoutAnyShiftAssigned() {
        User teacher = newUser();
        Employee teacherEmployee = newEmployee(teacher, Employee.EmployeeType.TEACHER);
        // Không gán employee_shifts nào cả, không có work_calendar override -- đúng kịch bản
        // gap đã báo cáo: GV chỉ dạy theo lịch, HR không tạo ca cố định cho họ.
        SchoolClass schoolClass = newSchoolClass(teacher);
        LocalTime now = LocalTime.now();
        newSession(schoolClass, teacher, minusHoursClamped(now, 1), plusHoursClamped(now, 1), ClassSession.Status.SCHEDULED);

        AttendanceRecordResponse response = attendanceService.checkIn(teacher.getId(),
                new AttendanceCheckRequest("GPS", site.getId(), SITE_LAT, SITE_LNG, null));

        assertThat(response.checkInAt()).isNotNull();
        var persisted = attendanceRecordRepository
                .findByEmployeeIdAndWorkDate(teacherEmployee.getId(), LocalDate.now()).orElseThrow();
        assertThat(persisted.getCheckInMatchedSource()).isEqualTo(AttendanceRecord.MatchedSource.TEACHING_SCHEDULE);
    }

    @Test
    void checkIn_UC09_A1_rejectsWhenWorkCalendarMarksHolidayEvenWithTeachingSessionToday() {
        User teacher = newUser();
        newEmployee(teacher, Employee.EmployeeType.TEACHER);
        SchoolClass schoolClass = newSchoolClass(teacher);
        LocalTime now = LocalTime.now();
        newSession(schoolClass, teacher, minusHoursClamped(now, 1), plusHoursClamped(now, 1), ClassSession.Status.SCHEDULED);
        // work_calendar override tường minh (scope ALL) đánh dấu hôm nay là ngày Lễ -- tiết dạy
        // KHÔNG được ghi đè quyết định này (xác nhận với PM: chỉ là fallback cuối cùng).
        WorkCalendar holiday = new WorkCalendar();
        holiday.setCalendarDate(LocalDate.now());
        holiday.setDayType(WorkCalendar.DayType.HOLIDAY);
        holiday.setAppliesToScope(WorkCalendar.Scope.ALL);
        workCalendarRepository.save(holiday);

        assertThatThrownBy(() -> attendanceService.checkIn(teacher.getId(),
                new AttendanceCheckRequest("GPS", site.getId(), SITE_LAT, SITE_LNG, null)))
                .isInstanceOf(NotAWorkingDayException.class);
    }

    @Test
    void checkIn_UC09_MainFlow_teacherWithoutSessionTodayStillUsesShiftWindow() {
        User teacher = newUser();
        newEmployee(teacher, Employee.EmployeeType.TEACHER);
        assignWideOpenShift(employeeRepository.findByUserId(teacher.getId()).orElseThrow());

        AttendanceRecordResponse response = attendanceService.checkIn(teacher.getId(),
                new AttendanceCheckRequest("GPS", site.getId(), SITE_LAT, SITE_LNG, null));

        var persisted = attendanceRecordRepository
                .findByEmployeeIdAndWorkDate(response.employeeId(), LocalDate.now()).orElseThrow();
        assertThat(persisted.getCheckInMatchedSource()).isEqualTo(AttendanceRecord.MatchedSource.SHIFT);
    }

    @Test
    void listRecords_UC09_Extension_filtersByEmployeeSiteAndDateRange() {
        assignWideOpenShift();
        attendanceService.checkIn(user.getId(), new AttendanceCheckRequest("GPS", site.getId(), SITE_LAT, SITE_LNG, null));

        User otherUser = newUser();
        Employee otherEmployee = newEmployee(otherUser);
        Site otherSite = newSite();
        assignWideOpenShift(otherEmployee);
        attendanceService.checkIn(otherUser.getId(), new AttendanceCheckRequest("GPS", otherSite.getId(), SITE_LAT, SITE_LNG, null));

        var all = attendanceService.listRecords(null, null, LocalDate.now(), LocalDate.now());
        assertThat(all).extracting("employeeId").contains(employee.getId(), otherEmployee.getId());

        var byEmployee = attendanceService.listRecords(employee.getId(), null, LocalDate.now(), LocalDate.now());
        assertThat(byEmployee).hasSize(1);
        assertThat(byEmployee.get(0).employeeId()).isEqualTo(employee.getId());
        assertThat(byEmployee.get(0).siteId()).isEqualTo(site.getId());

        var bySite = attendanceService.listRecords(null, otherSite.getId(), LocalDate.now(), LocalDate.now());
        assertThat(bySite).hasSize(1);
        assertThat(bySite.get(0).employeeId()).isEqualTo(otherEmployee.getId());

        var outsideRange = attendanceService.listRecords(null, null, LocalDate.now().minusDays(10), LocalDate.now().minusDays(9));
        assertThat(outsideRange).isEmpty();
    }

    @Test
    void listRecords_UC09_Extension_rejectsWhenFromAfterTo() {
        assertThatThrownBy(() -> attendanceService.listRecords(null, null, LocalDate.now(), LocalDate.now().minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SchoolClass newSchoolClass(User creator) {
        Curriculum curriculum = new Curriculum();
        curriculum.setCode("CUR-ATT-" + SEQ.incrementAndGet());
        curriculum.setName("Test curriculum");
        curriculum.setClassCategory(Curriculum.ClassCategory.MAIN);
        curriculum.setCreatedBy(creator);
        curriculum = curriculumRepository.save(curriculum);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setClassCode("CLS-ATT-" + SEQ.incrementAndGet());
        schoolClass.setName("Test class");
        schoolClass.setSite(site);
        schoolClass.setCurriculum(curriculum);
        schoolClass.setClassType(SchoolClass.ClassType.OPEN);
        schoolClass.setMaxStudents(20);
        schoolClass.setStartDate(LocalDate.now());
        schoolClass.setCreatedBy(creator);
        return schoolClassRepository.save(schoolClass);
    }

    private ClassSession newSession(SchoolClass schoolClass, User teacher, LocalTime start, LocalTime end, ClassSession.Status status) {
        ClassSession session = new ClassSession();
        session.setSchoolClass(schoolClass);
        session.setSessionDate(LocalDate.now());
        session.setStartTime(start);
        session.setEndTime(end);
        session.setPrimaryTeacher(teacher);
        session.setCreatedBy(teacher);
        session.setStatus(status);
        return classSessionRepository.save(session);
    }

    /**
     * Trừ N giờ nhưng giữ trong cùng ngày -- class_sessions có
     * chk_session_time CHECK (end_time > start_time) (V14); trừ giờ thẳng từ
     * LocalTime.now() có thể wrap qua ngày trước khi CI chạy gần 00:00, tạo
     * start_time > end_time và vỡ constraint (đã gặp thật ở PR #47 khi CI
     * chạy lúc 05:35 UTC). Clamp về LocalTime.MIN nếu bị wrap.
     */
    private static LocalTime minusHoursClamped(LocalTime base, long hours) {
        LocalTime candidate = base.minusHours(hours);
        return candidate.isAfter(base) ? LocalTime.MIN : candidate;
    }

    /** Cộng N giờ nhưng giữ trong cùng ngày -- xem minusHoursClamped. */
    private static LocalTime plusHoursClamped(LocalTime base, long hours) {
        LocalTime candidate = base.plusHours(hours);
        return candidate.isBefore(base) ? LocalTime.of(23, 59, 59) : candidate;
    }

    /** Trừ N phút nhưng giữ trong cùng ngày -- xem minusHoursClamped. */
    private static LocalTime minusMinutesClamped(LocalTime base, long minutes) {
        LocalTime candidate = base.minusMinutes(minutes);
        return candidate.isAfter(base) ? LocalTime.MIN : candidate;
    }

    /** Cộng N phút nhưng giữ trong cùng ngày -- xem minusHoursClamped. */
    private static LocalTime plusMinutesClamped(LocalTime base, long minutes) {
        LocalTime candidate = base.plusMinutes(minutes);
        return candidate.isBefore(base) ? LocalTime.of(23, 59, 59) : candidate;
    }

    private void assignShiftNotCoveringNow(Employee forEmployee) {
        Shift shift = newShift(LocalTime.now().minusHours(6), LocalTime.now().minusHours(5), 0, 0, 0, 0);
        EmployeeShift employeeShift = new EmployeeShift();
        employeeShift.setEmployee(forEmployee);
        employeeShift.setShift(shift);
        employeeShift.setEffectiveFrom(LocalDate.now().minusYears(1));
        employeeShiftRepository.save(employeeShift);
    }

    private void assignWideOpenShift(Employee forEmployee) {
        Shift shift = newShift(LocalTime.now(), LocalTime.now().plusHours(8), 600, 600, 600, 600);
        EmployeeShift employeeShift = new EmployeeShift();
        employeeShift.setEmployee(forEmployee);
        employeeShift.setShift(shift);
        employeeShift.setEffectiveFrom(LocalDate.now().minusYears(1));
        employeeShiftRepository.save(employeeShift);
    }

    private void assignWideOpenShift() {
        Shift shift = newShift(LocalTime.now(), LocalTime.now().plusHours(8), 600, 600, 600, 600);
        assignShift(shift);
    }

    private Shift newShift(LocalTime checkIn, LocalTime checkOut, int inBefore, int inAfter, int outBefore, int outAfter) {
        Shift shift = new Shift();
        shift.setCode("SHIFT-" + SEQ.incrementAndGet());
        shift.setName("Test shift");
        shift.setCheckInTime(checkIn);
        shift.setCheckOutTime(checkOut);
        shift.setCheckInWindowBeforeMinutes(inBefore);
        shift.setCheckInWindowAfterMinutes(inAfter);
        shift.setCheckOutWindowBeforeMinutes(outBefore);
        shift.setCheckOutWindowAfterMinutes(outAfter);
        shift.setAppliesToWeekdays(String.valueOf(LocalDate.now().getDayOfWeek().getValue()));
        shift.setWeekParity(Shift.WeekParity.ALL);
        shift.setActive(true);
        return shiftRepository.save(shift);
    }

    private void assignShift(Shift shift) {
        EmployeeShift employeeShift = new EmployeeShift();
        employeeShift.setEmployee(employee);
        employeeShift.setShift(shift);
        employeeShift.setEffectiveFrom(LocalDate.now().minusYears(1));
        employeeShiftRepository.save(employeeShift);
    }

    private User newUser() {
        User newUser = new User();
        newUser.setUsername("attendance.user." + SEQ.incrementAndGet());
        newUser.setEmail("attendance.user." + SEQ.incrementAndGet() + "@pps.edu.vn");
        newUser.setFullName("Attendance Test User");
        newUser.setStatus(User.Status.ACTIVE);
        return userRepository.save(newUser);
    }

    private Employee newEmployee(User forUser) {
        return newEmployee(forUser, Employee.EmployeeType.STAFF, false);
    }

    private Employee newEmployee(User forUser, Employee.EmployeeType type) {
        return newEmployee(forUser, type, false);
    }

    private Employee newEmployee(User forUser, Employee.EmployeeType type, boolean management) {
        Employee newEmployee = new Employee();
        newEmployee.setUser(forUser);
        newEmployee.setEmployeeCode("NVCC" + SEQ.incrementAndGet());
        newEmployee.setDateOfBirth(LocalDate.of(1995, 1, 1));
        newEmployee.setEmployeeType(type);
        newEmployee.setManagement(management);
        newEmployee.setDefaultShiftRequired(true);
        newEmployee.setHireDate(LocalDate.of(2024, 1, 1));
        return employeeRepository.save(newEmployee);
    }

    private Site newSite() {
        Site newSite = new Site();
        newSite.setCode("SITE-" + SEQ.incrementAndGet());
        newSite.setName("Test Site");
        newSite.setSiteType(Site.SiteType.OWNED);
        newSite = siteRepository.save(newSite);
        jdbcTemplate.update(
                "UPDATE sites SET geo_location = ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography WHERE id = ?",
                SITE_LNG, SITE_LAT, newSite.getId());
        return newSite;
    }
}
