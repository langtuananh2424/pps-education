package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Question;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByQuestionBankIdAndStatus(Long questionBankId, Question.Status status);

    /**
     * Chống trùng câu hỏi trong cùng 1 ngân hàng — xem QuestionBankService#createQuestion. CHỈ so với
     * câu đang ACTIVE — câu đã ARCHIVED không tính, để không chặn nhầm luồng sửa hợp lệ khi câu hỏi đã
     * có student_answers (bắt buộc archive câu cũ + tạo câu mới thay thế, có thể trùng nội dung nếu chỉ
     * sửa đáp án chứ không sửa câu hỏi — xem QuestionBankService#updateQuestion/QuestionLockedException).
     */
    boolean existsByQuestionBankIdAndContentAndStatus(Long questionBankId, String content, Question.Status status);
}
