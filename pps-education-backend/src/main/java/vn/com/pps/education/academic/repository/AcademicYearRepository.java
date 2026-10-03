package vn.com.pps.education.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.academic.domain.AcademicYear;

import java.util.List;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

    List<AcademicYear> findAllByOrderByStartDateDesc();

    boolean existsByCode(String code);
}
