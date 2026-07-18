package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.domain.PayrollEntry;
import vn.com.pps.education.domain.PayrollPeriod;
import vn.com.pps.education.dto.PayrollEntryResponse;
import vn.com.pps.education.exception.NotHrManagerException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.PayrollEntryRepository;
import vn.com.pps.education.repository.PayrollPeriodRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.util.List;
import java.util.Optional;

/**
 * UC-12: Xem bảng lương (FR-HRM-04). Chỉ đọc — Postcondition "dữ liệu không
 * bị thay đổi". Xem docs/uc/phan-he-04-nhan-su.md — Main Flow bước 1-5, A1
 * (kỳ chưa tính toán, fallback kỳ gần nhất). Việc TÍNH toán/ghi
 * payroll_entries thuộc 1 cơ chế khác chưa được đặc tả ở UC nào trong phạm
 * vi hiện tại — không implement ở đây (đã xác nhận với PM).
 */
@Service
public class PayrollService {

    private final EmployeeRepository employeeRepository;
    private final UserRoleRepository userRoleRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollEntryRepository payrollEntryRepository;

    public PayrollService(EmployeeRepository employeeRepository,
                           UserRoleRepository userRoleRepository,
                           PayrollPeriodRepository payrollPeriodRepository,
                           PayrollEntryRepository payrollEntryRepository) {
        this.employeeRepository = employeeRepository;
        this.userRoleRepository = userRoleRepository;
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.payrollEntryRepository = payrollEntryRepository;
    }

    /** Main Flow bước 1-2: Giáo viên/Nhân viên chỉ xem bảng lương của chính mình (NFR-SEC-03). */
    @Transactional(readOnly = true)
    public PayrollEntryResponse getMyPayroll(Long actorUserId, String periodCode) {
        Employee employee = employeeRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản chưa có hồ sơ nhân sự."));

        Optional<PayrollPeriod> requestedPeriod = periodCode == null
                ? payrollPeriodRepository.findTopByOrderByStartDateDesc()
                : payrollPeriodRepository.findByPeriodCode(periodCode);

        Optional<PayrollEntry> entry = requestedPeriod
                .flatMap(p -> payrollEntryRepository.findByPayrollPeriodIdAndEmployeeId(p.getId(), employee.getId()));

        boolean fallback = false;
        if (entry.isEmpty()) {
            // A1 -- kỳ được yêu cầu chưa có dữ liệu, hiển thị kỳ gần nhất đã có.
            entry = payrollEntryRepository.findTopByEmployeeIdOrderByPayrollPeriod_StartDateDesc(employee.getId());
            fallback = true;
        }

        PayrollEntry found = entry.orElseThrow(
                () -> new ResourceNotFoundException("Chưa có dữ liệu bảng lương nào cho nhân sự này."));
        return toResponse(found, fallback);
    }

    /** Main Flow bước 3: Quản lý nhân sự xem toàn hệ thống, lọc theo phòng ban/nhân sự. */
    @Transactional(readOnly = true)
    public List<PayrollEntryResponse> listPayroll(Long actorUserId, Long periodId, Long departmentId, Long employeeId) {
        requireHrManager(actorUserId);

        PayrollPeriod period = payrollPeriodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lương id=" + periodId));

        return payrollEntryRepository.findByPayrollPeriodId(period.getId()).stream()
                .filter(e -> employeeId == null || e.getEmployee().getId().equals(employeeId))
                .filter(e -> departmentId == null || matchesDepartment(e, departmentId))
                // Lọc theo điểm trường (site) chưa hỗ trợ -- chưa có cơ chế gán điểm trường
                // cho nhân sự trong schema hiện tại (xem UC-08 A1: phân công điểm trường
                // quản lý riêng ở Phân hệ Cơ sở vật chất/Học thuật, chưa build).
                .map(e -> toResponse(e, false))
                .toList();
    }

    private boolean matchesDepartment(PayrollEntry entry, Long departmentId) {
        var department = entry.getEmployee().getDepartment();
        return department != null && department.getId().equals(departmentId);
    }

    private void requireHrManager(Long actorUserId) {
        boolean isHrManager = userRoleRepository.findByUserId(actorUserId).stream()
                .anyMatch(ur -> "HR_MANAGER".equals(ur.getRole().getCode()));
        if (!isHrManager) {
            throw new NotHrManagerException("Chỉ Quản lý nhân sự được xem bảng lương toàn hệ thống.");
        }
    }

    private PayrollEntryResponse toResponse(PayrollEntry e, boolean fallback) {
        PayrollPeriod period = e.getPayrollPeriod();
        Employee employee = e.getEmployee();
        return new PayrollEntryResponse(
                e.getId(),
                period.getPeriodCode(),
                period.getStartDate(),
                period.getEndDate(),
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getUser().getFullName(),
                e.getBaseSalary(),
                e.getTeachingHours(),
                e.getHourlyRate(),
                e.getWorkDays(),
                e.getBonuses(),
                e.getPenalties(),
                e.getTax(),
                e.getSocialInsurance(),
                e.getHealthInsurance(),
                e.getUnemploymentInsurance(),
                e.getGrossSalary(),
                e.getTotalDeductions(),
                e.getNetSalary(),
                e.getStatus().name(),
                fallback);
    }
}
