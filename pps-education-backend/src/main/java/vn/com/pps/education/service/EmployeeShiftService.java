package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.EmployeeShift;
import vn.com.pps.education.domain.Shift;
import vn.com.pps.education.dto.AssignEmployeeShiftRequest;
import vn.com.pps.education.dto.EmployeeShiftResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.EmployeeShiftRepository;
import vn.com.pps.education.repository.ShiftRepository;

import java.util.List;
import java.util.Optional;

/**
 * UC-70: Gán/đổi ca làm việc cho nhân sự (employee_shifts) — bổ sung ngoài
 * SDD gốc, xác nhận với người dùng 2026-08-13. Xem ShiftService (Javadoc
 * đầy đủ bối cảnh) và docs/uc/phan-he-04-nhan-su.md.
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
     * Đóng bản ghi active hiện tại (nếu có, effectiveTo = effectiveFrom -
     * 1 ngày) rồi tạo bản ghi mới -- employee_shifts chỉ cho phép tối đa 1
     * bản ghi active/nhân sự (partial unique index idx_employee_shifts_active,
     * V7). saveAndFlush bản ghi đóng TRƯỚC khi insert bản ghi mới để tránh vi
     * phạm tạm thời index đó (đúng pattern SiteService.assignManagerInternal).
     */
    @Transactional
    public EmployeeShiftResponse assignShift(AssignEmployeeShiftRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự id=" + request.employeeId()));
        Shift shift = shiftRepository.findById(request.shiftId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc id=" + request.shiftId()));

        Optional<EmployeeShift> current = employeeShiftRepository.findByEmployeeIdAndEffectiveToIsNull(employee.getId());
        current.ifPresent(existing -> {
            existing.setEffectiveTo(request.effectiveFrom().minusDays(1));
            employeeShiftRepository.saveAndFlush(existing);
        });

        EmployeeShift assignment = new EmployeeShift();
        assignment.setEmployee(employee);
        assignment.setShift(shift);
        assignment.setEffectiveFrom(request.effectiveFrom());
        assignment = employeeShiftRepository.save(assignment);

        return toResponse(assignment);
    }

    @Transactional(readOnly = true)
    public List<EmployeeShiftResponse> listByEmployee(Long employeeId) {
        return employeeShiftRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
                .map(this::toResponse)
                .toList();
    }

    private EmployeeShiftResponse toResponse(EmployeeShift es) {
        return new EmployeeShiftResponse(
                es.getId(), es.getEmployee().getId(), es.getShift().getId(), es.getShift().getName(),
                es.getEffectiveFrom(), es.getEffectiveTo());
    }
}
