package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.StudentStatusHistory;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.StudentStatusHistoryResponse;
import vn.com.pps.education.dto.UpdateStudentStatusRequest;
import vn.com.pps.education.exception.InvalidStudentStatusTransitionException;
import vn.com.pps.education.exception.NotAuthorizedForStudentStatusException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.StudentStatusHistoryRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-14: Cập nhật trạng thái học tập (FR-STU-02).
 * Xem docs/uc/phan-he-05-hoc-sinh.md — Main Flow bước 1-5, A1 (chuyển trạng
 * thái không hợp lệ).
 *
 * Tách riêng khỏi StudentService (UC-13) — 2 UC khác actor chính (Nhân viên
 * Giáo vụ vs Quản lý điểm trường), khác lý do thay đổi (SOLID S — xem
 * .claude/rules/solid.md, giống cách AuthService/PermissionService tách
 * theo UC dù cùng Phân hệ).
 *
 * Không có @PreAuthorize permission code riêng — Precondition UC-14 không
 * nêu permission code (chỉ nêu actor "Quản lý điểm trường"), theo đúng
 * pattern LeaveRequestService (role-based check trong Service, không dùng
 * hasPermission()).
 */
@Service
public class StudentStatusService {

    private static final Set<String> AUTHORIZED_ROLE_CODES = Set.of("SITE_MANAGER", "STAFF");

    private final StudentRepository studentRepository;
    private final StudentStatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public StudentStatusService(StudentRepository studentRepository,
                                 StudentStatusHistoryRepository statusHistoryRepository,
                                 UserRepository userRepository,
                                 UserRoleRepository userRoleRepository) {
        this.studentRepository = studentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    /**
     * Main Flow bước 1-5, A1. GRADUATED→ACTIVE luôn bị chặn (chưa có cơ chế
     * "xác nhận đặc biệt" — xác nhận với user khi implement UC-14, chờ đặc
     * tả chi tiết hơn từ PM trước khi mở lại).
     */
    @Transactional
    public StudentStatusHistoryResponse updateStatus(Long studentId, UpdateStudentStatusRequest request, Long actorUserId) {
        Set<String> roleCodes = roleCodesOf(actorUserId);
        if (roleCodes.stream().noneMatch(AUTHORIZED_ROLE_CODES::contains)) {
            throw new NotAuthorizedForStudentStatusException(
                    "Tài khoản id=" + actorUserId + " không có vai trò SITE_MANAGER/STAFF để cập nhật trạng thái học tập.");
        }

        Student student = studentRepository.findByIdAndDeletedAtIsNull(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh id=" + studentId));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        Student.Status oldStatus = student.getStatus();
        Student.Status newStatus = Student.Status.valueOf(request.newStatus());

        if (oldStatus == newStatus) {
            throw new InvalidStudentStatusTransitionException(
                    "Trạng thái mới trùng với trạng thái hiện tại (" + oldStatus + ").");
        }
        // A1: chặn GRADUATED->ACTIVE, chưa có cơ chế xác nhận đặc biệt.
        if (oldStatus == Student.Status.GRADUATED && newStatus == Student.Status.ACTIVE) {
            throw new InvalidStudentStatusTransitionException(
                    "Không thể chuyển thẳng từ GRADUATED về ACTIVE mà không có xác nhận đặc biệt.");
        }

        student.setStatus(newStatus);
        if (newStatus == Student.Status.GRADUATED) {
            student.setGraduationDate(request.effectiveDate());
        }
        studentRepository.save(student);

        StudentStatusHistory history = new StudentStatusHistory();
        history.setStudent(student);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setReason(request.reason());
        history.setEffectiveDate(request.effectiveDate());
        history.setChangedBy(actor);
        history = statusHistoryRepository.save(history);

        return toResponse(history);
    }

    @Transactional(readOnly = true)
    public List<StudentStatusHistoryResponse> listStatusHistory(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Không tìm thấy học sinh id=" + studentId);
        }
        return statusHistoryRepository.findByStudentIdOrderByChangedAtDesc(studentId).stream()
                .map(this::toResponse)
                .toList();
    }

    private Set<String> roleCodesOf(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toSet());
    }

    private StudentStatusHistoryResponse toResponse(StudentStatusHistory h) {
        return new StudentStatusHistoryResponse(
                h.getId(), h.getStudent().getId(),
                h.getOldStatus() == null ? null : h.getOldStatus().name(),
                h.getNewStatus().name(), h.getReason(), h.getEffectiveDate(),
                h.getChangedBy().getId(), h.getChangedAt());
    }
}
