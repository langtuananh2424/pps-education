package vn.com.pps.education.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.crm.domain.PartnerContract;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PartnerContractRepository extends JpaRepository<PartnerContract, Long> {

    Optional<PartnerContract> findByIdAndDeletedAtIsNull(Long id);

    List<PartnerContract> findBySiteIdAndDeletedAtIsNullOrderByStartDateDesc(Long siteId);

    boolean existsByContractNumber(String contractNumber);

    long countByContractNumberStartingWith(String prefix);

    Optional<PartnerContract> findBySiteIdAndStatusAndDeletedAtIsNull(Long siteId, PartnerContract.Status status);

    /** UC-36b A1 — hợp đồng ACTIVE sắp/đã hết hạn tính tới mốc threshold (today + withinDays), giống EmploymentContract A2. */
    List<PartnerContract> findByDeletedAtIsNullAndStatusAndEndDateLessThanEqualOrderByEndDateAsc(
            PartnerContract.Status status, LocalDate threshold);
}
