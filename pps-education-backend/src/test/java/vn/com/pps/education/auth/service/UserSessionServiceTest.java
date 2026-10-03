package vn.com.pps.education.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.auth.domain.RefreshToken;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.auth.dto.UserSessionResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.auth.repository.RefreshTokenRepository;
import vn.com.pps.education.auth.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-44 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-29): Quản trị viên xem/gỡ thiết bị
 * đang đăng nhập — xem Javadoc UserSessionService.
 */
@Transactional
class UserSessionServiceTest extends AbstractIntegrationTest {

    @Autowired
    private UserSessionService userSessionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private User user;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setUsername("session.test.user");
        u.setEmail("session.test.user@pps.edu.vn");
        u.setFullName("Session Test User");
        u.setStatus(User.Status.ACTIVE);
        user = userRepository.save(u);
    }

    private RefreshToken token(OffsetDateTime issuedAt, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
        RefreshToken t = new RefreshToken();
        t.setUser(user);
        t.setTokenHash(UUID.randomUUID().toString());
        t.setIpAddress("113.160.10.20");
        t.setDeviceInfo("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/153.0.0.0");
        t.setIssuedAt(issuedAt);
        t.setExpiresAt(expiresAt);
        t.setRevokedAt(revokedAt);
        return refreshTokenRepository.save(t);
    }

    @Test
    void getActiveSessions_boSung_listsOnlyActiveSessionsNewestFirst() {
        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken older = token(now.minusDays(2), now.plusDays(12), null);
        RefreshToken newer = token(now.minusHours(1), now.plusDays(14), null);
        token(now.minusDays(1), now.plusDays(13), now.minusHours(2)); // đã thu hồi
        token(now.minusDays(20), now.minusDays(6), null);             // đã hết hạn

        List<UserSessionResponse> sessions = userSessionService.getActiveSessions(user.getId());

        assertThat(sessions).extracting(UserSessionResponse::id).containsExactly(newer.getId(), older.getId());
        assertThat(sessions.get(0).ipAddress()).isEqualTo("113.160.10.20");
        assertThat(sessions.get(0).deviceInfo()).contains("Chrome/153");
    }

    @Test
    void revokeSession_boSung_revokesOnlyThatSession() {
        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken target = token(now.minusDays(4), now.plusDays(10), null);
        RefreshToken other = token(now.minusHours(1), now.plusDays(14), null);

        userSessionService.revokeSession(user.getId(), target.getId());

        assertThat(refreshTokenRepository.findById(target.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(refreshTokenRepository.findById(other.getId()).orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    void revokeSession_boSung_rejectsSessionOfAnotherUser() {
        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken target = token(now, now.plusDays(14), null);
        User stranger = new User();
        stranger.setUsername("session.test.stranger");
        stranger.setEmail("session.test.stranger@pps.edu.vn");
        stranger.setFullName("Stranger");
        stranger.setStatus(User.Status.ACTIVE);
        Long strangerId = userRepository.save(stranger).getId();

        assertThatThrownBy(() -> userSessionService.revokeSession(strangerId, target.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(refreshTokenRepository.findById(target.getId()).orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    void revokeAllSessions_boSung_revokesEveryActiveSession() {
        OffsetDateTime now = OffsetDateTime.now();
        token(now.minusDays(1), now.plusDays(13), null);
        token(now, now.plusDays(14), null);

        userSessionService.revokeAllSessions(user.getId());

        assertThat(userSessionService.getActiveSessions(user.getId())).isEmpty();
    }
}
