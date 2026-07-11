package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.CurriculumHistory;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.domain.CurriculumSubjectHistory;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateCurriculumSubjectRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.CurriculumSubjectResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.CurriculumUpdateConfirmationRequiredException;
import vn.com.pps.education.exception.DuplicateCurriculumCodeException;
import vn.com.pps.education.exception.NotHeadAcademicException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.CurriculumHistoryRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.CurriculumSubjectHistoryRepository;
import vn.com.pps.education.repository.CurriculumSubjectRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-16: Quản lý khung chương trình (FR-ACA-01).
 * Xem docs/uc/phan-he-06-hoc-thuat.md — Main Flow bước 1-4, A1 (khung đang
 * dùng bởi lớp IN_PROGRESS).
 *
 * Phạm vi phiên này: CHỈ khung chuẩn (site_id luôn NULL). UC-16b (Quản lý
 * điểm trường tạo bản sao tùy biến) + UC-17 (phê duyệt) CHƯA code — để lại
 * cho phiên sau khi có nhu cầu thật.
 *
 * Không dùng @PreAuthorize hasPermission — Precondition UC-16 chỉ nêu
 * "role HEAD_ACADEMIC" (không có permission code cụ thể), theo đúng
 * pattern LeaveRequestService/StudentStatusService (role-based check
 * trong Service).
 */
@Service
public class CurriculumService {

    private final CurriculumRepository curriculumRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final CurriculumHistoryRepository curriculumHistoryRepository;
    private final CurriculumSubjectHistoryRepository curriculumSubjectHistoryRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public CurriculumService(CurriculumRepository curriculumRepository,
                              CurriculumSubjectRepository curriculumSubjectRepository,
                              CurriculumHistoryRepository curriculumHistoryRepository,
                              CurriculumSubjectHistoryRepository curriculumSubjectHistoryRepository,
                              SchoolClassRepository schoolClassRepository,
                              UserRepository userRepository,
                              UserRoleRepository userRoleRepository) {
        this.curriculumRepository = curriculumRepository;
        this.curriculumSubjectRepository = curriculumSubjectRepository;
        this.curriculumHistoryRepository = curriculumHistoryRepository;
        this.curriculumSubjectHistoryRepository = curriculumSubjectHistoryRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional(readOnly = true)
    public List<CurriculumResponse> listStandard() {
        return curriculumRepository.findByDeletedAtIsNullAndSiteIdIsNull().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CurriculumResponse getById(Long id) {
        return toResponse(getCurriculumOrThrow(id));
    }

    /** Main Flow bước 1-3: khởi tạo khung chương trình chuẩn mới (status DRAFT). */
    @Transactional
    public CurriculumResponse create(CreateCurriculumRequest request, Long actorUserId) {
        requireHeadAcademic(actorUserId);
        if (curriculumRepository.findByCode(request.code()).isPresent()) {
            throw new DuplicateCurriculumCodeException("Mã khung chương trình đã tồn tại: " + request.code());
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        Curriculum curriculum = new Curriculum();
        curriculum.setCode(request.code());
        curriculum.setName(request.name());
        curriculum.setClassCategory(Curriculum.ClassCategory.valueOf(request.classCategory()));
        curriculum.setLevel(request.level());
        curriculum.setTotalPeriods(request.totalPeriods());
        if (request.defaultGradePassThreshold() != null) {
            curriculum.setDefaultGradePassThreshold(request.defaultGradePassThreshold());
        }
        curriculum.setCreatedBy(actor);
        curriculum = curriculumRepository.save(curriculum);

        writeCurriculumHistory(curriculum, actor, CurriculumHistory.Action.CREATED);
        return toResponse(curriculum);
    }

    /** Main Flow bước 2, A1: cập nhật khung chương trình đã có. */
    @Transactional
    public CurriculumResponse update(Long id, UpdateCurriculumRequest request, Long actorUserId) {
        requireHeadAcademic(actorUserId);
        Curriculum curriculum = getCurriculumOrThrow(id);
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        // A1 -- khung đang dùng bởi lớp IN_PROGRESS, cần xác nhận lại trước khi lưu.
        boolean inUseByRunningClass = schoolClassRepository
                .countByCurriculumIdAndStatus(id, SchoolClass.Status.IN_PROGRESS) > 0;
        if (inUseByRunningClass && !request.confirm()) {
            throw new CurriculumUpdateConfirmationRequiredException(
                    "Khung chương trình id=" + id + " đang được lớp IN_PROGRESS sử dụng. "
                            + "Xác nhận lại (confirm=true) để tiếp tục lưu.");
        }

        curriculum.setName(request.name());
        curriculum.setLevel(request.level());
        curriculum.setTotalPeriods(request.totalPeriods());
        if (request.defaultGradePassThreshold() != null) {
            curriculum.setDefaultGradePassThreshold(request.defaultGradePassThreshold());
        }
        Curriculum.Status newStatus = Curriculum.Status.valueOf(request.status());
        curriculum.setStatus(newStatus);
        if (newStatus == Curriculum.Status.ACTIVE && curriculum.getApprovedAt() == null) {
            curriculum.setApprovedBy(actor);
            curriculum.setApprovedAt(java.time.OffsetDateTime.now());
        }
        curriculum = curriculumRepository.save(curriculum);

        writeCurriculumHistory(curriculum, actor, CurriculumHistory.Action.UPDATED);
        return toResponse(curriculum);
    }

    /** Main Flow bước 2: thêm 1 học phần vào khung chương trình. */
    @Transactional
    public CurriculumSubjectResponse addSubject(Long curriculumId, CreateCurriculumSubjectRequest request, Long actorUserId) {
        requireHeadAcademic(actorUserId);
        Curriculum curriculum = getCurriculumOrThrow(curriculumId);
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + actorUserId));

        CurriculumSubject subject = new CurriculumSubject();
        subject.setCurriculum(curriculum);
        subject.setSubjectCode(CurriculumSubject.SubjectCode.valueOf(request.subjectCode()));
        subject.setName(request.name());
        subject.setPeriodCount(request.periodCount());
        subject.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        subject = curriculumSubjectRepository.save(subject);

        CurriculumSubjectHistory history = new CurriculumSubjectHistory();
        history.setCurriculumSubject(subject);
        history.setChangedBy(actor);
        history.setAction(CurriculumSubjectHistory.Action.CREATED);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("subjectCode", subject.getSubjectCode().name());
        snapshot.put("name", subject.getName());
        snapshot.put("periodCount", subject.getPeriodCount());
        history.setDetails(snapshot);
        curriculumSubjectHistoryRepository.save(history);

        return toResponse(subject);
    }

    @Transactional(readOnly = true)
    public List<CurriculumSubjectResponse> listSubjects(Long curriculumId) {
        getCurriculumOrThrow(curriculumId);
        return curriculumSubjectRepository.findByCurriculumIdOrderByDisplayOrder(curriculumId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void requireHeadAcademic(Long actorUserId) {
        Set<String> roleCodes = roleCodesOf(actorUserId);
        if (!roleCodes.contains("HEAD_ACADEMIC")) {
            throw new NotHeadAcademicException(
                    "Tài khoản id=" + actorUserId + " không có role HEAD_ACADEMIC để quản lý khung chương trình.");
        }
    }

    private Set<String> roleCodesOf(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toSet());
    }

    private void writeCurriculumHistory(Curriculum curriculum, User actor, CurriculumHistory.Action action) {
        CurriculumHistory history = new CurriculumHistory();
        history.setCurriculum(curriculum);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(curriculumSnapshot(curriculum));
        curriculumHistoryRepository.save(history);
    }

    private Map<String, Object> curriculumSnapshot(Curriculum c) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", c.getCode());
        snapshot.put("name", c.getName());
        snapshot.put("classCategory", c.getClassCategory().name());
        snapshot.put("level", c.getLevel());
        snapshot.put("totalPeriods", c.getTotalPeriods());
        snapshot.put("status", c.getStatus().name());
        return snapshot;
    }

    private Curriculum getCurriculumOrThrow(Long id) {
        return curriculumRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung chương trình id=" + id));
    }

    private CurriculumResponse toResponse(Curriculum c) {
        return new CurriculumResponse(
                c.getId(), c.getCode(), c.getName(),
                c.getSite() == null ? null : c.getSite().getId(),
                c.getParentCurriculum() == null ? null : c.getParentCurriculum().getId(),
                c.getClassCategory().name(), c.getLevel(), c.getTotalPeriods(),
                c.getDefaultGradePassThreshold(), c.getStatus().name(),
                c.getCreatedBy().getId(), c.getApprovedBy() == null ? null : c.getApprovedBy().getId());
    }

    private CurriculumSubjectResponse toResponse(CurriculumSubject s) {
        return new CurriculumSubjectResponse(
                s.getId(), s.getCurriculum().getId(), s.getSubjectCode().name(), s.getName(),
                s.getPeriodCount(), s.getDisplayOrder());
    }
}
