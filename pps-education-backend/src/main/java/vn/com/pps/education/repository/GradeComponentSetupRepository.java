package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.GradeComponentSetup;

import java.util.List;
import java.util.Optional;

public interface GradeComponentSetupRepository extends JpaRepository<GradeComponentSetup, Long> {

    List<GradeComponentSetup> findBySchoolClassIdOrderByAcademicTermIdAscEvaluationTypeAsc(Long classId);

    List<GradeComponentSetup> findBySchoolClassIdAndAcademicTermId(Long classId, Long academicTermId);

    Optional<GradeComponentSetup> findBySchoolClassIdAndAcademicTermIdAndEvaluationType(
            Long classId, Long academicTermId, GradeComponentSetup.EvaluationType evaluationType);
}
