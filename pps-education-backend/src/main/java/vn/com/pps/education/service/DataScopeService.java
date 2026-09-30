package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.repository.UserRoleRepository;

import java.util.Comparator;

/**
 * V202 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-30) —
 * phạm vi dữ liệu hiệu lực của 1 tài khoản, đọc từ roles.data_scope thay
 * cho việc tự đoán theo mã vai trò. Tài khoản mang nhiều vai trò lấy phạm
 * vi rộng nhất (ALL > SITE > CLASS > SELF); không có vai trò nào thì SELF.
 */
@Service
public class DataScopeService {

    private final UserRoleRepository userRoleRepository;

    public DataScopeService(UserRoleRepository userRoleRepository) {
        this.userRoleRepository = userRoleRepository;
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
}
