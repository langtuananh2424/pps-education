package vn.com.pps.education.student.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.student.domain.StudentStatusHistory;

import java.util.List;

public interface StudentStatusHistoryRepository extends JpaRepository<StudentStatusHistory, Long> {
    List<StudentStatusHistory> findByStudentIdOrderByChangedAtDesc(Long studentId);
}
