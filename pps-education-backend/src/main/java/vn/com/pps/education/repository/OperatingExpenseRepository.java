package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.OperatingExpense;

import java.time.LocalDate;
import java.util.List;

public interface OperatingExpenseRepository extends JpaRepository<OperatingExpense, Long> {

    long countByExpenseNumberStartingWith(String prefix);

    List<OperatingExpense> findBySiteIdAndExpenseDateBetween(Long siteId, LocalDate from, LocalDate to);

    List<OperatingExpense> findByExpenseDateBetween(LocalDate from, LocalDate to);
}
