package vn.com.pps.education.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.com.pps.education.domain.LoginAttempt;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    /** UC-44 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-05): lịch sử đăng nhập/thiết bị. */
    Page<LoginAttempt> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
