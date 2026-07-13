package vn.com.pps.education.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.security.JwtService;

import java.util.List;

/**
 * Base cho test xác nhận @PreAuthorize("hasPermission(...)") chặn đúng qua
 * HTTP thật (MockMvc + JWT ký thật, đi qua JwtAuthenticationFilter +
 * PpsPermissionEvaluator + PermissionEvaluationService thật) — khác
 * *ServiceTest gọi thẳng method Service, bỏ qua hoàn toàn Spring Security
 * proxy đặt ở tầng Controller.
 */
@AutoConfigureMockMvc
public abstract class AbstractControllerTest extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    protected User userWithRole(String usernamePrefix, String roleCode) {
        User user = new User();
        user.setUsername(usernamePrefix + "." + System.nanoTime());
        user.setEmail(usernamePrefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + usernamePrefix);
        user.setStatus(User.Status.ACTIVE);
        user = userRepository.save(user);

        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);

        return user;
    }

    protected String bearerToken(User user, String roleCode) {
        return "Bearer " + jwtService.generateAccessToken(user.getId(), user.getUsername(), List.of(roleCode));
    }
}
