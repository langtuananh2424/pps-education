package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Department;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateDepartmentRequest;
import vn.com.pps.education.dto.DepartmentResponse;
import vn.com.pps.education.dto.UpdateDepartmentRequest;
import vn.com.pps.education.exception.DepartmentNotDeletableException;
import vn.com.pps.education.exception.DuplicateDepartmentCodeException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.DepartmentRepository;
import vn.com.pps.education.repository.EmployeeRepository;
import vn.com.pps.education.repository.TaskRepository;
import vn.com.pps.education.repository.UserRepository;

import java.util.Comparator;
import java.util.List;

/**
 * Bổ sung ngoài UC cụ thể (tương tự RoleService#createRole/#deleteRole) —
 * phòng ban là danh mục cấu hình tĩnh dùng chung
 * (docs/sdd-groups/02-nen-tang.md > a: "Không soft-delete, không history"),
 * ngầm định bởi FR-HRM-03 (duyệt đơn 2 cấp) và FR-TSK-01 (giao việc theo
 * phòng ban). CRUD ở đây phục vụ dropdown chọn phòng ban khi tạo/sửa nhân
 * sự (UC-08) và các luồng khác tham chiếu departments — trước đây chưa có
 * endpoint quản lý riêng.
 */
@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final TaskRepository taskRepository;

    public DepartmentService(DepartmentRepository departmentRepository,
                              UserRepository userRepository,
                              EmployeeRepository employeeRepository,
                              TaskRepository taskRepository) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request) {
        if (departmentRepository.findByCode(request.code()).isPresent()) {
            throw new DuplicateDepartmentCodeException("Mã phòng ban đã tồn tại: " + request.code());
        }

        Department department = new Department();
        department.setCode(request.code());
        department.setName(request.name());
        department.setHeadUser(resolveHeadUser(request.headUserId()));
        department.setParentDepartment(resolveParentDepartment(request.parentDepartmentId(), null));
        department = departmentRepository.save(department);
        return toResponse(department);
    }

    @Transactional
    public DepartmentResponse update(Long id, UpdateDepartmentRequest request) {
        Department department = getDepartmentOrThrow(id);
        department.setName(request.name());
        department.setHeadUser(resolveHeadUser(request.headUserId()));
        department.setParentDepartment(resolveParentDepartment(request.parentDepartmentId(), id));
        department = departmentRepository.save(department);
        return toResponse(department);
    }

    @Transactional(readOnly = true)
    public DepartmentResponse getById(Long id) {
        return toResponse(getDepartmentOrThrow(id));
    }

    /** Phục vụ dropdown chọn phòng ban (VD tạo nhân sự UC-08) — sắp theo tên cho dễ chọn. */
    @Transactional(readOnly = true)
    public List<DepartmentResponse> list() {
        return departmentRepository.findAll().stream()
                .sorted(Comparator.comparing(Department::getName))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Hard-delete (SDD: "Không soft-delete, không history" — danh mục cấu
     * hình tĩnh). Chặn nếu đang bị tham chiếu: nhân sự
     * (employees.department_id), công việc (tasks.department_id —
     * FR-TSK-01), hoặc là phòng ban cha của phòng ban khác
     * (parent_department_id) — cả 3 đều FK ràng buộc mặc định (RESTRICT),
     * xóa thẳng sẽ vỡ toàn vẹn dữ liệu.
     */
    @Transactional
    public void delete(Long id) {
        Department department = getDepartmentOrThrow(id);
        if (employeeRepository.existsByDepartmentId(id)) {
            throw new DepartmentNotDeletableException(
                    "Phòng ban '" + department.getCode() + "' đang có nhân sự trực thuộc — chuyển nhân sự sang phòng ban khác trước khi xóa.");
        }
        if (taskRepository.existsByDepartmentId(id)) {
            throw new DepartmentNotDeletableException(
                    "Phòng ban '" + department.getCode() + "' đang được gắn với công việc — không thể xóa.");
        }
        if (departmentRepository.existsByParentDepartmentId(id)) {
            throw new DepartmentNotDeletableException(
                    "Phòng ban '" + department.getCode() + "' đang là phòng ban cha của phòng ban khác — không thể xóa.");
        }
        departmentRepository.delete(department);
    }

    // ===================== Helpers =====================

    private User resolveHeadUser(Long headUserId) {
        if (headUserId == null) {
            return null;
        }
        return userRepository.findById(headUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.department.headUserNotFound", new Object[]{headUserId}, "Không tìm thấy tài khoản id=" + headUserId));
    }

    private Department resolveParentDepartment(Long parentDepartmentId, Long selfId) {
        if (parentDepartmentId == null) {
            return null;
        }
        if (parentDepartmentId.equals(selfId)) {
            throw new IllegalArgumentException("Phòng ban không thể là phòng ban cha của chính nó.");
        }
        return departmentRepository.findById(parentDepartmentId)
                .orElseThrow(() -> new ResourceNotFoundException("error.department.parentNotFound", new Object[]{parentDepartmentId}, "Không tìm thấy phòng ban cha id=" + parentDepartmentId));
    }

    private Department getDepartmentOrThrow(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.department.notFoundById", new Object[]{id}, "Không tìm thấy phòng ban id=" + id));
    }

    private DepartmentResponse toResponse(Department department) {
        User headUser = department.getHeadUser();
        Department parent = department.getParentDepartment();
        return new DepartmentResponse(
                department.getId(),
                department.getCode(),
                department.getName(),
                headUser == null ? null : headUser.getId(),
                headUser == null ? null : headUser.getFullName(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getName()
        );
    }
}
