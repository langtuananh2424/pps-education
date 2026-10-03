package vn.com.pps.education.student.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.student.domain.StudentHistory;

import java.util.List;
import java.util.Optional;

public interface StudentHistoryRepository extends JpaRepository<StudentHistory, Long> {
    List<StudentHistory> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    /** Bản ghi lịch sử liền trước của cùng học sinh — để hiển thị "giá trị cũ → mới" (V203). */
    Optional<StudentHistory> findFirstByStudentIdAndIdLessThanOrderByIdDesc(Long studentId, Long id);
}
