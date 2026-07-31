package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.domain.Notification;
import vn.com.pps.education.domain.ReviewVideo;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.domain.ReviewVideoProgress;
import vn.com.pps.education.domain.ReviewVideoQuestion;
import vn.com.pps.education.domain.ReviewVideoQuestionSubmission;
import vn.com.pps.education.domain.ReviewVideoSet;
import vn.com.pps.education.domain.ReviewVideoSetHistory;
import vn.com.pps.education.domain.ReviewVideoWatchSession;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AddReviewVideoQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.CreateReviewVideoSetRequest;
import vn.com.pps.education.dto.GradeReviewVideoSubmissionRequest;
import vn.com.pps.education.dto.MyReviewVideoAssignmentResponse;
import vn.com.pps.education.dto.ReportVideoProgressRequest;
import vn.com.pps.education.dto.ReviewVideoAssignmentResponse;
import vn.com.pps.education.dto.ReviewVideoProgressResponse;
import vn.com.pps.education.dto.ReviewVideoQuestionResponse;
import vn.com.pps.education.dto.ReviewVideoResponse;
import vn.com.pps.education.dto.ReviewVideoSetResponse;
import vn.com.pps.education.dto.ReviewVideoSetStatsResponse;
import vn.com.pps.education.dto.ReviewVideoSubmissionResponse;
import vn.com.pps.education.dto.StartWatchSessionResponse;
import vn.com.pps.education.dto.SubmitReviewVideoAudioRequest;
import vn.com.pps.education.dto.UpdateReviewVideoSetRequest;
import vn.com.pps.education.exception.InvalidReviewVideoSetScopeException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.RetakeNotAllowedException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.CurriculumSubjectRepository;
import vn.com.pps.education.repository.ReviewVideoAssignmentRepository;
import vn.com.pps.education.repository.ReviewVideoProgressRepository;
import vn.com.pps.education.repository.ReviewVideoQuestionRepository;
import vn.com.pps.education.repository.ReviewVideoQuestionSubmissionRepository;
import vn.com.pps.education.repository.ReviewVideoRepository;
import vn.com.pps.education.repository.ReviewVideoSetHistoryRepository;
import vn.com.pps.education.repository.ReviewVideoSetRepository;
import vn.com.pps.education.repository.ReviewVideoWatchSessionRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final ReviewVideoRepository reviewVideoRepository;
    private final ReviewVideoSetHistoryRepository reviewVideoSetHistoryRepository;
    private final ReviewVideoProgressRepository reviewVideoProgressRepository;
    private final ReviewVideoQuestionRepository reviewVideoQuestionRepository;
    private final ReviewVideoQuestionSubmissionRepository reviewVideoQuestionSubmissionRepository;
    private final ReviewVideoWatchSessionRepository reviewVideoWatchSessionRepository;
    private final ReviewVideoAssignmentRepository reviewVideoAssignmentRepository;
    private final CurriculumRepository curriculumRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ReviewVideoService(ReviewVideoSetRepository reviewVideoSetRepository,
                               ReviewVideoRepository reviewVideoRepository,
                               ReviewVideoSetHistoryRepository reviewVideoSetHistoryRepository,
                               ReviewVideoProgressRepository reviewVideoProgressRepository,
                               ReviewVideoQuestionRepository reviewVideoQuestionRepository,
                               ReviewVideoQuestionSubmissionRepository reviewVideoQuestionSubmissionRepository,
                               ReviewVideoWatchSessionRepository reviewVideoWatchSessionRepository,
                               ReviewVideoAssignmentRepository reviewVideoAssignmentRepository,
                               CurriculumRepository curriculumRepository,
                               SchoolClassRepository schoolClassRepository,
                               CurriculumSubjectRepository curriculumSubjectRepository,
                               ClassTeacherRepository classTeacherRepository,
                               ClassEnrollmentRepository classEnrollmentRepository,
                               StudentRepository studentRepository,
                               UserRepository userRepository,
                               NotificationService notificationService) {
        this.reviewVideoSetRepository = reviewVideoSetRepository;
        this.reviewVideoRepository = reviewVideoRepository;
        this.reviewVideoSetHistoryRepository = reviewVideoSetHistoryRepository;
        this.reviewVideoProgressRepository = reviewVideoProgressRepository;
        this.reviewVideoQuestionRepository = reviewVideoQuestionRepository;
        this.reviewVideoQuestionSubmissionRepository = reviewVideoQuestionSubmissionRepository;
        this.reviewVideoWatchSessionRepository = reviewVideoWatchSessionRepository;
        this.reviewVideoAssignmentRepository = reviewVideoAssignmentRepository;
        this.curriculumRepository = curriculumRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.curriculumSubjectRepository = curriculumSubjectRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /** UC-23 Main Flow bước 1: tạo bộ mới (metadata), gán vào khung chương trình hoặc lớp cụ thể. */
    @Transactional
    public ReviewVideoSetResponse createSet(CreateReviewVideoSetRequest request, Long actorUserId) {
        if ((request.curriculumId() == null) == (request.classId() == null)) {
            throw new InvalidReviewVideoSetScopeException(
                    "Bộ video ôn tập phải gán đúng 1 trong 2: curriculumId (bộ chung) hoặc classId (bộ riêng lớp), không cả hai hoặc không cái nào.");
        }
        User actor = getUserOrThrow(actorUserId);

        ReviewVideoSet set = new ReviewVideoSet();
        set.setCode(request.code());
        set.setTitle(request.title());
        set.setVideoType(ReviewVideoSet.VideoType.valueOf(request.videoType()));
        if (request.curriculumId() != null) {
            Curriculum curriculum = curriculumRepository.findByIdAndDeletedAtIsNull(request.curriculumId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung chương trình id=" + request.curriculumId()));
            requireAssignedTeacherForCurriculum(request.curriculumId(), actorUserId);
            set.setCurriculum(curriculum);
        } else {
            SchoolClass schoolClass = getClassOrThrow(request.classId());
            requireAssignedTeacher(request.classId(), actorUserId);
            set.setSchoolClass(schoolClass);
        }
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
        if (request.subjectId() != null) {
            set.setSubject(curriculumSubjectOrThrow(request.subjectId()));
        }
        set.setDisplayOrder(request.displayOrder());
        ReviewVideoSet.Status newStatus = ReviewVideoSet.Status.valueOf(request.status());
        if (newStatus == ReviewVideoSet.Status.PUBLISHED && set.getStatus() != ReviewVideoSet.Status.PUBLISHED) {
            set.setPublishedAt(OffsetDateTime.now());
        }
        set.setStatus(newStatus);
        set = reviewVideoSetRepository.save(set);

        writeHistory(set, actor, ReviewVideoSetHistory.Action.UPDATED);
        return toResponse(set);
    }

    /**
     * UC-23a: GV xem TẤT CẢ bộ riêng lớp X HOẶC bộ dùng chung theo khung
     * của lớp X, mọi trạng thái (logic OR — copy nguyên văn từ
     * Lesson.findVisibleForClass, đã có lịch sử sửa 1 bug thật trước đây)
     * — để chọn nguồn khi soạn "BTVN buổi sau". HS chỉ thấy bộ đã có
     * {@link ReviewVideoAssignment} ACTIVE giao cho lớp mình (V65, bổ
     * sung ngoài SDD gốc, đã xác nhận với người dùng — publish không còn
     * đồng nghĩa xem được ngay).
     */
    @Transactional(readOnly = true)
    public List<ReviewVideoSetResponse> listByClass(Long classId, Long actorUserId) {
        SchoolClass schoolClass = getClassOrThrow(classId);
        if (isStudent(actorUserId)) {
            Student student = studentRepository.findByUserId(actorUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId));
            requireStudentEnrolledInClass(classId, actorUserId);
            Set<Long> assignedSetIds = assignedSetIdsForStudentInClasses(List.of(classId), student.getId());
            return reviewVideoSetRepository.findVisibleForClass(classId, schoolClass.getCurriculum().getId(), ReviewVideoSet.Status.PUBLISHED)
                    .stream().filter(s -> assignedSetIds.contains(s.getId())).map(this::toResponse).toList();
        }
        return reviewVideoSetRepository.findVisibleForClass(classId, schoolClass.getCurriculum().getId(), null)
                .stream().map(this::toResponse).toList();
    }

    /** HS xem theo khung chương trình (nhiều lớp có thể học chung 1 khung) — hợp nhất bộ đã giao qua mọi lớp ACTIVE của HS thuộc đúng khung đó. */
    @Transactional(readOnly = true)
    public List<ReviewVideoSetResponse> listByCurriculum(Long curriculumId, Long actorUserId) {
        if (isStudent(actorUserId)) {
            Student student = studentRepository.findByUserId(actorUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung chương trình id=" + curriculumId));
            requireStudentEnrolledInCurriculum(curriculumId, actorUserId);
            List<Long> classIds = classEnrollmentRepository.findByStudentIdAndStatus(student.getId(), ClassEnrollment.Status.ACTIVE)
                    .stream()
                    .filter(e -> e.getSchoolClass().getCurriculum() != null
                            && e.getSchoolClass().getCurriculum().getId().equals(curriculumId))
                    .map(e -> e.getSchoolClass().getId()).toList();
            Set<Long> assignedSetIds = assignedSetIdsForStudentInClasses(classIds, student.getId());
            return reviewVideoSetRepository.findByCurriculumIdOrderByDisplayOrder(curriculumId).stream()
                    .filter(s -> s.getStatus() == ReviewVideoSet.Status.PUBLISHED && assignedSetIds.contains(s.getId()))
                    .map(this::toResponse).toList();
        }
        return reviewVideoSetRepository.findByCurriculumIdOrderByDisplayOrder(curriculumId).stream().map(this::toResponse).toList();
    }

    /**
     * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * giao 1 bộ Video Ôn tập (CONNECTION hoặc REFLEX) cho TOÀN BỘ học
     * sinh ACTIVE của 1 lớp, hạn nộp = tham số dueAt (buổi kế tiếp, tính
     * ở StudentCommentService). Gọi TỪ StudentCommentService khi Giáo
     * viên chọn bộ này làm "BTVN buổi sau" — KHÔNG expose qua Controller.
     * Validate bộ đang PUBLISHED + đúng scope (curriculum/lớp) của lớp
     * được giao, dùng lại findVisibleForClass để không lặp logic OR.
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
     */
    @Transactional
    public ReviewVideoAssignment deliverToClass(Long setId, Long classId, OffsetDateTime dueAt, Long actorUserId) {
        ReviewVideoSet set = getSetOrThrow(setId);
        if (set.getStatus() != ReviewVideoSet.Status.PUBLISHED) {
            throw new IllegalArgumentException("Bộ video id=" + setId + " chưa Publish — không giao lớp được.");
        }
        requireAssignedTeacher(classId, actorUserId);
        SchoolClass schoolClass = getClassOrThrow(classId);
        boolean inScope = reviewVideoSetRepository
                .findVisibleForClass(classId, schoolClass.getCurriculum().getId(), ReviewVideoSet.Status.PUBLISHED)
                .stream().anyMatch(s -> s.getId().equals(setId));
        if (!inScope) {
            throw new IllegalArgumentException("Bộ video id=" + setId + " không thuộc phạm vi lớp id=" + classId + ".");
        }
        User actor = getUserOrThrow(actorUserId);

        List<ReviewVideoAssignment> activeForSetAndClass = reviewVideoAssignmentRepository
                .findByReviewVideoSetIdAndSchoolClassIdAndStatus(setId, classId, ReviewVideoAssignment.Status.ACTIVE);
        var sameSession = activeForSetAndClass.stream().filter(a -> Objects.equals(a.getDueAt(), dueAt)).findFirst();
        if (sameSession.isPresent()) {
            return sameSession.get();
        }
        activeForSetAndClass.forEach(this::cancelAssignment);

        ReviewVideoAssignment assignment = new ReviewVideoAssignment();
        assignment.setReviewVideoSet(set);
        assignment.setSchoolClass(schoolClass);
        assignment.setAssignedBy(actor);
        assignment.setDueAt(dueAt);
        assignment = reviewVideoAssignmentRepository.save(assignment);

        notifyAssignedStudents(schoolClass, set, assignment);
        return assignment;
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
                        assignment.getAvailableFrom(), assignment.getDueAt()));
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

    /** Hợp nhất id các bộ đã giao ACTIVE cho HS qua danh sách lớp (target_student_ids null = cả lớp, hoặc có chứa studentId). */
    private Set<Long> assignedSetIdsForStudentInClasses(List<Long> classIds, Long studentId) {
        Set<Long> setIds = new HashSet<>();
        for (Long classId : classIds) {
            for (ReviewVideoAssignment a : reviewVideoAssignmentRepository.findBySchoolClassIdAndStatus(classId, ReviewVideoAssignment.Status.ACTIVE)) {
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
     */
    @Transactional
    public ReviewVideoQuestionResponse addQuestion(Long videoId, AddReviewVideoQuestionRequest request, Long actorUserId) {
        ReviewVideo video = getVideoOrThrow(videoId);
        requireOwnerScope(video.getReviewVideoSet(), actorUserId);
        if (video.getReviewVideoSet().getVideoType() != ReviewVideoSet.VideoType.REFLEX) {
            throw new IllegalArgumentException("Video id=" + videoId + " không phải loại Video phản xạ (REFLEX) — không nhận câu hỏi.");
        }

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

    @Transactional(readOnly = true)
    public List<ReviewVideoQuestionResponse> listQuestions(Long videoId, Long actorUserId) {
        ReviewVideo video = getVideoOrThrow(videoId);
        if (isStudent(actorUserId)) {
            requireStudentCanViewSet(video.getReviewVideoSet(), actorUserId);
        }
        return reviewVideoQuestionRepository.findByReviewVideoIdOrderByDisplayOrder(videoId).stream().map(this::toResponse).toList();
    }

    /**
     * UC-23a (V59): mở 1 LƯỢT xem mới cho video CONNECTION — gọi khi học
     * sinh bắt đầu/mở lại video. Trả sessionId để các lần reportProgress
     * tiếp theo của lượt này cập nhật đúng session, không lẫn với lượt
     * khác (khác cơ chế watermark suốt đời cũ, vốn không phân biệt được
     * "lần" nào với "lần" nào).
     */
    @Transactional
    public StartWatchSessionResponse startWatchSession(Long videoId, Long actorUserId) {
        ReviewVideo video = getVideoOrThrow(videoId);
        Student student = requireStudentCanViewSet(video.getReviewVideoSet(), actorUserId);

        ReviewVideoWatchSession session = new ReviewVideoWatchSession();
        session.setReviewVideo(video);
        session.setStudent(student);
        session = reviewVideoWatchSessionRepository.save(session);
        return new StartWatchSessionResponse(session.getId());
    }

    /**
     * UC-23a Main Flow bước 3 (V59): học sinh báo tiến độ xem (giây) cho
     * ĐÚNG 1 lượt xem (watchSessionId) — lấy max(cũ, mới) TRONG PHẠM VI
     * lượt đó, không bao giờ giảm. Lượt đạt completionThresholdPercent
     * của video được đánh dấu qualified — chỉ lượt qualified mới tính
     * vào viewCount. "Đạt" (completed) = viewCount >= requiredViewCount
     * của video (đã xác nhận với người dùng — bổ sung ngoài SDD gốc,
     * 2 tiêu chí ĐỘC LẬP, cả 2 đều cấu hình được khi tạo video).
     */
    @Transactional
    public ReviewVideoProgressResponse reportProgress(Long videoId, ReportVideoProgressRequest request, Long actorUserId) {
        ReviewVideo video = getVideoOrThrow(videoId);
        Student student = requireStudentCanViewSet(video.getReviewVideoSet(), actorUserId);

        ReviewVideoWatchSession session = reviewVideoWatchSessionRepository.findById(request.watchSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xem id=" + request.watchSessionId()));
        if (!session.getReviewVideo().getId().equals(videoId) || !session.getStudent().getId().equals(student.getId())) {
            throw new ResourceNotFoundException("Không tìm thấy lượt xem id=" + request.watchSessionId());
        }
        int sessionWatchedSeconds = Math.max(session.getWatchedSeconds(), request.watchedSeconds());
        session.setWatchedSeconds(sessionWatchedSeconds);
        session.setQualified(sessionWatchedSeconds >= video.getDurationSeconds() * (video.getCompletionThresholdPercent() / 100.0));
        reviewVideoWatchSessionRepository.save(session);

        ReviewVideoProgress progress = reviewVideoProgressRepository
                .findByReviewVideoIdAndStudentId(videoId, student.getId())
                .orElseGet(() -> {
                    ReviewVideoProgress p = new ReviewVideoProgress();
                    p.setReviewVideo(video);
                    p.setStudent(student);
                    return p;
                });
        progress.setWatchedSeconds(Math.max(progress.getWatchedSeconds(), sessionWatchedSeconds));
        int viewCount = reviewVideoWatchSessionRepository.countByReviewVideoIdAndStudentIdAndQualifiedTrue(videoId, student.getId());
        progress.setViewCount(viewCount);
        progress.setCompleted(viewCount >= video.getRequiredViewCount());
        progress = reviewVideoProgressRepository.save(progress);
        return toResponse(progress, video);
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
     */
    @Transactional
    public ReviewVideoSubmissionResponse submitQuestionAudio(Long questionId, SubmitReviewVideoAudioRequest request, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        ReviewVideo video = question.getReviewVideo();
        StudentAccess access = resolveStudentAccess(video.getReviewVideoSet(), actorUserId);
        Student student = access.student();
        if (video.getReviewVideoSet().getVideoType() != ReviewVideoSet.VideoType.REFLEX) {
            throw new IllegalArgumentException("Video id=" + video.getId() + " không phải loại Video phản xạ (REFLEX) — không nhận nộp audio.");
        }

        int previousAttempts = reviewVideoQuestionSubmissionRepository
                .countByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentId(questionId, student.getId(), access.assignment().getId());
        if (question.getMaxAttempts() != null && previousAttempts >= question.getMaxAttempts()) {
            throw new RetakeNotAllowedException(
                    "Câu hỏi id=" + questionId + " đã hết lượt nộp lại (tối đa " + question.getMaxAttempts() + ").");
        }

        ReviewVideoQuestionSubmission submission = new ReviewVideoQuestionSubmission();
        submission.setReviewVideoQuestion(question);
        submission.setStudent(student);
        submission.setReviewVideoAssignment(access.assignment());
        submission.setAttemptNumber(previousAttempts + 1);
        submission.setAudioUrl(request.audioUrl());
        submission.setSubmittedAt(OffsetDateTime.now());
        submission = reviewVideoQuestionSubmissionRepository.save(submission);
        return toResponse(submission);
    }

    /** UC-23b (V57): học sinh xem attempt MỚI NHẤT mình đã nộp cho 1 câu hỏi — null nếu chưa nộp lần nào. V69: chỉ tính trong phạm vi ĐÚNG lần giao hiện tại (xem Javadoc submitQuestionAudio). */
    @Transactional(readOnly = true)
    public ReviewVideoSubmissionResponse getMyLatestSubmission(Long questionId, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        StudentAccess access = resolveStudentAccess(question.getReviewVideo().getReviewVideoSet(), actorUserId);
        List<ReviewVideoQuestionSubmission> attempts = reviewVideoQuestionSubmissionRepository
                .findByReviewVideoQuestionIdAndStudentIdAndReviewVideoAssignmentIdOrderByAttemptNumberDesc(
                        questionId, access.student().getId(), access.assignment().getId());
        return attempts.isEmpty() ? null : toResponse(attempts.get(0));
    }

    /** UC-23b (V57): học sinh xem TOÀN BỘ lịch sử các lần đã nộp cho 1 câu hỏi (mới nhất trước) — giữ lịch sử thì phải xem lại được. V69: chỉ tính trong phạm vi ĐÚNG lần giao hiện tại (xem Javadoc submitQuestionAudio). */
    @Transactional(readOnly = true)
    public List<ReviewVideoSubmissionResponse> listMySubmissionHistory(Long questionId, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        StudentAccess access = resolveStudentAccess(question.getReviewVideo().getReviewVideoSet(), actorUserId);
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
        List<ReviewVideoProgress> progressRows = videoIds.isEmpty()
                ? List.of() : reviewVideoProgressRepository.findByReviewVideoIdIn(videoIds);
        Map<String, ReviewVideoProgress> progressByKey = progressRows.stream()
                .collect(Collectors.toMap(p -> p.getReviewVideo().getId() + ":" + p.getStudent().getId(), p -> p));

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

        List<Long> studentIds = classEnrollmentRepository.findBySchoolClassIdAndStatus(classId, ClassEnrollment.Status.ACTIVE)
                .stream().map(e -> e.getStudent().getId()).toList();
        List<Long> questionIds = reviewVideoRepository.findByReviewVideoSetIdOrderByDisplayOrder(setId).stream()
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
                .values().stream()
                .map(this::toResponse).toList();
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

    private void requireStudentEnrolledInClass(Long classId, Long actorUserId) {
        var student = studentRepository.findByUserId(actorUserId).orElseThrow();
        if (classEnrollmentRepository.findBySchoolClassIdAndStudentIdAndStatus(
                classId, student.getId(), ClassEnrollment.Status.ACTIVE).isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy lớp học id=" + classId);
        }
    }

    private void requireStudentEnrolledInCurriculum(Long curriculumId, Long actorUserId) {
        var student = studentRepository.findByUserId(actorUserId).orElseThrow();
        if (!classEnrollmentRepository.existsByStudentIdAndSchoolClass_CurriculumIdAndStatus(
                student.getId(), curriculumId, ClassEnrollment.Status.ACTIVE)) {
            throw new ResourceNotFoundException("Không tìm thấy khung chương trình id=" + curriculumId);
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

    /** Wrapper cho các nơi chỉ cần Student, không cần biết lần giao cụ thể (listVideos/listQuestions/startWatchSession/reportProgress). */
    private Student requireStudentCanViewSet(ReviewVideoSet set, Long actorUserId) {
        return resolveStudentAccess(set, actorUserId).student();
    }

    private void requireOwnerScope(ReviewVideoSet set, Long actorUserId) {
        if (set.getCurriculum() != null) {
            requireAssignedTeacherForCurriculum(set.getCurriculum().getId(), actorUserId);
        } else {
            requireAssignedTeacher(set.getSchoolClass().getId(), actorUserId);
        }
    }

    /**
     * Dùng chung cho getStats()/listSubmissionsForTeacher(): bộ riêng 1
     * lớp thì classId suy ra từ bộ (bỏ qua/đối chiếu classIdParam nếu
     * có); bộ dùng chung theo khung chương trình thì bắt buộc truyền
     * classIdParam và phải thuộc đúng khung đó.
     */
    private Long resolveClassIdForSet(ReviewVideoSet set, Long classIdParam) {
        if (set.getSchoolClass() != null) {
            if (classIdParam != null && !classIdParam.equals(set.getSchoolClass().getId())) {
                throw new IllegalArgumentException("classId không khớp lớp của bộ video id=" + set.getId() + ".");
            }
            return set.getSchoolClass().getId();
        }
        if (classIdParam == null) {
            throw new IllegalArgumentException(
                    "Bộ video id=" + set.getId() + " gán theo khung chương trình — bắt buộc truyền classId để xem theo đúng 1 lớp.");
        }
        SchoolClass schoolClass = getClassOrThrow(classIdParam);
        if (schoolClass.getCurriculum() == null || !schoolClass.getCurriculum().getId().equals(set.getCurriculum().getId())) {
            throw new IllegalArgumentException("Lớp id=" + classIdParam + " không thuộc khung chương trình của bộ video id=" + set.getId() + ".");
        }
        return classIdParam;
    }

    private void requireAssignedTeacher(Long classId, Long actorUserId) {
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "Tài khoản id=" + actorUserId + " không được phân công giảng dạy lớp id=" + classId + ".");
        }
    }

    private void requireAssignedTeacherForCurriculum(Long curriculumId, Long actorUserId) {
        if (!classTeacherRepository.existsBySchoolClass_CurriculumIdAndTeacherIdAndAssignedToIsNull(curriculumId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "Tài khoản id=" + actorUserId + " không dạy lớp nào thuộc khung chương trình id=" + curriculumId + ".");
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
                s.getCurriculum() == null ? null : s.getCurriculum().getId(),
                s.getSchoolClass() == null ? null : s.getSchoolClass().getId(),
                s.getSubject() == null ? null : s.getSubject().getId(),
                s.getDisplayOrder(), s.getStatus().name(), s.getPublishedAt(), s.getCreatedBy().getId());
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

    private ReviewVideoSubmissionResponse toResponse(ReviewVideoQuestionSubmission s) {
        return new ReviewVideoSubmissionResponse(
                s.getId(), s.getReviewVideoQuestion().getId(), s.getAttemptNumber(),
                s.getStudent().getId(), s.getStudent().getUser().getFullName(),
                s.getAudioUrl(), s.getSubmittedAt(), s.getScore(), s.getMaxScore(), s.getFeedback(),
                s.getGradedBy() == null ? null : s.getGradedBy().getId(), s.getGradedAt());
    }
}
