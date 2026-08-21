package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Scholarship;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateScholarshipRequest;
import vn.com.pps.education.dto.ScholarshipResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ScholarshipRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;

/**
 * Học bổng/Miễn giảm (SDD > Tài chính & Học phí > Học bổng/Miễn giảm) —
 * áp dụng ở UC-30 Main Flow bước 1 (A3). Không có UC nào mô tả riêng
 * luồng cấp/duyệt học bổng — không có ApprovalFlow tách rời như UC-19/20
 * hay UC-21/22, nên actor tạo trực tiếp là actor duyệt (approved_by =
 * actor, approved_at = now), gate dưới quyền finance.manage giống
 * {@link TuitionPlanService}.
 */
@Service
public class ScholarshipService {

    private final ScholarshipRepository scholarshipRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    public ScholarshipService(ScholarshipRepository scholarshipRepository,
                               StudentRepository studentRepository,
                               UserRepository userRepository) {
        this.scholarshipRepository = scholarshipRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ScholarshipResponse create(CreateScholarshipRequest request, Long actorUserId) {
        Student student = studentRepository.findByIdAndDeletedAtIsNull(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("error.scholarship.studentNotFound", new Object[]{request.studentId()}, "Không tìm thấy học sinh id=" + request.studentId()));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.scholarship.userNotFound", new Object[]{actorUserId}, "Không tìm thấy user id=" + actorUserId));

        Scholarship scholarship = new Scholarship();
        scholarship.setStudent(student);
        scholarship.setCode(request.code());
        scholarship.setName(request.name());
        scholarship.setDiscountType(Scholarship.DiscountType.valueOf(request.discountType()));
        scholarship.setDiscountValue(request.discountValue());
        if (request.applicableScope() != null) {
            scholarship.setApplicableScope(Scholarship.ApplicableScope.valueOf(request.applicableScope()));
        }
        scholarship.setValidFrom(request.validFrom());
        scholarship.setValidTo(request.validTo());
        scholarship.setMaxAmount(request.maxAmount());
        scholarship.setApprovedBy(actor);
        scholarship.setApprovedAt(OffsetDateTime.now());
        return toResponse(scholarshipRepository.save(scholarship));
    }

    /** Thu hồi học bổng — SDD "thay đổi thì REVOKE record cũ, tạo record mới", không tạo record mới ở đây vì UC không mô tả luồng cấp lại. */
    @Transactional
    public ScholarshipResponse revoke(Long id) {
        Scholarship scholarship = scholarshipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.scholarship.notFoundById", new Object[]{id}, "Không tìm thấy học bổng id=" + id));
        scholarship.setStatus(Scholarship.Status.REVOKED);
        return toResponse(scholarshipRepository.save(scholarship));
    }

    private ScholarshipResponse toResponse(Scholarship s) {
        return new ScholarshipResponse(
                s.getId(), s.getStudent().getId(), s.getCode(), s.getName(),
                s.getDiscountType().name(), s.getDiscountValue(),
                s.getApplicableScope().name(), s.getValidFrom(), s.getValidTo(), s.getMaxAmount(),
                s.getStatus().name(), s.getApprovedBy().getId(), s.getApprovedAt());
    }
}
