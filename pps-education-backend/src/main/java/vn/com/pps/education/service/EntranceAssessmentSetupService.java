package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.*;
import vn.com.pps.education.dto.*;
import vn.com.pps.education.exception.EntranceAssessmentNotDeletableException;
import vn.com.pps.education.exception.EntranceComponentLockedException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * UC-18c: Đánh giá đầu vào & đề xuất xếp lớp — CẤU HÌNH bộ đề (bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28). Xem
 * docs/uc/phan-he-06-hoc-thuat.md UC-18c để biết đầy đủ Main Flow/Alternate
 * Flow. Tách khỏi {@link EntranceAssessmentResultService} (nhập điểm) theo
 * nguyên tắc SRP — cấu hình đề vs nhập điểm là 2 nhóm nghiệp vụ khác nhau.
 */
@Service
public class EntranceAssessmentSetupService {

    private final EntranceAssessmentSetupRepository setupRepository;
    private final EntranceAssessmentComponentRepository componentRepository;
    private final EntranceAssessmentResultRepository resultRepository;
    private final EntranceAssessmentScoreRepository scoreRepository;
    private final SiteRepository siteRepository;
    private final AcademicYearRepository academicYearRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;

    public EntranceAssessmentSetupService(EntranceAssessmentSetupRepository setupRepository,
                                          EntranceAssessmentComponentRepository componentRepository,
                                          EntranceAssessmentResultRepository resultRepository,
                                          EntranceAssessmentScoreRepository scoreRepository,
                                          SiteRepository siteRepository,
                                          AcademicYearRepository academicYearRepository,
                                          SkillRepository skillRepository,
                                          UserRepository userRepository) {
        this.setupRepository = setupRepository;
        this.componentRepository = componentRepository;
        this.resultRepository = resultRepository;
        this.scoreRepository = scoreRepository;
        this.siteRepository = siteRepository;
        this.academicYearRepository = academicYearRepository;
        this.skillRepository = skillRepository;
        this.userRepository = userRepository;
    }

    // ===================== Bộ đề =====================

    @Transactional
    public EntranceAssessmentSetupResponse createSetup(CreateEntranceAssessmentSetupRequest request, Long actorUserId) {
        Site site = siteRepository.findById(request.siteId())
                .orElseThrow(() -> new ResourceNotFoundException("error.entranceAssessment.siteNotFound",
                        new Object[]{request.siteId()}, "Không tìm thấy điểm trường id=" + request.siteId()));
        AcademicYear year = academicYearRepository.findById(request.academicYearId())
                .orElseThrow(() -> new ResourceNotFoundException("error.entranceAssessment.academicYearNotFound",
                        new Object[]{request.academicYearId()}, "Không tìm thấy năm học id=" + request.academicYearId()));
        GradeComponentSetup.ScaleType scaleType = parseScaleType(request.scaleType());
        if (setupRepository.existsBySiteIdAndAcademicYearIdAndNameAndDeletedAtIsNull(
                request.siteId(), request.academicYearId(), request.name().trim())) {
            throw new IllegalArgumentException("Đã có bộ đề đánh giá đầu vào trùng tên cho điểm trường + năm học này.");
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.entranceAssessment.actorNotFound",
                        new Object[]{actorUserId}, "Không tìm thấy tài khoản id=" + actorUserId));

        EntranceAssessmentSetup setup = new EntranceAssessmentSetup();
        setup.setSite(site);
        setup.setAcademicYear(year);
        setup.setName(request.name().trim());
        setup.setScaleType(scaleType);
        setup.setCreatedBy(actor);
        setup = setupRepository.save(setup);
        return toSetupResponse(setup, List.of());
    }

    @Transactional
    public EntranceAssessmentSetupResponse updateSetup(Long id, UpdateEntranceAssessmentSetupRequest request) {
        EntranceAssessmentSetup setup = getSetupOrThrow(id);
        setup.setName(request.name().trim());
        setup.setScaleType(parseScaleType(request.scaleType()));
        setup = setupRepository.save(setup);
        return toSetupResponse(setup, componentRepository.findBySetupIdOrderByDisplayOrderAscIdAsc(id));
    }

    /** UC-18c A: chỉ xoá được bộ đề khi CHƯA có kết quả thí sinh nào. */
    @Transactional
    public void deleteSetup(Long id) {
        EntranceAssessmentSetup setup = getSetupOrThrow(id);
        if (resultRepository.countBySetupId(id) > 0) {
            throw new EntranceAssessmentNotDeletableException(
                    "error.entranceAssessment.setupHasResults", new Object[]{id},
                    "Không xoá được bộ đề đã có kết quả thí sinh (id=" + id + ").");
        }
        componentRepository.findBySetupIdOrderByDisplayOrderAscIdAsc(id).forEach(componentRepository::delete);
        setup.setDeletedAt(OffsetDateTime.now());
        setupRepository.save(setup);
    }

    @Transactional(readOnly = true)
    public List<EntranceAssessmentSetupResponse> listSetups(Long siteId, Long academicYearId) {
        List<EntranceAssessmentSetup> setups = academicYearId == null
                ? setupRepository.findBySiteIdAndDeletedAtIsNullOrderByIdDesc(siteId)
                : setupRepository.findBySiteIdAndAcademicYearIdAndDeletedAtIsNullOrderByIdDesc(siteId, academicYearId);
        return setups.stream()
                .map(s -> toSetupResponse(s, componentRepository.findBySetupIdOrderByDisplayOrderAscIdAsc(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public EntranceAssessmentSetupResponse getSetup(Long id) {
        EntranceAssessmentSetup setup = getSetupOrThrow(id);
        return toSetupResponse(setup, componentRepository.findBySetupIdOrderByDisplayOrderAscIdAsc(id));
    }

    // ===================== Đầu điểm =====================

    @Transactional
    public EntranceAssessmentComponentResponse addComponent(Long setupId, CreateEntranceAssessmentComponentRequest request) {
        EntranceAssessmentSetup setup = getSetupOrThrow(setupId);
        String code = request.code().trim();
        if (componentRepository.existsBySetupIdAndCode(setupId, code)) {
            throw new IllegalArgumentException("Đầu điểm mã '" + code + "' đã tồn tại trong bộ đề này.");
        }
        EntranceAssessmentComponent component = new EntranceAssessmentComponent();
        component.setSetup(setup);
        component.setCode(code);
        component.setName(request.name().trim());
        component.setMaxScore(request.maxScore());
        component.setSkill(resolveSkill(request.skillId()));
        component.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        component = componentRepository.save(component);
        return toComponentResponse(component);
    }

    @Transactional
    public EntranceAssessmentComponentResponse updateComponent(Long id, UpdateEntranceAssessmentComponentRequest request) {
        EntranceAssessmentComponent component = componentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.entranceAssessment.componentNotFound",
                        new Object[]{id}, "Không tìm thấy đầu điểm id=" + id));
        boolean maxScoreChanged = component.getMaxScore().compareTo(request.maxScore()) != 0;
        if (maxScoreChanged && scoreRepository.countByComponentIdAndScoreIsNotNull(id) > 0) {
            throw new EntranceComponentLockedException(
                    "error.entranceAssessment.componentLocked", new Object[]{id},
                    "Không sửa được thang điểm tối đa khi đã có điểm nhập (đầu điểm id=" + id + ").");
        }
        component.setName(request.name().trim());
        component.setMaxScore(request.maxScore());
        component.setSkill(resolveSkill(request.skillId()));
        if (request.displayOrder() != null) {
            component.setDisplayOrder(request.displayOrder());
        }
        component = componentRepository.save(component);
        return toComponentResponse(component);
    }

    /** UC-18c A: chỉ xoá được đầu điểm khi CHƯA có điểm nhập nào. */
    @Transactional
    public void deleteComponent(Long id) {
        EntranceAssessmentComponent component = componentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.entranceAssessment.componentNotFound",
                        new Object[]{id}, "Không tìm thấy đầu điểm id=" + id));
        if (scoreRepository.countByComponentIdAndScoreIsNotNull(id) > 0) {
            throw new EntranceAssessmentNotDeletableException(
                    "error.entranceAssessment.componentHasScores", new Object[]{id},
                    "Không xoá được đầu điểm đã có điểm nhập (id=" + id + ").");
        }
        componentRepository.delete(component);
    }

    // ===================== Helpers =====================

    EntranceAssessmentSetup getSetupOrThrow(Long id) {
        return setupRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.entranceAssessment.setupNotFound",
                        new Object[]{id}, "Không tìm thấy bộ đề đánh giá đầu vào id=" + id));
    }

    private Skill resolveSkill(Long skillId) {
        if (skillId == null) {
            return null;
        }
        return skillRepository.findById(skillId)
                .orElseThrow(() -> new ResourceNotFoundException("error.entranceAssessment.skillNotFound",
                        new Object[]{skillId}, "Không tìm thấy kỹ năng id=" + skillId));
    }

    private GradeComponentSetup.ScaleType parseScaleType(String raw) {
        try {
            return GradeComponentSetup.ScaleType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("scaleType không hợp lệ: " + raw + " (POINT_10 / PERCENT / IELTS).");
        }
    }

    private EntranceAssessmentSetupResponse toSetupResponse(EntranceAssessmentSetup s, List<EntranceAssessmentComponent> components) {
        return new EntranceAssessmentSetupResponse(
                s.getId(), s.getSite().getId(), s.getSite().getName(),
                s.getAcademicYear().getId(), s.getAcademicYear().getCode(), s.getAcademicYear().getName(),
                s.getName(), s.getScaleType().name(),
                components.stream().map(this::toComponentResponse).toList());
    }

    private EntranceAssessmentComponentResponse toComponentResponse(EntranceAssessmentComponent c) {
        Skill skill = c.getSkill();
        BigDecimal maxScore = c.getMaxScore();
        return new EntranceAssessmentComponentResponse(c.getId(), c.getSetup().getId(), c.getCode(), c.getName(),
                maxScore, skill == null ? null : skill.getId(), skill == null ? null : skill.getName(),
                c.getDisplayOrder());
    }
}
