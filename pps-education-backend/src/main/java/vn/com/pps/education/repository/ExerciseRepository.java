package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Exercise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
    Optional<Exercise> findByCode(String code);

    /** Kho đề — danh sách Bài thuộc 1 Đề. */
    List<Exercise> findByExamId(Long examId);

    /**
     * UC-40 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * danh sách Bài khả dụng làm nguồn "BTVN buổi sau" ở Nhận xét học viên
     * (UC-21) cho 1 lớp — Bài đã Publish, thuộc 1 Đề đã gán cho lớp đó
     * (exam_class_assignments là điều kiện DUY NHẤT, không còn khớp khung
     * chương trình như trước Kho đề).
     */
    @Query("""
            select e from Exercise e
            where e.status = :status
              and e.exam.id in (select a.exam.id from ExamClassAssignment a where a.schoolClass.id = :classId)
            """)
    List<Exercise> findAvailableForClass(@Param("classId") Long classId, @Param("status") Exercise.Status status);

    /** V65: dán uuid làm phương án thay dropdown khi nhập Excel — mirror ExerciseAssignmentRepository.findByUuid cũ. */
    Optional<Exercise> findByUuid(UUID uuid);
}
