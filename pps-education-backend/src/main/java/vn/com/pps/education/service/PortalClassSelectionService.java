package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.dto.PortalClassOptionResponse;
import vn.com.pps.education.exception.NotAuthorizedForPortalAccessException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-42: Chọn lớp đang xem (Portal Học sinh/Phụ huynh, FR-LMS-12).
 * Xem docs/uc/phan-he-07-lms-portal.md — Main Flow bước 1-3, A1 (chưa
 * từng xếp lớp), A2 (không còn lớp ACTIVE).
 *
 * Chỉ trả về danh sách + gợi ý mục nên pre-select (Main Flow bước 1-3) —
 * KHÔNG lưu "ngữ cảnh phiên" phía server (bước 5-6): kiến trúc hiện tại là
 * REST/JWT stateless, không có session storage, và chưa có UC nào khác
 * (UC-23a/24/26/27/25a/25b — Phân hệ 7) tồn tại để đọc lại lựa chọn đó.
 * Frontend tự giữ classEnrollmentId đã chọn, truyền lại cho các API Portal
 * sau này khi những UC đó được triển khai — không tự sáng tác cơ chế lưu
 * trạng thái phía server khi chưa có bên tiêu thụ.
 *
 * A3 (phụ huynh đổi con xem) không cần code riêng — endpoint đã nhận
 * studentId tường minh mỗi lần gọi, tự nhiên "làm lại UC từ đầu" cho con
 * khác mà không cần dọn state cũ.
 */
@Service
public class PortalClassSelectionService {

    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final UserRoleRepository userRoleRepository;

    public PortalClassSelectionService(ClassEnrollmentRepository classEnrollmentRepository,
                                        StudentRepository studentRepository,
                                        ParentRepository parentRepository,
                                        ParentStudentRepository parentStudentRepository,
                                        UserRoleRepository userRoleRepository) {
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.parentRepository = parentRepository;
        this.parentStudentRepository = parentStudentRepository;
        this.userRoleRepository = userRoleRepository;
    }

    /** Main Flow bước 1-3, A1, A2. */
    @Transactional(readOnly = true)
    public List<PortalClassOptionResponse> listClassOptions(Long studentId, Long actorUserId) {
        requireOwnerOrLinkedParent(studentId, actorUserId);

        List<ClassEnrollment> enrollments = classEnrollmentRepository.findByStudentId(studentId).stream()
                .sorted(Comparator.comparing(ClassEnrollment::getEnrolledDate).reversed())
                .toList();

        // A1 -- chưa từng được xếp lớp: danh sách rỗng, Postcondition không cần xử lý gì thêm.
        if (enrollments.isEmpty()) {
            return List.of();
        }

        // Main Flow bước 3 / A2: pre-select lớp ACTIVE gần nhất (enrolled_date mới nhất trong
        // các bản ghi ACTIVE); nếu không còn lớp nào ACTIVE, pre-select bản ghi enrolled_date
        // gần nhất trong toàn bộ danh sách (đã sort giảm dần -> phần tử đầu tiên).
        ClassEnrollment recommended = enrollments.stream()
                .filter(e -> e.getStatus() == ClassEnrollment.Status.ACTIVE
                        && e.getSchoolClass().getStatus() != SchoolClass.Status.CANCELLED)
                .findFirst()
                .orElse(enrollments.get(0));

        return enrollments.stream().map(e -> toResponse(e, e.getId().equals(recommended.getId()))).toList();
    }

    private void requireOwnerOrLinkedParent(Long studentId, Long actorUserId) {
        if (!studentRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("error.portalClassSelection.studentNotFound", new Object[]{studentId}, "Không tìm thấy học sinh id=" + studentId);
        }
        Set<String> roleCodes = roleCodesOf(actorUserId);

        if (roleCodes.contains("STUDENT")) {
            Student self = studentRepository.findByUserId(actorUserId).orElse(null);
            if (self != null && self.getId().equals(studentId)) {
                return;
            }
        }
        if (roleCodes.contains("PARENT")) {
            Parent parent = parentRepository.findByUserId(actorUserId).orElse(null);
            if (parent != null && parentStudentRepository.findByParentIdAndStudentId(parent.getId(), studentId).isPresent()) {
                return;
            }
        }
        throw new NotAuthorizedForPortalAccessException(
                "Tài khoản của bạn không phải học sinh này hoặc phụ huynh liên kết với học sinh này.");
    }

    private Set<String> roleCodesOf(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toSet());
    }

    private PortalClassOptionResponse toResponse(ClassEnrollment e, boolean recommended) {
        return new PortalClassOptionResponse(
                e.getId(), e.getSchoolClass().getId(), e.getSchoolClass().getName(), e.getSchoolClass().getClassCode(),
                e.getEnrolledDate(), e.getWithdrawnDate(), e.getStatus().name(), recommended,
                e.getSchoolClass().getSite().getId());
    }
}
