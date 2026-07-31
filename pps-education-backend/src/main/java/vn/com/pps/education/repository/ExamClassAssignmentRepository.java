package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ExamClassAssignment;

import java.util.List;
import java.util.Optional;

public interface ExamClassAssignmentRepository extends JpaRepository<ExamClassAssignment, Long> {
    boolean existsByExamIdAndSchoolClassId(Long examId, Long classId);

    Optional<ExamClassAssignment> findByExamIdAndSchoolClassId(Long examId, Long classId);

    List<ExamClassAssignment> findByExamId(Long examId);
}
