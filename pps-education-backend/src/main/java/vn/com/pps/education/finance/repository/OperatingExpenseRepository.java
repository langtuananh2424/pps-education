package vn.com.pps.education.finance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.finance.domain.OperatingExpense;

import java.time.LocalDate;
import java.util.List;

public interface OperatingExpenseRepository extends JpaRepository<OperatingExpense, Long> {

    long countByExpenseNumberStartingWith(String prefix);

    List<OperatingExpense> findBySiteIdAndExpenseDateBetween(Long siteId, LocalDate from, LocalDate to);

    List<OperatingExpense> findByExpenseDateBetween(LocalDate from, LocalDate to);

    /** UC-32: báo cáo chỉ cộng khoản chi chưa bị Ban giám đốc từ chối (RECORDED/APPROVED). */
    List<OperatingExpense> findBySiteIdAndExpenseDateBetweenAndStatusNot(Long siteId, LocalDate from, LocalDate to,
                                                                           OperatingExpense.Status status);

    List<OperatingExpense> findBySiteIsNullAndExpenseDateBetweenAndStatusNot(LocalDate from, LocalDate to,
                                                                              OperatingExpense.Status status);
}
