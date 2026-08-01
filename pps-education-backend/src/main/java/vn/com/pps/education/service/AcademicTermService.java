package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AcademicTermResponse;
import vn.com.pps.education.dto.CreateAcademicTermRequest;
import vn.com.pps.education.dto.UpdateAcademicTermRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;

import java.util.List;

/**
 * UC-18 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31) —
 * "Giai đoạn/Học kỳ" giới hạn theo điểm trường (site), độc lập với lớp học.
 * Xem Javadoc {@link vn.com.pps.education.domain.AcademicTerm}. Hồ sơ
 * lớp/học sinh theo kỳ (sĩ số, giáo viên, điểm danh, nhận xét) là dữ liệu
 * TÍNH RA từ các bảng đã có ngày tháng, KHÔNG thuộc phạm vi Service này —
 * đây thuộc phân hệ Báo cáo & Thống kê sẽ triển khai sau (đã xác nhận với
 * người dùng, xem docs/uc/phan-he-06-hoc-thuat.md UC-18).
 */
@Service
public class AcademicTermService {

    private final AcademicTermRepository academicTermRepository;
    private final SiteRepository siteRepository;
    private final UserRepository userRepository;

    public AcademicTermService(AcademicTermRepository academicTermRepository,
                                SiteRepository siteRepository,
                                UserRepository userRepository) {
        this.academicTermRepository = academicTermRepository;
        this.siteRepository = siteRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AcademicTermResponse create(CreateAcademicTermRequest request, Long actorUserId) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("endDate phải sau hoặc bằng startDate.");
        }
        Site site = siteRepository.findById(request.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm trường id=" + request.siteId()));
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        AcademicTerm term = new AcademicTerm();
        term.setSite(site);
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ học id=" + id));
    }

    private AcademicTermResponse toResponse(AcademicTerm t) {
        return new AcademicTermResponse(t.getId(), t.getSite().getId(), t.getSite().getName(),
                t.getCode(), t.getName(), t.getStartDate(), t.getEndDate());
    }
}
