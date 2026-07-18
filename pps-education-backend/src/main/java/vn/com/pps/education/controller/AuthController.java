package vn.com.pps.education.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.com.pps.education.dto.ChangeOwnPasswordRequest;
import vn.com.pps.education.dto.CurrentUserResponse;
import vn.com.pps.education.dto.GoogleLoginRequest;
import vn.com.pps.education.dto.LoginRequest;
import vn.com.pps.education.dto.LoginResponse;
import vn.com.pps.education.dto.LogoutRequest;
import vn.com.pps.education.dto.RefreshTokenRequest;
import vn.com.pps.education.dto.RefreshTokenResponse;
import vn.com.pps.education.security.AuthenticatedUser;
import vn.com.pps.education.service.AuthService;
import vn.com.pps.education.service.UserAccountService;

/** UC-01: Đăng nhập hệ thống (FR-AUT-01, FR-AUT-02). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserAccountService userAccountService;

    public AuthController(AuthService authService, UserAccountService userAccountService) {
        this.authService = authService;
        this.userAccountService = userAccountService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest));
    }

    @PostMapping("/login/google")
    public ResponseEntity<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request,
                                                           HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.loginWithGoogle(request, httpRequest));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                          HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refresh(request, httpRequest));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    /** Hồ sơ tài khoản đang đăng nhập — phục vụ sidebar/header phía frontend. Yêu cầu JWT (xem SecurityConfig). */
    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok(authService.getCurrentUser(actor.userId()));
    }

    /** UC-45 Main Flow: tự đổi mật khẩu của chính tài khoản đang đăng nhập. Yêu cầu JWT (xem SecurityConfig). */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changeOwnPassword(@AuthenticationPrincipal AuthenticatedUser actor,
                                                   @Valid @RequestBody ChangeOwnPasswordRequest request) {
        userAccountService.changeOwnPassword(actor.userId(), request);
        return ResponseEntity.noContent().build();
    }
}
