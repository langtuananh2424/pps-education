package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.com.pps.education.domain.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /** Rà soát bảo mật 2026-09-28 - JwtAuthenticationFilter kiểm tra tài khoản còn ACTIVE (xem AccountStatusService). */
    boolean existsByIdAndStatus(Long id, User.Status status);

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByPhone(String phone);
}
