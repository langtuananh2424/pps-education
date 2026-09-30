package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ClassEnrollmentHistory;

import java.util.List;
import java.util.Optional;

public interface ClassEnrollmentHistoryRepository extends JpaRepository<ClassEnrollmentHistory, Long> {
    List<ClassEnrollmentHistory> findByClassEnrollmentIdOrderByCreatedAtDesc(Long classEnrollmentId);

    /** Bản ghi lịch sử liền trước của cùng ghi danh — để hiển thị "giá trị cũ → mới" (V203). */
    Optional<ClassEnrollmentHistory> findFirstByClassEnrollmentIdAndIdLessThanOrderByIdDesc(Long classEnrollmentId, Long id);
}
