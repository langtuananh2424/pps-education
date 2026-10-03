package vn.com.pps.education.lms.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.lms.domain.QuestionChoice;

import java.util.List;

public interface QuestionChoiceRepository extends JpaRepository<QuestionChoice, Long> {
    List<QuestionChoice> findByQuestionIdOrderByDisplayOrder(Long questionId);
}
