package vn.com.pps.education.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.academic.domain.Curriculum;

import java.util.List;
import java.util.Optional;

public interface CurriculumRepository extends JpaRepository<Curriculum, Long> {

    Optional<Curriculum> findByIdAndDeletedAtIsNull(Long id);

    Optional<Curriculum> findByCode(String code);

    List<Curriculum> findByDeletedAtIsNullAndSiteIdIsNull();
}
