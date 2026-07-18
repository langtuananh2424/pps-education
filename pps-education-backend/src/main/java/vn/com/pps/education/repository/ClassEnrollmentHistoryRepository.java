package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ClassEnrollmentHistory;

import java.util.List;

public interface ClassEnrollmentHistoryRepository extends JpaRepository<ClassEnrollmentHistory, Long> {
    List<ClassEnrollmentHistory> findByClassEnrollmentIdOrderByCreatedAtDesc(Long classEnrollmentId);
}
