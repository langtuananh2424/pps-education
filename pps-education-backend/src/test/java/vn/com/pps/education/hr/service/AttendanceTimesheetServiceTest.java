package vn.com.pps.education.hr.service;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.academic.domain.AttendanceRecord;
import vn.com.pps.education.academic.repository.AttendanceRecordRepository;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.hr.domain.Employee;
import vn.com.pps.education.hr.domain.LeaveRequest;
import vn.com.pps.education.hr.dto.AttendanceTimesheetResponse;
import vn.com.pps.education.hr.repository.EmployeeRepository;
import vn.com.pps.education.hr.repository.LeaveRequestRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Bảng chấm công tháng + xuất Excel (2026-10-16) — dựng tay entity, mock repository, không cần database. */
class AttendanceTimesheetServiceTest {

    private final AttendanceRecordRepository recordRepository = mock(AttendanceRecordRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final LeaveRequestRepository leaveRepository = mock(LeaveRequestRepository.class);
    private final AttendanceTimesheetService service =
            new AttendanceTimesheetService(recordRepository, employeeRepository, leaveRepository);

    private Employee employee(long id, String code, String name, boolean management) {
        User user = new User();
        user.setFullName(name);
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeCode(code);
        e.setUser(user);
        e.setManagement(management);
        return e;
    }

    private AttendanceRecord record(Employee e, String date, int inHour, Integer outHour, AttendanceRecord.Status status) {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployee(e);
        r.setWorkDate(LocalDate.parse(date));
        r.setCheckInAt(OffsetDateTime.of(LocalDate.parse(date).atTime(inHour, 0), ZoneOffset.ofHours(7)));
        if (outHour != null) {
            r.setCheckOutAt(OffsetDateTime.of(LocalDate.parse(date).atTime(outHour, 0), ZoneOffset.ofHours(7)));
        }
        r.setStatus(status);
        return r;
    }

    @Test
    void marksCellsAndTotalsPerEmployeeAndSkipsExemptStaff() {
        Employee a = employee(1, "NV001", "An", false);
        Employee boss = employee(2, "NV002", "Boss", true);
        when(employeeRepository.findAllActive(null)).thenReturn(List.of(a, boss));
        when(recordRepository.search(any(), any(), any(), any())).thenReturn(List.of(
                record(a, "2026-10-01", 8, 17, AttendanceRecord.Status.NORMAL),
                record(a, "2026-10-02", 9, 17, AttendanceRecord.Status.LATE),
                record(a, "2026-10-05", 8, 15, AttendanceRecord.Status.EARLY_LEAVE),
                record(a, "2026-10-06", 8, null, AttendanceRecord.Status.MISSING)));
        LeaveRequest leave = new LeaveRequest();
        leave.setEmployee(a);
        leave.setLeaveType(LeaveRequest.LeaveType.ANNUAL);
        leave.setStartDate(LocalDate.parse("2026-10-07"));
        leave.setEndDate(LocalDate.parse("2026-10-08"));
        when(leaveRepository.findApprovedFullDayOverlapping(any(), any(), any())).thenReturn(List.of(leave));

        AttendanceTimesheetResponse ts = service.generate(2026, 10, null, null);

        assertThat(ts.rows()).hasSize(1);
        var row = ts.rows().get(0);
        assertThat(row.cells()).extracting(AttendanceTimesheetResponse.Cell::code)
                .containsExactly("X", "M", "S", "V", "P", "P");
        assertThat(row.totals().workDays()).isEqualTo(3);
        assertThat(row.totals().lateDays()).isEqualTo(1);
        assertThat(row.totals().earlyLeaveDays()).isEqualTo(1);
        assertThat(row.totals().missingDays()).isEqualTo(1);
        assertThat(row.totals().paidLeaveDays()).isEqualTo(2);
        assertThat(row.totals().totalHours()).isEqualTo(24.0);
    }

    @Test
    void siteFilterKeepsOnlyEmployeesWithRecordsThere() {
        Employee a = employee(1, "NV001", "An", false);
        Employee b = employee(2, "NV002", "Binh", false);
        when(employeeRepository.findAllActive(null)).thenReturn(List.of(a, b));
        when(recordRepository.search(any(), eq(5L), any(), any())).thenReturn(List.of(
                record(b, "2026-10-01", 8, 17, AttendanceRecord.Status.NORMAL)));
        when(leaveRepository.findApprovedFullDayOverlapping(any(), any(), any())).thenReturn(List.of());

        assertThat(service.generate(2026, 10, 5L, null).rows()).extracting("employeeCode").containsExactly("NV002");
    }

    @Test
    void rejectsInvalidMonth() {
        assertThatThrownBy(() -> service.generate(2026, 13, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void excelHasOneColumnPerDayAndTotals() throws IOException {
        Employee a = employee(1, "NV001", "An", false);
        when(employeeRepository.findAllActive(null)).thenReturn(List.of(a));
        when(recordRepository.search(any(), any(), any(), any())).thenReturn(List.of(
                record(a, "2026-10-01", 8, 17, AttendanceRecord.Status.NORMAL)));
        when(leaveRepository.findApprovedFullDayOverlapping(any(), any(), any())).thenReturn(List.of());

        byte[] bytes = service.exportExcel(2026, 10, null, null);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            // 4 cột cố định + 31 ngày + 7 cột tổng hợp
            assertThat((int) sheet.getRow(0).getLastCellNum()).isEqualTo(4 + 31 + 7);
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("An");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("X");
            assertThat(sheet.getRow(1).getCell(4 + 31).getNumericCellValue()).isEqualTo(1.0);
            assertThat(sheet.getRow(1).getCell(4 + 31 + 6).getNumericCellValue()).isEqualTo(9.0);
        }
    }
}
