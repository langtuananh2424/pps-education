package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.TuitionPlan;
import vn.com.pps.education.domain.TuitionPlanAssignment;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AssignTuitionPlanRequest;
import vn.com.pps.education.dto.CreateTuitionPlanRequest;
import vn.com.pps.education.dto.TuitionPlanAssignmentResponse;
import vn.com.pps.education.dto.TuitionPlanResponse;
import vn.com.pps.education.dto.UpdateTuitionPlanStatusRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.TuitionPlanNotActiveException;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.TuitionPlanAssignmentRepository;
import vn.com.pps.education.repository.TuitionPlanRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.LocalDate;

/**
 * Định mức học phí (SDD > Tài chính & Học phí > Định mức học phí) — hạ
 * tầng bắt buộc để {@link InvoiceService} sinh hóa đơn được (UC-30 Main
 * Flow bước 1). Không có UC nào trong docs/uc/ mô tả riêng luồng tạo/gán
 * định mức phí; gate dưới cùng quyền finance.manage như UC-31 (Precondition
 * "STAFF bộ phận Kế toán, quyền finance.manage") vì cùng thuộc phạm vi
 * Phân hệ 8 — đã xác nhận với user thay vì tự đoán actor khác.
 *
 * Bổ sung: updateStatus (ACTIVE/INACTIVE) — status tồn tại sẵn trong SDD
 * nhưng trước đây không endpoint nào set được; assignToClass nay chặn
 * gán plan INACTIVE (xem TuitionPlanNotActiveException).
 */
@Service
public class TuitionPlanService {

    private final TuitionPlanRepository tuitionPlanRepository;
    private final TuitionPlanAssignmentRepository tuitionPlanAssignmentRepository;
    private final CurriculumRepository curriculumRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final UserRepository userRepository;

    public TuitionPlanService(TuitionPlanRepository tuitionPlanRepository,
                               TuitionPlanAssignmentRepository tuitionPlanAssignmentRepository,
                               CurriculumRepository curriculumRepository,
                               SchoolClassRepository schoolClassRepository,
                               UserRepository userRepository) {
        this.tuitionPlanRepository = tuitionPlanRepository;
        this.tuitionPlanAssignmentRepository = tuitionPlanAssignmentRepository;
        this.curriculumRepository = curriculumRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TuitionPlanResponse createPlan(CreateTuitionPlanRequest request, Long actorUserId) {
        Curriculum curriculum = curriculumRepository.findByIdAndDeletedAtIsNull(request.curriculumId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung chương trình id=" + request.curriculumId()));
        User actor = getUserOrThrow(actorUserId);

        TuitionPlan plan = new TuitionPlan();
        plan.setCurriculum(curriculum);
        plan.setCode(request.code());
        plan.setName(request.name());
        plan.setPricingModel(TuitionPlan.PricingModel.valueOf(request.pricingModel()));
        if (request.classTypeFilter() != null) {
            plan.setClassTypeFilter(TuitionPlan.ClassTypeFilter.valueOf(request.classTypeFilter()));
        }
        plan.setBasePrice(request.basePrice());
        plan.setPricePerUnit(request.pricePerUnit());
        plan.setUnitCount(request.unitCount());
        plan.setEffectiveFrom(request.effectiveFrom());
        plan.setEffectiveTo(request.effectiveTo());
        plan.setCreatedBy(actor);
        return toResponse(tuitionPlanRepository.save(plan));
    }

    @Transactional
    public TuitionPlanAssignmentResponse assignToClass(AssignTuitionPlanRequest request, Long actorUserId) {
        SchoolClass schoolClass = schoolClassRepository.findByIdAndDeletedAtIsNull(request.classId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp id=" + request.classId()));
        if (schoolClass.getStatus() == SchoolClass.Status.CANCELLED) {
            throw new IllegalStateException("Lớp học \"" + schoolClass.getName() + "\" đã bị HỦY — không thể gán định mức phí.");
        }
        TuitionPlan plan = tuitionPlanRepository.findById(request.tuitionPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy định mức phí id=" + request.tuitionPlanId()));
        if (plan.getStatus() != TuitionPlan.Status.ACTIVE) {
            throw new TuitionPlanNotActiveException(
                    "Định mức phí id=" + plan.getId() + " đã INACTIVE, không thể gán cho lớp mới.");
        }
        User actor = getUserOrThrow(actorUserId);

        tuitionPlanAssignmentRepository.findBySchoolClassIdAndEffectiveToIsNull(request.classId())
                .ifPresent(existing -> {
                    existing.setEffectiveTo(LocalDate.now());
                    tuitionPlanAssignmentRepository.saveAndFlush(existing);
                });

        TuitionPlanAssignment assignment = new TuitionPlanAssignment();
        assignment.setSchoolClass(schoolClass);
        assignment.setTuitionPlan(plan);
        assignment.setPriceOverride(request.priceOverride());
        assignment.setOverrideReason(request.overrideReason());
        assignment.setEffectiveFrom(request.effectiveFrom() != null ? request.effectiveFrom() : LocalDate.now());
        assignment.setAssignedBy(actor);
        return toResponse(tuitionPlanAssignmentRepository.save(assignment));
    }

    @Transactional(readOnly = true)
    public TuitionPlanResponse getPlan(Long id) {
        return toResponse(tuitionPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy định mức phí id=" + id)));
    }

    /**
     * Bổ sung — chuyển ACTIVE/INACTIVE. status trong SDD tồn tại sẵn
     * nhưng chưa từng có endpoint nào set được; dùng để đóng plan cũ khi
     * đã tạo plan mới thay thế (SDD: "thay đổi plan tạo record mới thay
     * vì sửa"), tránh gán nhầm plan lỗi thời cho lớp mới.
     */
    @Transactional
    public TuitionPlanResponse updateStatus(Long id, UpdateTuitionPlanStatusRequest request, Long actorUserId) {
        TuitionPlan plan = tuitionPlanRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy định mức phí id=" + id));
        plan.setStatus(TuitionPlan.Status.valueOf(request.status()));
        plan = tuitionPlanRepository.save(plan);
        return toResponse(plan);
    }

    // ===================== Helpers =====================

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user id=" + userId));
    }

    private TuitionPlanResponse toResponse(TuitionPlan p) {
        return new TuitionPlanResponse(
                p.getId(), p.getCode(), p.getName(), p.getCurriculum().getId(),
                p.getPricingModel().name(), p.getClassTypeFilter() == null ? null : p.getClassTypeFilter().name(),
                p.getBasePrice(), p.getPricePerUnit(), p.getUnitCount(), p.getCurrency(),
                p.getEffectiveFrom(), p.getEffectiveTo(), p.getStatus().name());
    }

    private TuitionPlanAssignmentResponse toResponse(TuitionPlanAssignment a) {
        return new TuitionPlanAssignmentResponse(
                a.getId(), a.getSchoolClass().getId(), a.getTuitionPlan().getId(),
                a.getPriceOverride(), a.getOverrideReason(), a.getEffectiveFrom(), a.getEffectiveTo());
    }
}
