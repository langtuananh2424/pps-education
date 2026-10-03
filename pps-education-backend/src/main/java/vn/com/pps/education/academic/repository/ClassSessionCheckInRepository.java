package vn.com.pps.education.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.academic.domain.ClassSessionCheckIn;

import java.util.List;
import java.util.Optional;

public interface ClassSessionCheckInRepository extends JpaRepository<ClassSessionCheckIn, Long> {

    Optional<ClassSessionCheckIn> findByClassSessionId(Long classSessionId);

    List<ClassSessionCheckIn> findByClassSessionIdIn(List<Long> classSessionIds);

    boolean existsByClassSessionId(Long classSessionId);
}
