package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.QuestionChoice;

import java.util.List;

public interface QuestionChoiceRepository extends JpaRepository<QuestionChoice, Long> {
    List<QuestionChoice> findByQuestionIdOrderByDisplayOrder(Long questionId);
}
