package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.ReviewVideoSet;

import java.util.List;

public interface ReviewVideoSetRepository extends JpaRepository<ReviewVideoSet, Long> {

    List<ReviewVideoSet> findBySchoolClassIdOrderByDisplayOrder(Long classId);

    List<ReviewVideoSet> findByCurriculumIdOrderByDisplayOrder(Long curriculumId);

    /**
     * UC-23a: bộ video HS lớp X xem được — riêng lớp X hoặc chung theo
     * khung của lớp X (SDD). status=NULL cho phép staff xem cả DRAFT khi
     * quản lý; truyền PUBLISHED khi gọi cho học viên. Copy nguyên văn từ
     * Lesson.findVisibleForClass (đã có lịch sử sửa 1 bug thật — trước đây
     * bỏ sót bộ dùng chung theo curriculum, xem LessonService cũ).
     */
    @Query("""
            SELECT s FROM ReviewVideoSet s
            WHERE (:status IS NULL OR s.status = :status)
            AND (s.schoolClass.id = :classId OR s.curriculum.id = :curriculumId)
            ORDER BY s.displayOrder
            """)
    List<ReviewVideoSet> findVisibleForClass(@Param("classId") Long classId, @Param("curriculumId") Long curriculumId,
                                              @Param("status") ReviewVideoSet.Status status);
}
