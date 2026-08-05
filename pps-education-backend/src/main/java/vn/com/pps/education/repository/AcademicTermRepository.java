package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.AcademicTerm;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long> {
    List<AcademicTerm> findBySiteIdOrderByStartDateDesc(Long siteId);

    boolean existsBySiteIdAndCode(Long siteId, String code);

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — dùng cho HomeworkAlertTrackingService. */
    Optional<AcademicTerm> findFirstBySiteIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long siteId, LocalDate date1, LocalDate date2);
}
