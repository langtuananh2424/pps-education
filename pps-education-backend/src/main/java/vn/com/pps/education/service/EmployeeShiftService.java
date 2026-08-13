package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.EmployeeShift;
import vn.com.pps.education.domain.Shift;
import vn.com.pps.education.dto.AssignShiftRequest;
import vn.com.pps.education.dto.BulkAssignShiftRequest;
import vn.com.pps.education.dto.BulkAssignShiftResponse;
import vn.com.pps.education.dto.EmployeeShiftResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.EmployeeShiftRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Gán ca cho nhân sự (đơn lẻ/hàng loạt) — bổ sung 2026-08-13, xem
 * docs/uc/phan-he-04-nhan-su.md (khối bổ sung dưới UC-09/FR-HRM-02).
 */
@Service
public class EmployeeShiftService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeShiftRepository employeeShiftRepository;
    private final ShiftService shiftService;

    public EmployeeShiftService(EmployeeRepository employeeRepository,
                                 EmployeeShiftRepository employeeShiftRepository,
                                 ShiftService shiftService) {
        this.employeeRepository = employeeRepository;
        this.employeeShiftRepository = employeeShiftRepository;
        this.shiftService = shiftService;
    }

    public List<EmployeeShiftResponse> getHistory(Long employeeId) {
        return employeeShiftRepository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EmployeeShiftResponse assign(AssignShiftRequest request, Long actorUserId) {
        Employee employee = employeeRepository.findByIdAndDeletedAtIsNull(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự id=" + request.employeeId()));
        EmployeeShift created = assignInternal(employee, request.shiftId(), request.effectiveFrom());
        return toResponse(created);
    }

    /**
     * Lỗi của 1 nhân sự (không tồn tại, effective_from không hợp lệ) không rollback
     * những người còn lại — gom vào failures[], cùng cách EmployeeBatchImportService
     * báo lỗi theo dòng.
     */
    @Transactional
    public BulkAssignShiftResponse bulkAssign(BulkAssignShiftRequest request, Long actorUserId) {
        int successCount = 0;
        List<BulkAssignShiftResponse.Failure> failures = new ArrayList<>();
        for (Long employeeId : request.employeeIds()) {
            try {
                Employee employee = employeeRepository.findByIdAndDeletedAtIsNull(employeeId)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự id=" + employeeId));
                assignInternal(employee, request.shiftId(), request.effectiveFrom());
                successCount++;
            } catch (RuntimeException ex) {
                failures.add(new BulkAssignShiftResponse.Failure(employeeId, ex.getMessage()));
            }
        }
        return new BulkAssignShiftResponse(successCount, failures);
    }

    private EmployeeShift assignInternal(Employee employee, Long shiftId, LocalDate effectiveFrom) {
        Shift shift = shiftService.getShiftOrThrow(shiftId);
        if (!shift.isActive()) {
            throw new IllegalArgumentException("Ca \"" + shift.getCode() + "\" đã bị tắt, không thể gán.");
        }
        employeeShiftRepository.findByEmployeeIdAndEffectiveToIsNull(employee.getId()).ifPresent(current -> {
            if (!effectiveFrom.isAfter(current.getEffectiveFrom())) {
                throw new IllegalArgumentException(
                        "Ngày hiệu lực phải sau ngày bắt đầu ca đang áp dụng (" + current.getEffectiveFrom() + ").");
            }
            current.setEffectiveTo(effectiveFrom.minusDays(1));
            // saveAndFlush (không save thường) -- Hibernate flush INSERT trước UPDATE trong cùng
            // transaction, nếu không flush ngay thì bản ghi mới (effective_to=NULL) bị insert trước
            // khi bản ghi cũ kịp đóng, vỡ unique partial index idx_employee_shifts_active.
            employeeShiftRepository.saveAndFlush(current);
        });
        EmployeeShift next = new EmployeeShift();
        next.setEmployee(employee);
        next.setShift(shift);
        next.setEffectiveFrom(effectiveFrom);
        return employeeShiftRepository.save(next);
    }

    private EmployeeShiftResponse toResponse(EmployeeShift es) {
        return new EmployeeShiftResponse(
                es.getId(),
                es.getEmployee().getId(),
                es.getEmployee().getUser().getFullName(),
                es.getShift().getId(),
                es.getShift().getCode(),
                es.getShift().getName(),
                es.getEffectiveFrom(),
                es.getEffectiveTo()
        );
    }
}
