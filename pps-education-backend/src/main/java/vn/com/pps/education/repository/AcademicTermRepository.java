package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.AcademicTerm;

import java.util.List;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long> {
    List<AcademicTerm> findBySiteIdOrderByStartDateDesc(Long siteId);

    boolean existsBySiteIdAndCode(Long siteId, String code);
}
