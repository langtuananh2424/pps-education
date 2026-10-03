package vn.com.pps.education.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.auth.domain.UserHistory;

public interface UserHistoryRepository extends JpaRepository<UserHistory, Long> {
}
