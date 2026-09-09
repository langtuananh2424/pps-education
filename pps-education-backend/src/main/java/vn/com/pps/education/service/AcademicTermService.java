package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.AcademicYear;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AcademicTermResponse;
import vn.com.pps.education.dto.CreateAcademicTermRequest;
import vn.com.pps.education.dto.UpdateAcademicTermRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.AcademicYearRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * UC-18 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31) —
 * "Giai đoạn/Học kỳ" giới hạn theo điểm trường (site), độc lập với lớp học.
 * Xem Javadoc {@link vn.com.pps.education.domain.AcademicTerm}. Hồ sơ
 * lớp/học sinh theo kỳ (sĩ số, giáo viên, điểm danh, nhận xét) là dữ liệu
 * TÍNH RA từ các bảng đã có ngày tháng, KHÔNG thuộc phạm vi Service này —
 * đây thuộc phân hệ Báo cáo & Thống kê sẽ triển khai sau (đã xác nhận với
 * người dùng, xem docs/uc/phan-he-06-hoc-thuat.md UC-18).
 *
 * <p>V157 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28):
 * mỗi kỳ học bắt buộc thuộc 1 năm học ({@link AcademicYear}); khoảng thời
 * gian kỳ phải nằm trong năm học khi năm học đã khai báo đủ ngày.
 */
@Service
public class AcademicTermService {

    private final AcademicTermRepository academicTermRepository;
    private final AcademicYearRepository academicYearRepository;
    private final SiteRepository siteRepository;
    private final UserRepository userRepository;

    public AcademicTermService(AcademicTermRepository academicTermRepository,
                                AcademicYearRepository academicYearRepository,
                                SiteRepository siteRepository,
                                UserRepository userRepository) {
        this.academicTermRepository = academicTermRepository;
        this.academicYearRepository = academicYearRepository;
        this.siteRepository = siteRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AcademicTermResponse create(CreateAcademicTermRequest request, Long actorUserId) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("endDate phải sau hoặc bằng startDate.");
        }
        Site site = siteRepository.findById(request.siteId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.academicTerm.siteNotFound", new Object[]{request.siteId()},
                        "Không tìm thấy điểm trường id=" + request.siteId()));
        AcademicYear academicYear = getAcademicYearOrThrow(request.academicYearId());
        validateWithinYear(academicYear, request.startDate(), request.endDate());
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.academicTerm.actorNotFound", new Object[]{actorUserId},
                        "Không tìm thấy tài khoản id=" + actorUserId));

        AcademicTerm term = new AcademicTerm();
        term.setSite(site);
        term.setAcademicYear(academicYear);
        term.setCode(request.code());
        term.setName(request.name());
        term.setStartDate(request.startDate());
        term.setEndDate(request.endDate());
        term.setCreatedBy(actor);
        term = academicTermRepository.save(term);
        return toResponse(term);
    }

    @Transactional
    public AcademicTermResponse update(Long id, UpdateAcademicTermRequest request, Long actorUserId) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("endDate phải sau hoặc bằng startDate.");
        }
        AcademicTerm term = getOrThrow(id);
        AcademicYear academicYear = getAcademicYearOrThrow(request.academicYearId());
        validateWithinYear(academicYear, request.startDate(), request.endDate());
        term.setAcademicYear(academicYear);
        term.setName(request.name());
        term.setStartDate(request.startDate());
        term.setEndDate(request.endDate());
        term = academicTermRepository.save(term);
        return toResponse(term);
    }

    @Transactional(readOnly = true)
    public List<AcademicTermResponse> listBySite(Long siteId) {
        return academicTermRepository.findBySiteIdOrderByStartDateDesc(siteId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AcademicTermResponse getById(Long id) {
        return toResponse(getOrThrow(id));
    }

    private AcademicTerm getOrThrow(Long id) {
        return academicTermRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.academicTerm.notFound", new Object[]{id},
                        "Không tìm thấy kỳ học id=" + id));
    }

    private AcademicYear getAcademicYearOrThrow(Long academicYearId) {
        return academicYearRepository.findById(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.academicTerm.academicYearNotFound", new Object[]{academicYearId},
                        "Không tìm thấy năm học id=" + academicYearId));
    }

    /**
     * V157: khoảng [startDate, endDate] của kỳ phải nằm gọn trong năm học —
     * chỉ kiểm khi năm học đã khai báo đủ cả start_date và end_date (2 cột
     * này nullable ở {@link AcademicYear}).
     */
    private void validateWithinYear(AcademicYear year, LocalDate startDate, LocalDate endDate) {
        if (year.getStartDate() != null && year.getEndDate() != null
                && (startDate.isBefore(year.getStartDate()) || endDate.isAfter(year.getEndDate()))) {
            throw new IllegalArgumentException(
                    "Khoảng thời gian kỳ học phải nằm trong năm học " + year.getCode() + ".");
        }
    }

    private AcademicTermResponse toResponse(AcademicTerm t) {
        AcademicYear ay = t.getAcademicYear();
        return new AcademicTermResponse(t.getId(), t.getSite().getId(), t.getSite().getName(),
                ay == null ? null : ay.getId(),
                ay == null ? null : ay.getCode(),
                ay == null ? null : ay.getName(),
                t.getCode(), t.getName(), t.getStartDate(), t.getEndDate());
    }
}
