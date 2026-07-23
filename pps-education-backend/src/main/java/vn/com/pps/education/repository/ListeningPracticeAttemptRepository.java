package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ListeningPracticeAttempt;
import vn.com.pps.education.domain.ListeningPracticeItem;

import java.util.List;

public interface ListeningPracticeAttemptRepository extends JpaRepository<ListeningPracticeAttempt, Long> {

    long countByPracticeItemIdAndStudentId(Long practiceItemId, Long studentId);

    List<ListeningPracticeAttempt> findByPracticeItemIdAndStudentIdOrderByAttemptNumberDesc(Long practiceItemId, Long studentId);

    /** Hàng chờ chấm thủ công riêng (UC-26 chế độ Nói) — mọi attempt SUBMITTED thuộc item mode=SPEAKING. */
    List<ListeningPracticeAttempt> findByStatusAndPracticeItem_Mode(
            ListeningPracticeAttempt.Status status, ListeningPracticeItem.Mode mode);
}
