package vn.com.pps.education.lms.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.lms.domain.Question;

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

    java.util.Optional<Question> findByIdAndQuestionBankId(Long id, Long questionBankId);

    /**
     * Bổ sung 2026-10-19 (đã xác nhận với người dùng khi review hiệu năng trang "Ngân hàng câu hỏi")
     * — trước đây FE tự load TOÀN BỘ câu hỏi của MỌI khung chương trình/MỌI ngân hàng rồi mới lọc/phân
     * trang phía client (xem QuestionBankPage.tsx cũ). Query này cho phép lọc + phân trang NGAY ở DB,
     * cùng điều kiện "chỉ bank legacy" với QuestionBankRepository#findLegacyByCurriculumId (loại bank
     * nội bộ của Exam). join fetch question_bank/curriculum để tránh N+1 khi map ra response theo trang.
     */
    @Query("""
            select q from Question q
            join fetch q.questionBank qb
            left join fetch qb.curriculum c
            where q.status = :status
              and not exists (select e.id from Exam e where e.questionBank = qb)
              and (:search is null or lower(q.content) like :search or cast(q.id as string) like :search)
              and (:questionType is null or q.questionType = :questionType)
              and (:difficulty is null or q.difficulty = :difficulty)
              and (:level is null or qb.level = :level)
            order by q.id desc
            """)
    Page<Question> searchLegacyQuestions(@Param("status") Question.Status status,
                                          @Param("search") String search,
                                          @Param("questionType") Question.QuestionType questionType,
                                          @Param("difficulty") Question.Difficulty difficulty,
                                          @Param("level") String level,
                                          Pageable pageable);
}
