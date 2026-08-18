package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.EmployeeShift;
import vn.com.pps.education.domain.Shift;
import vn.com.pps.education.dto.AssignEmployeeShiftRequest;
import vn.com.pps.education.dto.EmployeeShiftResponse;
import vn.com.pps.education.dto.EndEmployeeShiftRequest;
import vn.com.pps.education.exception.EmployeeShiftAlreadyEndedException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.ShiftAssignmentOverlapException;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.EmployeeShiftRepository;
import vn.com.pps.education.repository.ShiftRepository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * UC-70: Gán/gỡ ca làm việc cho nhân sự (employee_shifts) — bổ sung ngoài
 * SDD gốc, xác nhận với người dùng 2026-08-13. Xem ShiftService (Javadoc
 * đầy đủ bối cảnh) và docs/uc/phan-he-04-nhan-su.md.
 *
 * V124 (2026-08-14, thay thế hướng thiết kế ban đầu "1 ca có ngày xen
 * kẽ"): 1 nhân sự có thể có NHIỀU ca active song song (VD "T7 xen kẽ" =
 * gán 2 ca weekParity ODD/EVEN cùng lúc) — không còn tự đóng ca active cũ
 * khi gán ca mới, thay bằng validate chống chồng chéo lịch + endShift để
 * chủ động kết thúc 1 ca cụ thể.
 */
@Service
public class EmployeeShiftService {

    private final EmployeeShiftRepository employeeShiftRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftRepository shiftRepository;

    public EmployeeShiftService(EmployeeShiftRepository employeeShiftRepository, EmployeeRepository employeeRepository,
                                 ShiftRepository shiftRepository) {
        this.employeeShiftRepository = employeeShiftRepository;
        this.employeeRepository = employeeRepository;
        this.shiftRepository = shiftRepository;
    }

    /**
     * Gán 1 ca cho nhân sự -- KHÔNG còn tự đóng các ca active khác (V124, xem
     * Javadoc lớp). Chặn gán nếu ca mới chồng chéo lịch (cùng ngày trong tuần +
     * week_parity giao nhau) với bất kỳ ca nào đang active của nhân sự đó.
     * Muốn đổi ca: gọi endShift() để kết thúc ca cũ trước, rồi mới gọi lại đây.
     */
    @Transactional
    public EmployeeShiftResponse assignShift(AssignEmployeeShiftRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự id=" + request.employeeId()));
        Shift shift = shiftRepository.findById(request.shiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc id=" + request.shiftId()));

        List<EmployeeShift> activeShifts = employeeShiftRepository.findByEmployeeIdAndEffectiveToIsNull(employee.getId());
        for (EmployeeShift existing : activeShifts) {
            if (overlaps(shift, existing.getShift())) {
                throw new ShiftAssignmentOverlapException(
                        "Ca id=" + shift.getId() + " chồng chéo lịch với ca đang active id=" + existing.getShift().getId()
                                + " (employee_shift id=" + existing.getId()
                                + ") của nhân sự id=" + employee.getId()
                                + " -- kết thúc ca đang chồng chéo (endShift) trước khi gán ca mới.");
            }
        }

        EmployeeShift assignment = new EmployeeShift();
        assignment.setEmployee(employee);
        assignment.setShift(shift);
        assignment.setEffectiveFrom(request.effectiveFrom());
        assignment = employeeShiftRepository.save(assignment);

        return toResponse(assignment);
    }

    /** Chủ động kết thúc 1 ca đang active (V124, 2026-08-14). */
    @Transactional
    public EmployeeShiftResponse endShift(Long id, EndEmployeeShiftRequest request) {
        EmployeeShift assignment = employeeShiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản ghi gán ca id=" + id));
        if (assignment.getEffectiveTo() != null) {
            throw new EmployeeShiftAlreadyEndedException(
                    "Bản ghi gán ca id=" + id + " đã kết thúc từ " + assignment.getEffectiveTo());
        }
        if (request.effectiveTo().isBefore(assignment.getEffectiveFrom())) {
            throw new IllegalArgumentException(
                    "effectiveTo không được trước effectiveFrom (" + assignment.getEffectiveFrom() + "): " + request.effectiveTo());
        }
        assignment.setEffectiveTo(request.effectiveTo());
        assignment = employeeShiftRepository.save(assignment);
        return toResponse(assignment);
    }

    @Transactional(readOnly = true)
    public List<EmployeeShiftResponse> listByEmployee(Long employeeId) {
        return employeeShiftRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Bổ sung ngoài SDD gốc, xác nhận 2026-08-17: batch nhiều nhân viên cho
     * trang roster "Lịch làm việc" toàn công ty (EmployeeScheduleService).
     * employeeIds rỗng nghĩa là "không có nhân viên nào trong phạm vi lọc
     * hiện tại" -- trả về rỗng ngay, không gọi query.
     */
    @Transactional(readOnly = true)
    public List<EmployeeShiftResponse> listForScheduleOverview(List<Long> employeeIds, LocalDate fromDate, LocalDate toDate) {
        if (employeeIds.isEmpty()) {
            return List.of();
        }
        return employeeShiftRepository.findByEmployeeIdInAndDateRangeOverlap(employeeIds, fromDate, toDate).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 2 ca "chồng chéo" nếu có chung ít nhất 1 ngày trong tuần VÀ week_parity
     * giao nhau (ALL giao với mọi parity; ODD chỉ giao với ODD/ALL; tương tự
     * EVEN) -- khi đó AttendanceService không biết chọn cửa sổ chấm công của
     * ca nào cho ngày đó.
     */
    private boolean overlaps(Shift a, Shift b) {
        Set<String> daysA = new HashSet<>(Arrays.asList(a.getAppliesToWeekdays().split(",")));
        Set<String> daysB = new HashSet<>(Arrays.asList(b.getAppliesToWeekdays().split(",")));
        daysA.retainAll(daysB);
        if (daysA.isEmpty()) {
            return false;
        }
        return parityOverlaps(a.getWeekParity(), b.getWeekParity());
    }

    private boolean parityOverlaps(Shift.WeekParity p1, Shift.WeekParity p2) {
        return p1 == Shift.WeekParity.ALL || p2 == Shift.WeekParity.ALL || p1 == p2;
    }

    private EmployeeShiftResponse toResponse(EmployeeShift es) {
        return new EmployeeShiftResponse(
                es.getId(), es.getEmployee().getId(), es.getShift().getId(), es.getShift().getName(),
                es.getEffectiveFrom(), es.getEffectiveTo());
    }
}
