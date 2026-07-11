package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.PayrollEntry;

import java.util.List;
import java.util.Optional;

public interface PayrollEntryRepository extends JpaRepository<PayrollEntry, Long> {

    Optional<PayrollEntry> findByPayrollPeriodIdAndEmployeeId(Long payrollPeriodId, Long employeeId);

    /** A1 — kỳ gần nhất đã có dữ liệu cho nhân sự (khi kỳ được yêu cầu chưa có). */
    Optional<PayrollEntry> findTopByEmployeeIdOrderByPayrollPeriod_StartDateDesc(Long employeeId);

    List<PayrollEntry> findByPayrollPeriodId(Long payrollPeriodId);
}
