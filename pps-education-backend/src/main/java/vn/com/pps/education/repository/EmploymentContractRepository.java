package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.EmploymentContract;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, Long> {

    Optional<EmploymentContract> findByIdAndDeletedAtIsNull(Long id);

    List<EmploymentContract> findByEmployeeIdAndDeletedAtIsNullOrderByStartDateDesc(Long employeeId);

    Optional<EmploymentContract> findByEmployeeIdAndStatusAndDeletedAtIsNull(
            Long employeeId, EmploymentContract.Status status);

    Optional<EmploymentContract> findByContractNumber(String contractNumber);

    /** A2 — hợp đồng ACTIVE sắp/đã hết hạn tính tới mốc threshold (today + withinDays). */
    List<EmploymentContract> findByDeletedAtIsNullAndStatusAndEndDateIsNotNullAndEndDateLessThanEqualOrderByEndDateAsc(
            EmploymentContract.Status status, LocalDate threshold);
}
