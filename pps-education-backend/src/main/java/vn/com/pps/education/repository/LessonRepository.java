package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.Lesson;

import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findBySchoolClassIdOrderByLessonOrder(Long classId);

    List<Lesson> findByCurriculumIdOrderByLessonOrder(Long curriculumId);

    /** UC-25/UC-27: bài giảng HS lớp X xem được — riêng lớp X hoặc chung theo khung của lớp X (SDD). */
    @Query("""
            SELECT l FROM Lesson l
            WHERE l.status = :status
            AND (l.schoolClass.id = :classId OR l.curriculum.id = :curriculumId)
            ORDER BY l.lessonOrder
            """)
    List<Lesson> findVisibleForClass(@Param("classId") Long classId, @Param("curriculumId") Long curriculumId,
                                      @Param("status") Lesson.Status status);
}
