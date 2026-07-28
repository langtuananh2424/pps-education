package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.domain.ReviewVideo;
import vn.com.pps.education.domain.ReviewVideoProgress;
import vn.com.pps.education.domain.ReviewVideoQuestion;
import vn.com.pps.education.domain.ReviewVideoQuestionSubmission;
import vn.com.pps.education.domain.ReviewVideoSet;
import vn.com.pps.education.domain.ReviewVideoSetHistory;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AddReviewVideoQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.CreateReviewVideoSetRequest;
import vn.com.pps.education.dto.GradeReviewVideoSubmissionRequest;
import vn.com.pps.education.dto.ReportVideoProgressRequest;
import vn.com.pps.education.dto.ReviewVideoProgressResponse;
import vn.com.pps.education.dto.ReviewVideoQuestionResponse;
import vn.com.pps.education.dto.ReviewVideoResponse;
import vn.com.pps.education.dto.ReviewVideoSetResponse;
import vn.com.pps.education.dto.ReviewVideoSetStatsResponse;
import vn.com.pps.education.dto.ReviewVideoSubmissionResponse;
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
import vn.com.pps.education.repository.ReviewVideoProgressRepository;
import vn.com.pps.education.repository.ReviewVideoQuestionRepository;
import vn.com.pps.education.repository.ReviewVideoQuestionSubmissionRepository;
import vn.com.pps.education.repository.ReviewVideoRepository;
import vn.com.pps.education.repository.ReviewVideoSetHistoryRepository;
import vn.com.pps.education.repository.ReviewVideoSetRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 */
@Service
public class ReviewVideoService {

    private static final double COMPLETION_THRESHOLD = 0.8;

    private final ReviewVideoSetRepository reviewVideoSetRepository;
    private final ReviewVideoRepository reviewVideoRepository;
    private final ReviewVideoSetHistoryRepository reviewVideoSetHistoryRepository;
    private final ReviewVideoProgressRepository reviewVideoProgressRepository;
    private final ReviewVideoQuestionRepository reviewVideoQuestionRepository;
    private final ReviewVideoQuestionSubmissionRepository reviewVideoQuestionSubmissionRepository;
    private final CurriculumRepository curriculumRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    public ReviewVideoService(ReviewVideoSetRepository reviewVideoSetRepository,
                               ReviewVideoRepository reviewVideoRepository,
                               ReviewVideoSetHistoryRepository reviewVideoSetHistoryRepository,
                               ReviewVideoProgressRepository reviewVideoProgressRepository,
                               ReviewVideoQuestionRepository reviewVideoQuestionRepository,
                               ReviewVideoQuestionSubmissionRepository reviewVideoQuestionSubmissionRepository,
                               CurriculumRepository curriculumRepository,
                               SchoolClassRepository schoolClassRepository,
                               CurriculumSubjectRepository curriculumSubjectRepository,
                               ClassTeacherRepository classTeacherRepository,
                               ClassEnrollmentRepository classEnrollmentRepository,
                               StudentRepository studentRepository,
                               UserRepository userRepository) {
        this.reviewVideoSetRepository = reviewVideoSetRepository;
        this.reviewVideoRepository = reviewVideoRepository;
        this.reviewVideoSetHistoryRepository = reviewVideoSetHistoryRepository;
        this.reviewVideoProgressRepository = reviewVideoProgressRepository;
        this.reviewVideoQuestionRepository = reviewVideoQuestionRepository;
        this.reviewVideoQuestionSubmissionRepository = reviewVideoQuestionSubmissionRepository;
        this.curriculumRepository = curriculumRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.curriculumSubjectRepository = curriculumSubjectRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
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
     * UC-23a: HS trong lớp X xem được bộ riêng lớp X HOẶC bộ dùng chung
     * theo khung của lớp X (logic OR — copy nguyên văn từ
     * Lesson.findVisibleForClass, đã có lịch sử sửa 1 bug thật trước đây).
     */
    @Transactional(readOnly = true)
    public List<ReviewVideoSetResponse> listByClass(Long classId, Long actorUserId) {
        SchoolClass schoolClass = getClassOrThrow(classId);
        ReviewVideoSet.Status statusFilter = null;
        if (isStudent(actorUserId)) {
            requireStudentEnrolledInClass(classId, actorUserId);
            statusFilter = ReviewVideoSet.Status.PUBLISHED;
        }
        return reviewVideoSetRepository.findVisibleForClass(classId, schoolClass.getCurriculum().getId(), statusFilter)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewVideoSetResponse> listByCurriculum(Long curriculumId, Long actorUserId) {
        if (isStudent(actorUserId)) {
            requireStudentEnrolledInCurriculum(curriculumId, actorUserId);
            return reviewVideoSetRepository.findByCurriculumIdOrderByDisplayOrder(curriculumId).stream()
                    .filter(s -> s.getStatus() == ReviewVideoSet.Status.PUBLISHED)
                    .map(this::toResponse).toList();
        }
        return reviewVideoSetRepository.findByCurriculumIdOrderByDisplayOrder(curriculumId).stream().map(this::toResponse).toList();
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
     * UC-23a Main Flow bước 3: học sinh báo tiến độ xem (giây) — lấy
     * max(cũ, mới), không bao giờ giảm (chống tua tới báo khống). Bề mặt
     * ghi đầu tiên của học sinh trong module này — chặn bởi
     * requireStudentCanViewSet trước để không lộ sự tồn tại của video
     * ngoài phạm vi lớp mình (A2).
     */
    @Transactional
    public ReviewVideoProgressResponse reportProgress(Long videoId, ReportVideoProgressRequest request, Long actorUserId) {
        ReviewVideo video = getVideoOrThrow(videoId);
        Student student = requireStudentCanViewSet(video.getReviewVideoSet(), actorUserId);

        ReviewVideoProgress progress = reviewVideoProgressRepository
                .findByReviewVideoIdAndStudentId(videoId, student.getId())
                .orElseGet(() -> {
                    ReviewVideoProgress p = new ReviewVideoProgress();
                    p.setReviewVideo(video);
                    p.setStudent(student);
                    return p;
                });
        int newWatchedSeconds = Math.max(progress.getWatchedSeconds(), request.watchedSeconds());
        progress.setWatchedSeconds(newWatchedSeconds);
        progress.setCompleted(newWatchedSeconds >= video.getDurationSeconds() * COMPLETION_THRESHOLD);
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
     */
    @Transactional
    public ReviewVideoSubmissionResponse submitQuestionAudio(Long questionId, SubmitReviewVideoAudioRequest request, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        ReviewVideo video = question.getReviewVideo();
        Student student = requireStudentCanViewSet(video.getReviewVideoSet(), actorUserId);
        if (video.getReviewVideoSet().getVideoType() != ReviewVideoSet.VideoType.REFLEX) {
            throw new IllegalArgumentException("Video id=" + video.getId() + " không phải loại Video phản xạ (REFLEX) — không nhận nộp audio.");
        }

        int previousAttempts = reviewVideoQuestionSubmissionRepository
                .countByReviewVideoQuestionIdAndStudentId(questionId, student.getId());
        if (question.getMaxAttempts() != null && previousAttempts >= question.getMaxAttempts()) {
            throw new RetakeNotAllowedException(
                    "Câu hỏi id=" + questionId + " đã hết lượt nộp lại (tối đa " + question.getMaxAttempts() + ").");
        }

        ReviewVideoQuestionSubmission submission = new ReviewVideoQuestionSubmission();
        submission.setReviewVideoQuestion(question);
        submission.setStudent(student);
        submission.setAttemptNumber(previousAttempts + 1);
        submission.setAudioUrl(request.audioUrl());
        submission.setSubmittedAt(OffsetDateTime.now());
        submission = reviewVideoQuestionSubmissionRepository.save(submission);
        return toResponse(submission);
    }

    /** UC-23b (V57): học sinh xem attempt MỚI NHẤT mình đã nộp cho 1 câu hỏi — null nếu chưa nộp lần nào. */
    @Transactional(readOnly = true)
    public ReviewVideoSubmissionResponse getMyLatestSubmission(Long questionId, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        Student student = requireStudentCanViewSet(question.getReviewVideo().getReviewVideoSet(), actorUserId);
        List<ReviewVideoQuestionSubmission> attempts = reviewVideoQuestionSubmissionRepository
                .findByReviewVideoQuestionIdAndStudentIdOrderByAttemptNumberDesc(questionId, student.getId());
        return attempts.isEmpty() ? null : toResponse(attempts.get(0));
    }

    /** UC-23b (V57): học sinh xem TOÀN BỘ lịch sử các lần đã nộp cho 1 câu hỏi (mới nhất trước) — giữ lịch sử thì phải xem lại được. */
    @Transactional(readOnly = true)
    public List<ReviewVideoSubmissionResponse> listMySubmissionHistory(Long questionId, Long actorUserId) {
        ReviewVideoQuestion question = getQuestionOrThrow(questionId);
        Student student = requireStudentCanViewSet(question.getReviewVideo().getReviewVideoSet(), actorUserId);
        return reviewVideoQuestionSubmissionRepository
                .findByReviewVideoQuestionIdAndStudentIdOrderByAttemptNumberDesc(questionId, student.getId())
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
                .map(v -> new ReviewVideoSetStatsResponse.VideoHeader(v.getId(), v.getTitle(), v.getDurationSeconds()))
                .toList();

        List<ReviewVideoSetStatsResponse.StatsCell> cells = new ArrayList<>();
        for (ClassEnrollment enrollment : roster) {
            Long studentId = enrollment.getStudent().getId();
            for (ReviewVideo video : videos) {
                ReviewVideoProgress progress = progressByKey.get(video.getId() + ":" + studentId);
                int watchedSeconds = progress == null ? 0 : progress.getWatchedSeconds();
                boolean completed = progress != null && progress.isCompleted();
                int watchedPercent = watchedPercentOf(watchedSeconds, video.getDurationSeconds());
                cells.add(new ReviewVideoSetStatsResponse.StatsCell(studentId, video.getId(), watchedSeconds, watchedPercent, completed));
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

    /**
     * UC-23a: HS chỉ xem/báo tiến độ được bộ PUBLISHED và (đúng lớp mình
     * học HOẶC đúng khung của lớp mình học). Trả về Student để tái dùng ở
     * reportProgress (tránh query lại). 404 (không 403) cho mọi trường hợp
     * không hợp lệ — không lộ sự tồn tại của bộ/video ngoài phạm vi.
     */
    private Student requireStudentCanViewSet(ReviewVideoSet set, Long actorUserId) {
        if (set.getStatus() != ReviewVideoSet.Status.PUBLISHED) {
            throw new ResourceNotFoundException("Không tìm thấy bộ video id=" + set.getId());
        }
        var studentOpt = studentRepository.findByUserId(actorUserId);
        if (studentOpt.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy bộ video id=" + set.getId());
        }
        Student student = studentOpt.get();
        boolean visible;
        if (set.getSchoolClass() != null) {
            visible = classEnrollmentRepository.findBySchoolClassIdAndStudentIdAndStatus(
                    set.getSchoolClass().getId(), student.getId(), ClassEnrollment.Status.ACTIVE).isPresent();
        } else {
            visible = classEnrollmentRepository.existsByStudentIdAndSchoolClass_CurriculumIdAndStatus(
                    student.getId(), set.getCurriculum().getId(), ClassEnrollment.Status.ACTIVE);
        }
        if (!visible) {
            throw new ResourceNotFoundException("Không tìm thấy bộ video id=" + set.getId());
        }
        return student;
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
                v.getFileSizeBytes(), v.getDurationSeconds(), v.getDisplayOrder());
    }

    private ReviewVideoProgressResponse toResponse(ReviewVideoProgress p, ReviewVideo video) {
        int percent = watchedPercentOf(p.getWatchedSeconds(), video.getDurationSeconds());
        return new ReviewVideoProgressResponse(video.getId(), p.getWatchedSeconds(), video.getDurationSeconds(), percent, p.isCompleted());
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
