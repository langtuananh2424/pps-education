package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Permission;
import vn.com.pps.education.domain.RolePermission;
import vn.com.pps.education.domain.UserPermissionOverride;
import vn.com.pps.education.dto.CreatePermissionRequest;
import vn.com.pps.education.dto.PermissionResponse;
import vn.com.pps.education.dto.UpdatePermissionRequest;
import vn.com.pps.education.exception.DuplicatePermissionCodeException;
import vn.com.pps.education.exception.PermissionInUseException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.PermissionRepository;
import vn.com.pps.education.repository.RolePermissionRepository;
import vn.com.pps.education.repository.UserPermissionOverrideRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-02: Quản lý danh mục quyền (FR-PER-01).
 * Xem docs/uc/phan-he-02-phan-quyen.md — Main Flow, A1 (trùng code), A2 (xóa quyền đang dùng).
 */
@Service
public class PermissionService {

    private static final Set<String> VALID_MODULES = Set.of(
            "AUTH", "USER", "TASK", "HRM", "STUDENT", "ACADEMIC", "LMS", "FINANCE", "CRM", "FACILITY");

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;

    public PermissionService(PermissionRepository permissionRepository,
                              RolePermissionRepository rolePermissionRepository,
                              UserPermissionOverrideRepository userPermissionOverrideRepository) {
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userPermissionOverrideRepository = userPermissionOverrideRepository;
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listAll() {
        return permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(Permission::getModule).thenComparing(Permission::getCode))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PermissionResponse create(CreatePermissionRequest request) {
        if (!VALID_MODULES.contains(request.module())) {
            throw new IllegalArgumentException("module không hợp lệ: " + request.module());
        }
        // A1 -- kiểm tra trùng code trước khi insert, tránh lộ raw DataIntegrityViolationException
        if (permissionRepository.findByCode(request.code()).isPresent()) {
            throw new DuplicatePermissionCodeException("Mã quyền đã tồn tại: " + request.code());
        }

        Permission permission = new Permission();
        permission.setCode(request.code());
        permission.setName(request.name());
        permission.setModule(request.module());
        permission.setDescription(request.description());
        return toResponse(permissionRepository.save(permission));
    }

    @Transactional
    public PermissionResponse update(Long id, UpdatePermissionRequest request) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.permission.notFoundById",
                        new Object[]{id}, "Không tìm thấy quyền id=" + id));
        // code bất biến khi đã tồn tại -- không nằm trong UpdatePermissionRequest
        permission.setName(request.name());
        permission.setDescription(request.description());
        return toResponse(permissionRepository.save(permission));
    }

    @Transactional
    public void delete(Long id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.permission.notFoundById",
                        new Object[]{id}, "Không tìm thấy quyền id=" + id));

        // A2 -- không xóa quyền đang được role hoặc tài khoản tham chiếu
        List<RolePermission> referencingRoles = rolePermissionRepository.findByPermissionId(id);
        List<UserPermissionOverride> referencingOverrides = userPermissionOverrideRepository.findByPermissionId(id);
        if (!referencingRoles.isEmpty() || !referencingOverrides.isEmpty()) {
            String roleCodes = referencingRoles.stream().map(rp -> rp.getRole().getCode())
                    .collect(Collectors.joining(", "));
            String usernames = referencingOverrides.stream().map(o -> o.getUser().getUsername())
                    .collect(Collectors.joining(", "));
            throw new PermissionInUseException(
                    "Không thể xóa quyền '%s' vì đang được sử dụng. Role: [%s]. Tài khoản: [%s]."
                            .formatted(permission.getCode(), roleCodes, usernames));
        }

        permissionRepository.delete(permission);
    }

    private PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getCode(), permission.getName(),
                permission.getModule(), permission.getDescription());
    }
}
