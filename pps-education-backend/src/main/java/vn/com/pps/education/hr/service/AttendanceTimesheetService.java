package vn.com.pps.education.hr.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.academic.domain.AttendanceRecord;
import vn.com.pps.education.academic.repository.AttendanceRecordRepository;
import vn.com.pps.education.common.ExcelExportHelper;
import vn.com.pps.education.hr.domain.Employee;
import vn.com.pps.education.hr.domain.LeaveRequest;
import vn.com.pps.education.hr.dto.AttendanceTimesheetResponse;
import vn.com.pps.education.hr.dto.AttendanceTimesheetResponse.Cell;
import vn.com.pps.education.hr.dto.AttendanceTimesheetResponse.Row;
import vn.com.pps.education.hr.dto.AttendanceTimesheetResponse.Totals;
import vn.com.pps.education.hr.repository.EmployeeRepository;
import vn.com.pps.education.hr.repository.LeaveRequestRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bảng chấm công tháng tự động + xuất Excel (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-10-16).
 * Chỉ ĐỌC dữ liệu sẵn có — attendance_records (UC-09) và đơn nghỉ đã duyệt (leave_requests) — không đổi
 * logic chấm vào/ra. Nhân sự thuộc diện miễn trừ chấm công (is_management) và nhân sự đã nghỉ việc/vào làm
 * sau tháng được bỏ qua, trừ khi vẫn có bản ghi chấm công trong tháng. Ngày không có bản ghi và không có đơn
 * nghỉ để trống, KHÔNG tự suy ra vắng (giống AttendanceMissingSchedulerService: MISSING chỉ do job quét/HR).
 */
@Service
public class AttendanceTimesheetService {

    static final String WORKED = "X";
    static final String LATE = "M";
    static final String EARLY_LEAVE = "S";
    static final String MISSING = "V";
    static final String PAID_LEAVE = "P";
    static final String UNPAID_LEAVE = "KP";

    private static final List<LeaveRequest.LeaveType> FULL_DAY_LEAVE_TYPES = List.of(
            LeaveRequest.LeaveType.ANNUAL, LeaveRequest.LeaveType.SICK,
            LeaveRequest.LeaveType.UNPAID, LeaveRequest.LeaveType.PERSONAL);
    private static final Locale VI = Locale.forLanguageTag("vi");

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public AttendanceTimesheetService(AttendanceRecordRepository attendanceRecordRepository,
                                      EmployeeRepository employeeRepository,
                                      LeaveRequestRepository leaveRequestRepository) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @Transactional(readOnly = true)
    public AttendanceTimesheetResponse generate(int year, int month, Long siteId, Long departmentId) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new IllegalArgumentException("Tháng/năm không hợp lệ.");
        }
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        List<AttendanceRecord> records = attendanceRecordRepository.search(null, siteId, from, to);
        Map<Long, Map<LocalDate, AttendanceRecord>> recordsByEmployee = new HashMap<>();
        for (AttendanceRecord r : records) {
            recordsByEmployee.computeIfAbsent(r.getEmployee().getId(), k -> new HashMap<>()).put(r.getWorkDate(), r);
        }

        Map<Long, Employee> employees = new LinkedHashMap<>();
        for (Employee e : employeeRepository.findAllActive(departmentId)) {
            if (isInScope(e, from, to) || recordsByEmployee.containsKey(e.getId())) {
                employees.put(e.getId(), e);
            }
        }
        // Lọc theo điểm trường: chỉ giữ nhân sự có chấm công tại điểm đó (nhân sự không gắn cố định 1 điểm).
        if (siteId != null) {
            employees.keySet().removeIf(id -> !recordsByEmployee.containsKey(id));
        }
        // Nhân sự có bản ghi nhưng bị lọc phòng ban → không thêm (findAllActive đã lọc theo phòng ban).

        Map<Long, List<LeaveRequest>> leavesByEmployee = new HashMap<>();
        for (LeaveRequest lr : leaveRequestRepository.findApprovedFullDayOverlapping(from, to, FULL_DAY_LEAVE_TYPES)) {
            leavesByEmployee.computeIfAbsent(lr.getEmployee().getId(), k -> new ArrayList<>()).add(lr);
        }

        List<Row> rows = new ArrayList<>();
        for (Employee e : employees.values()) {
            rows.add(buildRow(e, ym, recordsByEmployee.getOrDefault(e.getId(), Map.of()),
                    leavesByEmployee.getOrDefault(e.getId(), List.of())));
        }
        return new AttendanceTimesheetResponse(year, month, from, to, rows);
    }

    @Transactional(readOnly = true)
    public byte[] exportExcel(int year, int month, Long siteId, Long departmentId) {
        return buildWorkbook(generate(year, month, siteId, departmentId));
    }

    byte[] buildWorkbook(AttendanceTimesheetResponse timesheet) {
        int days = timesheet.toDate().getDayOfMonth();
        List<String> headers = new ArrayList<>(List.of("STT", "Mã nhân sự", "Họ và tên", "Phòng ban"));
        for (int d = 1; d <= days; d++) {
            LocalDate date = timesheet.fromDate().withDayOfMonth(d);
            headers.add(d + " (" + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, VI) + ")");
        }
        headers.addAll(List.of("Ngày công", "Đi muộn", "Về sớm", "Thiếu chấm công", "Nghỉ phép", "Nghỉ không lương", "Tổng giờ làm"));

        List<List<Object>> data = new ArrayList<>();
        int stt = 1;
        for (Row row : timesheet.rows()) {
            List<Object> line = new ArrayList<>();
            line.add(stt++);
            line.add(row.employeeCode());
            line.add(row.employeeFullName());
            line.add(row.departmentName());
            Map<LocalDate, String> codeByDate = new HashMap<>();
            row.cells().forEach(c -> codeByDate.put(c.date(), c.code()));
            for (int d = 1; d <= days; d++) {
                line.add(codeByDate.get(timesheet.fromDate().withDayOfMonth(d)));
            }
            Totals t = row.totals();
            line.add(t.workDays());
            line.add(t.lateDays());
            line.add(t.earlyLeaveDays());
            line.add(t.missingDays());
            line.add(t.paidLeaveDays());
            line.add(t.unpaidLeaveDays());
            line.add(t.totalHours());
            data.add(line);
        }
        List<String> notes = List.of(
                "Bảng chấm công tháng " + timesheet.month() + "/" + timesheet.year(),
                "X = đủ công; M = đi muộn; S = về sớm; V = thiếu chấm công; P = nghỉ phép có lương (đã duyệt); KP = nghỉ không lương (đã duyệt).",
                "Ngày công = số ngày có mặt (X + M + S). Ô trống = không có bản ghi chấm công và không có đơn nghỉ đã duyệt.",
                "Tổng giờ làm tính từ giờ vào đến giờ ra của các ngày có đủ cả hai; nhân sự miễn trừ chấm công không có trong bảng.");
        return ExcelExportHelper.buildWorkbook("Tháng " + timesheet.month() + "-" + timesheet.year(), headers, data, notes);
    }

    private static boolean isInScope(Employee e, LocalDate from, LocalDate to) {
        if (e.isManagement()) {
            return false;
        }
        if (e.getHireDate() != null && e.getHireDate().isAfter(to)) {
            return false;
        }
        return e.getTerminationDate() == null || !e.getTerminationDate().isBefore(from);
    }

    private static Row buildRow(Employee e, YearMonth ym, Map<LocalDate, AttendanceRecord> byDate, List<LeaveRequest> leaves) {
        List<Cell> cells = new ArrayList<>();
        int worked = 0, late = 0, early = 0, missing = 0, paid = 0, unpaid = 0;
        double totalHours = 0;
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            AttendanceRecord r = byDate.get(date);
            String leaveCode = leaveCode(leaves, date);
            Cell cell;
            if (r != null && r.getStatus() != AttendanceRecord.Status.MISSING) {
                Double hours = hours(r);
                String code = switch (r.getStatus()) {
                    case LATE -> LATE;
                    case EARLY_LEAVE -> EARLY_LEAVE;
                    default -> WORKED;
                };
                cell = new Cell(date, code, r.getCheckInAt(), r.getCheckOutAt(), hours);
                worked++;
                if (LATE.equals(code)) late++;
                if (EARLY_LEAVE.equals(code)) early++;
                if (hours != null) totalHours += hours;
            } else if (leaveCode != null) {
                cell = new Cell(date, leaveCode, null, null, null);
                if (PAID_LEAVE.equals(leaveCode)) paid++; else unpaid++;
            } else if (r != null) {
                cell = new Cell(date, MISSING, r.getCheckInAt(), r.getCheckOutAt(), null);
                missing++;
            } else {
                continue;
            }
            cells.add(cell);
        }
        String department = e.getDepartment() == null ? null : e.getDepartment().getName();
        return new Row(e.getId(), e.getEmployeeCode(), e.getUser().getFullName(), department, cells,
                new Totals(worked, late, early, missing, paid, unpaid, Math.round(totalHours * 100) / 100.0));
    }

    private static String leaveCode(List<LeaveRequest> leaves, LocalDate date) {
        for (LeaveRequest lr : leaves) {
            if (!date.isBefore(lr.getStartDate()) && !date.isAfter(lr.getEndDate())) {
                return lr.getLeaveType() == LeaveRequest.LeaveType.UNPAID ? UNPAID_LEAVE : PAID_LEAVE;
            }
        }
        return null;
    }

    private static Double hours(AttendanceRecord r) {
        if (r.getCheckInAt() == null || r.getCheckOutAt() == null || !r.getCheckOutAt().isAfter(r.getCheckInAt())) {
            return null;
        }
        return Math.round(Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes() / 60.0 * 100) / 100.0;
    }
}
