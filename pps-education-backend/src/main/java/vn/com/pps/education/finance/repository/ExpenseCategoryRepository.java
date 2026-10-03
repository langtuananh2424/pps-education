package vn.com.pps.education.finance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.finance.domain.ExpenseCategory;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    Optional<ExpenseCategory> findByCode(String code);

    List<ExpenseCategory> findByActiveTrue();

    List<ExpenseCategory> findByActiveTrueOrderByNameAsc();
}
