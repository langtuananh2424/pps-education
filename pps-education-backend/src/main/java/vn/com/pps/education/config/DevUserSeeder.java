package vn.com.pps.education.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.util.List;
import java.util.Locale;

/**
 * Seed 1 tài khoản demo / role (11 role hệ thống - V4__seed_roles_and_permissions.sql)
 * để clone repo về là đăng nhập thử được ngay, không phải tự tạo user qua SQL thủ công.
 *
 * Chỉ chạy khi property app.seed.dev-users=true (SEED_DEV_USERS env var - đã bật sẵn
 * ở docker-compose.yml và .env.example cho local dev). Mặc định false: KHÔNG tự bật
 * theo Spring profile "dev" vì Testcontainers (test suite) cũng khởi động context ở
 * profile mặc định "dev" khi không có @ActiveProfiles - bật theo profile sẽ lọt seed
 * data vào DB test. Property riêng đảm bảo test/CI/staging/production không bao giờ
 * dính, trừ khi ai đó chủ động set env var này.
 *
 * Idempotent (kiểm tra username đã tồn tại trước khi tạo) - an toàn khi restart app
 * nhiều lần, không insert trùng.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "dev-users", havingValue = "true")
public class DevUserSeeder implements ApplicationRunner {

    /** Mật khẩu dùng chung cho toàn bộ tài khoản demo - CHỈ dùng ở local dev, không dùng cho staging/production. */
    public static final String DEV_PASSWORD = "Dev@123456";

    private static final List<String> ROLE_CODES = List.of(
            "SYS_ADMIN", "HEAD_ACADEMIC", "SITE_MANAGER", "HR_MANAGER", "STAFF",
            "OPS_MANAGER", "EXECUTIVE", "PARTNER_REP", "TEACHER", "PARENT", "STUDENT"
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public DevUserSeeder(UserRepository userRepository, RoleRepository roleRepository,
                          UserRoleRepository userRoleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role sysAdminRole = roleRepository.findByCode("SYS_ADMIN").orElseThrow();
        User sysAdmin = ensureUser("sysadmin", sysAdminRole, null); // tự gán quyền cho chính mình (bootstrap)

        for (String roleCode : ROLE_CODES) {
            if ("SYS_ADMIN".equals(roleCode)) continue;
            Role role = roleRepository.findByCode(roleCode).orElseThrow();
            String username = roleCode.toLowerCase(Locale.ROOT).replace("_", "");
            ensureUser(username, role, sysAdmin);
        }

        log.info("[DevUserSeeder] San sang {} tai khoan demo (username = ma role viet thuong, VD sysadmin/teacher/student), mat khau chung: {}",
                ROLE_CODES.size(), DEV_PASSWORD);
    }

    private User ensureUser(String username, Role role, User assignedBy) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setEmail(username + "@pps.edu.vn");
            user.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
            user.setFullName(role.getName() + " (Demo)");
            user.setStatus(User.Status.ACTIVE);
            User saved = userRepository.save(user);

            UserRole userRole = new UserRole();
            userRole.setUser(saved);
            userRole.setRole(role);
            userRole.setAssignedBy(assignedBy != null ? assignedBy : saved);
            userRoleRepository.save(userRole);
            return saved;
        });
    }
}
