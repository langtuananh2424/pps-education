package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.SessionPeriod;

import java.util.List;

public interface SessionPeriodRepository extends JpaRepository<SessionPeriod, Long> {
    List<SessionPeriod> findByClassSessionIdOrderByPeriodNumber(Long classSessionId);
}
