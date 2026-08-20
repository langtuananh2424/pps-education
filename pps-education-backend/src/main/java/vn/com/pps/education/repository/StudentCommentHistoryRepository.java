package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.StudentCommentHistory;

import java.util.List;

public interface StudentCommentHistoryRepository extends JpaRepository<StudentCommentHistory, Long> {

    /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-19) — timeline version history, mới nhất trước. */
    List<StudentCommentHistory> findByStudentCommentIdOrderByCreatedAtDesc(Long studentCommentId);

    /** Mirror method trên nhưng gộp CẢ BUỔI (mọi học sinh của 1 class_session) — dùng cho màn "Lịch sử phiên bản" toàn bảng. */
    List<StudentCommentHistory> findByStudentComment_ClassSession_IdOrderByCreatedAtDesc(Long classSessionId);
}
