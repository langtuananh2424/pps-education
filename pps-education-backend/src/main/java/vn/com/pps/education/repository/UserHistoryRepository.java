package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.UserHistory;

public interface UserHistoryRepository extends JpaRepository<UserHistory, Long> {
}
