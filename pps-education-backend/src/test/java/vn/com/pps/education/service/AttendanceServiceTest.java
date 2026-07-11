package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.EmployeeShift;
import vn.com.pps.education.domain.Shift;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AttendanceCheckRequest;
import vn.com.pps.education.dto.AttendanceRecordResponse;
import vn.com.pps.education.exception.AttendanceMethodNotAvailableException;
import vn.com.pps.education.exception.BiometricVerificationFailedException;
import vn.com.pps.education.exception.ManagementExemptFromAttendanceException;
import vn.com.pps.education.exception.OutsideAttendanceWindowException;
import vn.com.pps.education.exception.OutsideGpsRadiusException;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.EmployeeShiftRepository;
import vn.com.pps.education.repository.ShiftRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
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
    private JdbcTemplate jdbcTemplate;

    private User user;
    private Employee employee;
    private Site site;

    @BeforeEach
    void setUp() {
        user = newUser(false);
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
        User manager = newUser(true);
        newEmployee(manager);

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

    private User newUser(boolean management) {
        User newUser = new User();
        newUser.setUsername("attendance.user." + SEQ.incrementAndGet());
        newUser.setEmail("attendance.user." + SEQ.incrementAndGet() + "@pps.edu.vn");
        newUser.setFullName("Attendance Test User");
        newUser.setStatus(User.Status.ACTIVE);
        newUser.setManagement(management);
        return userRepository.save(newUser);
    }

    private Employee newEmployee(User forUser) {
        Employee newEmployee = new Employee();
        newEmployee.setUser(forUser);
        newEmployee.setEmployeeCode("NVCC" + SEQ.incrementAndGet());
        newEmployee.setDateOfBirth(LocalDate.of(1995, 1, 1));
        newEmployee.setEmployeeType(Employee.EmployeeType.STAFF);
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
