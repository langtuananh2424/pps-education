package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ClassTeacherHistory;

import java.util.List;

public interface ClassTeacherHistoryRepository extends JpaRepository<ClassTeacherHistory, Long> {
    List<ClassTeacherHistory> findByClassTeacherIdOrderByCreatedAtDesc(Long classTeacherId);
}
