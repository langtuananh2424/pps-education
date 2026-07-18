package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.EmploymentContractHistory;

import java.util.List;

public interface EmploymentContractHistoryRepository extends JpaRepository<EmploymentContractHistory, Long> {
    List<EmploymentContractHistory> findByContractIdOrderByCreatedAtDesc(Long contractId);
}
