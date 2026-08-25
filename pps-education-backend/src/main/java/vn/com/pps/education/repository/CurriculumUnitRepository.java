package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.CurriculumUnit;

import java.util.List;

public interface CurriculumUnitRepository extends JpaRepository<CurriculumUnit, Long> {
    List<CurriculumUnit> findByBookIdOrderByDisplayOrder(Long bookId);
}
