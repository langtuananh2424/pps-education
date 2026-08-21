package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Position;
import vn.com.pps.education.domain.PositionDefaultRole;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.PositionDefaultRoleRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FR-HRM-06/UC-52: đồng bộ role (user_roles) theo chức vụ (positions) của 1
 * tài khoản — dùng chung cho EmployeeService (UC-08 A5, tạo/sửa hồ sơ) và
 * EmployeeBatchImportService (UC-51 bước 4). Chỉ đụng tới role do CHÍNH cơ
 * chế này từng gán (user_roles.granted_via_position_id != NULL) — role gán
 * tay qua UC-46 không bao giờ bị đụng tới, kể cả khi trùng với 1 role mặc
 * định của chức vụ mới (giữ nguyên trạng thái "gán tay", không "nhận nuôi").
 */
@Service
public class PositionRoleSyncService {

    private final PositionDefaultRoleRepository positionDefaultRoleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;

    public PositionRoleSyncService(PositionDefaultRoleRepository positionDefaultRoleRepository,
                                    UserRoleRepository userRoleRepository,
                                    UserRepository userRepository) {
        this.positionDefaultRoleRepository = positionDefaultRoleRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
    }

    /**
     * oldPositionId: chức vụ TRƯỚC thay đổi (NULL nếu vừa tạo hồ sơ hoặc
     * hồ sơ chưa từng có chức vụ). newPosition: chức vụ SAU thay đổi (NULL
     * = bỏ trống chức vụ — chỉ thu hồi, không gán thêm gì).
     */
    @Transactional
    public void sync(User user, Long oldPositionId, Position newPosition, Long actorUserId) {
        List<PositionDefaultRole> newDefaults = newPosition == null
                ? List.of()
                : positionDefaultRoleRepository.findByPositionId(newPosition.getId());
        Set<Long> newDefaultRoleIds = newDefaults.stream()
                .map(pdr -> pdr.getRole().getId())
                .collect(Collectors.toSet());

        List<UserRole> existingRoles = userRoleRepository.findByUserId(user.getId());
        Map<Long, UserRole> existingByRoleId = existingRoles.stream()
                .collect(Collectors.toMap(ur -> ur.getRole().getId(), ur -> ur, (a, b) -> a));

        // Thu hồi role hệ thống từng tự gán theo CHỨC VỤ CŨ mà chức vụ mới không còn liệt kê.
        if (oldPositionId != null) {
            for (UserRole ur : existingRoles) {
                Position grantedVia = ur.getGrantedViaPosition();
                if (grantedVia != null && grantedVia.getId().equals(oldPositionId)
                        && !newDefaultRoleIds.contains(ur.getRole().getId())) {
                    userRoleRepository.delete(ur);
                }
            }
        }

        if (newDefaults.isEmpty()) {
            return;
        }
        User actor = getUserOrThrow(actorUserId);

        for (PositionDefaultRole pdr : newDefaults) {
            Long roleId = pdr.getRole().getId();
            UserRole existing = existingByRoleId.get(roleId);
            if (existing == null) {
                UserRole newRole = new UserRole();
                newRole.setUser(user);
                newRole.setRole(pdr.getRole());
                newRole.setAssignedBy(actor);
                newRole.setGrantedViaPosition(newPosition);
                userRoleRepository.save(newRole);
            } else if (existing.getGrantedViaPosition() != null) {
                // Vẫn là role do vị trí quản lý (chỉ đổi chức vụ trỏ tới) -- cập nhật để lần đồng bộ sau xử lý đúng.
                existing.setGrantedViaPosition(newPosition);
                userRoleRepository.save(existing);
            }
            // else: role đã tồn tại do gán tay (grantedViaPosition == null) -- giữ nguyên, không "nhận nuôi".
        }
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("error.positionRoleSync.userNotFound", new Object[]{userId}, "Không tìm thấy tài khoản id=" + userId));
    }
}
