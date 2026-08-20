package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Position;
import vn.com.pps.education.domain.PositionDefaultRole;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.dto.CreatePositionRequest;
import vn.com.pps.education.dto.PositionDefaultRolesResponse;
import vn.com.pps.education.dto.PositionResponse;
import vn.com.pps.education.dto.RoleResponse;
import vn.com.pps.education.dto.UpdatePositionDefaultRolesRequest;
import vn.com.pps.education.dto.UpdatePositionRequest;
import vn.com.pps.education.exception.DuplicatePositionCodeException;
import vn.com.pps.education.exception.PositionNotDeletableException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.domain.Employee;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.PositionDefaultRoleRepository;
import vn.com.pps.education.repository.PositionRepository;
import vn.com.pps.education.repository.RoleRepository;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FR-HRM-06/UC-52: danh mục Chức vụ + ánh xạ role mặc định theo chức vụ.
 * Bổ sung ngoài UC cụ thể (tương tự RoleService#createRole/#deleteRole) —
 * xem docs/uc/phan-he-04-nhan-su.md. CRUD ở đây phục vụ dropdown chọn chức
 * vụ khi tạo/sửa hồ sơ nhân sự (UC-08) và cấu hình role mặc định để
 * PositionRoleSyncService tự động gán khi hồ sơ được gán chức vụ đó.
 */
@Service
public class PositionService {

    private final PositionRepository positionRepository;
    private final PositionDefaultRoleRepository positionDefaultRoleRepository;
    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;
    private final PositionRoleSyncService positionRoleSyncService;

    public PositionService(PositionRepository positionRepository,
                            PositionDefaultRoleRepository positionDefaultRoleRepository,
                            RoleRepository roleRepository,
                            EmployeeRepository employeeRepository,
                            PositionRoleSyncService positionRoleSyncService) {
        this.positionRepository = positionRepository;
        this.positionDefaultRoleRepository = positionDefaultRoleRepository;
        this.roleRepository = roleRepository;
        this.employeeRepository = employeeRepository;
        this.positionRoleSyncService = positionRoleSyncService;
    }

    @Transactional
    public PositionResponse create(CreatePositionRequest request) {
        if (positionRepository.findByCode(request.code()).isPresent()) {
            throw new DuplicatePositionCodeException("Mã chức vụ đã tồn tại: " + request.code());
        }
        Position position = new Position();
        position.setCode(request.code());
        position.setName(request.name());
        position = positionRepository.save(position);
        return toResponse(position);
    }

    @Transactional
    public PositionResponse update(Long id, UpdatePositionRequest request) {
        Position position = getPositionOrThrow(id);
        position.setName(request.name());
        position = positionRepository.save(position);
        return toResponse(position);
    }

    @Transactional(readOnly = true)
    public PositionResponse getById(Long id) {
        return toResponse(getPositionOrThrow(id));
    }

    /** Phục vụ dropdown chọn chức vụ (VD tạo nhân sự UC-08) — sắp theo tên cho dễ chọn. */
    @Transactional(readOnly = true)
    public List<PositionResponse> list() {
        return positionRepository.findAll().stream()
                .sorted(Comparator.comparing(Position::getName))
                .map(this::toResponse)
                .toList();
    }

    /** UC-52 A1 — chặn xóa nếu đang có nhân sự mang chức vụ này (employees.position_id, FK RESTRICT mặc định). */
    @Transactional
    public void delete(Long id) {
        Position position = getPositionOrThrow(id);
        if (employeeRepository.existsByPositionId(id)) {
            throw new PositionNotDeletableException(
                    "Chức vụ '" + position.getCode() + "' đang được gán cho nhân sự — chuyển nhân sự sang chức vụ khác trước khi xóa.");
        }
        positionDefaultRoleRepository.deleteAll(positionDefaultRoleRepository.findByPositionId(id));
        positionRepository.delete(position);
    }

    @Transactional(readOnly = true)
    public PositionDefaultRolesResponse getDefaultRoles(Long positionId) {
        Position position = getPositionOrThrow(positionId);
        List<RoleResponse> roles = positionDefaultRoleRepository.findByPositionId(positionId).stream()
                .map(PositionDefaultRole::getRole)
                .sorted(Comparator.comparing(Role::getCode))
                .map(this::toResponse)
                .toList();
        return new PositionDefaultRolesResponse(position.getId(), position.getCode(), roles);
    }

    /**
     * UC-52 bước 2-5: thay thế TOÀN BỘ danh sách role mặc định của 1 chức vụ
     * (diff add/remove, giống RoleService#updatePermissions), sau đó backfill
     * ngay vai trò cho mọi nhân sự đang giữ chức vụ này — không chỉ áp dụng
     * cho các lần gán chức vụ sau này (dùng lại PositionRoleSyncService, cùng
     * cơ chế UC-08 A5).
     */
    @Transactional
    public void updateDefaultRoles(Long positionId, UpdatePositionDefaultRolesRequest request, Long actorUserId) {
        Position position = getPositionOrThrow(positionId);
        List<PositionDefaultRole> current = positionDefaultRoleRepository.findByPositionId(positionId);
        Set<Long> currentRoleIds = current.stream().map(pdr -> pdr.getRole().getId()).collect(Collectors.toSet());
        Set<Long> requestedRoleIds = new HashSet<>(request.roleIds());

        List<PositionDefaultRole> toRemove = current.stream()
                .filter(pdr -> !requestedRoleIds.contains(pdr.getRole().getId()))
                .toList();
        positionDefaultRoleRepository.deleteAll(toRemove);

        Set<Long> toAdd = new HashSet<>(requestedRoleIds);
        toAdd.removeAll(currentRoleIds);
        List<PositionDefaultRole> newRows = toAdd.stream().map(roleId -> {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.position.roleNotFound", new Object[]{roleId}, "Không tìm thấy role id=" + roleId));
            PositionDefaultRole pdr = new PositionDefaultRole();
            pdr.setPosition(position);
            pdr.setRole(role);
            return pdr;
        }).toList();
        positionDefaultRoleRepository.saveAll(newRows);

        List<Employee> holders = employeeRepository.findByPositionIdAndDeletedAtIsNull(positionId);
        for (Employee employee : holders) {
            positionRoleSyncService.sync(employee.getUser(), positionId, position, actorUserId);
        }
    }

    private Position getPositionOrThrow(Long id) {
        return positionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.position.notFoundById", new Object[]{id}, "Không tìm thấy chức vụ id=" + id));
    }

    private PositionResponse toResponse(Position position) {
        return new PositionResponse(position.getId(), position.getCode(), position.getName());
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isSystem());
    }
}
