package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ApprovalFlow;
import vn.com.pps.education.domain.Book;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.CurriculumHistory;
import vn.com.pps.education.domain.CurriculumSubTopic;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.domain.CurriculumSubjectHistory;
import vn.com.pps.education.domain.CurriculumUnit;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.BookResponse;
import vn.com.pps.education.dto.CreateBookRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateCurriculumSubjectRequest;
import vn.com.pps.education.dto.CreateCustomCurriculumRequest;
import vn.com.pps.education.dto.CreateSubTopicRequest;
import vn.com.pps.education.dto.CreateUnitRequest;
import vn.com.pps.education.dto.CurriculumApprovalResponse;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.CurriculumSubjectResponse;
import vn.com.pps.education.dto.DecideCurriculumApprovalRequest;
import vn.com.pps.education.dto.SubTopicResponse;
import vn.com.pps.education.dto.UnitResponse;
import vn.com.pps.education.dto.UpdateBookRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateCustomCurriculumRequest;
import vn.com.pps.education.dto.UpdateSubTopicRequest;
import vn.com.pps.education.dto.UpdateUnitRequest;
import vn.com.pps.education.exception.ApprovalAlreadyDecidedException;
import vn.com.pps.education.exception.CurriculumNotEditableException;
import vn.com.pps.education.exception.CurriculumUpdateConfirmationRequiredException;
import vn.com.pps.education.exception.DuplicateCurriculumCodeException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ApprovalFlowRepository;
import vn.com.pps.education.repository.CurriculumHistoryRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.BookRepository;
import vn.com.pps.education.repository.CurriculumSubTopicRepository;
import vn.com.pps.education.repository.CurriculumSubjectHistoryRepository;
import vn.com.pps.education.repository.CurriculumSubjectRepository;
import vn.com.pps.education.repository.CurriculumUnitRepository;
import vn.com.pps.education.repository.ExamRepository;
import vn.com.pps.education.repository.ReviewVideoSetRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.SkillRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-16: Quản lý khung chương trình (FR-ACA-01) + UC-16b: Đề xuất khung
 * chương trình tùy biến + UC-17: Phê duyệt khung chương trình tùy biến.
 * Xem docs/uc/phan-he-06-hoc-thuat.md.
 *
 * Gộp cả 3 UC vào 1 Service — cùng 1 entity (curriculums) + 1 luồng trạng
 * thái DRAFT→PENDING_APPROVAL→ACTIVE (hoặc quay lại DRAFT nếu bị từ chối),
 * chỉ khác actor theo từng bước (HEAD_ACADEMIC tạo khung chuẩn + duyệt,
 * SITE_MANAGER đề xuất tùy biến) — giống cách LeaveRequestService gộp
 * UC-10 (nộp) + UC-11 (duyệt) vì "2 UC cùng 1 workflow trạng thái".
 *
 * UC-17 dùng lại bảng approval_flows dùng chung (entity_type=CURRICULUM)
 * thay vì tự thêm cột riêng vào curriculums — đúng theo SDD (ERD-Nhom5A:
 * "approval_flows đã thiết kế ở Nhóm 1, không tạo bảng riêng"). Khi bị
 * REJECTED, curriculum quay về DRAFT (không có giá trị "REJECTED" riêng
 * trong curriculums.status — lý do từ chối lưu ở approval_flows.comment).
 *
 * HEAD_ACADEMIC (create/update/addSubject/listPendingApprovals/decideApproval)
 * qua @PreAuthorize("hasPermission(null,'academic.curriculum.create/update/approve')")
 * ở CurriculumController (Hybrid PBAC — V28/V62). SITE_MANAGER (createCustomCopy/
 * updateCustomCopy/submitForApproval) vẫn giữ requireSiteManagerForSite —
 * đây là row-level scope check (site cụ thể), không phải role-hardcode nên
 * hasPermission(null,...) không thay thế được (tham số object luôn null).
 */
@Service
public class CurriculumService {

    private final CurriculumRepository curriculumRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final CurriculumHistoryRepository curriculumHistoryRepository;
    private final CurriculumSubjectHistoryRepository curriculumSubjectHistoryRepository;
    private final BookRepository bookRepository;
    private final CurriculumUnitRepository curriculumUnitRepository;
    private final CurriculumSubTopicRepository curriculumSubTopicRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SiteRepository siteRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final ApprovalFlowRepository approvalFlowRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final ExamRepository examRepository;
    private final ReviewVideoSetRepository reviewVideoSetRepository;

    public CurriculumService(CurriculumRepository curriculumRepository,
                              CurriculumSubjectRepository curriculumSubjectRepository,
                              CurriculumHistoryRepository curriculumHistoryRepository,
                              CurriculumSubjectHistoryRepository curriculumSubjectHistoryRepository,
                              BookRepository bookRepository,
                              CurriculumUnitRepository curriculumUnitRepository,
                              CurriculumSubTopicRepository curriculumSubTopicRepository,
                              SchoolClassRepository schoolClassRepository,
                              SiteRepository siteRepository,
                              SiteManagerRepository siteManagerRepository,
                              ApprovalFlowRepository approvalFlowRepository,
                              SkillRepository skillRepository,
                              UserRepository userRepository,
                              ExamRepository examRepository,
                              ReviewVideoSetRepository reviewVideoSetRepository) {
        this.curriculumRepository = curriculumRepository;
        this.curriculumSubjectRepository = curriculumSubjectRepository;
        this.curriculumHistoryRepository = curriculumHistoryRepository;
        this.curriculumSubjectHistoryRepository = curriculumSubjectHistoryRepository;
        this.bookRepository = bookRepository;
        this.curriculumUnitRepository = curriculumUnitRepository;
        this.curriculumSubTopicRepository = curriculumSubTopicRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.siteRepository = siteRepository;
        this.siteManagerRepository = siteManagerRepository;
        this.approvalFlowRepository = approvalFlowRepository;
        this.skillRepository = skillRepository;
        this.userRepository = userRepository;
        this.examRepository = examRepository;
        this.reviewVideoSetRepository = reviewVideoSetRepository;
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
        if (curriculumRepository.findByCode(request.code()).isPresent()) {
            throw new DuplicateCurriculumCodeException("error.duplicateCurriculumCode.default", new Object[]{request.code()}, "Mã khung chương trình đã tồn tại: " + request.code());
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.curriculum.actorNotFound", new Object[]{actorUserId},
                        "Không tìm thấy tài khoản id=" + actorUserId));

        Curriculum curriculum = new Curriculum();
        curriculum.setCode(request.code());
        curriculum.setName(request.name());
        curriculum.setClassCategory(Curriculum.ClassCategory.valueOf(request.classCategory()));
        curriculum.setLevel(request.level());
        curriculum.setGradeLevel(parseGradeLevel(request.gradeLevel()));
        curriculum.setTrack(parseTrack(request.track()));
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
        Curriculum curriculum = getCurriculumOrThrow(id);
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.curriculum.actorNotFound", new Object[]{actorUserId},
                        "Không tìm thấy tài khoản id=" + actorUserId));

        // A1 -- khung đang dùng bởi lớp IN_PROGRESS, cần xác nhận lại trước khi lưu.
        boolean inUseByRunningClass = schoolClassRepository
                .countByCurriculumIdAndStatus(id, SchoolClass.Status.IN_PROGRESS) > 0;
        if (inUseByRunningClass && !request.confirm()) {
            throw new CurriculumUpdateConfirmationRequiredException(
                    "error.curriculumUpdateConfirmationRequired.default", new Object[]{},
                    "Khung chương trình này đang được lớp IN_PROGRESS sử dụng. "
                            + "Xác nhận lại để tiếp tục lưu.");
        }

        curriculum.setName(request.name());
        curriculum.setLevel(request.level());
        curriculum.setGradeLevel(parseGradeLevel(request.gradeLevel()));
        curriculum.setTrack(parseTrack(request.track()));
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
        Curriculum curriculum = getCurriculumOrThrow(curriculumId);
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.curriculum.actorNotFound", new Object[]{actorUserId},
                        "Không tìm thấy tài khoản id=" + actorUserId));

        CurriculumSubject subject = new CurriculumSubject();
        subject.setCurriculum(curriculum);
        subject.setSubjectCode(CurriculumSubject.SubjectCode.valueOf(request.subjectCode()));
        if (request.skillId() != null) {
            subject.setSkill(skillRepository.findById(request.skillId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "error.curriculum.skillNotFound", new Object[]{request.skillId()},
                            "Không tìm thấy kỹ năng id=" + request.skillId())));
        }
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

    /**
     * V148 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-24) — thêm 1 Sách vào khung
     * chương trình (Kho đề: Curriculum (chương trình+khối) -&gt; Sách -&gt; Unit -&gt; Sub Topic ->
     * Lesson -&gt; Bài). Thuần điều hướng/phân loại, không có workflow duyệt/history — mirror
     * curriculum_subjects nhưng đơn giản hơn, giống hệt pattern addUnit/addSubTopic bên dưới.
     */
    @Transactional
    public BookResponse addBook(Long curriculumId, CreateBookRequest request) {
        Curriculum curriculum = getCurriculumOrThrow(curriculumId);
        Book book = new Book();
        book.setCurriculum(curriculum);
        book.setTitle(request.title());
        book.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        return toResponse(bookRepository.save(book));
    }

    @Transactional(readOnly = true)
    public List<BookResponse> listBooks(Long curriculumId) {
        getCurriculumOrThrow(curriculumId);
        return bookRepository.findByCurriculumIdOrderByDisplayOrder(curriculumId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — sửa tên/thứ tự 1 Sách. */
    @Transactional
    public BookResponse updateBook(Long id, UpdateBookRequest request) {
        Book book = getBookOrThrow(id);
        book.setTitle(request.title());
        book.setDisplayOrder(request.displayOrder() == null ? book.getDisplayOrder() : request.displayOrder());
        return toResponse(bookRepository.save(book));
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — chỉ xóa được khi Sách đã hết Unit. */
    @Transactional
    public void deleteBook(Long id) {
        getBookOrThrow(id);
        if (curriculumUnitRepository.existsByBookId(id)) {
            throw new IllegalArgumentException("Sách này còn Unit — xóa hết Unit trước khi xóa Sách.");
        }
        bookRepository.deleteById(id);
    }

    /** V144/V148: thêm 1 Unit vào 1 Sách (trước V148 gắn thẳng Curriculum, xem Javadoc CurriculumUnit). */
    @Transactional
    public UnitResponse addUnit(Long bookId, CreateUnitRequest request) {
        Book book = getBookOrThrow(bookId);
        CurriculumUnit unit = new CurriculumUnit();
        unit.setBook(book);
        unit.setTitle(request.title());
        unit.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        return toResponse(curriculumUnitRepository.save(unit));
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> listUnits(Long bookId) {
        getBookOrThrow(bookId);
        return curriculumUnitRepository.findByBookIdOrderByDisplayOrder(bookId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — sửa tên/thứ tự 1 Unit. */
    @Transactional
    public UnitResponse updateUnit(Long id, UpdateUnitRequest request) {
        CurriculumUnit unit = getUnitOrThrow(id);
        unit.setTitle(request.title());
        unit.setDisplayOrder(request.displayOrder() == null ? unit.getDisplayOrder() : request.displayOrder());
        return toResponse(curriculumUnitRepository.save(unit));
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — chỉ xóa được khi Unit đã hết Sub Topic. */
    @Transactional
    public void deleteUnit(Long id) {
        getUnitOrThrow(id);
        if (curriculumSubTopicRepository.existsByUnitId(id)) {
            throw new IllegalArgumentException("Unit này còn Sub Topic — xóa hết Sub Topic trước khi xóa Unit.");
        }
        curriculumUnitRepository.deleteById(id);
    }

    /** V144: thêm 1 Sub Topic vào 1 Unit. */
    @Transactional
    public SubTopicResponse addSubTopic(Long unitId, CreateSubTopicRequest request) {
        CurriculumUnit unit = getUnitOrThrow(unitId);
        CurriculumSubTopic subTopic = new CurriculumSubTopic();
        subTopic.setUnit(unit);
        subTopic.setTitle(request.title());
        subTopic.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        return toResponse(curriculumSubTopicRepository.save(subTopic));
    }

    @Transactional(readOnly = true)
    public List<SubTopicResponse> listSubTopics(Long unitId) {
        getUnitOrThrow(unitId);
        return curriculumSubTopicRepository.findByUnitIdOrderByDisplayOrder(unitId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — sửa tên/thứ tự 1 Sub Topic. */
    @Transactional
    public SubTopicResponse updateSubTopic(Long id, UpdateSubTopicRequest request) {
        CurriculumSubTopic subTopic = getSubTopicOrThrow(id);
        subTopic.setTitle(request.title());
        subTopic.setDisplayOrder(request.displayOrder() == null ? subTopic.getDisplayOrder() : request.displayOrder());
        return toResponse(curriculumSubTopicRepository.save(subTopic));
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-26 — chỉ xóa được khi chưa Lesson (Đề)
     * hay Bộ video ôn tập nào tham chiếu Sub Topic này qua sub_topic_id (nullable, không cascade —
     * xem Exam#getSubTopic()/ReviewVideoSet#getSubTopic()).
     */
    @Transactional
    public void deleteSubTopic(Long id) {
        getSubTopicOrThrow(id);
        if (examRepository.existsBySubTopicId(id) || reviewVideoSetRepository.existsBySubTopicId(id)) {
            throw new IllegalArgumentException("Sub Topic này đang được 1 Đề hoặc Bộ video ôn tập sử dụng — không xóa được.");
        }
        curriculumSubTopicRepository.deleteById(id);
    }

    private Book getBookOrThrow(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.curriculum.bookNotFound", new Object[]{id},
                        "Không tìm thấy Sách id=" + id));
    }

    private CurriculumUnit getUnitOrThrow(Long id) {
        return curriculumUnitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.curriculum.unitNotFound", new Object[]{id},
                        "Không tìm thấy Unit id=" + id));
    }

    private CurriculumSubTopic getSubTopicOrThrow(Long id) {
        return curriculumSubTopicRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.curriculum.subTopicNotFound", new Object[]{id},
                        "Không tìm thấy Sub Topic id=" + id));
    }

    /** UC-16b Main Flow bước 1-2: tạo bản sao tùy biến gắn với 1 điểm trường. */
    @Transactional
    public CurriculumResponse createCustomCopy(CreateCustomCurriculumRequest request, Long actorUserId) {
        Site site = siteRepository.findById(request.siteId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.curriculum.siteNotFound", new Object[]{request.siteId()},
                        "Không tìm thấy điểm trường id=" + request.siteId()));
        requireSiteManagerForSite(site.getId(), actorUserId);
        Curriculum parent = getCurriculumOrThrow(request.parentCurriculumId());
        if (parent.getSite() != null) {
            throw new CurriculumNotEditableException(
                    "error.curriculumNotEditable.notStandard", new Object[]{},
                    "Khung chương trình được chọn không phải khung chuẩn (đã là bản tùy biến), không thể dùng làm khung gốc.");
        }
        if (curriculumRepository.findByCode(request.code()).isPresent()) {
            throw new DuplicateCurriculumCodeException("error.duplicateCurriculumCode.default", new Object[]{request.code()}, "Mã khung chương trình đã tồn tại: " + request.code());
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.curriculum.actorNotFound", new Object[]{actorUserId},
                        "Không tìm thấy tài khoản id=" + actorUserId));

        // Main Flow bước 2 -- tạo bản sao, sao chép nội dung từ bản gốc.
        Curriculum copy = new Curriculum();
        copy.setCode(request.code());
        copy.setName(request.name() == null || request.name().isBlank() ? parent.getName() : request.name());
        copy.setSite(site);
        copy.setParentCurriculum(parent);
        copy.setClassCategory(parent.getClassCategory());
        copy.setLevel(parent.getLevel());
        copy.setGradeLevel(parent.getGradeLevel());
        copy.setTrack(parent.getTrack());
        copy.setTotalPeriods(parent.getTotalPeriods());
        copy.setDefaultGradePassThreshold(parent.getDefaultGradePassThreshold());
        copy.setCreatedBy(actor);
        copy = curriculumRepository.save(copy);

        writeCurriculumHistory(copy, actor, CurriculumHistory.Action.CREATED);
        return toResponse(copy);
    }

    /** Main Flow bước 3, A1 (lưu nháp): chỉnh sửa nội dung bản tùy biến — chỉ khi đang DRAFT. */
    @Transactional
    public CurriculumResponse updateCustomCopy(Long id, UpdateCustomCurriculumRequest request, Long actorUserId) {
        Curriculum curriculum = getCurriculumOrThrow(id);
        requireCustomCopy(curriculum);
        requireSiteManagerForSite(curriculum.getSite().getId(), actorUserId);
        if (curriculum.getStatus() != Curriculum.Status.DRAFT) {
            throw new CurriculumNotEditableException(
                    "error.curriculumNotEditable.notDraftForEdit", new Object[]{curriculum.getStatus()},
                    "Khung chương trình tùy biến này đang ở trạng thái " + curriculum.getStatus()
                            + " — chỉ chỉnh sửa được khi còn ở trạng thái Nháp (DRAFT).");
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.curriculum.actorNotFound", new Object[]{actorUserId},
                        "Không tìm thấy tài khoản id=" + actorUserId));

        curriculum.setName(request.name());
        curriculum.setLevel(request.level());
        curriculum.setTotalPeriods(request.totalPeriods());
        if (request.defaultGradePassThreshold() != null) {
            curriculum.setDefaultGradePassThreshold(request.defaultGradePassThreshold());
        }
        curriculum = curriculumRepository.save(curriculum);

        writeCurriculumHistory(curriculum, actor, CurriculumHistory.Action.UPDATED);
        return toResponse(curriculum);
    }

    /**
     * Main Flow bước 4-5: gửi đề xuất tùy biến để Trưởng phòng đào tạo phê
     * duyệt. Cũng dùng lại cho A1 của UC-17 (đề xuất lại sau khi bị từ chối
     * — curriculum đã quay về DRAFT nên gọi lại y hệt bước này).
     */
    @Transactional
    public CurriculumApprovalResponse submitForApproval(Long id, Long actorUserId) {
        Curriculum curriculum = getCurriculumOrThrow(id);
        requireCustomCopy(curriculum);
        requireSiteManagerForSite(curriculum.getSite().getId(), actorUserId);
        if (curriculum.getStatus() != Curriculum.Status.DRAFT) {
            throw new CurriculumNotEditableException(
                    "error.curriculumNotEditable.notDraftForSubmit", new Object[]{curriculum.getStatus()},
                    "Khung chương trình tùy biến này đang ở trạng thái " + curriculum.getStatus()
                            + " — chỉ gửi duyệt được khi còn ở trạng thái Nháp (DRAFT).");
        }
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.curriculum.actorNotFound", new Object[]{actorUserId},
                        "Không tìm thấy tài khoản id=" + actorUserId));

        curriculum.setStatus(Curriculum.Status.PENDING_APPROVAL);
        curriculum = curriculumRepository.save(curriculum);
        writeCurriculumHistory(curriculum, actor, CurriculumHistory.Action.UPDATED);

        ApprovalFlow flow = new ApprovalFlow();
        flow.setEntityType(ApprovalFlow.EntityType.CURRICULUM);
        flow.setEntityId(curriculum.getId());
        flow.setStatus(ApprovalFlow.Status.PENDING);
        flow.setSubmittedBy(actor);
        flow = approvalFlowRepository.save(flow);

        return toResponse(flow, curriculum);
    }

    /** UC-17 Main Flow bước 1: danh sách đề xuất tùy biến đang Chờ duyệt. */
    @Transactional(readOnly = true)
    public List<CurriculumApprovalResponse> listPendingApprovals(Long actorUserId) {
        return approvalFlowRepository
                .findByEntityTypeAndStatusOrderBySubmittedAtAsc(ApprovalFlow.EntityType.CURRICULUM, ApprovalFlow.Status.PENDING)
                .stream()
                .map(flow -> toResponse(flow, getCurriculumOrThrow(flow.getEntityId())))
                .toList();
    }

    /**
     * Main Flow bước 3-5: Trưởng phòng đào tạo Phê duyệt (-> ACTIVE, có hiệu
     * lực) hoặc Từ chối kèm lý do (-> quay về DRAFT để Quản lý điểm trường
     * sửa và đề xuất lại — A1).
     */
    @Transactional
    public CurriculumApprovalResponse decideApproval(Long approvalFlowId, DecideCurriculumApprovalRequest request, Long actorUserId) {
        ApprovalFlow flow = approvalFlowRepository.findById(approvalFlowId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.curriculum.approvalNotFound", new Object[]{approvalFlowId},
                        "Không tìm thấy đề xuất id=" + approvalFlowId));
        if (flow.getEntityType() != ApprovalFlow.EntityType.CURRICULUM) {
            throw new ResourceNotFoundException(
                    "error.curriculum.approvalFlowNotFound", new Object[]{approvalFlowId},
                    "Không tìm thấy đề xuất khung chương trình id=" + approvalFlowId);
        }
        if (flow.getStatus() != ApprovalFlow.Status.PENDING) {
            throw new ApprovalAlreadyDecidedException("error.approvalAlreadyDecided.curriculum", new Object[]{flow.getStatus()}, "Đề xuất này đã được quyết định (" + flow.getStatus() + ").");
        }
        Curriculum curriculum = getCurriculumOrThrow(flow.getEntityId());
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.curriculum.actorNotFound", new Object[]{actorUserId},
                        "Không tìm thấy tài khoản id=" + actorUserId));
        ApprovalFlow.Decision decision = ApprovalFlow.Decision.valueOf(request.decision());

        flow.setDecision(decision);
        flow.setApprover(actor);
        flow.setComment(request.comment());
        flow.setDecidedAt(OffsetDateTime.now());

        if (decision == ApprovalFlow.Decision.APPROVED) {
            flow.setStatus(ApprovalFlow.Status.APPROVED);
            curriculum.setStatus(Curriculum.Status.ACTIVE);
            curriculum.setApprovedBy(actor);
            curriculum.setApprovedAt(OffsetDateTime.now());
        } else {
            if (request.comment() == null || request.comment().isBlank()) {
                throw new IllegalArgumentException("Từ chối đề xuất bắt buộc phải kèm lý do (comment).");
            }
            flow.setStatus(ApprovalFlow.Status.REJECTED);
            // Không có trạng thái REJECTED riêng trong curriculums.status (SDD) --
            // quay về DRAFT để Quản lý điểm trường sửa và đề xuất lại (A1).
            curriculum.setStatus(Curriculum.Status.DRAFT);
        }
        flow = approvalFlowRepository.save(flow);
        curriculum = curriculumRepository.save(curriculum);
        writeCurriculumHistory(curriculum, actor, CurriculumHistory.Action.UPDATED);

        return toResponse(flow, curriculum);
    }

    private void requireCustomCopy(Curriculum curriculum) {
        if (curriculum.getSite() == null) {
            throw new CurriculumNotEditableException(
                    "error.curriculumNotEditable.notCustomCopy", new Object[]{},
                    "Khung chương trình này là khung chuẩn, không phải bản tùy biến.");
        }
    }

    private void requireSiteManagerForSite(Long siteId, Long actorUserId) {
        if (!siteManagerRepository.existsBySiteIdAndUserIdAndRoleTypeAndAssignedToIsNull(
                siteId, actorUserId, SiteManager.RoleType.SITE_MANAGER)) {
            throw new NotSiteManagerForSiteException(
                    "error.notSiteManagerForSite.default", new Object[]{}, "Bạn không được gán phụ trách điểm trường này.");
        }
    }


    private void writeCurriculumHistory(Curriculum curriculum, User actor, CurriculumHistory.Action action) {
        CurriculumHistory history = new CurriculumHistory();
        history.setCurriculum(curriculum);
        history.setChangedBy(actor);
        history.setAction(action);
        history.setDetails(curriculumSnapshot(curriculum));
        curriculumHistoryRepository.save(history);
    }

    private CurriculumApprovalResponse toResponse(ApprovalFlow flow, Curriculum curriculum) {
        return new CurriculumApprovalResponse(
                flow.getId(), curriculum.getId(), curriculum.getCode(), curriculum.getName(),
                flow.getStatus().name(), flow.getSubmittedBy().getId(), flow.getSubmittedAt(),
                flow.getApprover() == null ? null : flow.getApprover().getId(),
                flow.getDecision() == null ? null : flow.getDecision().name(),
                flow.getComment(), flow.getDecidedAt());
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
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.curriculum.notFound", new Object[]{id},
                        "Không tìm thấy khung chương trình id=" + id));
    }

    private CurriculumResponse toResponse(Curriculum c) {
        return new CurriculumResponse(
                c.getId(), c.getCode(), c.getName(),
                c.getSite() == null ? null : c.getSite().getId(),
                c.getParentCurriculum() == null ? null : c.getParentCurriculum().getId(),
                c.getClassCategory().name(), c.getLevel(),
                c.getGradeLevel() == null ? null : c.getGradeLevel().name(),
                c.getTrack() == null ? null : c.getTrack().name(),
                c.getTotalPeriods(),
                c.getDefaultGradePassThreshold(), c.getStatus().name(),
                c.getCreatedBy().getId(), c.getApprovedBy() == null ? null : c.getApprovedBy().getId());
    }

    /** V140 — chuỗi null/rỗng = chưa phân loại (không throw), khớp cách "level" (String tự do) đang xử lý. */
    private Curriculum.GradeLevel parseGradeLevel(String s) {
        return s == null || s.isBlank() ? null : Curriculum.GradeLevel.valueOf(s);
    }

    /** V140 — chuỗi null/rỗng = chưa phân loại. */
    private Curriculum.Track parseTrack(String s) {
        return s == null || s.isBlank() ? null : Curriculum.Track.valueOf(s);
    }

    private CurriculumSubjectResponse toResponse(CurriculumSubject s) {
        return new CurriculumSubjectResponse(
                s.getId(), s.getCurriculum().getId(), s.getSubjectCode().name(),
                s.getSkill() == null ? null : s.getSkill().getId(), s.getName(),
                s.getPeriodCount(), s.getDisplayOrder());
    }

    private BookResponse toResponse(Book b) {
        return new BookResponse(b.getId(), b.getCurriculum().getId(), b.getTitle(), b.getDisplayOrder());
    }

    private UnitResponse toResponse(CurriculumUnit u) {
        return new UnitResponse(u.getId(), u.getBook().getId(), u.getTitle(), u.getDisplayOrder());
    }

    private SubTopicResponse toResponse(CurriculumSubTopic s) {
        return new SubTopicResponse(s.getId(), s.getUnit().getId(), s.getTitle(), s.getDisplayOrder());
    }
}
