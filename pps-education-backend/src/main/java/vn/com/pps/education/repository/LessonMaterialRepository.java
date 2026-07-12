package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.LessonMaterial;

import java.util.List;

public interface LessonMaterialRepository extends JpaRepository<LessonMaterial, Long> {
    List<LessonMaterial> findByLessonIdOrderByDisplayOrder(Long lessonId);
}
