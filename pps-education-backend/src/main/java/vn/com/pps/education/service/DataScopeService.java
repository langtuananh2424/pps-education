package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteTeacherRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * V202 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-30) —
 * phạm vi dữ liệu hiệu lực của 1 tài khoản, đọc từ roles.data_scope thay
 * cho việc tự đoán theo mã vai trò. Tài khoản mang nhiều vai trò lấy phạm
 * vi rộng nhất (ALL > SITE > CLASS > SELF); không có vai trò nào thì SELF.
 */
@Service
public class DataScopeService {

    private final UserRoleRepository userRoleRepository;
    private final PermissionEvaluationService permissionEvaluationService;
    private final SiteTeacherRepository siteTeacherRepository;
    private final SiteManagerRepository siteManagerRepository;

    public DataScopeService(UserRoleRepository userRoleRepository,
                            PermissionEvaluationService permissionEvaluationService,
                            SiteTeacherRepository siteTeacherRepository,
                            SiteManagerRepository siteManagerRepository) {
        this.userRoleRepository = userRoleRepository;
        this.permissionEvaluationService = permissionEvaluationService;
        this.siteTeacherRepository = siteTeacherRepository;
        this.siteManagerRepository = siteManagerRepository;
    }

    @Transactional(readOnly = true)
    public Role.DataScope resolve(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> ur.getRole().getDataScope())
                .min(Comparator.naturalOrder())
                .orElse(Role.DataScope.SELF);
    }

    /** true = xem được dữ liệu của mọi điểm trường (phạm vi ALL). */
    @Transactional(readOnly = true)
    public boolean isUnrestricted(Long userId) {
        return resolve(userId) == Role.DataScope.ALL;
    }

    /**
     * V203 — các điểm trường tài khoản được xem dữ liệu, cùng quy tắc với
     * ClassService#resolveAllowedSiteIds: null = không giới hạn (phạm vi ALL
     * hoặc có academic.class.view-all); ngược lại là hợp các điểm trường được
     * gán qua site_teachers và site_managers (có thể rỗng = không thấy gì).
     */
    @Transactional(readOnly = true)
    public List<Long> resolveAllowedSiteIds(Long userId) {
        if (isUnrestricted(userId) || permissionEvaluationService.hasPermission(userId, "academic.class.view-all")) {
            return null;
        }
        return Stream.concat(
                        siteTeacherRepository.findByTeacherIdAndAssignedToIsNull(userId).stream()
                                .map(st -> st.getSite().getId()),
                        siteManagerRepository.findByUserIdAndRoleTypeAndAssignedToIsNull(userId, SiteManager.RoleType.SITE_MANAGER).stream()
                                .map(sm -> sm.getSite().getId()))
                .distinct().toList();
    }
}
