package vn.com.pps.education.hr.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.hr.domain.Department;
import vn.com.pps.education.hr.domain.Employee;
import vn.com.pps.education.hr.domain.EmployeeHistory;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.hr.dto.AddDepartmentMembersRequest;
import vn.com.pps.education.hr.dto.CreateDepartmentRequest;
import vn.com.pps.education.hr.dto.DepartmentMemberResponse;
import vn.com.pps.education.hr.dto.DepartmentResponse;
import vn.com.pps.education.hr.dto.UpdateDepartmentRequest;
import vn.com.pps.education.exception.DepartmentNotDeletableException;
import vn.com.pps.education.exception.DuplicateDepartmentCodeException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.hr.repository.DepartmentRepository;
import vn.com.pps.education.hr.repository.EmployeeHistoryRepository;
import vn.com.pps.education.hr.repository.EmployeeRepository;
import vn.com.pps.education.task.repository.TaskRepository;
import vn.com.pps.education.auth.repository.UserRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final EmployeeHistoryRepository employeeHistoryRepository;

    /** Số ứng viên tối đa trả về mỗi lần tìm khi thêm thành viên — đủ cho ô tìm kiếm, tránh trả toàn bộ nhân sự. */
    private static final int MEMBER_CANDIDATE_LIMIT = 50;

    public DepartmentService(DepartmentRepository departmentRepository,
                              UserRepository userRepository,
                              EmployeeRepository employeeRepository,
                              TaskRepository taskRepository,
                              EmployeeHistoryRepository employeeHistoryRepository) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.taskRepository = taskRepository;
        this.employeeHistoryRepository = employeeHistoryRepository;
    }

    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request) {
        if (departmentRepository.findByCode(request.code()).isPresent()) {
            throw new DuplicateDepartmentCodeException("error.duplicateDepartmentCode.default", new Object[]{request.code()},
                    "Mã phòng ban đã tồn tại: " + request.code());
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
            throw new DepartmentNotDeletableException("error.departmentNotDeletable.hasEmployees", new Object[]{department.getCode()},
                    "Phòng ban '" + department.getCode() + "' đang có nhân sự trực thuộc — chuyển nhân sự sang phòng ban khác trước khi xóa.");
        }
        if (taskRepository.existsByDepartmentId(id)) {
            throw new DepartmentNotDeletableException("error.departmentNotDeletable.hasTasks", new Object[]{department.getCode()},
                    "Phòng ban '" + department.getCode() + "' đang được gắn với công việc — không thể xóa.");
        }
        if (departmentRepository.existsByParentDepartmentId(id)) {
            throw new DepartmentNotDeletableException("error.departmentNotDeletable.hasChildDepartments", new Object[]{department.getCode()},
                    "Phòng ban '" + department.getCode() + "' đang là phòng ban cha của phòng ban khác — không thể xóa.");
        }
        departmentRepository.delete(department);
    }

    // ===================== Thành viên phòng ban =====================
    // Bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-10-01: trước đây chỉ gán được phòng ban từng người
    // trong Hồ sơ cán bộ (UC-08). Thành viên vẫn là employees.department_id — cùng cột Hồ sơ cán bộ, tổng quan
    // công việc (TaskService) và lịch sử thay đổi theo phòng ban (ChangeHistoryService) đang dùng.

    @Transactional(readOnly = true)
    public List<DepartmentMemberResponse> listMembers(Long id) {
        getDepartmentOrThrow(id);
        return employeeRepository.findByDepartmentIdAndDeletedAtIsNull(id).stream()
                .sorted(Comparator.comparing(e -> e.getUser().getFullName()))
                .map(this::toMemberResponse)
                .toList();
    }

    /** Nhân sự chưa thuộc phòng ban này, khớp tên/mã nhân sự (để trống = những người đầu danh sách). */
    @Transactional(readOnly = true)
    public List<DepartmentMemberResponse> searchMemberCandidates(Long id, String query) {
        getDepartmentOrThrow(id);
        List<Employee> employees = query == null || query.isBlank()
                ? employeeRepository.findAllActive(null)
                : employeeRepository.searchByQuery(query.trim(), null);
        return employees.stream()
                .filter(e -> e.getDepartment() == null || !e.getDepartment().getId().equals(id))
                .limit(MEMBER_CANDIDATE_LIMIT)
                .map(this::toMemberResponse)
                .toList();
    }

    /**
     * Thêm nhân sự vào phòng ban — nhân sự đang thuộc phòng khác sẽ chuyển sang phòng này (mỗi nhân sự chỉ
     * thuộc 1 phòng ban, employees.department_id). Mỗi người được chuyển ghi 1 dòng employees_history.
     */
    @Transactional
    public List<DepartmentMemberResponse> addMembers(Long id, AddDepartmentMembersRequest request, Long actorUserId) {
        Department department = getDepartmentOrThrow(id);
        User actor = getActorOrThrow(actorUserId);
        for (Long employeeId : request.employeeIds().stream().distinct().toList()) {
            Employee employee = employeeRepository.findByIdAndDeletedAtIsNull(employeeId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.department.memberNotFound", new Object[]{employeeId},
                            "Không tìm thấy nhân sự id=" + employeeId));
            if (employee.getDepartment() != null && employee.getDepartment().getId().equals(id)) {
                continue;
            }
            employee.setDepartment(department);
            employeeRepository.save(employee);
            writeDepartmentChangeHistory(employee, department, actor);
        }
        return listMembers(id);
    }

    /** Gỡ nhân sự khỏi phòng ban (nhân sự không còn thuộc phòng ban nào, như "bỏ trống phòng ban" ở UC-08 A1). */
    @Transactional
    public void removeMember(Long id, Long employeeId, Long actorUserId) {
        getDepartmentOrThrow(id);
        Employee employee = employeeRepository.findByIdAndDeletedAtIsNull(employeeId)
                .filter(e -> e.getDepartment() != null && e.getDepartment().getId().equals(id))
                .orElseThrow(() -> new ResourceNotFoundException("error.department.memberNotInDepartment", new Object[]{employeeId},
                        "Nhân sự id=" + employeeId + " không thuộc phòng ban này."));
        employee.setDepartment(null);
        employeeRepository.save(employee);
        writeDepartmentChangeHistory(employee, null, getActorOrThrow(actorUserId));
    }

    // ===================== Helpers =====================

    private void writeDepartmentChangeHistory(Employee employee, Department department, User actor) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeCode", employee.getEmployeeCode());
        snapshot.put("departmentName", department == null ? null : department.getName());
        EmployeeHistory history = new EmployeeHistory();
        history.setEmployee(employee);
        history.setChangedBy(actor);
        history.setAction(EmployeeHistory.Action.UPDATED);
        history.setDetails(snapshot);
        employeeHistoryRepository.save(history);
    }

    private User getActorOrThrow(Long actorUserId) {
        return userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.employee.userNotFound", new Object[]{actorUserId},
                        "Không tìm thấy tài khoản id=" + actorUserId));
    }

    private DepartmentMemberResponse toMemberResponse(Employee e) {
        Department department = e.getDepartment();
        return new DepartmentMemberResponse(
                e.getId(),
                e.getUser().getId(),
                e.getEmployeeCode(),
                e.getUser().getFullName(),
                e.getPosition() == null ? null : e.getPosition().getName(),
                e.getEmployeeType().name(),
                e.getStatus().name(),
                department == null ? null : department.getId(),
                department == null ? null : department.getName());
    }

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
