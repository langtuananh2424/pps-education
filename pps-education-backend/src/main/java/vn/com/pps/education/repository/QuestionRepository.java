package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.Question;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByQuestionBankIdAndStatus(Long questionBankId, Question.Status status);

    /** Chống trùng câu hỏi trong cùng 1 ngân hàng — xem QuestionBankService#createQuestion. */
    boolean existsByQuestionBankIdAndContent(Long questionBankId, String content);
}
