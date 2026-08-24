package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.Exam;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    Optional<Exam> findByCode(String code);

    Optional<Exam> findByUuid(UUID uuid);

    /** V87 — dùng thay findById ở mọi nơi đọc/sửa 1 Đề, không lộ Đề đã "xóa" (deleted_at). */
    Optional<Exam> findByIdAndDeletedAtIsNull(Long id);

    Optional<Exam> findByQuestionBankId(Long questionBankId);

    boolean existsByQuestionBankId(Long questionBankId);

    /**
     * V144 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — thay các derived query tổ
     * hợp curriculumId×teacherType cũ (ExamService#listExams if/else 4 nhánh) bằng 1 query lọc linh
     * hoạt, thêm tiêu chí skillCategory MỚI (lọc Kho đề theo nhóm kỹ năng) — mọi tham số null = không
     * lọc theo tiêu chí đó.
     */
    @Query("""
            select e from Exam e
            where e.deletedAt is null
              and (:curriculumId is null or e.curriculum.id = :curriculumId)
              and (:teacherType is null or e.teacherType = :teacherType)
              and (:skillCategory is null or e.skillCategory = :skillCategory)
            """)
    List<Exam> search(@Param("curriculumId") Long curriculumId, @Param("teacherType") Exam.TeacherType teacherType,
                       @Param("skillCategory") Exam.SkillCategory skillCategory);

    /**
     * V144 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — nguồn cho dropdown "giao cả
     * Đề" ở Nhận xét học viên (UC-21): Đề đã gán cho lớp (exam_class_assignments) VÀ có ít nhất 1 Bài
     * đã Publish (mirror ExerciseRepository#findAvailableForClass, đổi chiều Exam thay vì Exercise).
     */
    @Query("""
            select distinct a.exam from ExamClassAssignment a
            where a.schoolClass.id = :classId
              and a.exam.deletedAt is null
              and exists (select 1 from Exercise ex where ex.exam = a.exam and ex.status = vn.com.pps.education.domain.Exercise.Status.PUBLISHED)
            """)
    List<Exam> findPublishedForClass(@Param("classId") Long classId);
}
