package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.QuestionBank;

import java.util.List;
import java.util.Optional;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {
    List<QuestionBank> findByCurriculumId(Long curriculumId);

    /** V75: Màn quản lý generic chỉ trả bank legacy, loại mọi bank nội bộ đang được Exam tham chiếu. */
    @Query("""
            select qb from QuestionBank qb
            where qb.curriculum.id = :curriculumId
              and not exists (select e.id from Exam e where e.questionBank = qb)
            """)
    List<QuestionBank> findLegacyByCurriculumId(@Param("curriculumId") Long curriculumId);

    Optional<QuestionBank> findByCode(String code);
}
