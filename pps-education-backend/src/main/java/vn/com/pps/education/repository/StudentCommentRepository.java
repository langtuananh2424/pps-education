package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.StudentComment;

import java.util.List;
import java.util.Optional;

public interface StudentCommentRepository extends JpaRepository<StudentComment, Long> {

    List<StudentComment> findBySchoolClassIdAndStudentIdOrderByCommentDateDesc(Long classId, Long studentId);

    /** UC-21 (bổ sung — nhận xét Hàng ngày kiểu mới): 1 học sinh chỉ có tối đa 1 nhận xét DAILY / buổi học. */
    Optional<StudentComment> findByClassSessionIdAndStudentId(Long classSessionId, Long studentId);

    /** UC-25 Portal Phụ huynh — nhận xét/cảnh báo: student_comments WHERE status=APPROVED (SDD). */
    List<StudentComment> findBySchoolClassIdAndStudentIdAndStatusOrderByCommentDateDesc(
            Long classId, Long studentId, StudentComment.Status status);

    @Query("""
            SELECT c FROM StudentComment c
            WHERE c.status = :status
            AND c.schoolClass.site.id = :siteId
            ORDER BY c.submittedAt ASC
            """)
    List<StudentComment> findByStatusAndSiteId(@Param("status") StudentComment.Status status, @Param("siteId") Long siteId);
}
