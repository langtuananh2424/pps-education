package vn.com.pps.education.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng (2026-08-27) — seed
 * ĐÚNG 1 tài khoản `sysadmin` để bootstrap staging/production lần đầu (từ
 * đó admin tự tạo các tài khoản thật khác qua UI quản lý người dùng), KHÁC
 * hẳn {@link DevUserSeeder} (seed 11 tài khoản demo mọi role, mật khẩu
 * cứng dùng chung — CHỈ an toàn cho local dev, không phù hợp môi trường có
 * dữ liệu thật).
 *
 * Chỉ chạy khi property app.seed.initial-admin=true (SEED_INITIAL_ADMIN env
 * var — mặc định false, không tự bật theo profile nào). Mật khẩu BẮT BUỘC
 * lấy từ app.seed.sysadmin-password (SEED_SYSADMIN_PASSWORD) — KHÔNG hardcode,
 * KHÔNG log ra — app từ chối khởi động nếu bật seeder này mà thiếu mật khẩu,
 * tránh âm thầm tạo tài khoản admin với mật khẩu yếu/mặc định trên môi
 * trường có dữ liệu thật.
 *
 * Idempotent (kiểm tra username đã tồn tại trước khi tạo) - an toàn khi
 * restart app nhiều lần, không insert trùng, không ghi đè mật khẩu đã đổi.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "initial-admin", havingValue = "true")
public class InitialAdminSeeder implements ApplicationRunner {

    private static final String SYSADMIN_USERNAME = "sysadmin";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String sysadminPassword;

    public InitialAdminSeeder(UserRepository userRepository, RoleRepository roleRepository,
                               UserRoleRepository userRoleRepository, PasswordEncoder passwordEncoder,
                               @Value("${app.seed.sysadmin-password:}") String sysadminPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.sysadminPassword = sysadminPassword;
        if (sysadminPassword == null || sysadminPassword.isBlank()) {
            throw new IllegalStateException(
                    "SEED_INITIAL_ADMIN=true nhưng thiếu SEED_SYSADMIN_PASSWORD — bắt buộc phải đặt "
                            + "mật khẩu mạnh riêng cho tài khoản sysadmin bootstrap, không dùng mật khẩu mặc định "
                            + "trên môi trường có dữ liệu thật. Đặt biến môi trường SEED_SYSADMIN_PASSWORD rồi khởi động lại.");
        }
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername(SYSADMIN_USERNAME).isPresent()) {
            log.info("[InitialAdminSeeder] Tài khoản '{}' đã tồn tại — bỏ qua, không tạo lại/ghi đè mật khẩu.",
                    SYSADMIN_USERNAME);
            return;
        }

        Role sysAdminRole = roleRepository.findByCode("SYS_ADMIN").orElseThrow();
        User sysAdmin = new User();
        sysAdmin.setUsername(SYSADMIN_USERNAME);
        sysAdmin.setEmail(SYSADMIN_USERNAME + "@pps.edu.vn");
        sysAdmin.setPasswordHash(passwordEncoder.encode(sysadminPassword));
        sysAdmin.setFullName("System Administrator");
        sysAdmin.setStatus(User.Status.ACTIVE);
        User saved = userRepository.save(sysAdmin);

        UserRole userRole = new UserRole();
        userRole.setUser(saved);
        userRole.setRole(sysAdminRole);
        userRole.setAssignedBy(saved); // tự gán quyền cho chính mình (bootstrap, chưa có admin nào khác)
        userRoleRepository.save(userRole);

        log.info("[InitialAdminSeeder] Đã tạo tài khoản '{}' (role SYS_ADMIN) — mật khẩu lấy từ SEED_SYSADMIN_PASSWORD, không in ra log.",
                SYSADMIN_USERNAME);
    }
}
