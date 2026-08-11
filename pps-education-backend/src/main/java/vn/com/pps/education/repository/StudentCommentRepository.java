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

    /** V65: toàn bộ nhận xét DAILY của 1 buổi học (mọi học sinh) — dùng kiểm tra xung đột lựa chọn BTVN buổi sau cùng buổi. */
    List<StudentComment> findByClassSessionId(Long classSessionId);

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

    /** Bổ sung ngoài SDD gốc — StudentProfileService (FR-REP-04): JOIN FETCH lớp/kỳ học/buổi học để tránh N+1 khi gộp toàn bộ nhận xét của 1 học sinh qua mọi lớp. */
    @Query("""
            SELECT c FROM StudentComment c
            JOIN FETCH c.schoolClass sc
            LEFT JOIN FETCH c.academicTerm t
            LEFT JOIN FETCH c.classSession cs
            WHERE c.student.id = :studentId
            ORDER BY c.commentDate DESC
            """)
    List<StudentComment> findByStudentIdWithContext(@Param("studentId") Long studentId);
}
