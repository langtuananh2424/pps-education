package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.ReviewVideoSet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewVideoSetRepository extends JpaRepository<ReviewVideoSet, Long> {

    /** UC-21 mở rộng (BTVN online — dán uuid làm phương án thay dropdown, V55). */
    Optional<ReviewVideoSet> findByUuid(UUID uuid);

    /** V156 — dùng thay findById ở mọi nơi đọc/sửa 1 Bộ, không lộ Bộ đã "xóa" (deleted_at), mirror ExamRepository#findByIdAndDeletedAtIsNull. */
    Optional<ReviewVideoSet> findByIdAndDeletedAtIsNull(Long id);

    /** Kho Video — lọc theo khung chương trình/loại giáo viên (V98, mirror ExamRepository). V156: thêm lọc deletedAt IS NULL. */
    List<ReviewVideoSet> findByCurriculumIdAndDeletedAtIsNullOrderByDisplayOrder(Long curriculumId);

    List<ReviewVideoSet> findByCurriculumIdAndTeacherTypeAndDeletedAtIsNullOrderByDisplayOrder(Long curriculumId, ReviewVideoSet.TeacherType teacherType);

    List<ReviewVideoSet> findByTeacherTypeAndDeletedAtIsNullOrderByDisplayOrder(ReviewVideoSet.TeacherType teacherType);

    List<ReviewVideoSet> findByDeletedAtIsNullOrderByDisplayOrder();

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — gate "Xóa Sub Topic" (xem CurriculumService#deleteSubTopic): còn Bộ video tham chiếu thì không cho xóa. */
    boolean existsBySubTopicId(Long subTopicId);

    /**
     * UC-23a: bộ video HS/GV lớp X xem được — đã gán tường minh cho lớp X
     * qua {@link vn.com.pps.education.domain.ReviewVideoSetClassAssignment}
     * (V98, mirror ExerciseRepository#findAvailableForClass). status=NULL
     * cho phép GV xem cả DRAFT khi quản lý; truyền PUBLISHED khi gọi cho
     * học viên/chọn nguồn giao bài.
     */
    @Query("""
            SELECT s FROM ReviewVideoSet s
            WHERE s.id IN (SELECT a.reviewVideoSet.id FROM ReviewVideoSetClassAssignment a WHERE a.schoolClass.id = :classId)
            AND (:status IS NULL OR s.status = :status)
            ORDER BY s.displayOrder
            """)
    List<ReviewVideoSet> findAvailableForClass(@Param("classId") Long classId, @Param("status") ReviewVideoSet.Status status);
}
