package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Exercise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
    Optional<Exercise> findByCode(String code);

    /** V65: danh sách đề khả dụng làm nguồn "BTVN Ngữ pháp buổi sau" ở Nhận xét — đúng khung chương trình của lớp, đã Publish, loại ASSIGNED. */
    List<Exercise> findByCurriculumIdAndExerciseTypeAndStatus(Long curriculumId, Exercise.ExerciseType exerciseType, Exercise.Status status);

    /** V65: dán uuid làm phương án thay dropdown khi nhập Excel — mirror ExerciseAssignmentRepository.findByUuid cũ. */
    Optional<Exercise> findByUuid(UUID uuid);
}
