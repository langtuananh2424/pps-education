package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.StudentTransferHistory;

import java.util.List;

public interface StudentTransferHistoryRepository extends JpaRepository<StudentTransferHistory, Long> {
    List<StudentTransferHistory> findByStudentIdOrderByEffectiveDateDesc(Long studentId);
}
