package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ClassTeacher;

import java.util.List;
import java.util.Optional;

public interface ClassTeacherRepository extends JpaRepository<ClassTeacher, Long> {
    List<ClassTeacher> findBySchoolClassId(Long classId);
    Optional<ClassTeacher> findBySchoolClassIdAndTeacherRoleAndSubjectIdIsNullAndAssignedToIsNull(
            Long classId, ClassTeacher.TeacherRole teacherRole);
    boolean existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(Long classId, Long teacherId);

    /** UC-23: GV được coi là "phụ trách khung chương trình" nếu đang dạy ít nhất 1 lớp dùng khung đó. */
    boolean existsBySchoolClass_CurriculumIdAndTeacherIdAndAssignedToIsNull(Long curriculumId, Long teacherId);
}
