package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.ClassHistory;

import java.util.List;

public interface ClassHistoryRepository extends JpaRepository<ClassHistory, Long> {
    List<ClassHistory> findBySchoolClassIdOrderByCreatedAtDesc(Long schoolClassId);
}
