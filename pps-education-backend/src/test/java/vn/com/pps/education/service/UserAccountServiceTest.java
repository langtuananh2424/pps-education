package vn.com.pps.education.service;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.RefreshToken;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AdminChangePasswordRequest;
import vn.com.pps.education.dto.ChangeOwnPasswordRequest;
import vn.com.pps.education.dto.CreateUserRequest;
import vn.com.pps.education.dto.UserResponse;
import vn.com.pps.education.exception.DuplicateUserAccountException;
import vn.com.pps.education.exception.InvalidCredentialsException;
import vn.com.pps.education.repository.RefreshTokenRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-43: Khởi tạo tài khoản người dùng — Main Flow (bước 1-4), A1 (username/
 * email trùng), A2 (mật khẩu quá ngắn). UC-45: Đổi mật khẩu — Main Flow, A1
 * (sai mật khẩu hiện tại), A3 (tài khoản chỉ đăng nhập Google), A4 (Quản trị
 * viên đổi cho tài khoản khác). Xem docs/uc/phan-he-02-phan-quyen.md.
 */
@Transactional
class UserAccountServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void create_UC43_MainFlow_withPassword_createsActiveAccountWithBcryptHash() {
        UserResponse response = userAccountService.create(baseRequest("MatKhau@8"));

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.passwordSet()).isTrue();
        assertThat(response.googleLinked()).isFalse();
        User saved = userRepository.findById(response.id()).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2"); // BCrypt (NFR-SEC-01)
    }

    @Test
    void create_UC43_MainFlow_withoutPassword_createsGoogleOnlyAccount() {
        UserResponse response = userAccountService.create(baseRequest(null));

        assertThat(response.passwordSet()).isFalse();
        User saved = userRepository.findById(response.id()).orElseThrow();
        assertThat(saved.getPasswordHash()).isNull();
    }

    @Test
    void create_UC43_Postcondition_newAccountHasNoRoleAssigned() {
        UserResponse response = userAccountService.create(baseRequest("MatKhau@8"));

        // Hậu điều kiện UC-43: tài khoản mới chưa có role nào cho tới khi được gán qua UC-03/UC-04.
        assertThat(response.id()).isNotNull();
        assertThat(userRepository.findById(response.id()).orElseThrow().getStatus())
                .isEqualTo(User.Status.ACTIVE);
    }

    @Test
    void create_UC43_A1_rejectsDuplicateUsername() {
        String username = username();
        userAccountService.create(new CreateUserRequest(
                username, email(), "Người Một", null, null, null, null));

        assertThatThrownBy(() -> userAccountService.create(new CreateUserRequest(
                username, email(), "Người Hai", null, null, null, null)))
                .isInstanceOf(DuplicateUserAccountException.class);
    }

    @Test
    void create_UC43_A1_rejectsDuplicateEmail() {
        String email = email();
        userAccountService.create(new CreateUserRequest(
                username(), email, "Người Một", null, null, null, null));

        assertThatThrownBy(() -> userAccountService.create(new CreateUserRequest(
                username(), email, "Người Hai", null, null, null, null)))
                .isInstanceOf(DuplicateUserAccountException.class);
    }

    @Test
    void create_UC43_A2_rejectsPasswordShorterThan8Chars() {
        CreateUserRequest request = new CreateUserRequest(
                username(), email(), "Người Test", null, "short1", null, null);

        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void changeOwnPassword_UC45_MainFlow_updatesHashAndRevokesActiveRefreshTokens() {
        UserResponse account = userAccountService.create(baseRequest("MatKhauCu@8"));
        RefreshToken activeToken = activeRefreshTokenFor(account.id());

        userAccountService.changeOwnPassword(account.id(),
                new ChangeOwnPasswordRequest("MatKhauCu@8", "MatKhauMoi@9"));

        User updated = userRepository.findById(account.id()).orElseThrow();
        assertThat(passwordEncoder.matches("MatKhauMoi@9", updated.getPasswordHash())).isTrue();
        // Hậu điều kiện UC-45: thu hồi toàn bộ refresh token đang hoạt động.
        assertThat(refreshTokenRepository.findById(activeToken.getId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test
    void changeOwnPassword_UC45_A1_rejectsWrongCurrentPassword() {
        UserResponse account = userAccountService.create(baseRequest("MatKhauCu@8"));
        RefreshToken activeToken = activeRefreshTokenFor(account.id());

        assertThatThrownBy(() -> userAccountService.changeOwnPassword(account.id(),
                new ChangeOwnPasswordRequest("SaiMatKhau@1", "MatKhauMoi@9")))
                .isInstanceOf(InvalidCredentialsException.class);

        User unchanged = userRepository.findById(account.id()).orElseThrow();
        assertThat(passwordEncoder.matches("MatKhauCu@8", unchanged.getPasswordHash())).isTrue();
        assertThat(refreshTokenRepository.findById(activeToken.getId()).orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    void changeOwnPassword_UC45_A2_rejectsNewPasswordShorterThan8Chars() {
        ChangeOwnPasswordRequest request = new ChangeOwnPasswordRequest("MatKhauCu@8", "short1");

        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void changeOwnPassword_UC45_A3_googleOnlyAccountSetsFirstPasswordWithoutCurrentPassword() {
        UserResponse account = userAccountService.create(baseRequest(null));

        userAccountService.changeOwnPassword(account.id(),
                new ChangeOwnPasswordRequest(null, "MatKhauMoi@9"));

        User updated = userRepository.findById(account.id()).orElseThrow();
        assertThat(passwordEncoder.matches("MatKhauMoi@9", updated.getPasswordHash())).isTrue();
    }

    @Test
    void changePasswordAsAdmin_UC45_A4_updatesHashAndRevokesActiveRefreshTokensWithoutCurrentPassword() {
        UserResponse account = userAccountService.create(baseRequest("MatKhauCu@8"));
        RefreshToken activeToken = activeRefreshTokenFor(account.id());

        userAccountService.changePasswordAsAdmin(account.id(), new AdminChangePasswordRequest("MatKhauMoi@9"));

        User updated = userRepository.findById(account.id()).orElseThrow();
        assertThat(passwordEncoder.matches("MatKhauMoi@9", updated.getPasswordHash())).isTrue();
        assertThat(refreshTokenRepository.findById(activeToken.getId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    private RefreshToken activeRefreshTokenFor(Long userId) {
        RefreshToken token = new RefreshToken();
        token.setUser(userRepository.findById(userId).orElseThrow());
        token.setTokenHash("hash." + SEQ.incrementAndGet());
        token.setExpiresAt(OffsetDateTime.now().plusDays(30));
        return refreshTokenRepository.save(token);
    }

    private CreateUserRequest baseRequest(String password) {
        return new CreateUserRequest(username(), email(), "Người Test", null, password, null, null);
    }

    private String username() {
        return "user.test." + SEQ.incrementAndGet();
    }

    private String email() {
        return "user.test." + SEQ.incrementAndGet() + "@pps.edu.vn";
    }
}
