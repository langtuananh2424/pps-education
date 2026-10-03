package vn.com.pps.education.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.academic.domain.CurriculumSubject;

import java.util.List;
import java.util.Optional;

public interface CurriculumSubjectRepository extends JpaRepository<CurriculumSubject, Long> {
    List<CurriculumSubject> findByCurriculumIdOrderByDisplayOrder(Long curriculumId);
    Optional<CurriculumSubject> findByCurriculumIdAndSubjectCode(Long curriculumId, CurriculumSubject.SubjectCode subjectCode);
}
