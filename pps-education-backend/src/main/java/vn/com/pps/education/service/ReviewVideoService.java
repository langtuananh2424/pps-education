package vn.com.pps.education.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.com.pps.education.domain.AttemptIntegrityEvent;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.ClassSession;
import vn.com.pps.education.domain.ClassTeacher;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.ReviewVideo;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.ReviewVideoConnectionAnswer;
import vn.com.pps.education.domain.ReviewVideoConnectionChoice;
import vn.com.pps.education.domain.ReviewVideoConnectionQuestion;
import vn.com.pps.education.domain.ReviewVideoConnectionQuestionSlot;
import vn.com.pps.education.domain.ReviewVideoProgress;
import vn.com.pps.education.domain.ReviewVideoQuestion;
import vn.com.pps.education.domain.ReviewVideoQuestionSubmission;
import vn.com.pps.education.domain.ReviewVideoSet;
import vn.com.pps.education.domain.ReviewVideoSetClassAssignment;
import vn.com.pps.education.domain.ReviewVideoSetHistory;
import vn.com.pps.education.domain.ReviewVideoWatchSession;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AddReviewVideoConnectionQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.ConnectionAnswerResult;
import vn.com.pps.education.dto.ConnectionChoiceRequest;
import vn.com.pps.education.dto.CreateReviewVideoSetRequest;
import vn.com.pps.education.dto.GradeReviewVideoSubmissionRequest;
import vn.com.pps.education.dto.MyReviewVideoAssignmentResponse;
import vn.com.pps.education.dto.PendingGradingClassSummaryResponse;
import vn.com.pps.education.dto.ReportVideoProgressRequest;
import vn.com.pps.education.dto.ReviewVideoAssignmentResponse;
import vn.com.pps.education.dto.ReviewVideoAssignmentStatsResponse;
import vn.com.pps.education.dto.ReviewVideoConnectionChoiceResponse;
import vn.com.pps.education.dto.ReviewVideoConnectionQuestionResponse;
import vn.com.pps.education.dto.ReviewVideoConnectionQuizResultResponse;
import vn.com.pps.education.dto.ReviewVideoProgressResponse;
import vn.com.pps.education.dto.ReviewVideoQuestionResponse;
import vn.com.pps.education.dto.ReviewVideoResponse;
import vn.com.pps.education.dto.ReviewVideoSetResponse;
import vn.com.pps.education.dto.ReviewVideoSetStatsResponse;
import vn.com.pps.education.dto.ReviewVideoSubmissionResponse;
import vn.com.pps.education.dto.StartWatchSessionResponse;
import vn.com.pps.education.dto.SubmitConnectionAnswersRequest;
import vn.com.pps.education.dto.SubmitReviewVideoAudioRequest;
import vn.com.pps.education.dto.UpdateConnectionChoiceRequest;
import vn.com.pps.education.dto.UpdateReviewVideoConnectionQuestionRequest;
import vn.com.pps.education.dto.UpdateReviewVideoQuestionRequest;
import vn.com.pps.education.dto.UpdateReviewVideoSetRequest;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.QuizAlreadyCompletedException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.RetakeNotAllowedException;
import vn.com.pps.education.exception.ReviewVideoQuestionOverlapException;
import vn.com.pps.education.exception.SubmissionPastDeadlineException;
import vn.com.pps.education.exception.VideoNotYetQualifiedException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.CurriculumSubjectRepository;
import vn.com.pps.education.repository.ReviewVideoAssignmentRepository;
import vn.com.pps.education.repository.ReviewVideoConnectionAnswerRepository;
import vn.com.pps.education.repository.ReviewVideoConnectionQuestionSlotRepository;
import vn.com.pps.education.repository.ReviewVideoConnectionChoiceRepository;
import vn.com.pps.education.repository.ReviewVideoConnectionQuestionRepository;
import vn.com.pps.education.repository.ReviewVideoProgressRepository;
import vn.com.pps.education.repository.ReviewVideoQuestionRepository;
import vn.com.pps.education.repository.ReviewVideoQuestionSubmissionRepository;
import vn.com.pps.education.repository.ReviewVideoRepository;
import vn.com.pps.education.repository.ReviewVideoSetClassAssignmentRepository;
import vn.com.pps.education.repository.ReviewVideoSetHistoryRepository;
import vn.com.pps.education.repository.ReviewVideoSetRepository;
import vn.com.pps.education.repository.ReviewVideoWatchSessionRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.service.integrity.AttemptIntegrityService;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-23: Quản lý Kho Video Ôn tập (FR-LMS-01, Giáo viên) + UC-23a: Xem &
 * Theo dõi Kho Video Ôn tập (FR-LMS-01, Học sinh xem + báo tiến độ, Giáo
 * viên xem thống kê) + UC-23b: Nộp & Chấm điểm Audio cho Video Phản xạ
 * (FR-LMS-01, Học sinh nộp audio cho video REFLEX, Giáo viên chấm điểm).
 * Xem docs/uc/phan-he-07-lms-portal.md.
 *
 * Tái cấu trúc 2026-07-27 từ "Kho bài giảng" (LessonService) — đã xác
 * nhận với người dùng: bỏ hẳn PDF/Slide/Word, chỉ còn video/audio, thêm
 * theo dõi tiến độ xem + thống kê giáo viên (hoàn toàn mới).
 *
 * Tệp (video/audio) đã upload lên CDN/Object Storage (Cloudflare R2, qua
 * MediaStorageService module REVIEW_VIDEO) hoặc là link YouTube — Service
 * chỉ nhận URL + thời lượng (giây) đã có sẵn, không tự làm multipart
 * upload, không tự dò thời lượng (FE tự phát hiện trước khi gọi API).
 *
 * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * Publish (status=PUBLISHED) giờ chỉ đánh dấu bộ "đủ điều kiện dùng làm
 * nguồn" — KHÔNG còn tự động cho học sinh xem ngay (áp dụng cho CẢ
 * CONNECTION lẫn REFLEX). Học sinh chỉ xem được khi có
 * {@link ReviewVideoAssignment} ACTIVE giao cho lớp mình — tạo qua
 * {@code deliverToClass}, gọi TỪ StudentCommentService khi Giáo viên chọn
 * bộ này làm "BTVN buổi sau" ở Nhận xét (UC-21), không còn tự phát sinh
 * từ hành động publish nữa.
 */
@Service
public class ReviewVideoService {

    private final ReviewVideoSetRepository reviewVideoSetRepository;
    private final ReviewVideoSetClassAssignmentRepository reviewVideoSetClassAssignmentRepository;
    private final ReviewVideoRepository reviewVideoRepository;
    private final ReviewVideoSetHistoryRepository reviewVideoSetHistoryRepository;
    private final ReviewVideoProgressRepository reviewVideoProgressRepository;
    private final ReviewVideoQuestionRepository reviewVideoQuestionRepository;
    private final ReviewVideoQuestionSubmissionRepository reviewVideoQuestionSubmissionRepository;
    private final ReviewVideoWatchSessionRepository reviewVideoWatchSessionRepository;
    private final ReviewVideoAssignmentRepository reviewVideoAssignmentRepository;
    private final ReviewVideoConnectionQuestionRepository reviewVideoConnectionQuestionRepository;
    private final ReviewVideoConnectionChoiceRepository reviewVideoConnectionChoiceRepository;
    private final ReviewVideoConnectionAnswerRepository reviewVideoConnectionAnswerRepository;
    private final ReviewVideoConnectionQuestionSlotRepository reviewVideoConnectionQuestionSlotRepository;
    private final CurriculumRepository curriculumRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AttemptIntegrityService attemptIntegrityService;
    private final ReviewVideoSettings reviewVideoSettings;
    private final PermissionEvaluationService permissionEvaluationService;
    /** V71: chạy riêng 1 giao dịch lồng (PROPAGATION_REQUIRES_NEW) khi thử tạo bản giao — race thua (bắt
     * DataIntegrityViolationException do UNIQUE index) chỉ rollback đúng giao dịch con này, không kéo
     * theo giao dịch ngoài (đang cần đọc lại bản ghi đã thắng) — xem Javadoc deliverToClass. */
    private final TransactionTemplate requiresNewTransactionTemplate;

    private static final String PERM_REVIEW_VIDEO_MANAGE = "lms.review-video.manage";

    public ReviewVideoService(ReviewVideoSetRepository reviewVideoSetRepository,
                               ReviewVideoSetClassAssignmentRepository reviewVideoSetClassAssignmentRepository,
                               ReviewVideoRepository reviewVideoRepository,
                               ReviewVideoSetHistoryRepository reviewVideoSetHistoryRepository,
                               ReviewVideoProgressRepository reviewVideoProgressRepository,
                               ReviewVideoQuestionRepository reviewVideoQuestionRepository,
                               ReviewVideoQuestionSubmissionRepository reviewVideoQuestionSubmissionRepository,
                               ReviewVideoWatchSessionRepository reviewVideoWatchSessionRepository,
                               ReviewVideoAssignmentRepository reviewVideoAssignmentRepository,
                               ReviewVideoConnectionQuestionRepository reviewVideoConnectionQuestionRepository,
                               ReviewVideoConnectionChoiceRepository reviewVideoConnectionChoiceRepository,
                               ReviewVideoConnectionAnswerRepository reviewVideoConnectionAnswerRepository,
                               ReviewVideoConnectionQuestionSlotRepository reviewVideoConnectionQuestionSlotRepository,
                               CurriculumRepository curriculumRepository,
                               SchoolClassRepository schoolClassRepository,
                               CurriculumSubjectRepository curriculumSubjectRepository,
                               ClassTeacherRepository classTeacherRepository,
                               ClassEnrollmentRepository classEnrollmentRepository,
                               StudentRepository studentRepository,
                               UserRepository userRepository,
                               NotificationService notificationService,
                               AttemptIntegrityService attemptIntegrityService,
                               ReviewVideoSettings reviewVideoSettings,
                               PermissionEvaluationService permissionEvaluationService,
                               PlatformTransactionManager transactionManager) {
        this.reviewVideoSetRepository = reviewVideoSetRepository;
        this.reviewVideoSetClassAssignmentRepository = reviewVideoSetClassAssignmentRepository;
        this.reviewVideoRepository = reviewVideoRepository;
        this.reviewVideoSetHistoryRepository = reviewVideoSetHistoryRepository;
        this.reviewVideoProgressRepository = reviewVideoProgressRepository;
        this.reviewVideoQuestionRepository = reviewVideoQuestionRepository;
        this.reviewVideoQuestionSubmissionRepository = reviewVideoQuestionSubmissionRepository;
        this.reviewVideoWatchSessionRepository = reviewVideoWatchSessionRepository;
        this.reviewVideoAssignmentRepository = reviewVideoAssignmentRepository;
        this.reviewVideoConnectionQuestionRepository = reviewVideoConnectionQuestionRepository;
        this.reviewVideoConnectionChoiceRepository = reviewVideoConnectionChoiceRepository;
        this.reviewVideoConnectionAnswerRepository = reviewVideoConnectionAnswerRepository;
        this.reviewVideoConnectionQuestionSlotRepository = reviewVideoConnectionQuestionSlotRepository;
        this.curriculumRepository = curriculumRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.curriculumSubjectRepository = curriculumSubjectRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.attemptIntegrityService = attemptIntegrityService;
        this.reviewVideoSettings = reviewVideoSettings;
        this.permissionEvaluationService = permissionEvaluationService;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** UC-23 Main Flow bước 1: tạo bộ mới (metadata) — curriculum CHỈ dùng lọc/tìm kiếm (V98, xem Javadoc lớp). */
    @Transactional
    public ReviewVideoSetResponse createSet(CreateReviewVideoSetRequest request, Long actorUserId) {
        User actor = getUserOrThrow(actorUserId);
        Curriculum curriculum = curriculumRepository.findByIdAndDeletedAtIsNull(request.curriculumId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung chương trình id=" + request.curriculumId()));
        requireAssignedTeacherForCurriculum(request.curriculumId(), actorUserId);

        ReviewVideoSet set = new ReviewVideoSet();
        set.setCode(request.code());
        set.setTitle(request.title());
        set.setVideoType(ReviewVideoSet.VideoType.valueOf(request.videoType()));
        set.setCurriculum(curriculum);
        set.setTeacherType(ReviewVideoSet.TeacherType.valueOf(request.teacherType()));
        if (request.subjectId() != null) {
            set.setSubject(curriculumSubjectOrThrow(request.subjectId()));
        }
        set.setDisplayOrder(request.displayOrder());
        set.setCreatedBy(actor);
        set = reviewVideoSetRepository.save(set);

        writeHistory(set, actor, ReviewVideoSetHistory.Action.CREATED);
        return toResponse(set);
    }

    /** UC-23 Main Flow bước 4-5: sửa metadata, công bố (PUBLISHED) hoặc gỡ bộ (status=ARCHIVED — soft-remove, không xóa cứng). */
    @Transactional
    public ReviewVideoSetResponse updateSet(Long id, UpdateReviewVideoSetRequest request, Long actorUserId) {
        ReviewVideoSet set = getSetOrThrow(id);
        requireOwnerScope(set, actorUserId);
        User actor = getUserOrThrow(actorUserId);

        set.setTitle(request.title());
        set.setTeacherType(ReviewVideoSet.TeacherType.valueOf(request.teacherType()));
        if (request.subjectId() != null) {
            set.setSubject(curriculumSubjectOrThrow(request.subjectId()));
        }
        set.setDisplayOrder(request.displayOrder());
        ReviewVideoSet.Status newStatus = ReviewVideoSet.Status.valueOf(request.status());
        if (newStatus == ReviewVideoSet.Status.PUBLISHED) {
            requireConnectionVideosHaveQuestions(set);
            if (set.getStatus() != ReviewVideoSet.Status.PUBLISHED) {
                set.setPublishedAt(OffsetDateTime.now());
            }
        }
        set.setStatus(newStatus);
        set = reviewVideoSetRepository.save(set);

        writeHistory(set, actor, ReviewVideoSetHistory.Action.UPDATED);
        return toResponse(set);
    }

    /** Kho Video — lọc theo khung chương trình/loại giáo viên (V98, mirror ExamService#listExams). */
    @Transactional(readOnly = true)
    public List<ReviewVideoSetResponse> listSets(Long curriculumId, String teacherType, Long actorUserId) {
        ReviewVideoSet.TeacherType type = teacherType == null ? null : ReviewVideoSet.TeacherType.valueOf(teacherType);
        List<ReviewVideoSet> sets;
        if (curriculumId != null && type != null) {
            sets = reviewVideoSetRepository.findByCurriculumIdAndTeacherTypeOrderByDisplayOrder(curriculumId, type);
        } else if (curriculumId != null) {
            sets = reviewVideoSetRepository.findByCurriculumIdOrderByDisplayOrder(curriculumId);
        } else if (type != null) {
            sets = reviewVideoSetRepository.findByTeacherTypeOrderByDisplayOrder(type);
        } else {
            sets = reviewVideoSetRepository.findAllByOrderByDisplayOrder();
        }
        return sets.stream().map(this::toResponse).toList();
    }

    /** V98 (mirror ExamService#assignToClass) — gán Bộ cho 1 lớp, điều kiện hiển thị DUY NHẤT cho học sinh lớp đó. Idempotent. */
    @Transactional
    public void assignToClass(Long setId, Long classId, Long actorUserId) {
        ReviewVideoSet set = getSetOrThrow(setId);
        requireAssignedTeacher(classId, actorUserId);
        SchoolClass schoolClass = getClassOrThrow(classId);
        if (reviewVideoSetClassAssignmentRepository.existsByReviewVideoSetIdAndSchoolClassId(setId, classId)) {
            return;
        }
        User actor = getUserOrThrow(actorUserId);
        ReviewVideoSetClassAssignment assignment = new ReviewVideoSetClassAssignment();
        assignment.setReviewVideoSet(set);
        assignment.setSchoolClass(schoolClass);
        assignment.setAssignedBy(actor);
        reviewVideoSetClassAssignmentRepository.save(assignment);
    }

    /** V98 (mirror ExamService#unassignFromClass) — gỡ Bộ khỏi 1 lớp, xóa cứng (join thuần, không phải bản giao). */
    @Transactional
    public void unassignFromClass(Long setId, Long classId, Long actorUserId) {
        getSetOrThrow(setId);
        requireAssignedTeacher(classId, actorUserId);
        reviewVideoSetClassAssignmentRepository.findByReviewVideoSetIdAndSchoolClassId(setId, classId)
                .ifPresent(reviewVideoSetClassAssignmentRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<ClassResponse> listAssignedClasses(Long setId, Long actorUserId) {
        getSetOrThrow(setId);
        return reviewVideoSetClassAssignmentRepository.findByReviewVideoSetId(setId).stream()
                .map(a -> toResponse(a.getSchoolClass())).toList();
    }

    /**
     * UC-23a: GV xem TẤT CẢ bộ đã gán tường minh cho lớp X (mọi trạng
     * thái) — để chọn nguồn khi soạn "BTVN buổi sau" (V98, bổ sung ngoài
     * SDD gốc, đã xác nhận với người dùng 2026-08-06 — thay logic OR
     * curriculum/lớp cũ bằng {@link ReviewVideoSetClassAssignment}, mirror
     * ExerciseRepository#findAvailableForClass). HS chỉ thấy bộ đã có
     * {@link ReviewVideoAssignment} giao cho lớp mình (V65, bổ sung ngoài
     * SDD gốc, đã xác nhận với người dùng — publish không còn đồng nghĩa
     * xem được ngay). Bổ sung 2026-08-06: KHÔNG còn giới hạn status=ACTIVE
     * — bản giao đã CANCELLED (bị thay bằng lần giao lại mới hơn) vẫn cho
     * học sinh xem, không biến mất khỏi tầm nhìn (mirror
     * ExerciseAttemptService#listMyAssignedExercises, xem
     * {@link #assignedSetIdsForStudentInClasses}).
     */
    @Transactional(readOnly = true)
    public List<ReviewVideoSetResponse> listByClass(Long classId, Long actorUserId) {
        getClassOrThrow(classId);
        if (isStudent(actorUserId)) {
            Student student = studentRepository.findByUserId(actorUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId));
            requireStudentEnrolledInClass(classId, actorUserId);
            Set<Long> assignedSetIds = assignedSetIdsForStudentInClasses(List.of(classId), student.getId());
            return reviewVideoSetRepository.findAvailableForClass(classId, ReviewVideoSet.Status.PUBLISHED)
                    .stream().filter(s -> assignedSetIds.contains(s.getId())).map(this::toResponse).toList();
        }
        return reviewVideoSetRepository.findAvailableForClass(classId, null)
                .stream().map(this::toResponse).toList();
    }

    /**
     * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * giao 1 bộ Video Ôn tập (CONNECTION hoặc REFLEX) cho TOÀN BỘ học
     * sinh ACTIVE của 1 lớp, hạn nộp = tham số dueAt (buổi kế tiếp, tính
     * ở StudentCommentService). Gọi TỪ StudentCommentService khi Giáo
     * viên chọn bộ này làm "BTVN buổi sau" — KHÔNG expose qua Controller.
     * Validate bộ đang PUBLISHED + đã gán tường minh cho lớp được giao
     * (V98: {@link ReviewVideoSetClassAssignment}).
     *
     * V69 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31,
     * fix bug "Đã nộp bài" hiện sai khi giao lại): 1 lần giao MỚI cho
     * cùng (bộ, lớp) hủy (CANCELLED) mọi lần giao ACTIVE cũ trước đó —
     * "giao lại = 1 lượt MỚI, học sinh phải làm lại", đồng thời đảm bảo
     * tại mọi thời điểm chỉ có TỐI ĐA 1 lần giao ACTIVE cho 1 (bộ, lớp),
     * tránh mơ hồ khi resolveStudentAccess tra lần giao nào đang hiệu lực.
     *
     * V70 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31,
     * fix bug thông báo bị gửi lặp N lần): "Gửi nhận xét" hàng loạt cho
     * nhiều học sinh CÙNG buổi, CÙNG chọn 1 bộ video → FE gửi N request
     * riêng biệt, mỗi request gọi ĐÚNG đây với CÙNG (setId, classId,
     * dueAt) — dueAt tính từ buổi học (resolveNextSessionDueAt), giống
     * hệt nhau cho mọi học sinh trong cùng buổi. TRƯỚC khi hủy+tạo mới:
     * nếu đã có 1 lần giao ACTIVE khớp CHÍNH XÁC (bộ, lớp, dueAt) — tức
     * request TRÙNG LẶP trong CÙNG 1 đợt gửi, không phải giao lại ở buổi
     * KHÁC (dueAt sẽ khác) — tái dùng nguyên bản ghi đó, KHÔNG tạo mới,
     * KHÔNG gọi lại notifyAssignedStudents (tránh N thông báo giống hệt
     * nhau cho toàn bộ học sinh lớp).
     *
     * V71 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-03,
     * fix race condition của chính cơ chế chống trùng V70): check-rồi-
     * insert ở tầng ứng dụng KHÔNG atomic — 2 request tới gần như đồng
     * thời có thể cùng thấy "chưa có bản giao" (bước check) TRƯỚC KHI 1
     * trong 2 kịp INSERT xong, dẫn tới vẫn tạo ra 2 bản giao + 2 thông báo
     * trùng (đã tái hiện thực tế). Thêm UNIQUE index DB
     * (review_video_set_id, class_id, due_at) WHERE status='ACTIVE' làm
     * chốt chặn cuối cùng — INSERT chạy trong giao dịch lồng
     * PROPAGATION_REQUIRES_NEW để nếu thua race (bắt
     * DataIntegrityViolationException do vi phạm UNIQUE) chỉ giao dịch
     * con rollback, giao dịch ngoài (đang cancel bản giao cũ) không bị
     * kéo theo — sau đó đọc lại bản ghi đã thắng, KHÔNG tạo mới/không báo
     * lại đúng như tinh thần V70.
     */
    @Transactional
    public ReviewVideoAssignment deliverToClass(Long setId, Long classId, OffsetDateTime dueAt, Long actorUserId) {
        return deliverToClass(setId, classId, dueAt, actorUserId, null);
    }

    /**
     * V123 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-14): overload nhận thêm buổi
     * học nguồn — mirror {@link ExerciseService#deliverToClass(Long, Long, OffsetDateTime, Long, ClassSession)}.
     */
    @Transactional
    public ReviewVideoAssignment deliverToClass(Long setId, Long classId, OffsetDateTime dueAt, Long actorUserId,
                                                 ClassSession sourceClassSession) {
        // Cắt về độ chính xác microsecond + so theo instant thực (không so cả offset) NGAY từ đầu —
        // xem giải thích chi tiết ở ExerciseService#deliverToClass/sameDueAt() (bug thật, tái hiện
        // được cả khi chạy 1 mình với DB sạch, KHÔNG phải lỗi rò rỉ dữ liệu giữa các test).
        dueAt = dueAt == null ? null : dueAt.truncatedTo(ChronoUnit.MICROS);
        ReviewVideoSet set = getSetOrThrow(setId);
        if (set.getStatus() != ReviewVideoSet.Status.PUBLISHED) {
            throw new IllegalArgumentException("Bộ video này chưa Publish — không giao lớp được.");
        }
        requireAssignedTeacher(classId, actorUserId);
        SchoolClass schoolClass = getClassOrThrow(classId);
        // V98: thay logic OR curriculum/lớp cũ bằng kiểm tra đã gán tường minh (ReviewVideoSetClassAssignment).
        if (!reviewVideoSetClassAssignmentRepository.existsByReviewVideoSetIdAndSchoolClassId(setId, classId)) {
            throw new IllegalArgumentException("Bộ video này không thuộc phạm vi lớp đang chọn.");
        }
        User actor = getUserOrThrow(actorUserId);

        OffsetDateTime finalDueAt = dueAt;
        Long sourceSessionId = sourceClassSession == null ? null : sourceClassSession.getId();
        List<ReviewVideoAssignment> activeForSetAndClass = reviewVideoAssignmentRepository
                .findByReviewVideoSetIdAndSchoolClassIdAndStatus(setId, classId, ReviewVideoAssignment.Status.ACTIVE);
        // V128 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — mirror ĐÚNG
        // ExerciseService#deliverToClass: "giao lại = huỷ bản cũ" giờ CHỈ áp dụng khi giao lại từ ĐÚNG
        // CÙNG buổi Nhận xét nguồn — giao cùng 1 bộ video từ 2 buổi khác nhau giờ là 2 bài tập độc lập.
        List<ReviewVideoAssignment> activeFromSameSession = activeForSetAndClass.stream()
                .filter(a -> java.util.Objects.equals(a.getSourceClassSession() == null ? null : a.getSourceClassSession().getId(), sourceSessionId))
                .toList();
        var sameSession = activeFromSameSession.stream().filter(a -> sameDueAt(a.getDueAt(), finalDueAt)).findFirst();
        if (sameSession.isPresent()) {
            return sameSession.get();
        }
        activeFromSameSession.forEach(this::cancelAssignment);

        ReviewVideoAssignment assignment;
        try {
            assignment = requiresNewTransactionTemplate.execute(status -> {
                ReviewVideoAssignment a = new ReviewVideoAssignment();
                a.setReviewVideoSet(set);
                a.setSchoolClass(schoolClass);
                a.setAssignedBy(actor);
                a.setDueAt(finalDueAt);
                a.setSourceClassSession(sourceClassSession);
                return reviewVideoAssignmentRepository.saveAndFlush(a);
            });
        } catch (DataIntegrityViolationException e) {
            // Race condition (V71): request khác đã tạo xong bản giao ACTIVE trùng (setId, classId, dueAt)
            // trong lúc request này đang xử lý — "Gửi nhận xét" hàng loạt cho nhiều học sinh CÙNG buổi,
            // CÙNG chọn 1 nguồn gửi N request đồng thời (Promise.allSettled ở FE). Chạy trong giao dịch
            // lồng REQUIRES_NEW để chỉ giao dịch con này rollback khi thua race (UNIQUE index chặn), giao
            // dịch ngoài không bị ảnh hưởng — đọc lại bản ghi đã thắng, KHÔNG tạo mới/không báo lại. V128:
            // lọc thêm theo đúng buổi nguồn, tránh khớp nhầm bản giao của 1 buổi Nhận xét khác.
            return reviewVideoAssignmentRepository
                    .findByReviewVideoSetIdAndSchoolClassIdAndStatus(set.getId(), schoolClass.getId(), ReviewVideoAssignment.Status.ACTIVE)
                    .stream()
                    .filter(a -> java.util.Objects.equals(a.getSourceClassSession() == null ? null : a.getSourceClassSession().getId(), sourceSessionId))
                    .filter(a -> sameDueAt(a.getDueAt(), finalDueAt)).findFirst()
                    .orElseThrow(() -> e);
        }

        notifyAssignedStudents(schoolClass, set, assignment);
        return assignment;
    }

    /**
     * So 2 due_at theo INSTANT thực (isEqual), không so cả offset — TIMESTAMPTZ round-trip qua
     * JDBC trả về offset UTC "Z" trong khi OffsetDateTime.now() ở tầng gọi mang offset hệ thống
     * (VD "+07:00"); Objects.equals() coi 2 giá trị cùng 1 thời điểm nhưng khác offset là KHÁC
     * nhau, khiến sameSession không bao giờ khớp dù buổi trùng nhau. Mirror ExerciseService.
     */
    private static boolean sameDueAt(OffsetDateTime a, OffsetDateTime b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.isEqual(b);
    }

    /** Hủy 1 bản giao (VD Giáo viên đổi lựa chọn "BTVN buổi sau" ở Nhận xét khi comment còn DRAFT — V65). */
    @Transactional
    public void cancelAssignment(ReviewVideoAssignment assignment) {
        assignment.setStatus(ReviewVideoAssignment.Status.CANCELLED);
        reviewVideoAssignmentRepository.save(assignment);
    }

    /**
     * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * GV xem lại danh sách bản giao ACTIVE của 1 lớp — mirror
     * ExerciseService.listAssignmentsForClass. FE dùng để tra ngược
     * "bản giao id X ứng với ReviewVideoSet nào" khi tải lại 1 comment
     * DAILY đã chọn sẵn kênh Video (StudentCommentResponse chỉ trả id
     * bản giao, không trả thẳng reviewVideoSetId).
     */
    @Transactional(readOnly = true)
    public List<ReviewVideoAssignmentResponse> listAssignmentsForClass(Long classId, Long actorUserId) {
        requireAssignedTeacher(classId, actorUserId);
        return reviewVideoAssignmentRepository.findBySchoolClassIdAndStatus(classId, ReviewVideoAssignment.Status.ACTIVE)
                .stream().map(this::toAssignmentResponse).toList();
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31: Học
     * sinh tự tra hạn nộp (dueAt) của (các) bộ Video Ôn tập đã được giao
     * cho lớp mình đang học ACTIVE — trước đây chỉ có
     * listAssignmentsForClass (chặn bởi requireAssignedTeacher, chỉ Giáo
     * viên gọi được), Portal không có nguồn nào đọc được dueAt. Mirror
     * ExerciseAttemptService.listMyAssignedExercises — CHỈ tính enrollment
     * ACTIVE (khớp đúng điều kiện resolveStudentAccess đang dùng để cấp
     * quyền xem/nộp, khác listMyAssignedExercises vốn cho phép cả lớp cũ).
     */
    @Transactional(readOnly = true)
    public List<MyReviewVideoAssignmentResponse> listMyAssignments(Long actorUserId, Long classIdFilter) {
        Student student = studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ học sinh cho tài khoản id=" + actorUserId));
        List<ClassEnrollment> enrollments = classEnrollmentRepository
                .findByStudentIdAndStatus(student.getId(), ClassEnrollment.Status.ACTIVE).stream()
                .filter(e -> classIdFilter == null || e.getSchoolClass().getId().equals(classIdFilter))
                .toList();

        List<MyReviewVideoAssignmentResponse> result = new ArrayList<>();
        for (ClassEnrollment enrollment : enrollments) {
            SchoolClass schoolClass = enrollment.getSchoolClass();
            for (ReviewVideoAssignment assignment : reviewVideoAssignmentRepository
                    .findBySchoolClassIdAndStatus(schoolClass.getId(), ReviewVideoAssignment.Status.ACTIVE)) {
                if (assignment.getTargetStudentIds() != null && !assignment.getTargetStudentIds().contains(student.getId())) {
                    continue;
                }
                ReviewVideoSet set = assignment.getReviewVideoSet();
                result.add(new MyReviewVideoAssignmentResponse(
                        assignment.getId(), set.getId(), set.getTitle(), set.getVideoType().name(),
                        schoolClass.getId(), schoolClass.getName(),
                        assignment.getAvailableFrom(), assignment.getDueAt(),
                        assignment.getSourceClassSession() == null ? null : assignment.getSourceClassSession().getSessionDate()));
            }
        }
        return result;
    }

    private ReviewVideoAssignmentResponse toAssignmentResponse(ReviewVideoAssignment a) {
        return new ReviewVideoAssignmentResponse(
                a.getId(), a.getUuid(), a.getReviewVideoSet().getId(), a.getReviewVideoSet().getTitle(),
                a.getSchoolClass().getId(), a.getAssignedBy().getId(),
                a.getAvailableFrom(), a.getDueAt(), a.getTargetStudentIds(), a.getStatus().name());
    }

    private void notifyAssignedStudents(SchoolClass schoolClass, ReviewVideoSet set, ReviewVideoAssignment assignment) {
        List<ClassEnrollment> enrollments = classEnrollmentRepository
                .findBySchoolClassIdAndStatus(schoolClass.getId(), ClassEnrollment.Status.ACTIVE);
        String title = "Video ôn tập mới được giao";
        String content = "Bộ video \"" + set.getTitle() + "\" đã được giao cho lớp " + schoolClass.getName() + ".";
        for (ClassEnrollment enrollment : enrollments) {
            notificationService.notify(enrollment.getStudent().getUser().getId(),
                    Notification.NotificationType.OTHER, title, content,
                    null, "REVIEW_VIDEO_ASSIGNMENT", assignment.getId(),
                    Notification.Priority.NORMAL, null);
        }
    }

    /**
     * Hợp nhất id các bộ đã giao cho HS qua danh sách lớp (target_student_ids null = cả lớp, hoặc có
     * chứa studentId). Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — lấy CẢ
     * ACTIVE/COMPLETED/CANCELLED, không chỉ ACTIVE: bản giao cũ bị hủy do giao lại (V69) vẫn phải hiện
     * cho học sinh xem lại, không được biến mất khỏi tầm nhìn (mirror ExerciseAttemptService).
     */
    private Set<Long> assignedSetIdsForStudentInClasses(List<Long> classIds, Long studentId) {
        Set<Long> setIds = new HashSet<>();
        List<ReviewVideoAssignment.Status> anyStatus = List.of(
                ReviewVideoAssignment.Status.ACTIVE, ReviewVideoAssignment.Status.COMPLETED, ReviewVideoAssignment.Status.CANCELLED);
        for (Long classId : classIds) {
            for (ReviewVideoAssignment a : reviewVideoAssignmentRepository.findBySchoolClassIdAndStatusIn(classId, anyStatus)) {
                if (a.getTargetStudentIds() == null || a.getTargetStudentIds().contains(studentId)) {
                    setIds.add(a.getReviewVideoSet().getId());
                }
            }
        }
        return setIds;
    }

    /** UC-23 Main Flow bước 2-3: đính kèm 1 video/audio (đã upload CDN hoặc link YouTube) vào bộ. */
    @Transactional
    public ReviewVideoResponse addVideo(Long setId, AddReviewVideoRequest request, Long actorUserId) {
        ReviewVideoSet set = getSetOrThrow(setId);
        requireOwnerScope(set, actorUserId);

        ReviewVideo video = new ReviewVideo();
        video.setReviewVideoSet(set);
        video.setSourceType(ReviewVideo.SourceType.valueOf(request.sourceType()));
        video.setTitle(request.title());
        video.setFileUrl(request.fileUrl());
        video.setFileSizeBytes(request.fileSizeBytes());
        video.setDurationSeconds(request.durationSeconds());
        video.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        video.setCompletionThresholdPercent(request.completionThresholdPercent() == null ? 80 : request.completionThresholdPercent());
        video.setRequiredViewCount(request.requiredViewCount() == null ? 1 : request.requiredViewCount());
        video = reviewVideoRepository.save(video);
        return toResponse(video);
    }

    @Transactional(readOnly = true)
    public List<ReviewVideoResponse> listVideos(Long setId, Long actorUserId) {
        ReviewVideoSet set = getSetOrThrow(setId);
        if (isStudent(actorUserId)) {
            requireStudentCanViewSet(set, actorUserId);
        }
        return reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(setId).stream().map(this::toResponse).toList();
    }

    /**
     * UC-23b (V57): giáo viên thêm 1 câu hỏi gắn mốc thời gian vào video
     * REFLEX khi soạn — thời lượng ghi âm/số lần nộp lại tối đa đặt riêng
     * theo TỪNG câu hỏi (đã xác nhận với người dùng, không dùng chung 1
     * giá trị cho cả video). Chỉ áp dụng videoType=REFLEX (cùng rào A1
     * với submitQuestionAudio).
     *
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — chặn tạo câu hỏi có khoảng ghi âm
     * [timestampSeconds, timestampSeconds + maxRecordingSeconds] CHỒNG LẤN câu hỏi khác trong cùng video:
     * trình phát chỉ ghi âm được 1 câu tại 1 thời điểm (không cho ghi âm song song, xem ReflexVideoTaskPage
     * ở FE), nếu 2 câu chồng giờ thì câu tới sau sẽ bị bỏ lỡ hoàn toàn lúc học sinh làm bài.
     */
    @Transactional
    public ReviewVideoQuestionResponse addQuestion(Long videoId, AddReviewVideoQuestionRequest request, Long actorUserId) {
        ReviewVideo video = getVideoOrThrow(videoId);
        requireOwnerScope(video.getReviewVideoSet(), actorUserId);
        if (video.getReviewVideoSet().getVideoType() != ReviewVideoSet.VideoType.REFLEX) {
            throw new IllegalArgumentException("Video này không phải loại Video phản xạ (REFLEX) — không nhận câu hỏi.");
        }
        requireNoOverlap(videoId, request.timestampSeconds(), request.maxRecordingSeconds());

        ReviewVideoQuestion question = new ReviewVideoQuestion();
        question.setReviewVideo(video);
        question.setTimestampSeconds(request.timestampSeconds());
        question.setPrompt(request.prompt());
        question.setMaxRecordingSeconds(request.maxRecordingSeconds());
        question.setMaxAttempts(request.maxAttempts());
        question.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        question = reviewVideoQuestionRepository.save(question);
        return toResponse(question);
    }

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — sửa 1 câu hỏi REFLEX đã có (trước
     * đây chỉ thêm mới được). Mirror addQuestion — vẫn chặn chồng lấn khoảng ghi âm với câu hỏi KHÁC
     * trong cùng video (loại chính câu đang sửa ra khỏi phép kiểm tra qua excludeQuestionId, nếu không
     * sẽ luôn tự báo chồng lấn với chính nó).
     */
    @Transactional
    public ReviewVideoQuestionResponse updateQuestion(Long questionId, UpdateReviewVideoQuestionRequest request, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        requireOwnerScope(question.getReviewVideo().getReviewVideoSet(), actorUserId);
        requireNoOverlap(question.getReviewVideo().getId(), request.timestampSeconds(), request.maxRecordingSeconds(), questionId);

        question.setTimestampSeconds(request.timestampSeconds());
        question.setPrompt(request.prompt());
        question.setMaxRecordingSeconds(request.maxRecordingSeconds());
        question.setMaxAttempts(request.maxAttempts());
        question.setDisplayOrder(request.displayOrder() == null ? question.getDisplayOrder() : request.displayOrder());
        question = reviewVideoQuestionRepository.save(question);
        return toResponse(question);
    }

    /**
     * Kiểm tra khoảng ghi âm [newStart, newStart + newDurationSeconds) của câu hỏi MỚI có chồng lấn câu
     * hỏi nào đã có trong cùng video hay không — dùng phép kiểm tra giao nhau nửa-mở kinh điển (tương tự
     * ClassSessionService#checkClassConflict): 2 khoảng [a1,a2) và [b1,b2) chồng nhau khi a1 < b2 VÀ b1 < a2.
     */
    private void requireNoOverlap(Long videoId, int newStart, int newDurationSeconds) {
        requireNoOverlap(videoId, newStart, newDurationSeconds, null);
    }

    /** Overload dùng khi SỬA 1 câu hỏi đã có — excludeQuestionId loại chính câu đang sửa khỏi phép kiểm tra chồng lấn. */
    private void requireNoOverlap(Long videoId, int newStart, int newDurationSeconds, Long excludeQuestionId) {
        int newEnd = newStart + newDurationSeconds;
        List<ReviewVideoQuestion> siblings = reviewVideoQuestionRepository.findByReviewVideoIdOrderByDisplayOrder(videoId);
        for (ReviewVideoQuestion sibling : siblings) {
            if (excludeQuestionId != null && excludeQuestionId.equals(sibling.getId())) {
                continue;
            }
            int siblingEnd = sibling.getTimestampSeconds() + sibling.getMaxRecordingSeconds();
            if (sibling.getTimestampSeconds() < newEnd && newStart < siblingEnd) {
                throw new ReviewVideoQuestionOverlapException(
                        "Khoảng ghi âm câu hỏi mới (giây " + newStart + "-" + newEnd + ") chồng lấn 1 câu hỏi khác"
                                + " (giây " + sibling.getTimestampSeconds() + "-" + siblingEnd + ") — video chỉ ghi âm được 1 câu tại 1 thời điểm,"
                                + " hãy đặt mốc thời gian cách nhau xa hơn hoặc giảm thời lượng ghi âm tối đa.");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ReviewVideoQuestionResponse> listQuestions(Long videoId, Long actorUserId) {
        ReviewVideo video = getVideoOrThrow(videoId);
        if (isStudent(actorUserId)) {
            requireStudentCanViewSet(video.getReviewVideoSet(), actorUserId);
        }
        return reviewVideoQuestionRepository.findByReviewVideoIdOrderByDisplayOrder(videoId).stream().map(this::toResponse).toList();
    }

    /**
     * V83 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): giáo viên
     * thêm 1 câu hỏi trắc nghiệm tự chấm vào video CONNECTION (mirror
     * addQuestion của REFLEX nhưng gate NGƯỢC LẠI — chỉ áp dụng CONNECTION).
     * Video CONNECTION giờ bắt buộc có câu hỏi trước khi Publish được (xem
     * requireConnectionVideosHaveQuestions ở updateSet).
     */
    @Transactional
    public ReviewVideoConnectionQuestionResponse addConnectionQuestion(
            Long videoId, AddReviewVideoConnectionQuestionRequest request, Long actorUserId) {
        ReviewVideo video = getVideoOrThrow(videoId);
        requireOwnerScope(video.getReviewVideoSet(), actorUserId);
        if (video.getReviewVideoSet().getVideoType() != ReviewVideoSet.VideoType.CONNECTION) {
            throw new IllegalArgumentException(
                    "Video này không phải loại Video kết nối (CONNECTION) — không nhận câu hỏi trắc nghiệm.");
        }

        ReviewVideoConnectionQuestion question = new ReviewVideoConnectionQuestion();
        question.setReviewVideo(video);
        question.setPrompt(request.prompt());
        question.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        question = reviewVideoConnectionQuestionRepository.save(question);

        List<ReviewVideoConnectionChoice> choices = new ArrayList<>();
        for (ConnectionChoiceRequest c : request.choices()) {
            ReviewVideoConnectionChoice choice = new ReviewVideoConnectionChoice();
            choice.setReviewVideoConnectionQuestion(question);
            choice.setChoiceLabel(c.choiceLabel());
            choice.setContent(c.content());
            choice.setCorrect(c.isCorrect());
            choice.setDisplayOrder(c.displayOrder());
            choices.add(reviewVideoConnectionChoiceRepository.save(choice));
        }
        return toResponse(question, choices, true);
    }

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — sửa nội dung câu hỏi + nội dung/
     * đáp án đúng của TỪNG đáp án đã có (trước đây chỉ thêm mới được). KHÔNG cho thêm/bớt số lượng đáp
     * án qua đường này — {@code review_video_connection_answers.selected_choice_id} là FK bắt buộc trỏ
     * thẳng vào 1 đáp án, xoá đáp án đã có học sinh chọn sẽ vỡ ràng buộc dữ liệu (đã xác nhận với người
     * dùng, muốn đổi số lượng đáp án phải tạo câu hỏi mới). {@code request.choices()} phải khớp CHÍNH
     * XÁC số đáp án hiện có, mỗi choiceId phải khớp 1-1 với 1 đáp án đang có — không khớp thì từ chối rõ
     * ràng, không âm thầm bỏ qua.
     */
    @Transactional
    public ReviewVideoConnectionQuestionResponse updateConnectionQuestion(
            Long questionId, UpdateReviewVideoConnectionQuestionRequest request, Long actorUserId) {
        ReviewVideoConnectionQuestion question = getConnectionQuestionOrThrow(questionId);
        requireOwnerScope(question.getReviewVideo().getReviewVideoSet(), actorUserId);

        List<ReviewVideoConnectionChoice> existingChoices = reviewVideoConnectionChoiceRepository
                .findByReviewVideoConnectionQuestionIdOrderByDisplayOrder(questionId);
        if (request.choices().size() != existingChoices.size()) {
            throw new IllegalArgumentException(
                    "Câu hỏi này đang có " + existingChoices.size() + " đáp án — không thể sửa thành "
                            + request.choices().size() + " đáp án (không hỗ trợ thêm/bớt đáp án khi sửa, chỉ sửa nội dung/đáp án đúng).");
        }
        Map<Long, ReviewVideoConnectionChoice> existingById = existingChoices.stream()
                .collect(Collectors.toMap(ReviewVideoConnectionChoice::getId, c -> c));

        question.setPrompt(request.prompt());
        question.setDisplayOrder(request.displayOrder() == null ? question.getDisplayOrder() : request.displayOrder());
        question = reviewVideoConnectionQuestionRepository.save(question);

        List<ReviewVideoConnectionChoice> updatedChoices = new ArrayList<>();
        for (UpdateConnectionChoiceRequest c : request.choices()) {
            ReviewVideoConnectionChoice choice = existingById.get(c.choiceId());
            if (choice == null) {
                throw new IllegalArgumentException(
                        "Một đáp án bạn gửi lên không thuộc câu hỏi này — không sửa được.");
            }
            choice.setContent(c.content());
            choice.setCorrect(c.isCorrect());
            updatedChoices.add(reviewVideoConnectionChoiceRepository.save(choice));
        }
        updatedChoices.sort((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()));
        return toResponse(question, updatedChoices, true);
    }

    /**
     * V83: xem danh sách câu hỏi trắc nghiệm CONNECTION — ẩn đáp án đúng
     * (isCorrect=null) khi actor là học sinh, mirror cách
     * ExerciseQuestionChoiceResponse không lộ isCorrect trước khi nộp bài.
     */
    @Transactional(readOnly = true)
    public List<ReviewVideoConnectionQuestionResponse> listConnectionQuestions(Long videoId, Long actorUserId) {
        ReviewVideo video = getVideoOrThrow(videoId);
        boolean showAnswers = !isStudent(actorUserId);
        if (!showAnswers) {
            requireStudentCanViewSet(video.getReviewVideoSet(), actorUserId);
        }
        return reviewVideoConnectionQuestionRepository.findByReviewVideoIdOrderByDisplayOrder(videoId).stream()
                .map(q -> toResponse(q, reviewVideoConnectionChoiceRepository
                        .findByReviewVideoConnectionQuestionIdOrderByDisplayOrder(q.getId()), showAnswers))
                .toList();
    }

    /**
     * UC-23a (V59): mở 1 LƯỢT xem mới cho video CONNECTION — gọi khi học
     * sinh bắt đầu/mở lại video. Trả sessionId để các lần reportProgress
     * tiếp theo của lượt này cập nhật đúng session, không lẫn với lượt
     * khác (khác cơ chế watermark suốt đời cũ, vốn không phân biệt được
     * "lần" nào với "lần" nào).
     */
    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 — đọc lại
     * tiến độ ĐÃ LƯU (viewCount/completed) cho video CONNECTION mà KHÔNG mở
     * lượt xem mới, dùng cho màn "Bài tập về nhà" (danh sách) hiển thị đúng
     * trạng thái đã đạt/chưa đạt mà không cần học sinh mở video ra xem lại
     * mới biết — trước đây không có API nào đọc lại được, chỉ có được qua
     * report tiến độ SỐNG trong lúc đang xem (reportProgress).
     *
     * V93/V101 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06,
     * giảm mặc định 80%→70% ngày 2026-08-07):
     * "đạt" (completed) của video CONNECTION đổi từ "đủ SỐ LƯỢT tuyệt đối"
     * sang TỶ LỆ % (viewCount/requiredViewCount ≥ ngưỡng cấu hình, mặc định
     * 70%) — xem {@link #recomputeProgress}. Ví dụ: yêu cầu 4 lượt, học sinh
     * xem+nộp đúng 3 lượt = 75%, ĐẠT (≥70%).
     */
    /** V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — thêm assignmentId bắt buộc, mirror Javadoc resolveStudentAccessForAssignment. */
    @Transactional(readOnly = true)
    public ReviewVideoProgressResponse getProgress(Long videoId, Long assignmentId, Long actorUserId) {
        ReviewVideo video = getVideoOrThrow(videoId);
        StudentAccess access = resolveStudentAccessForAssignment(video.getReviewVideoSet(), assignmentId, actorUserId);
        return toResponse(getOrCreateProgress(video, access.student(), access.assignment()), video);
    }

    /**
     * V128/V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — assignmentId bắt
     * buộc: từ khi 1 bộ video có thể có NHIỀU bản giao ACTIVE song song (giao độc lập từ nhiều buổi
     * Nhận xét khác nhau, xem {@link #deliverToClass}), không thể suy ra "bản giao nào" chỉ từ videoId
     * nữa. Lượt xem giờ gắn thẳng với đúng bản giao (xem {@link ReviewVideoWatchSession#getReviewVideoAssignment()})
     * — mọi report/submit tiếp theo trên lượt này (reportProgress/submitConnectionAnswers) tự suy ra
     * đúng bản giao từ chính session, không cần truyền lại assignmentId.
     */
    @Transactional
    public StartWatchSessionResponse startWatchSession(Long videoId, Long assignmentId, Long actorUserId) {
        ReviewVideo video = getVideoOrThrow(videoId);
        StudentAccess access = resolveStudentAccessForAssignment(video.getReviewVideoSet(), assignmentId, actorUserId);
        Student student = access.student();

        ReviewVideoWatchSession session = new ReviewVideoWatchSession();
        session.setReviewVideo(video);
        session.setStudent(student);
        session.setReviewVideoAssignment(access.assignment());
        if (video.getReviewVideoSet().getVideoType() == ReviewVideoSet.VideoType.CONNECTION) {
            ensureConnectionQuestionSlotsAssigned(video, student);
            // Cố tình vẫn đếm TOÀN CỤC (video, học sinh), KHÔNG lọc theo assignment — bộ câu hỏi random
            // (ensureConnectionQuestionSlotsAssigned) là 1 lần duy nhất cho cả đời học sinh+video, chu
            // kỳ slotIndex tiếp tục xuyên suốt qua các lần giao khác nhau, không reset lại (đã xác nhận
            // với người dùng — khác hẳn viewCount/completed, LUÔN tách riêng theo từng bản giao).
            int priorSessionCount = reviewVideoWatchSessionRepository.countByReviewVideoIdAndStudentId(videoId, student.getId());
            session.setSlotIndex((priorSessionCount % video.getRequiredViewCount()) + 1);
        }
        session = reviewVideoWatchSessionRepository.save(session);
        return new StartWatchSessionResponse(session.getId());
    }

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — sinh phân bổ câu hỏi CONNECTION
     * theo nhóm (1..M, M = {@link ReviewVideo#getRequiredViewCount()}) CHO ĐÚNG 1 học sinh, 1 LẦN DUY
     * NHẤT (lúc học sinh vào xem lượt đầu tiên) — ngẫu nhiên độc lập mỗi học sinh, chia đều nhất có
     * thể, NHÓM ĐẦU nhận số câu dư (VD 10 câu/3 nhóm = 4,3,3). Idempotent — không sinh lại nếu đã có.
     */
    private void ensureConnectionQuestionSlotsAssigned(ReviewVideo video, Student student) {
        if (!reviewVideoConnectionQuestionSlotRepository
                .findByReviewVideoConnectionQuestion_ReviewVideoIdAndStudentId(video.getId(), student.getId()).isEmpty()) {
            return;
        }
        List<ReviewVideoConnectionQuestion> questions = reviewVideoConnectionQuestionRepository
                .findByReviewVideoIdOrderByDisplayOrder(video.getId());
        int groupCount = video.getRequiredViewCount();
        if (questions.isEmpty() || groupCount <= 0) {
            return;
        }
        List<ReviewVideoConnectionQuestion> shuffled = new ArrayList<>(questions);
        Collections.shuffle(shuffled);

        int base = shuffled.size() / groupCount;
        int remainder = shuffled.size() % groupCount;
        List<ReviewVideoConnectionQuestionSlot> slots = new ArrayList<>();
        int cursor = 0;
        for (int slotIndex = 1; slotIndex <= groupCount; slotIndex++) {
            int sizeForSlot = base + (slotIndex <= remainder ? 1 : 0);
            for (int i = 0; i < sizeForSlot && cursor < shuffled.size(); i++, cursor++) {
                ReviewVideoConnectionQuestionSlot slot = new ReviewVideoConnectionQuestionSlot();
                slot.setReviewVideoConnectionQuestion(shuffled.get(cursor));
                slot.setStudent(student);
                slot.setSlotIndex(slotIndex);
                slots.add(slot);
            }
        }
        reviewVideoConnectionQuestionSlotRepository.saveAll(slots);
    }

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — học sinh chỉ nhận ĐÚNG nhóm câu
     * hỏi của lượt xem này (`session.getSlotIndex()`), không phải toàn bộ ngân hàng câu hỏi của video
     * (khác endpoint cũ `listConnectionQuestions(videoId)` — endpoint đó vẫn giữ nguyên cho giáo viên
     * soạn/xem TOÀN BỘ ngân hàng câu hỏi).
     */
    @Transactional(readOnly = true)
    public List<ReviewVideoConnectionQuestionResponse> listConnectionQuestionsForSession(Long watchSessionId, Long actorUserId) {
        ReviewVideoWatchSession session = reviewVideoWatchSessionRepository.findById(watchSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xem id=" + watchSessionId));
        ReviewVideo video = session.getReviewVideo();
        Student student = requireStudentCanViewSet(video.getReviewVideoSet(), actorUserId);
        if (!session.getStudent().getId().equals(student.getId())) {
            throw new ResourceNotFoundException("Không tìm thấy lượt xem id=" + watchSessionId);
        }
        if (video.getReviewVideoSet().getVideoType() != ReviewVideoSet.VideoType.CONNECTION) {
            throw new IllegalArgumentException("Video này không phải loại Video kết nối (CONNECTION).");
        }
        Set<Long> assignedQuestionIds = reviewVideoConnectionQuestionSlotRepository
                .findByReviewVideoConnectionQuestion_ReviewVideoIdAndStudentId(video.getId(), student.getId())
                .stream()
                .filter(slot -> slot.getSlotIndex() == session.getSlotIndex())
                .map(slot -> slot.getReviewVideoConnectionQuestion().getId())
                .collect(Collectors.toSet());
        return reviewVideoConnectionQuestionRepository.findByReviewVideoIdOrderByDisplayOrder(video.getId())
                .stream()
                .filter(q -> assignedQuestionIds.contains(q.getId()))
                .map(q -> toResponse(q, reviewVideoConnectionChoiceRepository
                        .findByReviewVideoConnectionQuestionIdOrderByDisplayOrder(q.getId()), false))
                .toList();
    }

    /**
     * UC-23a Main Flow bước 3 (V59): học sinh báo tiến độ xem (giây) cho
     * ĐÚNG 1 lượt xem (watchSessionId) — lấy max(cũ, mới) TRONG PHẠM VI
     * lượt đó, không bao giờ giảm. Lượt đạt completionThresholdPercent
     * của video được đánh dấu qualified — chỉ lượt qualified mới tính
     * vào viewCount. "Đạt" (completed) = viewCount >= requiredViewCount
     * của video (đã xác nhận với người dùng — bổ sung ngoài SDD gốc,
     * 2 tiêu chí ĐỘC LẬP, cả 2 đều cấu hình được khi tạo video).
     *
     * V83 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): với video
     * CONNECTION, "qualified" ở đây MỚI chỉ là điều kiện CẦN — lượt xem chỉ
     * thật sự tính vào viewCount khi HỌC SINH nộp đủ câu hỏi trắc nghiệm
     * CHO ĐÚNG lượt này qua {@link #submitConnectionAnswers}. Xem
     * {@link #recomputeProgress}.
     */
    @Transactional
    public ReviewVideoProgressResponse reportProgress(Long videoId, ReportVideoProgressRequest request, Long actorUserId) {
        ReviewVideo video = getVideoOrThrow(videoId);
        // V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — không còn gọi
        // resolveStudentAccess (tự đoán "1 bản giao ACTIVE", VỠ khi có nhiều bản song song) — lượt xem
        // (session) đã gắn CHÍNH XÁC đúng bản giao từ lúc startWatchSession, dùng thẳng từ đó.
        Student student = studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xem id=" + request.watchSessionId()));
        ReviewVideoWatchSession session = reviewVideoWatchSessionRepository.findById(request.watchSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xem id=" + request.watchSessionId()));
        if (!session.getReviewVideo().getId().equals(videoId) || !session.getStudent().getId().equals(student.getId())) {
            throw new ResourceNotFoundException("Không tìm thấy lượt xem id=" + request.watchSessionId());
        }
        ReviewVideoAssignment assignment = session.getReviewVideoAssignment();
        requireNotPastDeadline(assignment);
        int sessionWatchedSeconds = Math.max(session.getWatchedSeconds(), request.watchedSeconds());
        session.setWatchedSeconds(sessionWatchedSeconds);
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-11 — CONNECTION giờ luôn yêu cầu
        // xem HẾT (100%, cố định, không còn cấu hình %) mới được làm câu hỏi; completionThresholdPercent
        // đổi ý nghĩa cho CONNECTION thành "ngưỡng % pass" (dùng ở computeConnectionPassScore), KHÔNG
        // còn dùng để tính qualified nữa. REFLEX giữ nguyên y hệt logic cũ (đọc completionThresholdPercent).
        boolean qualified = video.getReviewVideoSet().getVideoType() == ReviewVideoSet.VideoType.CONNECTION
                ? sessionWatchedSeconds >= video.getDurationSeconds()
                : sessionWatchedSeconds >= video.getDurationSeconds() * (video.getCompletionThresholdPercent() / 100.0);
        session.setQualified(qualified);
        reviewVideoWatchSessionRepository.save(session);

        ReviewVideoProgress progress = getOrCreateProgress(video, student, assignment);
        progress.setWatchedSeconds(Math.max(progress.getWatchedSeconds(), sessionWatchedSeconds));
        reviewVideoProgressRepository.save(progress);
        progress = recomputeProgress(video, student, assignment);
        return toResponse(progress, video);
    }

    /**
     * V83 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): học sinh nộp
     * TOÀN BỘ câu trả lời trắc nghiệm cho ĐÚNG 1 lượt xem (watchSessionId) —
     * khớp cặp 1-1 "xem lượt nào, trả lời lượt đó". Chặn nếu lượt CHƯA đạt
     * ngưỡng xem (chưa xem xong thì chưa được làm câu hỏi) hoặc lượt đó ĐÃ
     * nộp đủ rồi (không cho nộp lại/đổi đáp án). Trả kết quả tự chấm ngay +
     * tiến độ mới nhất (viewCount có thể vừa tăng thêm 1).
     */
    @Transactional
    public ReviewVideoConnectionQuizResultResponse submitConnectionAnswers(
            Long watchSessionId, SubmitConnectionAnswersRequest request, Long actorUserId) {
        ReviewVideoWatchSession session = reviewVideoWatchSessionRepository.findById(watchSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xem id=" + watchSessionId));
        ReviewVideo video = session.getReviewVideo();
        // V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — mirror reportProgress:
        // dùng thẳng bản giao đã gắn sẵn trên session, không tự đoán lại qua resolveStudentAccess.
        Student student = studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xem id=" + watchSessionId));
        if (!session.getStudent().getId().equals(student.getId())) {
            throw new ResourceNotFoundException("Không tìm thấy lượt xem id=" + watchSessionId);
        }
        ReviewVideoAssignment assignment = session.getReviewVideoAssignment();
        requireNotPastDeadline(assignment);
        if (!session.isQualified()) {
            throw new VideoNotYetQualifiedException(
                    "Lượt xem này chưa xem đạt ngưỡng — chưa thể làm câu hỏi.");
        }
        if (session.getQuizCompletedAt() != null) {
            throw new QuizAlreadyCompletedException(
                    "Lượt xem này đã nộp đủ câu hỏi rồi, không thể nộp lại.");
        }

        List<ReviewVideoConnectionQuestion> questions = reviewVideoConnectionQuestionRepository
                .findByReviewVideoIdOrderByDisplayOrder(video.getId());
        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-11 — chỉ cần trả lời ĐÚNG nhóm câu
        // hỏi đã gán cho slotIndex của LƯỢT NÀY (xem ensureConnectionQuestionSlotsAssigned), không còn
        // phải trả lời TOÀN BỘ ngân hàng câu hỏi mỗi lượt như trước.
        Set<Long> questionIds = reviewVideoConnectionQuestionSlotRepository
                .findByReviewVideoConnectionQuestion_ReviewVideoIdAndStudentId(video.getId(), student.getId())
                .stream()
                .filter(slot -> slot.getSlotIndex() == session.getSlotIndex())
                .map(slot -> slot.getReviewVideoConnectionQuestion().getId())
                .collect(Collectors.toSet());
        Set<Long> answeredQuestionIds = request.answers().stream().map(a -> a.questionId()).collect(Collectors.toSet());
        if (!questionIds.equals(answeredQuestionIds)) {
            throw new IllegalArgumentException(
                    "Phải trả lời đúng nhóm câu hỏi của lượt xem này — không thiếu, không thừa.");
        }

        List<ConnectionAnswerResult> results = new ArrayList<>();
        for (var item : request.answers()) {
            ReviewVideoConnectionQuestion question = questions.stream()
                    .filter(q -> q.getId().equals(item.questionId())).findFirst().orElseThrow();
            List<ReviewVideoConnectionChoice> choices = reviewVideoConnectionChoiceRepository
                    .findByReviewVideoConnectionQuestionIdOrderByDisplayOrder(question.getId());
            ReviewVideoConnectionChoice selected = choices.stream()
                    .filter(c -> c.getId().equals(item.selectedChoiceId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Một lựa chọn bạn gửi lên không thuộc câu hỏi tương ứng."));
            ReviewVideoConnectionChoice correctChoice = choices.stream().filter(ReviewVideoConnectionChoice::isCorrect)
                    .findFirst().orElse(null);

            ReviewVideoConnectionAnswer answer = new ReviewVideoConnectionAnswer();
            answer.setReviewVideoConnectionQuestion(question);
            answer.setWatchSession(session);
            answer.setStudent(student);
            answer.setSelectedChoice(selected);
            answer.setCorrect(selected.isCorrect());
            reviewVideoConnectionAnswerRepository.save(answer);

            results.add(new ConnectionAnswerResult(question.getId(), selected.getId(), selected.isCorrect(),
                    correctChoice == null ? null : correctChoice.getId()));
        }

        session.setQuizCompletedAt(OffsetDateTime.now());
        reviewVideoWatchSessionRepository.save(session);
        ReviewVideoProgress progress = recomputeProgress(video, student, assignment);
        return new ReviewVideoConnectionQuizResultResponse(results, toResponse(progress, video));
    }

    /**
     * UC-23b Main Flow bước 1-2 (V57): học sinh nộp audio trả lời cho 1
     * CÂU HỎI của video REFLEX — chỉ áp dụng videoType=REFLEX (A1). Nộp
     * lại tạo attempt MỚI, GIỮ LỊCH SỬ các attempt trước (khác cơ chế ghi
     * đè cũ) — trừ phi đã đạt maxAttempts của câu hỏi đó thì từ chối
     * (RetakeNotAllowedException, tái dùng nguyên exception của UC-24/27).
     * Chặn bởi requireStudentCanViewSet TRƯỚC khi check videoType để
     * không lộ loại video ngoài phạm vi lớp mình (A2, cùng cơ chế 404 với
     * UC-23a).
     *
     * V69 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31):
     * đếm lượt đã nộp + gắn attempt mới SCOPED theo ĐÚNG lần giao hiện tại
     * (không tính lượt đã nộp ở các lần giao TRƯỚC đó) — "giao lại = 1
     * lượt MỚI", maxAttempts vì vậy áp dụng lại từ đầu mỗi lần giao.
     *
     * V128 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — assignmentId bắt buộc:
     * mirror ExerciseAttemptService#startAttempt/resolveActiveAssignmentForStudent, không còn tự đoán
     * "lần giao ACTIVE duy nhất" (có thể có nhiều lần giao song song).
     */
    @Transactional
    public ReviewVideoSubmissionResponse submitQuestionAudio(Long questionId, Long assignmentId, SubmitReviewVideoAudioRequest request, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        ReviewVideo video = question.getReviewVideo();
        StudentAccess access = resolveStudentAccessForAssignment(video.getReviewVideoSet(), assignmentId, actorUserId);
        Student student = access.student();
        requireNotPastDeadline(access.assignment());
        if (video.getReviewVideoSet().getVideoType() != ReviewVideoSet.VideoType.REFLEX) {
            throw new IllegalArgumentException("Video này không phải loại Video phản xạ (REFLEX) — không nhận nộp audio.");
        }

        int previousAttempts = reviewVideoQuestionSubmissionRepository
                .countByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentId(questionId, student.getId(), access.assignment().getId());
        if (question.getMaxAttempts() != null && previousAttempts >= question.getMaxAttempts()) {
            throw new RetakeNotAllowedException(
                    "Câu hỏi này đã hết lượt nộp lại (tối đa " + question.getMaxAttempts() + ").");
        }

        ReviewVideoQuestionSubmission submission = new ReviewVideoQuestionSubmission();
        submission.setReviewVideoQuestion(question);
        submission.setStudent(student);
        submission.setReviewVideoAssignment(access.assignment());
        submission.setAttemptNumber(previousAttempts + 1);
        submission.setAudioUrl(request.audioUrl());
        submission.setSubmittedAt(OffsetDateTime.now());
        submission = reviewVideoQuestionSubmissionRepository.save(submission);

        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — sự kiện thoát ra ngoài lúc ghi âm
        // được client đệm rồi gửi kèm cùng lúc nộp (không có "phiên bắt đầu ghi âm" ở backend để gửi real-time).
        if (request.integrityEvents() != null && !request.integrityEvents().events().isEmpty()) {
            attemptIntegrityService.recordEvents(AttemptIntegrityEvent.AttemptType.REVIEW_VIDEO_QUESTION,
                    submission.getId(), request.integrityEvents(), actorUserId);
        }
        return toResponse(submission);
    }

    /** UC-23b (V57): học sinh xem attempt MỚI NHẤT mình đã nộp cho 1 câu hỏi — null nếu chưa nộp lần nào. V69: chỉ tính trong phạm vi ĐÚNG lần giao hiện tại (xem Javadoc submitQuestionAudio). V128: assignmentId bắt buộc, mirror submitQuestionAudio. */
    @Transactional(readOnly = true)
    public ReviewVideoSubmissionResponse getMyLatestSubmission(Long questionId, Long assignmentId, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        StudentAccess access = resolveStudentAccessForAssignment(question.getReviewVideo().getReviewVideoSet(), assignmentId, actorUserId);
        List<ReviewVideoQuestionSubmission> attempts = reviewVideoQuestionSubmissionRepository
                .findByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentIdOrderByAttemptNumberDesc(
                        questionId, access.student().getId(), access.assignment().getId());
        return attempts.isEmpty() ? null : toResponse(attempts.get(0));
    }

    /** UC-23b (V57): học sinh xem TOÀN BỘ lịch sử các lần đã nộp cho 1 câu hỏi (mới nhất trước) — giữ lịch sử thì phải xem lại được. V69: chỉ tính trong phạm vi ĐÚNG lần giao hiện tại (xem Javadoc submitQuestionAudio). V128: assignmentId bắt buộc, mirror submitQuestionAudio. */
    @Transactional(readOnly = true)
    public List<ReviewVideoSubmissionResponse> listMySubmissionHistory(Long questionId, Long assignmentId, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        StudentAccess access = resolveStudentAccessForAssignment(question.getReviewVideo().getReviewVideoSet(), assignmentId, actorUserId);
        return reviewVideoQuestionSubmissionRepository
                .findByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentIdOrderByAttemptNumberDesc(
                        questionId, access.student().getId(), access.assignment().getId())
                .stream().map(this::toResponse).toList();
    }

    /**
     * UC-23a Main Flow bước 4: thống kê giáo viên — ma trận học sinh ×
     * video cho 1 lớp cụ thể. Bắt đầu từ roster lớp (ClassEnrollment
     * ACTIVE) LEFT JOIN video/tiến độ (không bắt đầu từ bảng progress) để
     * học sinh chưa xem gì vẫn hiện 0%, không biến mất khỏi ma trận.
     */
    @Transactional(readOnly = true)
    public ReviewVideoSetStatsResponse getStats(Long setId, Long classIdParam, Long actorUserId) {
        ReviewVideoSet set = getSetOrThrow(setId);
        requireOwnerScope(set, actorUserId);
        Long classId = resolveClassIdForSet(set, classIdParam);

        List<ClassEnrollment> roster = classEnrollmentRepository.findBySchoolClassIdAndStatus(classId, ClassEnrollment.Status.ACTIVE);
        List<ReviewVideo> videos = reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(setId);
        List<Long> videoIds = videos.stream().map(ReviewVideo::getId).toList();
        // V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — 1 học sinh giờ có thể có
        // NHIỀU dòng tiến độ cho CÙNG 1 video (1 dòng/lần giao độc lập) — ma trận tổng quan theo cả BỘ
        // này (khác toAssignmentStats bên dưới, scope đúng 1 lần giao) cố tình gộp merge (a, b) -> a để
        // tránh vỡ (Collectors.toMap ném lỗi trùng khóa nếu không có merge function) — chưa tách hiển
        // thị riêng từng lần giao ở màn tổng quan này, ngoài phạm vi thay đổi hôm nay.
        List<ReviewVideoProgress> progressRows = videoIds.isEmpty()
                ? List.of() : reviewVideoProgressRepository.findByReviewVideoIdIn(videoIds);
        Map<String, ReviewVideoProgress> progressByKey = progressRows.stream()
                .collect(Collectors.toMap(p -> p.getReviewVideo().getId() + ":" + p.getStudent().getId(), p -> p, (a, b) -> a));

        List<ReviewVideoSetStatsResponse.VideoHeader> headers = videos.stream()
                .map(v -> new ReviewVideoSetStatsResponse.VideoHeader(v.getId(), v.getTitle(), v.getDurationSeconds(), v.getRequiredViewCount()))
                .toList();

        List<ReviewVideoSetStatsResponse.StatsCell> cells = new ArrayList<>();
        for (ClassEnrollment enrollment : roster) {
            Long studentId = enrollment.getStudent().getId();
            for (ReviewVideo video : videos) {
                ReviewVideoProgress progress = progressByKey.get(video.getId() + ":" + studentId);
                int watchedSeconds = progress == null ? 0 : progress.getWatchedSeconds();
                boolean completed = progress != null && progress.isCompleted();
                int viewCount = progress == null ? 0 : progress.getViewCount();
                int watchedPercent = watchedPercentOf(watchedSeconds, video.getDurationSeconds());
                cells.add(new ReviewVideoSetStatsResponse.StatsCell(studentId, video.getId(), watchedSeconds, watchedPercent, completed, viewCount));
            }
        }
        return new ReviewVideoSetStatsResponse(headers, cells);
    }

    /**
     * UC-66 bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — 1 dòng "BTVN Video Ôn tập"
     * cho mỗi {@link ReviewVideoAssignment} ACTIVE/COMPLETED của 1 lớp (loại CANCELLED — coi như đã bị
     * giao lại/thay thế, mirror {@code ExerciseReportService#listAssignmentStats}), dùng để gộp vào
     * trang "Thống kê BTVN theo lớp" (UC-66) cùng với Exercise. "Hoàn thành" (completedCount) = học
     * sinh có {@link ReviewVideoProgress#isCompleted()} = true cho TẤT CẢ video trong bộ (1 bộ có thể
     * nhiều video — mirror tinh thần "làm hết mới tính" của Exercise). KHÔNG có "% đạt" (pass rate) —
     * Video Ôn tập chưa có khái niệm ngưỡng điểm đạt/rớt nào trong schema, không bịa ra 1 con số.
     */
    @Transactional(readOnly = true)
    public List<ReviewVideoAssignmentStatsResponse> listAssignmentStatsForClass(Long classId, Long actorUserId) {
        requireAssignedTeacher(classId, actorUserId);
        List<ReviewVideoAssignment> assignments = reviewVideoAssignmentRepository.findBySchoolClassIdAndStatusIn(
                classId, List.of(ReviewVideoAssignment.Status.ACTIVE, ReviewVideoAssignment.Status.COMPLETED));
        if (assignments.isEmpty()) {
            return List.of();
        }
        List<ClassEnrollment> roster = classEnrollmentRepository.findBySchoolClassIdAndStatus(classId, ClassEnrollment.Status.ACTIVE);
        return assignments.stream().map(a -> toAssignmentStats(a, roster)).toList();
    }

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-12) — đổi từ {@code private} sang
     * {@code public} để {@link ReviewVideoReportService} tái dùng đúng 1 nguồn logic tính header
     * assignment (completedCount/passedCount...) cho trang "Xem chi tiết", KHÔNG tự viết lại 1 bản
     * riêng có nguy cơ lệch dần theo thời gian (khác Exercise — ExerciseService không có method
     * tương đương nên ExerciseReportService buộc phải tự viết, không phải trường hợp này).
     */
    public ReviewVideoAssignmentStatsResponse toAssignmentStats(ReviewVideoAssignment assignment, List<ClassEnrollment> roster) {
        ReviewVideoSet set = assignment.getReviewVideoSet();
        List<ReviewVideo> videos = reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(set.getId());
        List<Long> videoIds = videos.stream().map(ReviewVideo::getId).toList();
        // V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — lọc đúng lần giao đang
        // xem ("mỗi ReviewVideoAssignment" — xem Javadoc method), khớp đúng cách toán homeworkNextDueAt/
        // targetStudentIds ở dưới cũng scope theo assignment này, không gộp lẫn lần giao khác.
        List<ReviewVideoProgress> progressRows = videoIds.isEmpty()
                ? List.of() : reviewVideoProgressRepository.findByReviewVideoIdInAndReviewVideoAssignmentId(videoIds, assignment.getId());
        Map<String, ReviewVideoProgress> progressByKey = progressRows.stream()
                .collect(Collectors.toMap(p -> p.getReviewVideo().getId() + ":" + p.getStudent().getId(), p -> p));

        List<Long> targetStudentIds = assignment.getTargetStudentIds();
        List<Long> scopedStudentIds = targetStudentIds != null
                ? targetStudentIds
                : roster.stream().map(e -> e.getStudent().getId()).toList();

        int completedCount = 0;
        for (Long studentId : scopedStudentIds) {
            boolean allCompleted = !videos.isEmpty() && videos.stream().allMatch(v -> {
                ReviewVideoProgress p = progressByKey.get(v.getId() + ":" + studentId);
                return p != null && p.isCompleted();
            });
            if (allCompleted) {
                completedCount++;
            }
        }
        int totalStudents = scopedStudentIds.size();
        int completionPercent = totalStudents == 0 ? 0 : completedCount * 100 / totalStudents;

        // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-11 — CHỈ CONNECTION có "% đạt" (điểm
        // trắc nghiệm tổng ≥ ngưỡng % pass MỌI video CONNECTION trong bộ, mirror quy tắc "làm hết mới
        // tính" của completedCount ở trên). REFLEX chưa có khái niệm đạt/rớt nào, giữ null (FE hiện "—").
        Integer passedCount = null;
        Integer passRatePercent = null;
        if (set.getVideoType() == ReviewVideoSet.VideoType.CONNECTION) {
            int passed = 0;
            for (Long studentId : scopedStudentIds) {
                boolean allPassed = !videos.isEmpty() && videos.stream().allMatch(v -> isConnectionVideoPassed(v, studentId));
                if (allPassed) {
                    passed++;
                }
            }
            passedCount = passed;
            passRatePercent = totalStudents == 0 ? 0 : passed * 100 / totalStudents;
        }

        return new ReviewVideoAssignmentStatsResponse(
                assignment.getId(), set.getId(), set.getCode(), set.getTitle(), set.getVideoType(), set.getTeacherType(),
                assignment.getAvailableFrom(), assignment.getDueAt(), assignment.getStatus(),
                totalStudents, completedCount, completionPercent, passedCount, passRatePercent);
    }

    /**
     * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-08-11) — 1 học sinh "đạt" 1 video
     * CONNECTION khi tổng số câu trả lời ĐÚNG (lấy bản MỚI NHẤT mỗi câu hỏi — 1 câu có thể bị hỏi lại
     * ở chu kỳ lượt xem sau, xem ensureConnectionQuestionSlotsAssigned) / TỔNG số câu hỏi của video ≥
     * completionThresholdPercent (ý nghĩa mới: "ngưỡng % pass", xem reportProgress). Video chưa có câu
     * hỏi nào coi như đạt (vacuously true) — publish đã chặn trường hợp này (requireConnectionVideosHaveQuestions).
     */
    private boolean isConnectionVideoPassed(ReviewVideo video, Long studentId) {
        List<ReviewVideoConnectionQuestion> questions = reviewVideoConnectionQuestionRepository
                .findByReviewVideoIdOrderByDisplayOrder(video.getId());
        if (questions.isEmpty()) {
            return true;
        }
        List<ReviewVideoConnectionAnswer> answers = reviewVideoConnectionAnswerRepository
                .findByReviewVideoConnectionQuestion_ReviewVideoIdAndStudentId(video.getId(), studentId);
        Map<Long, ReviewVideoConnectionAnswer> latestByQuestion = answers.stream()
                .collect(Collectors.toMap(a -> a.getReviewVideoConnectionQuestion().getId(), a -> a,
                        (a, b) -> a.getAnsweredAt().isAfter(b.getAnsweredAt()) ? a : b));
        long correctCount = latestByQuestion.values().stream().filter(ReviewVideoConnectionAnswer::isCorrect).count();
        double percent = correctCount * 100.0 / questions.size();
        return percent >= video.getCompletionThresholdPercent();
    }

    /**
     * UC-23b Main Flow bước 3 (V57): giáo viên xem danh sách bài audio đã
     * nộp — chỉ ATTEMPT MỚI NHẤT mỗi (câu hỏi, học sinh) (chấm bản mới
     * nhất là chính, giống cách UC-40 lấy attempt mới nhất của bài ngữ
     * pháp) — không dựng ma trận roster × video đầy đủ như getStats(), vì
     * đây là "danh sách chờ chấm" không phải "thống kê xem video".
     */
    @Transactional(readOnly = true)
    public List<ReviewVideoSubmissionResponse> listSubmissionsForTeacher(Long setId, Long classIdParam, Long actorUserId) {
        ReviewVideoSet set = getSetOrThrow(setId);
        requireOwnerScope(set, actorUserId);
        Long classId = resolveClassIdForSet(set, classIdParam);
        return latestSubmissionsForSetAndClass(set, classId).stream().map(this::toResponse).toList();
    }

    /**
     * Attempt mới nhất mỗi (câu hỏi, học sinh) cho 1 (Bộ REFLEX, lớp) — tách ra từ
     * listSubmissionsForTeacher (2026-08-17) để dùng chung với hàng chờ chấm GỘP
     * theo lớp (getPendingGradingSummaryForTeacher/listSubmissionsForTeacherByClass).
     */
    private List<ReviewVideoQuestionSubmission> latestSubmissionsForSetAndClass(ReviewVideoSet set, Long classId) {
        List<Long> studentIds = classEnrollmentRepository.findBySchoolClassIdAndStatus(classId, ClassEnrollment.Status.ACTIVE)
                .stream().map(e -> e.getStudent().getId()).toList();
        List<Long> questionIds = reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(set.getId()).stream()
                .flatMap(v -> reviewVideoQuestionRepository.findByReviewVideoIdOrderByDisplayOrder(v.getId()).stream())
                .map(ReviewVideoQuestion::getId).toList();
        if (studentIds.isEmpty() || questionIds.isEmpty()) {
            return List.of();
        }
        return reviewVideoQuestionSubmissionRepository.findByReviewVideoQuestionIdInAndStudentIdIn(questionIds, studentIds)
                .stream()
                .collect(Collectors.toMap(
                        s -> s.getReviewVideoQuestion().getId() + ":" + s.getStudent().getId(),
                        s -> s,
                        (a, b) -> a.getAttemptNumber() >= b.getAttemptNumber() ? a : b))
                .values().stream().toList();
    }

    /**
     * UC-23b (bổ sung ngoài SDD gốc, xác nhận 2026-08-17) — tóm tắt số bài Video
     * phản xạ chưa chấm theo TỪNG lớp giáo viên ĐANG ĐỨNG TÊN THẬT (class_teachers,
     * assignedTo IS NULL), gộp mọi Bộ REFLEX đã gán cho lớp đó. Phạm vi HẸP HƠN
     * requireOwnerScope theo khung chương trình mà listSubmissionsForTeacher/
     * gradeSubmission đang dùng (2 method đó giữ nguyên, không đổi) — cố tình chỉ
     * tính lớp đứng tên thật, đúng tinh thần "lớp của tôi", mirror UC-62 hàng chờ
     * phúc khảo (ClassTeacherRepository#findByTeacherIdAndAssignedToIsNull). Dùng
     * để hiện badge số LỚP ở Sidebar + landing "Hàng chờ chấm bài".
     */
    @Transactional(readOnly = true)
    public List<PendingGradingClassSummaryResponse> getPendingGradingSummaryForTeacher(Long actorUserId) {
        List<SchoolClass> myClasses = classTeacherRepository.findByTeacherIdAndAssignedToIsNull(actorUserId).stream()
                .map(ClassTeacher::getSchoolClass)
                .distinct()
                .toList();
        List<PendingGradingClassSummaryResponse> result = new ArrayList<>();
        for (SchoolClass schoolClass : myClasses) {
            List<ReviewVideoSet> reflexSets = reviewVideoSetClassAssignmentRepository.findBySchoolClassId(schoolClass.getId())
                    .stream()
                    .map(ReviewVideoSetClassAssignment::getReviewVideoSet)
                    .filter(s -> s.getVideoType() == ReviewVideoSet.VideoType.REFLEX)
                    .distinct()
                    .toList();
            int pendingCount = 0;
            for (ReviewVideoSet set : reflexSets) {
                pendingCount += (int) latestSubmissionsForSetAndClass(set, schoolClass.getId()).stream()
                        .filter(s -> s.getScore() == null)
                        .count();
            }
            if (pendingCount > 0) {
                result.add(new PendingGradingClassSummaryResponse(
                        schoolClass.getId(), schoolClass.getClassCode(), schoolClass.getName(), pendingCount));
            }
        }
        result.sort(Comparator.comparing(PendingGradingClassSummaryResponse::className, Comparator.nullsLast(String::compareTo)));
        return result;
    }

    /**
     * UC-23b (bổ sung ngoài SDD gốc, xác nhận 2026-08-17) — hàng chờ chấm GỘP mọi
     * Bộ REFLEX đã gán cho 1 lớp, để giáo viên không phải tự chọn Bộ trước. Mỗi
     * dòng trả kèm thông tin Bộ/Video/câu hỏi để FE gắn nhãn nguồn (xem
     * ReviewVideoSubmissionResponse). Auth theo requireAssignedTeacher (lớp cụ
     * thể) — KHÁC requireOwnerScope theo khung chương trình mà endpoint Bộ→Lớp cũ
     * (listSubmissionsForTeacher) vẫn dùng, giữ nguyên song song làm lối xem phụ.
     */
    @Transactional(readOnly = true)
    public List<ReviewVideoSubmissionResponse> listSubmissionsForTeacherByClass(Long classId, Long actorUserId) {
        requireAssignedTeacher(classId, actorUserId);
        List<ReviewVideoSet> reflexSets = reviewVideoSetClassAssignmentRepository.findBySchoolClassId(classId)
                .stream()
                .map(ReviewVideoSetClassAssignment::getReviewVideoSet)
                .filter(s -> s.getVideoType() == ReviewVideoSet.VideoType.REFLEX)
                .distinct()
                .toList();
        List<ReviewVideoSubmissionResponse> result = new ArrayList<>();
        for (ReviewVideoSet set : reflexSets) {
            latestSubmissionsForSetAndClass(set, classId).forEach(s -> result.add(toResponseWithVideoInfo(s, set)));
        }
        return result;
    }

    /** UC-23b Main Flow bước 4: giáo viên chấm điểm + nhận xét cho 1 attempt đã nộp (A3 nếu không phụ trách lớp, A4 nếu không tồn tại). */
    @Transactional
    public ReviewVideoSubmissionResponse gradeSubmission(Long submissionId, GradeReviewVideoSubmissionRequest request, Long actorUserId) {
        ReviewVideoQuestionSubmission submission = reviewVideoQuestionSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp id=" + submissionId));
        requireOwnerScope(submission.getReviewVideoQuestion().getReviewVideo().getReviewVideoSet(), actorUserId);
        User actor = getUserOrThrow(actorUserId);

        submission.setScore(request.score());
        submission.setMaxScore(request.maxScore());
        submission.setFeedback(request.feedback());
        submission.setGradedBy(actor);
        submission.setGradedAt(OffsetDateTime.now());
        submission = reviewVideoQuestionSubmissionRepository.save(submission);
        return toResponse(submission);
    }

    // ===================== Helpers =====================

    private boolean isStudent(Long actorUserId) {
        return studentRepository.findByUserId(actorUserId).isPresent();
    }

    /**
     * UC-13 (2026-07-29): học sinh/phụ huynh xem được cả lớp cũ (đã chuyển đi) — không giới hạn
     * ACTIVE, chỉ cần đã TỪNG ghi danh lớp này (bất kỳ status), mirror đúng cách
     * ExerciseAttemptService#listMyAssignedExercises đang làm. Trước đây chỉ chấp nhận ACTIVE khiến
     * học sinh chọn lớp cũ ở dropdown "Lớp đang học" bị lỗi 404 "Không tìm thấy lớp học" khi mở tab
     * BTVN (bug đã báo lại, sửa 2026-08-12) — đi ngược đúng quy tắc UC-13 đã xác nhận từ trước.
     */
    private void requireStudentEnrolledInClass(Long classId, Long actorUserId) {
        var student = studentRepository.findByUserId(actorUserId).orElseThrow();
        boolean everEnrolled = classEnrollmentRepository.findByStudentId(student.getId()).stream()
                .anyMatch(e -> e.getSchoolClass().getId().equals(classId));
        if (!everEnrolled) {
            throw new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId);
        }
    }

    /** V69: kết quả tra quyền xem của học sinh — kèm ĐÚNG 1 lần giao (ReviewVideoAssignment) đang cấp quyền, dùng để scope submission theo đúng lần giao hiện tại. */
    private record StudentAccess(Student student, ReviewVideoAssignment assignment) {}

    /**
     * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * HS chỉ xem/báo tiến độ được bộ đã có {@link ReviewVideoAssignment}
     * ACTIVE giao cho 1 trong các lớp mình đang học ACTIVE (target_student_ids
     * null hoặc chứa đúng học sinh này) — publish không còn đồng nghĩa
     * xem được ngay. 404 (không 403) cho mọi trường hợp không hợp lệ —
     * không lộ sự tồn tại của bộ/video ngoài phạm vi.
     *
     * V69: trả kèm chính lần giao (ACTIVE) đã cấp quyền — deliverToClass
     * đảm bảo tại mọi thời điểm chỉ có TỐI ĐA 1 lần giao ACTIVE cho 1 (bộ,
     * lớp) nên không mơ hồ chọn nhầm lần giao cũ (trường hợp học sinh học
     * nhiều lớp cùng thấy 1 bộ thì lấy lần giao khớp lớp đầu tiên tìm thấy).
     */
    private StudentAccess resolveStudentAccess(ReviewVideoSet set, Long actorUserId) {
        if (set.getStatus() != ReviewVideoSet.Status.PUBLISHED) {
            throw new ResourceNotFoundException("Không tìm thấy bộ video id=" + set.getId());
        }
        var studentOpt = studentRepository.findByUserId(actorUserId);
        if (studentOpt.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy bộ video id=" + set.getId());
        }
        Student student = studentOpt.get();
        List<Long> classIds = classEnrollmentRepository.findByStudentIdAndStatus(student.getId(), ClassEnrollment.Status.ACTIVE)
                .stream().map(e -> e.getSchoolClass().getId()).toList();
        ReviewVideoAssignment matched = classIds.stream()
                .flatMap(classId -> reviewVideoAssignmentRepository.findByReviewVideoSetIdAndSchoolClassIdAndStatus(
                                set.getId(), classId, ReviewVideoAssignment.Status.ACTIVE)
                        .stream())
                .filter(a -> a.getTargetStudentIds() == null || a.getTargetStudentIds().contains(student.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bộ video id=" + set.getId()));
        return new StudentAccess(student, matched);
    }

    /** Wrapper cho các nơi chỉ cần Student, không cần biết lần giao cụ thể (listVideos/listQuestions). */
    private Student requireStudentCanViewSet(ReviewVideoSet set, Long actorUserId) {
        return resolveStudentAccess(set, actorUserId).student();
    }

    /**
     * V128/V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — mirror
     * {@link #resolveStudentAccess} nhưng nhận thẳng {@code assignmentId} thay vì tự đoán "lần giao
     * ACTIVE duy nhất" (không còn đúng từ khi 1 bộ có thể có NHIỀU lần giao ACTIVE song song, giao độc
     * lập từ nhiều buổi Nhận xét khác nhau — xem {@link #deliverToClass}). Dùng cho mọi thao tác cần
     * biết CHÍNH XÁC đang thao tác trên lần giao nào: startWatchSession, submitQuestionAudio,
     * getMyLatestSubmission, listMySubmissionHistory, getProgress. Mirror
     * {@code ExerciseAttemptService#resolveActiveAssignmentForStudent}.
     */
    private StudentAccess resolveStudentAccessForAssignment(ReviewVideoSet set, Long assignmentId, Long actorUserId) {
        if (set.getStatus() != ReviewVideoSet.Status.PUBLISHED) {
            throw new ResourceNotFoundException("Không tìm thấy bộ video id=" + set.getId());
        }
        Student student = studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bộ video id=" + set.getId()));
        ReviewVideoAssignment assignment = reviewVideoAssignmentRepository.findById(assignmentId)
                .filter(a -> a.getReviewVideoSet().getId().equals(set.getId()))
                .filter(a -> a.getStatus() == ReviewVideoAssignment.Status.ACTIVE)
                .filter(a -> a.getTargetStudentIds() == null || a.getTargetStudentIds().contains(student.getId()))
                .filter(a -> classEnrollmentRepository
                        .findBySchoolClassIdAndStudentIdAndStatus(a.getSchoolClass().getId(), student.getId(), ClassEnrollment.Status.ACTIVE)
                        .isPresent())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bộ video id=" + set.getId()));
        return new StudentAccess(student, assignment);
    }

    /**
     * Chặn ghi nhận kết quả (xem tiến độ/nộp đáp án/nộp audio) sau khi lần giao đã quá hạn nộp — mirror
     * đúng ExerciseAttemptService#submitAttempt (đã xác nhận với người dùng 2026-08-12, sửa lỗ hổng
     * Video Ôn tập trước đây KHÔNG hề chặn theo dueAt, khác Bài Ngữ pháp). Cố tình KHÔNG có cờ kiểu
     * lateSubmissionAllowed như Exercise — chặn cứng, chưa cần tùy chọn nộp trễ cho Video.
     *
     * Từ V128 (dedup theo sourceClassSession): giao lại cùng buổi học sẽ HỦY bản giao ACTIVE cũ (xem
     * {@link #cancelAssignment}) rồi tạo bản giao mới — nhưng phiên xem đã bắt đầu dưới bản giao cũ vẫn
     * giữ FK trỏ về đúng bản ghi đó (không tự trỏ sang bản mới). Bản giao cũ bị hủy KHÔNG đổi lại dueAt,
     * nên chỉ kiểm tra dueAt là chưa đủ — phải chặn cả khi bản giao đã bị thay thế/hủy, tránh học sinh
     * tiếp tục ghi nhận tiến độ vào 1 bản giao GV đã chủ động thay thế (lỗi phát hiện qua CI 2026-08-21,
     * bổ sung ngoài SDD gốc, xác nhận với người dùng).
     */
    private void requireNotPastDeadline(ReviewVideoAssignment assignment) {
        if (assignment.getStatus() != ReviewVideoAssignment.Status.ACTIVE) {
            throw new SubmissionPastDeadlineException("Bản giao Video Ôn tập này đã bị thay thế hoặc hủy, không thể ghi nhận thêm.");
        }
        if (assignment.getDueAt() != null && OffsetDateTime.now().isAfter(assignment.getDueAt())) {
            throw new SubmissionPastDeadlineException(
                    "Bản giao Video Ôn tập này đã quá hạn nộp (" + assignment.getDueAt() + ").");
        }
    }

    /** V98: curriculum luôn khác NULL — không còn nhánh schoolClass (xem Javadoc lớp ReviewVideoSet). */
    private void requireOwnerScope(ReviewVideoSet set, Long actorUserId) {
        requireAssignedTeacherForCurriculum(set.getCurriculum().getId(), actorUserId);
    }

    /**
     * Dùng chung cho getStats()/listSubmissionsForTeacher(): bắt buộc
     * truyền classId, phải là 1 lớp đã được gán tường minh cho bộ này
     * (V98, mirror điều kiện visibility của {@link #listByClass}).
     */
    private Long resolveClassIdForSet(ReviewVideoSet set, Long classIdParam) {
        if (classIdParam == null) {
            throw new IllegalArgumentException(
                    "Bộ video này bắt buộc chọn đúng 1 lớp để xem.");
        }
        getClassOrThrow(classIdParam);
        if (!reviewVideoSetClassAssignmentRepository.existsByReviewVideoSetIdAndSchoolClassId(set.getId(), classIdParam)) {
            throw new IllegalArgumentException("Lớp bạn chọn chưa được gán cho bộ video này.");
        }
        return classIdParam;
    }

    /** Quyền lms.review-video.manage (V107) vượt rào — quản trị viên gán/gỡ Bộ video của lớp bất kỳ. */
    private void requireAssignedTeacher(Long classId, Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, PERM_REVIEW_VIDEO_MANAGE)) {
            return;
        }
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "Bạn không được phân công giảng dạy lớp này.");
        }
    }

    /** Quyền lms.review-video.manage (V107) vượt rào — quản trị viên sửa Bộ video thuộc khung chương trình bất kỳ. */
    private void requireAssignedTeacherForCurriculum(Long curriculumId, Long actorUserId) {
        if (permissionEvaluationService.hasPermission(actorUserId, PERM_REVIEW_VIDEO_MANAGE)) {
            return;
        }
        if (!classTeacherRepository.existsBySchoolClass_CurriculumIdAndTeacherIdAndAssignedToIsNull(curriculumId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "Bạn không dạy lớp nào thuộc khung chương trình này.");
        }
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    private SchoolClass getClassOrThrow(Long id) {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + id));
    }

    private CurriculumSubject curriculumSubjectOrThrow(Long id) {
        return curriculumSubjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học phần id=" + id));
    }

    private ReviewVideoSet getSetOrThrow(Long id) {
        return reviewVideoSetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bộ video id=" + id));
    }

    private ReviewVideo getVideoOrThrow(Long id) {
        return reviewVideoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy video id=" + id));
    }

    private ReviewVideoQuestion getQuestionOrThrow(Long id) {
        return reviewVideoQuestionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy câu hỏi id=" + id));
    }

    private ReviewVideoConnectionQuestion getConnectionQuestionOrThrow(Long id) {
        return reviewVideoConnectionQuestionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy câu hỏi id=" + id));
    }

    /** V83: gate bắt buộc khi Publish 1 bộ CONNECTION — mọi video trong bộ phải có ít nhất 1 câu hỏi trắc nghiệm. */
    private void requireConnectionVideosHaveQuestions(ReviewVideoSet set) {
        if (set.getVideoType() != ReviewVideoSet.VideoType.CONNECTION) {
            return;
        }
        List<ReviewVideo> videos = reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(set.getId());
        for (ReviewVideo video : videos) {
            if (!reviewVideoConnectionQuestionRepository.existsByReviewVideoId(video.getId())) {
                throw new IllegalArgumentException(
                        "Video \"" + video.getTitle() + "\" chưa có câu hỏi trắc nghiệm — " +
                        "video Kết nối bắt buộc có câu hỏi trước khi Publish.");
            }
        }
    }

    /**
     * V129 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-19) — thêm tham số
     * {@code assignment}: rollup viewCount/completed giờ tách riêng theo TỪNG lần giao (trước đây gộp
     * chung 1 rollup theo (video, học sinh) — xem Javadoc {@link ReviewVideoProgress#getReviewVideoAssignment()}).
     */
    private ReviewVideoProgress getOrCreateProgress(ReviewVideo video, Student student, ReviewVideoAssignment assignment) {
        return reviewVideoProgressRepository
                .findByReviewVideoIdAndStudentIdAndReviewVideoAssignmentId(video.getId(), student.getId(), assignment.getId())
                .orElseGet(() -> {
                    ReviewVideoProgress p = new ReviewVideoProgress();
                    p.setReviewVideo(video);
                    p.setStudent(student);
                    p.setReviewVideoAssignment(assignment);
                    return p;
                });
    }

    /**
     * V83 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): tách từ
     * reportProgress thành helper dùng chung cho CẢ reportProgress LẪN
     * submitConnectionAnswers — video CONNECTION tính viewCount theo lượt
     * xem VỪA đạt ngưỡng VỪA đã nộp đủ câu hỏi (quizCompletedAt khác NULL);
     * video khác (REFLEX, nếu có gọi watch-session) giữ nguyên công thức cũ
     * (chỉ cần qualified) — không đổi hành vi REFLEX.
     *
     * V93/V101 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06,
     * giảm mặc định 80%→70% ngày 2026-08-07):
     * "đạt" (completed) của CONNECTION đổi từ đủ SỐ LƯỢT tuyệt đối
     * (viewCount >= requiredViewCount) sang TỶ LỆ %
     * (viewCount/requiredViewCount >= ReviewVideoSettings#completionPassThresholdPercent,
     * mặc định 70%) — VD yêu cầu 4 lượt, xem+nộp đúng 3 lượt = 75%, ĐẠT.
     * REFLEX giữ nguyên công thức cũ (không đổi hành vi, xem ghi chú V83
     * phía trên).
     */
    private ReviewVideoProgress recomputeProgress(ReviewVideo video, Student student, ReviewVideoAssignment assignment) {
        ReviewVideoProgress progress = getOrCreateProgress(video, student, assignment);
        boolean requiresQuiz = video.getReviewVideoSet().getVideoType() == ReviewVideoSet.VideoType.CONNECTION;
        int viewCount = requiresQuiz
                ? reviewVideoWatchSessionRepository.countByReviewVideoIdAndStudentIdAndReviewVideoAssignmentIdAndQualifiedTrueAndQuizCompletedAtIsNotNull(
                        video.getId(), student.getId(), assignment.getId())
                : reviewVideoWatchSessionRepository.countByReviewVideoIdAndStudentIdAndReviewVideoAssignmentIdAndQualifiedTrue(
                        video.getId(), student.getId(), assignment.getId());
        progress.setViewCount(viewCount);
        boolean completed = requiresQuiz
                ? viewCount * 100.0 / video.getRequiredViewCount() >= reviewVideoSettings.completionPassThresholdPercent()
                : viewCount >= video.getRequiredViewCount();
        progress.setCompleted(completed);
        return reviewVideoProgressRepository.save(progress);
    }

    private void writeHistory(ReviewVideoSet set, User actor, ReviewVideoSetHistory.Action action) {
        ReviewVideoSetHistory history = new ReviewVideoSetHistory();
        history.setReviewVideoSet(set);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", set.getTitle());
        snapshot.put("videoType", set.getVideoType().name());
        snapshot.put("status", set.getStatus().name());
        history.setDetails(snapshot);
        reviewVideoSetHistoryRepository.save(history);
    }

    private static int watchedPercentOf(int watchedSeconds, int durationSeconds) {
        if (durationSeconds <= 0) {
            return 0;
        }
        return Math.min(100, (int) Math.round(watchedSeconds * 100.0 / durationSeconds));
    }

    private ReviewVideoSetResponse toResponse(ReviewVideoSet s) {
        return new ReviewVideoSetResponse(
                s.getId(), s.getUuid(), s.getCode(), s.getTitle(), s.getVideoType().name(),
                s.getCurriculum().getId(), s.getCurriculum().getCode(),
                s.getSubject() == null ? null : s.getSubject().getId(),
                s.getTeacherType().name(),
                s.getDisplayOrder(), s.getStatus().name(), s.getPublishedAt(), s.getCreatedBy().getId());
    }

    private ClassResponse toResponse(SchoolClass c) {
        return new ClassResponse(c.getId(), c.getClassCode(), c.getName(),
                c.getSite().getId(), c.getSite().getName(),
                c.getCurriculum().getId(), c.getCurriculum().getCode(),
                c.getClassType().name(), c.getClassCategory(),
                c.getMaxStudents(), c.getMinStudents(), c.getStartDate(), c.getEndDate(),
                c.getAcademicYear() == null ? null : c.getAcademicYear().getId(),
                c.getAcademicYear() == null ? null : c.getAcademicYear().getCode(),
                c.getStatus().name(), c.getColor());
    }

    private ReviewVideoResponse toResponse(ReviewVideo v) {
        return new ReviewVideoResponse(
                v.getId(), v.getReviewVideoSet().getId(), v.getSourceType().name(), v.getTitle(), v.getFileUrl(),
                v.getFileSizeBytes(), v.getDurationSeconds(), v.getDisplayOrder(),
                v.getCompletionThresholdPercent(), v.getRequiredViewCount());
    }

    private ReviewVideoProgressResponse toResponse(ReviewVideoProgress p, ReviewVideo video) {
        int percent = watchedPercentOf(p.getWatchedSeconds(), video.getDurationSeconds());
        return new ReviewVideoProgressResponse(video.getId(), p.getWatchedSeconds(), video.getDurationSeconds(), percent,
                p.isCompleted(), p.getViewCount(), video.getRequiredViewCount());
    }

    private ReviewVideoQuestionResponse toResponse(ReviewVideoQuestion q) {
        return new ReviewVideoQuestionResponse(
                q.getId(), q.getReviewVideo().getId(), q.getTimestampSeconds(), q.getPrompt(),
                q.getMaxRecordingSeconds(), q.getMaxAttempts(), q.getDisplayOrder());
    }

    /** showAnswers=false (học sinh chưa nộp) -> isCorrect=null cho mọi lựa chọn, mirror ExerciseQuestionChoiceResponse. */
    private ReviewVideoConnectionQuestionResponse toResponse(
            ReviewVideoConnectionQuestion q, List<ReviewVideoConnectionChoice> choices, boolean showAnswers) {
        List<ReviewVideoConnectionChoiceResponse> choiceResponses = choices.stream()
                .map(c -> new ReviewVideoConnectionChoiceResponse(
                        c.getId(), c.getChoiceLabel(), c.getContent(), showAnswers ? c.isCorrect() : null, c.getDisplayOrder()))
                .toList();
        return new ReviewVideoConnectionQuestionResponse(q.getId(), q.getReviewVideo().getId(), q.getPrompt(),
                q.getDisplayOrder(), choiceResponses);
    }

    private ReviewVideoSubmissionResponse toResponse(ReviewVideoQuestionSubmission s) {
        return new ReviewVideoSubmissionResponse(
                s.getId(), s.getReviewVideoQuestion().getId(), s.getAttemptNumber(),
                s.getStudent().getId(), s.getStudent().getUser().getFullName(),
                s.getAudioUrl(), s.getSubmittedAt(), s.getScore(), s.getMaxScore(), s.getFeedback(),
                s.getGradedBy() == null ? null : s.getGradedBy().getId(), s.getGradedAt(),
                null, null, null, null, null, null, null);
    }

    /** Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — như toResponse(ReviewVideoQuestionSubmission), kèm thêm
     *  thông tin Bộ/Video/câu hỏi để FE gắn nhãn nguồn khi gộp nhiều Bộ trong hàng chờ chấm theo lớp. */
    private ReviewVideoSubmissionResponse toResponseWithVideoInfo(ReviewVideoQuestionSubmission s, ReviewVideoSet set) {
        ReviewVideoQuestion q = s.getReviewVideoQuestion();
        ReviewVideo video = q.getReviewVideo();
        return new ReviewVideoSubmissionResponse(
                s.getId(), q.getId(), s.getAttemptNumber(),
                s.getStudent().getId(), s.getStudent().getUser().getFullName(),
                s.getAudioUrl(), s.getSubmittedAt(), s.getScore(), s.getMaxScore(), s.getFeedback(),
                s.getGradedBy() == null ? null : s.getGradedBy().getId(), s.getGradedAt(),
                set.getId(), set.getTitle(), video.getId(), video.getTitle(), video.getDisplayOrder(),
                q.getPrompt(), q.getTimestampSeconds());
    }
}
