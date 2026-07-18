package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.PayrollPeriod;

import java.util.Optional;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, Long> {
    Optional<PayrollPeriod> findByPeriodCode(String periodCode);
    Optional<PayrollPeriod> findTopByOrderByStartDateDesc();
}
