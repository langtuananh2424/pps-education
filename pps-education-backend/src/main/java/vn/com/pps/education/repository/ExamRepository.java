package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Exam;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    Optional<Exam> findByCode(String code);

    Optional<Exam> findByUuid(UUID uuid);

    List<Exam> findByCurriculumId(Long curriculumId);

    List<Exam> findByCurriculumIdAndTeacherType(Long curriculumId, Exam.TeacherType teacherType);

    List<Exam> findByTeacherType(Exam.TeacherType teacherType);
}
