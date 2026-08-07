package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicYear;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AcademicYearResponse;
import vn.com.pps.education.dto.CreateAcademicYearRequest;
import vn.com.pps.education.dto.UpdateAcademicYearRequest;
import vn.com.pps.education.exception.DuplicateAcademicYearCodeException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AcademicYearRepository;
import vn.com.pps.education.repository.UserRepository;

import java.util.List;

/**
 * V102 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-07) —
 * "Năm học", danh mục DÙNG CHUNG TOÀN HỆ THỐNG (khác {@link
 * vn.com.pps.education.domain.AcademicTerm} — Kỳ học, giới hạn theo điểm
 * trường). Nguồn FK cho academic_year_id trên classes/grade_entries/
 * student_comments/class_enrollments/teaching_plans.
 */
@Service
public class AcademicYearService {

    private final AcademicYearRepository academicYearRepository;
    private final UserRepository userRepository;

    public AcademicYearService(AcademicYearRepository academicYearRepository, UserRepository userRepository) {
        this.academicYearRepository = academicYearRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AcademicYearResponse create(CreateAcademicYearRequest request, Long actorUserId) {
        if (academicYearRepository.existsByCode(request.code())) {
            throw new DuplicateAcademicYearCodeException("Mã năm học đã tồn tại: " + request.code());
        }
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("endDate phải sau hoặc bằng startDate.");
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        AcademicYear year = new AcademicYear();
        year.setCode(request.code());
        year.setName(request.name());
        year.setStartDate(request.startDate());
        year.setEndDate(request.endDate());
        year.setCreatedBy(actor);
        year = academicYearRepository.save(year);
        return toResponse(year);
    }

    @Transactional
    public AcademicYearResponse update(Long id, UpdateAcademicYearRequest request, Long actorUserId) {
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("endDate phải sau hoặc bằng startDate.");
        }
        AcademicYear year = getOrThrow(id);
        year.setName(request.name());
        year.setStartDate(request.startDate());
        year.setEndDate(request.endDate());
        year.setStatus(AcademicYear.Status.valueOf(request.status()));
        year = academicYearRepository.save(year);
        return toResponse(year);
    }

    @Transactional(readOnly = true)
    public List<AcademicYearResponse> list() {
        return academicYearRepository.findAllByOrderByStartDateDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AcademicYearResponse getById(Long id) {
        return toResponse(getOrThrow(id));
    }

    private AcademicYear getOrThrow(Long id) {
        return academicYearRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy năm học id=" + id));
    }

    private AcademicYearResponse toResponse(AcademicYear y) {
        return new AcademicYearResponse(y.getId(), y.getCode(), y.getName(), y.getStartDate(), y.getEndDate(), y.getStatus().name());
    }
}
