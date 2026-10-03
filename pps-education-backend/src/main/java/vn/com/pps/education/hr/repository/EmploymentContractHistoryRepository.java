package vn.com.pps.education.hr.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.hr.domain.EmploymentContractHistory;

import java.util.List;

public interface EmploymentContractHistoryRepository extends JpaRepository<EmploymentContractHistory, Long> {
    List<EmploymentContractHistory> findByContractIdOrderByCreatedAtDesc(Long contractId);
}
