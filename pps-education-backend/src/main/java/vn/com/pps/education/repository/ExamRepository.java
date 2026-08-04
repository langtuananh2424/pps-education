package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Exam;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    Optional<Exam> findByCode(String code);

    Optional<Exam> findByUuid(UUID uuid);

    /** V87 — dùng thay findById ở mọi nơi đọc/sửa 1 Đề, không lộ Đề đã "xóa" (deleted_at). */
    Optional<Exam> findByIdAndDeletedAtIsNull(Long id);

    List<Exam> findByDeletedAtIsNull();

    List<Exam> findByCurriculumIdAndDeletedAtIsNull(Long curriculumId);

    List<Exam> findByCurriculumIdAndTeacherTypeAndDeletedAtIsNull(Long curriculumId, Exam.TeacherType teacherType);

    List<Exam> findByTeacherTypeAndDeletedAtIsNull(Exam.TeacherType teacherType);

    Optional<Exam> findByQuestionBankId(Long questionBankId);

    boolean existsByQuestionBankId(Long questionBankId);
}
