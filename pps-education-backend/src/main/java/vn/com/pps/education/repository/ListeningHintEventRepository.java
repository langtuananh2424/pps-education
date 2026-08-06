package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.ListeningHintEvent;

public interface ListeningHintEventRepository extends JpaRepository<ListeningHintEvent, Long> {
    /** Số lượt học sinh mở gợi ý tapescript của 1 câu hỏi — dùng cho ExerciseReportService (thống kê "Phân tích câu hỏi"). */
    long countByQuestionId(Long questionId);

    @Query("SELECT COUNT(DISTINCT e.student.id) FROM ListeningHintEvent e WHERE e.question.id = :questionId")
    long countDistinctStudentByQuestionId(@Param("questionId") Long questionId);
}
