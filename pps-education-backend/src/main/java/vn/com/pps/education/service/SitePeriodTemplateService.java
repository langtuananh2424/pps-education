package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SitePeriodTemplate;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateSitePeriodTemplateRequest;
import vn.com.pps.education.dto.SitePeriodTemplateResponse;
import vn.com.pps.education.dto.UpdateSitePeriodTemplateRequest;
import vn.com.pps.education.exception.DuplicateSitePeriodNumberException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.SitePeriodTemplateNotDeletableException;
import vn.com.pps.education.repository.SessionPeriodRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.SitePeriodTemplateRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * "Tiết học theo điểm trường" (V127, mở rộng V129 — bổ sung ngoài SDD gốc,
 * xác nhận với người dùng 2026-08-19/2026-08-20) — CRUD danh sách tiết cố
 * định của 1 site, chia theo buổi (Sáng/Chiều/Tối — mỗi buổi đánh số tiết
 * riêng, khớp thời khóa biểu giấy thực tế), dùng làm nguồn "chọn tiết" khi
 * xếp lịch (UC-48/56/57, xem ClassSessionService#generatePeriodsFromTemplate).
 * Đặt trong trang Điểm trường (Cơ sở vật chất & Đối tác) nên dùng lại
 * permission facility.site.update, không tách permission riêng.
 */
@Service
public class SitePeriodTemplateService {

    private static final List<SitePeriodTemplate.DayPart> DAY_PART_DISPLAY_ORDER =
            List.of(SitePeriodTemplate.DayPart.MORNING, SitePeriodTemplate.DayPart.AFTERNOON, SitePeriodTemplate.DayPart.EVENING);

    private final SitePeriodTemplateRepository sitePeriodTemplateRepository;
    private final SiteRepository siteRepository;
    private final UserRepository userRepository;
    private final SessionPeriodRepository sessionPeriodRepository;

    public SitePeriodTemplateService(SitePeriodTemplateRepository sitePeriodTemplateRepository,
                                      SiteRepository siteRepository,
                                      UserRepository userRepository,
                                      SessionPeriodRepository sessionPeriodRepository) {
        this.sitePeriodTemplateRepository = sitePeriodTemplateRepository;
        this.siteRepository = siteRepository;
        this.userRepository = userRepository;
        this.sessionPeriodRepository = sessionPeriodRepository;
    }

    /** Sắp theo thứ tự hiển thị Sáng→Chiều→Tối rồi theo tiết — day_part lưu STRING nên ORDER BY SQL trực tiếp ra sai thứ tự (alphabet). */
    @Transactional(readOnly = true)
    public List<SitePeriodTemplateResponse> listBySite(Long siteId) {
        return sitePeriodTemplateRepository.findBySiteIdAndDeletedAtIsNullOrderByPeriodNumberAsc(siteId).stream()
                .sorted(Comparator.comparingInt((SitePeriodTemplate t) -> DAY_PART_DISPLAY_ORDER.indexOf(t.getDayPart()))
                        .thenComparingInt(SitePeriodTemplate::getPeriodNumber))
                .map(this::toResponse).toList();
    }

    @Transactional
    public SitePeriodTemplateResponse create(Long siteId, CreateSitePeriodTemplateRequest request, Long actorUserId) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("endTime phải sau startTime.");
        }
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm trường id=" + siteId));
        SitePeriodTemplate.DayPart dayPart = SitePeriodTemplate.DayPart.valueOf(request.dayPart());
        if (sitePeriodTemplateRepository.existsBySiteIdAndDayPartAndPeriodNumberAndDeletedAtIsNull(siteId, dayPart, request.periodNumber())) {
            throw new DuplicateSitePeriodNumberException(
                    "Điểm trường này đã có Tiết " + request.periodNumber() + " buổi " + dayPart + " — sửa tiết đã có thay vì tạo trùng.");
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        SitePeriodTemplate template = new SitePeriodTemplate();
        template.setSite(site);
        template.setDayPart(dayPart);
        template.setPeriodNumber(request.periodNumber());
        template.setLabel(request.label());
        template.setStartTime(request.startTime());
        template.setEndTime(request.endTime());
        template.setCreatedBy(actor);
        template = sitePeriodTemplateRepository.save(template);
        return toResponse(template);
    }

    @Transactional
    public SitePeriodTemplateResponse update(Long siteId, Long id, UpdateSitePeriodTemplateRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("endTime phải sau startTime.");
        }
        SitePeriodTemplate template = getOrThrow(siteId, id);
        template.setLabel(request.label());
        template.setStartTime(request.startTime());
        template.setEndTime(request.endTime());
        template = sitePeriodTemplateRepository.save(template);
        return toResponse(template);
    }

    /** Soft delete — chặn nếu còn buổi SCHEDULED từ hôm nay trở đi đang dùng đúng tiết + buổi này. */
    @Transactional
    public void delete(Long siteId, Long id) {
        SitePeriodTemplate template = getOrThrow(siteId, id);
        if (sessionPeriodRepository.existsFutureScheduledUsage(siteId, template.getDayPart(), template.getPeriodNumber(), LocalDate.now())) {
            throw new SitePeriodTemplateNotDeletableException(
                    "Không thể xoá Tiết " + template.getPeriodNumber() + " buổi " + template.getDayPart()
                            + " — vẫn còn buổi học SCHEDULED sắp tới đang dùng tiết này.");
        }
        template.setDeletedAt(OffsetDateTime.now());
        sitePeriodTemplateRepository.save(template);
    }

    private SitePeriodTemplate getOrThrow(Long siteId, Long id) {
        SitePeriodTemplate template = sitePeriodTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tiết học id=" + id));
        if (!template.getSite().getId().equals(siteId) || template.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Không tìm thấy tiết học id=" + id + " thuộc điểm trường id=" + siteId);
        }
        return template;
    }

    private SitePeriodTemplateResponse toResponse(SitePeriodTemplate t) {
        return new SitePeriodTemplateResponse(t.getId(), t.getSite().getId(), t.getDayPart().name(), t.getPeriodNumber(), t.getLabel(), t.getStartTime(), t.getEndTime());
    }
}
