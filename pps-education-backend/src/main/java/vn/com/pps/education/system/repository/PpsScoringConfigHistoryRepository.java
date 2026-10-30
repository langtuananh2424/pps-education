package vn.com.pps.education.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.system.domain.PpsScoringConfigHistory;

import java.util.List;

public interface PpsScoringConfigHistoryRepository extends JpaRepository<PpsScoringConfigHistory, Long> {

    List<PpsScoringConfigHistory> findTop50ByOrderByCreatedAtDesc();
}
