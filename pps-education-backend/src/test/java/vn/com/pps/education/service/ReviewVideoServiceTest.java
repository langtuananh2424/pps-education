package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AddReviewVideoQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateReviewVideoSetRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.GradeReviewVideoSubmissionRequest;
import vn.com.pps.education.dto.ReportVideoProgressRequest;
import vn.com.pps.education.dto.ReviewVideoProgressResponse;
import vn.com.pps.education.dto.ReviewVideoQuestionResponse;
import vn.com.pps.education.dto.ReviewVideoResponse;
import vn.com.pps.education.dto.ReviewVideoSetResponse;
import vn.com.pps.education.dto.ReviewVideoSetStatsResponse;
import vn.com.pps.education.dto.ReviewVideoSubmissionResponse;
import vn.com.pps.education.dto.SubmitReviewVideoAudioRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateReviewVideoSetRequest;
import vn.com.pps.education.exception.InvalidReviewVideoSetScopeException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.RetakeNotAllowedException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-23: Quản lý Kho Video Ôn tập, UC-23a: Xem & Theo dõi Kho Video Ôn
 * tập — Main Flow, A1 (không áp dụng ở tầng Service — upload CDN nằm
 * ngoài phạm vi), A2 (học sinh/video ngoài phạm vi lớp). Tái cấu trúc
 * 2026-07-27 từ LessonServiceTest.
 *
 * UC-23b: Nộp & Chấm điểm Audio cho Video Phản xạ — Main Flow, A1 (video
 * không phải REFLEX), A2 (học sinh ngoài phạm vi), A3 (giáo viên không
 * phụ trách), A4 (chấm bài không tồn tại), A5 (nộp lại xoá điểm cũ).
 */
@Transactional
class ReviewVideoServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ReviewVideoService reviewVideoService;

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private StudentRepository studentRepository;

    private User headAcademic;
    private User teacher;
    private CurriculumResponse activeCurriculum;
    private ClassResponse schoolClass;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null), headAcademic.getId());
        activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());
    }

    @Test
    void createSet_UC23_MainFlow_savesClassScopedSetAsDraft() {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Video TKN Unit 1", "CONNECTION", null, schoolClass.id(), null, 1),
                teacher.getId());

        assertThat(set.status()).isEqualTo("DRAFT");
        assertThat(set.videoType()).isEqualTo("CONNECTION");
        assertThat(set.classId()).isEqualTo(schoolClass.id());
        assertThat(set.curriculumId()).isNull();
    }

    @Test
    void createSet_UC23_MainFlow_savesCurriculumScopedSharedSet() {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Video phản xạ chung", "REFLEX", activeCurriculum.id(), null, null, 1),
                teacher.getId());

        assertThat(set.curriculumId()).isEqualTo(activeCurriculum.id());
        assertThat(set.classId()).isNull();
    }

    @Test
    void createSet_UC23_A_rejectsWhenBothScopesGiven() {
        assertThatThrownBy(() -> reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "X", "CONNECTION", activeCurriculum.id(), schoolClass.id(), null, null),
                teacher.getId()))
                .isInstanceOf(InvalidReviewVideoSetScopeException.class);
    }

    @Test
    void createSet_UC23_A_rejectsWhenNoScopeGiven() {
        assertThatThrownBy(() -> reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "X", "CONNECTION", null, null, null, null),
                teacher.getId()))
                .isInstanceOf(InvalidReviewVideoSetScopeException.class);
    }

    @Test
    void createSet_rejectsWhenActorNotAssignedTeacherForClass() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "X", "CONNECTION", null, schoolClass.id(), null, null),
                outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void createSet_rejectsWhenActorNotAssignedTeacherForCurriculum() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "X", "REFLEX", activeCurriculum.id(), null, null, null),
                outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void updateSet_UC23_MainFlow_publishingSetsPublishedAtOnce() {
        ReviewVideoSetResponse set = createClassScopedSet();

        ReviewVideoSetResponse published = reviewVideoService.updateSet(set.id(),
                new UpdateReviewVideoSetRequest(set.title(), null, 1, "PUBLISHED"), teacher.getId());
        var firstPublishedAt = published.publishedAt();

        ReviewVideoSetResponse republished = reviewVideoService.updateSet(set.id(),
                new UpdateReviewVideoSetRequest("Đổi tiêu đề", null, 1, "PUBLISHED"), teacher.getId());

        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(firstPublishedAt).isNotNull();
        assertThat(republished.publishedAt()).isEqualTo(firstPublishedAt);
    }

    @Test
    void updateSet_UC23_MainFlow_archivingDoesNotHardDelete() {
        ReviewVideoSetResponse set = createClassScopedSet();

        ReviewVideoSetResponse archived = reviewVideoService.updateSet(set.id(),
                new UpdateReviewVideoSetRequest(set.title(), null, 1, "ARCHIVED"), teacher.getId());

        assertThat(archived.status()).isEqualTo("ARCHIVED");
        // Postcondition: soft-remove, không xóa cứng -- bản ghi vẫn tồn tại và đọc lại được.
        assertThat(reviewVideoService.listByClass(schoolClass.id(), teacher.getId()))
                .extracting(ReviewVideoSetResponse::id).contains(archived.id());
    }

    @Test
    void addVideo_UC23_MainFlow_savesSourceTypeAndDuration() {
        ReviewVideoSetResponse set = createClassScopedSet();

        ReviewVideoResponse youtube = reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("YOUTUBE_URL", "Video TKN 1", "https://youtube.com/watch?v=abc123", null, 180, 1, null, null),
                teacher.getId());
        ReviewVideoResponse r2Video = reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_VIDEO", "Video TKN 2", "https://media.pps.edu.vn/lms/review-videos/video/x.mp4", 5_000_000L, 200, 2, null, null),
                teacher.getId());
        ReviewVideoResponse r2Audio = reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_AUDIO", "Video phản xạ audio", "https://media.pps.edu.vn/lms/review-videos/audio/y.mp3", 1_000_000L, 90, 3, null, null),
                teacher.getId());

        assertThat(youtube.sourceType()).isEqualTo("YOUTUBE_URL");
        assertThat(youtube.durationSeconds()).isEqualTo(180);
        assertThat(r2Video.sourceType()).isEqualTo("R2_VIDEO");
        assertThat(r2Video.fileSizeBytes()).isEqualTo(5_000_000L);
        assertThat(r2Audio.sourceType()).isEqualTo("R2_AUDIO");
        List<ReviewVideoResponse> videos = reviewVideoService.listVideos(set.id(), teacher.getId());
        assertThat(videos).extracting(ReviewVideoResponse::id).contains(youtube.id(), r2Video.id(), r2Audio.id());
    }

    @Test
    void listByClass_UC23a_MainFlow_includesCurriculumWideSetsViaOrLogic() {
        ReviewVideoSetResponse classScoped = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Bộ riêng lớp", "CONNECTION", null, schoolClass.id(), null, 1),
                teacher.getId());
        reviewVideoService.updateSet(classScoped.id(), new UpdateReviewVideoSetRequest(classScoped.title(), null, 1, "PUBLISHED"), teacher.getId());
        reviewVideoService.deliverToClass(classScoped.id(), schoolClass.id(), null, teacher.getId());
        ReviewVideoSetResponse curriculumScoped = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Bộ chung khung", "REFLEX", activeCurriculum.id(), null, null, 2),
                teacher.getId());
        reviewVideoService.updateSet(curriculumScoped.id(), new UpdateReviewVideoSetRequest(curriculumScoped.title(), null, 2, "PUBLISHED"), teacher.getId());
        reviewVideoService.deliverToClass(curriculumScoped.id(), schoolClass.id(), null, teacher.getId());
        Student student = enrollStudent(schoolClass.id());

        List<ReviewVideoSetResponse> visible = reviewVideoService.listByClass(schoolClass.id(), student.getUser().getId());

        assertThat(visible).extracting(ReviewVideoSetResponse::id).contains(classScoped.id(), curriculumScoped.id());
    }

    @Test
    void listByClass_UC23a_MainFlow_studentOnlySeesPublishedSets() {
        ReviewVideoSetResponse draft = createClassScopedSet();
        Student student = enrollStudent(schoolClass.id());

        List<ReviewVideoSetResponse> visibleToStudent = reviewVideoService.listByClass(schoolClass.id(), student.getUser().getId());
        List<ReviewVideoSetResponse> visibleToTeacher = reviewVideoService.listByClass(schoolClass.id(), teacher.getId());

        assertThat(visibleToStudent).extracting(ReviewVideoSetResponse::id).doesNotContain(draft.id());
        assertThat(visibleToTeacher).extracting(ReviewVideoSetResponse::id).contains(draft.id());
    }

    @Test
    void listByClass_UC23a_A1_rejectsWhenStudentNotEnrolled() {
        User outsiderStudentUser = newUser("student.outsider");
        newStudent(outsiderStudentUser);

        assertThatThrownBy(() -> reviewVideoService.listByClass(schoolClass.id(), outsiderStudentUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listVideos_UC23a_A2_rejectsWhenStudentOutsideScope() {
        ReviewVideoSetResponse set = createClassScopedSet();
        reviewVideoService.updateSet(set.id(), new UpdateReviewVideoSetRequest(set.title(), null, 1, "PUBLISHED"), teacher.getId());
        User outsiderStudentUser = newUser("student.outsider2");
        newStudent(outsiderStudentUser);

        assertThatThrownBy(() -> reviewVideoService.listVideos(set.id(), outsiderStudentUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reportProgress_UC23a_MainFlow_upsertsAndComputesCompletionAt80Percent() {
        ReviewVideoResponse video = createPublishedSetWithVideo(100);
        Student student = enrollStudent(schoolClass.id());
        Long sessionId = startSession(video.id(), student.getUser().getId());

        ReviewVideoProgressResponse below = reportProgress(video.id(), sessionId, 79, student.getUser().getId());
        ReviewVideoProgressResponse atThreshold = reportProgress(video.id(), sessionId, 80, student.getUser().getId());

        assertThat(below.completed()).isFalse();
        assertThat(below.watchedPercent()).isEqualTo(79);
        // Mặc định requiredViewCount=1 — session đạt ngưỡng 80% trong CHÍNH lượt này là đủ "đạt".
        assertThat(atThreshold.completed()).isTrue();
        assertThat(atThreshold.watchedPercent()).isEqualTo(80);
        assertThat(atThreshold.viewCount()).isEqualTo(1);
    }

    @Test
    void reportProgress_UC23a_MainFlow_watchedSecondsNeverDecreases() {
        ReviewVideoResponse video = createPublishedSetWithVideo(100);
        Student student = enrollStudent(schoolClass.id());
        Long sessionId = startSession(video.id(), student.getUser().getId());
        reportProgress(video.id(), sessionId, 90, student.getUser().getId());

        ReviewVideoProgressResponse afterLowerReport = reportProgress(video.id(), sessionId, 30, student.getUser().getId());

        assertThat(afterLowerReport.watchedSeconds()).isEqualTo(90);
        assertThat(afterLowerReport.completed()).isTrue();
    }

    @Test
    void reportProgress_UC23a_A2_rejectsForStudentOutsideScope() {
        ReviewVideoResponse video = createPublishedSetWithVideo(100);
        User outsiderStudentUser = newUser("student.outsider3");
        newStudent(outsiderStudentUser);

        assertThatThrownBy(() -> reviewVideoService.startWatchSession(video.id(), outsiderStudentUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reportProgress_UC59_MainFlow_requiresConfiguredViewCountBeforeCompleted() {
        ReviewVideoResponse video = createPublishedSetWithVideo(100, 80, 2);
        Student student = enrollStudent(schoolClass.id());

        ReviewVideoProgressResponse afterFirstSession = reportProgress(video.id(), startSession(video.id(), student.getUser().getId()), 90, student.getUser().getId());
        assertThat(afterFirstSession.viewCount()).isEqualTo(1);
        assertThat(afterFirstSession.completed()).isFalse();

        ReviewVideoProgressResponse afterSecondSession = reportProgress(video.id(), startSession(video.id(), student.getUser().getId()), 90, student.getUser().getId());
        assertThat(afterSecondSession.viewCount()).isEqualTo(2);
        assertThat(afterSecondSession.completed()).isTrue();
    }

    @Test
    void reportProgress_UC59_MainFlow_unqualifiedSessionDoesNotIncrementViewCount() {
        ReviewVideoResponse video = createPublishedSetWithVideo(100, 80, 1);
        Student student = enrollStudent(schoolClass.id());
        reportProgress(video.id(), startSession(video.id(), student.getUser().getId()), 50, student.getUser().getId());

        ReviewVideoProgressResponse afterUnqualifiedSession = reportProgress(video.id(),
                startSession(video.id(), student.getUser().getId()), 60, student.getUser().getId());

        assertThat(afterUnqualifiedSession.viewCount()).isZero();
        assertThat(afterUnqualifiedSession.completed()).isFalse();
    }

    @Test
    void reportProgress_UC59_MainFlow_sessionsForDifferentVideosTrackIndependently() {
        ReviewVideoResponse videoA = createPublishedSetWithVideo(100);
        ReviewVideoSetResponse setB = createClassScopedSet();
        reviewVideoService.updateSet(setB.id(), new UpdateReviewVideoSetRequest(setB.title(), null, 1, "PUBLISHED"), teacher.getId());
        reviewVideoService.deliverToClass(setB.id(), schoolClass.id(), null, teacher.getId());
        ReviewVideoResponse videoB = reviewVideoService.addVideo(setB.id(),
                new AddReviewVideoRequest("R2_VIDEO", "Video B", "https://media.pps.edu.vn/lms/review-videos/video/b.mp4", 1_000_000L, 100, 1, null, null),
                teacher.getId());
        Student student = enrollStudent(schoolClass.id());

        reportProgress(videoA.id(), startSession(videoA.id(), student.getUser().getId()), 80, student.getUser().getId());
        ReviewVideoProgressResponse progressB = reportProgress(videoB.id(),
                startSession(videoB.id(), student.getUser().getId()), 10, student.getUser().getId());

        assertThat(progressB.viewCount()).isZero();
        assertThat(progressB.watchedSeconds()).isEqualTo(10);
    }

    @Test
    void getStats_UC23a_MainFlow_showsZeroForStudentsWhoNeverWatched() {
        ReviewVideoResponse video = createPublishedSetWithVideo(100);
        Long setId = video.reviewVideoSetId();
        Student watcher = enrollStudent(schoolClass.id());
        Student neverWatched = enrollStudent(schoolClass.id());
        reportProgress(video.id(), startSession(video.id(), watcher.getUser().getId()), 80, watcher.getUser().getId());

        ReviewVideoSetStatsResponse stats = reviewVideoService.getStats(setId, schoolClass.id(), teacher.getId());

        assertThat(stats.cells()).anySatisfy(cell -> {
            if (cell.studentId().equals(watcher.getId())) {
                assertThat(cell.completed()).isTrue();
                assertThat(cell.watchedPercent()).isEqualTo(80);
                assertThat(cell.viewCount()).isEqualTo(1);
            }
        });
        assertThat(stats.cells()).anySatisfy(cell -> {
            if (cell.studentId().equals(neverWatched.getId())) {
                assertThat(cell.watchedSeconds()).isZero();
                assertThat(cell.completed()).isFalse();
            }
        });
        // Postcondition: học sinh chưa xem gì vẫn xuất hiện trong ma trận (không biến mất).
        assertThat(stats.cells()).extracting(ReviewVideoSetStatsResponse.StatsCell::studentId)
                .contains(watcher.getId(), neverWatched.getId());
    }

    @Test
    void getStats_deniedForNonAssignedTeacher() {
        ReviewVideoResponse video = createPublishedSetWithVideo(100);
        Long setId = video.reviewVideoSetId();
        User outsider = newUser("outsider.stats");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> reviewVideoService.getStats(setId, schoolClass.id(), outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void getStats_UC23a_A_requiresClassIdForCurriculumScopedSet() {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Bộ chung khung", "REFLEX", activeCurriculum.id(), null, null, 1),
                teacher.getId());

        assertThatThrownBy(() -> reviewVideoService.getStats(set.id(), null, teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addQuestion_UC23b_MainFlow_savesQuestionForReflexVideo() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);

        ReviewVideoQuestionResponse question = addQuestion(video.id(), 53, 15, null);

        assertThat(question.timestampSeconds()).isEqualTo(53);
        assertThat(question.maxRecordingSeconds()).isEqualTo(15);
        assertThat(question.maxAttempts()).isNull();
        assertThat(reviewVideoService.listQuestions(video.id(), teacher.getId()))
                .extracting(ReviewVideoQuestionResponse::id).contains(question.id());
    }

    @Test
    void addQuestion_UC23b_A1_rejectsForConnectionVideo() {
        ReviewVideoResponse video = createPublishedSetWithVideo(100);

        assertThatThrownBy(() -> reviewVideoService.addQuestion(video.id(),
                new AddReviewVideoQuestionRequest(53, null, 15, null, null), teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submitQuestionAudio_UC23b_MainFlow_createsFirstAttempt() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        ReviewVideoQuestionResponse question = addQuestion(video.id(), 53, 15, null);
        Student student = enrollStudent(schoolClass.id());

        ReviewVideoSubmissionResponse submission = reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/lms/review-video-submissions/audio/a.mp3"),
                student.getUser().getId());

        assertThat(submission.audioUrl()).isEqualTo("https://media.pps.edu.vn/lms/review-video-submissions/audio/a.mp3");
        assertThat(submission.studentId()).isEqualTo(student.getId());
        assertThat(submission.attemptNumber()).isEqualTo(1);
        assertThat(submission.submittedAt()).isNotNull();
        assertThat(submission.score()).isNull();
    }

    @Test
    void submitQuestionAudio_UC23b_A2_rejectsForStudentOutsideScope() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        ReviewVideoQuestionResponse question = addQuestion(video.id(), 53, 15, null);
        User outsiderStudentUser = newUser("student.outsider4");
        newStudent(outsiderStudentUser);

        assertThatThrownBy(() -> reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/a.mp3"), outsiderStudentUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitQuestionAudio_UC23b_MainFlow_resubmitCreatesNewAttemptKeepingHistory() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        ReviewVideoQuestionResponse question = addQuestion(video.id(), 53, 15, null);
        Student student = enrollStudent(schoolClass.id());
        ReviewVideoSubmissionResponse first = reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/first.mp3"), student.getUser().getId());
        reviewVideoService.gradeSubmission(first.id(),
                new GradeReviewVideoSubmissionRequest(new BigDecimal("8.00"), new BigDecimal("10.00"), "Tốt"), teacher.getId());

        ReviewVideoSubmissionResponse second = reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/second.mp3"), student.getUser().getId());

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.attemptNumber()).isEqualTo(2);
        assertThat(second.audioUrl()).isEqualTo("https://media.pps.edu.vn/second.mp3");
        assertThat(second.score()).isNull();
        // Postcondition: giữ lịch sử — attempt 1 vẫn còn nguyên điểm đã chấm, không bị xoá.
        List<ReviewVideoSubmissionResponse> history = reviewVideoService.listMySubmissionHistory(question.id(), student.getUser().getId());
        assertThat(history).hasSize(2);
        assertThat(history).extracting(ReviewVideoSubmissionResponse::id).containsExactly(second.id(), first.id());
        assertThat(history.get(1).score()).isEqualByComparingTo("8.00");
    }

    @Test
    void submitQuestionAudio_UC23b_A5_rejectsWhenMaxAttemptsExceeded() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        ReviewVideoQuestionResponse question = addQuestion(video.id(), 53, 15, 1);
        Student student = enrollStudent(schoolClass.id());
        reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/first.mp3"), student.getUser().getId());

        assertThatThrownBy(() -> reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/second.mp3"), student.getUser().getId()))
                .isInstanceOf(RetakeNotAllowedException.class);
    }

    @Test
    void getMyLatestSubmission_UC23b_MainFlow_returnsLatestAttempt() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        ReviewVideoQuestionResponse question = addQuestion(video.id(), 53, 15, null);
        Student student = enrollStudent(schoolClass.id());
        reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/first.mp3"), student.getUser().getId());
        reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/second.mp3"), student.getUser().getId());

        ReviewVideoSubmissionResponse mine = reviewVideoService.getMyLatestSubmission(question.id(), student.getUser().getId());

        assertThat(mine.attemptNumber()).isEqualTo(2);
        assertThat(mine.audioUrl()).isEqualTo("https://media.pps.edu.vn/second.mp3");
    }

    @Test
    void getMyLatestSubmission_UC23b_MainFlow_returnsNullWhenNotYetSubmitted() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        ReviewVideoQuestionResponse question = addQuestion(video.id(), 53, 15, null);
        Student student = enrollStudent(schoolClass.id());

        ReviewVideoSubmissionResponse mine = reviewVideoService.getMyLatestSubmission(question.id(), student.getUser().getId());

        assertThat(mine).isNull();
    }

    @Test
    void getMyLatestSubmission_UC23b_A2_rejectsForStudentOutsideScope() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        ReviewVideoQuestionResponse question = addQuestion(video.id(), 53, 15, null);
        User outsiderStudentUser = newUser("student.outsider5");
        newStudent(outsiderStudentUser);

        assertThatThrownBy(() -> reviewVideoService.getMyLatestSubmission(question.id(), outsiderStudentUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listSubmissionsForTeacher_UC23b_MainFlow_returnsOnlyLatestAttemptPerStudent() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        ReviewVideoQuestionResponse question = addQuestion(video.id(), 53, 15, null);
        Long setId = video.reviewVideoSetId();
        Student submitted = enrollStudent(schoolClass.id());
        Student notSubmitted = enrollStudent(schoolClass.id());
        reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/first.mp3"), submitted.getUser().getId());
        reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/second.mp3"), submitted.getUser().getId());

        List<ReviewVideoSubmissionResponse> submissions = reviewVideoService.listSubmissionsForTeacher(setId, schoolClass.id(), teacher.getId());

        assertThat(submissions).extracting(ReviewVideoSubmissionResponse::studentId).contains(submitted.getId());
        assertThat(submissions).extracting(ReviewVideoSubmissionResponse::studentId).doesNotContain(notSubmitted.getId());
        assertThat(submissions).filteredOn(s -> s.studentId().equals(submitted.getId()))
                .extracting(ReviewVideoSubmissionResponse::attemptNumber).containsExactly(2);
    }

    @Test
    void listSubmissionsForTeacher_UC23b_A3_rejectsForNonAssignedTeacher() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        Long setId = video.reviewVideoSetId();
        User outsider = newUser("outsider.submissions");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> reviewVideoService.listSubmissionsForTeacher(setId, schoolClass.id(), outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void gradeSubmission_UC23b_MainFlow_savesScoreMaxScoreAndFeedback() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        ReviewVideoQuestionResponse question = addQuestion(video.id(), 53, 15, null);
        Student student = enrollStudent(schoolClass.id());
        ReviewVideoSubmissionResponse submission = reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/a.mp3"), student.getUser().getId());

        ReviewVideoSubmissionResponse graded = reviewVideoService.gradeSubmission(submission.id(),
                new GradeReviewVideoSubmissionRequest(new BigDecimal("8.50"), new BigDecimal("10.00"), "Phát âm tốt"),
                teacher.getId());

        assertThat(graded.score()).isEqualByComparingTo("8.50");
        assertThat(graded.maxScore()).isEqualByComparingTo("10.00");
        assertThat(graded.feedback()).isEqualTo("Phát âm tốt");
        assertThat(graded.gradedByUserId()).isEqualTo(teacher.getId());
        assertThat(graded.gradedAt()).isNotNull();
    }

    @Test
    void gradeSubmission_UC23b_A3_rejectsForNonAssignedTeacher() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        ReviewVideoQuestionResponse question = addQuestion(video.id(), 53, 15, null);
        Student student = enrollStudent(schoolClass.id());
        ReviewVideoSubmissionResponse submission = reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/a.mp3"), student.getUser().getId());
        User outsider = newUser("outsider.grade");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> reviewVideoService.gradeSubmission(submission.id(),
                new GradeReviewVideoSubmissionRequest(new BigDecimal("5"), new BigDecimal("10"), null), outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void gradeSubmission_UC23b_A4_rejectsWhenSubmissionNotFound() {
        assertThatThrownBy(() -> reviewVideoService.gradeSubmission(999_999L,
                new GradeReviewVideoSubmissionRequest(new BigDecimal("5"), new BigDecimal("10"), null), teacher.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private ReviewVideoQuestionResponse addQuestion(Long videoId, int timestampSeconds, int maxRecordingSeconds, Integer maxAttempts) {
        return reviewVideoService.addQuestion(videoId,
                new AddReviewVideoQuestionRequest(timestampSeconds, null, maxRecordingSeconds, maxAttempts, null), teacher.getId());
    }

    /**
     * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
     * Publish giờ chỉ là "đủ điều kiện dùng làm nguồn" — học sinh chỉ xem/
     * làm được khi có thêm 1 ReviewVideoAssignment ACTIVE cho lớp (giao
     * qua deliverToClass, bình thường gọi TỪ StudentCommentService khi GV
     * chọn làm "BTVN buổi sau"). Test gọi thẳng deliverToClass ở đây để mô
     * phỏng "đã được giao" mà không cần dựng lại toàn bộ luồng nhận xét.
     */
    private ReviewVideoResponse createPublishedReflexSetWithVideo(int durationSeconds) {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Bài 1: Video phản xạ", "REFLEX", null, schoolClass.id(), null, 1),
                teacher.getId());
        reviewVideoService.updateSet(set.id(), new UpdateReviewVideoSetRequest(set.title(), null, 1, "PUBLISHED"), teacher.getId());
        reviewVideoService.deliverToClass(set.id(), schoolClass.id(), null, teacher.getId());
        return reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_AUDIO", "Audio", "https://media.pps.edu.vn/lms/review-videos/audio/x.mp3", 1_000_000L, durationSeconds, 1, null, null),
                teacher.getId());
    }

    private ReviewVideoResponse createPublishedSetWithVideo(int durationSeconds) {
        return createPublishedSetWithVideo(durationSeconds, null, null);
    }

    /** V65: xem Javadoc createPublishedReflexSetWithVideo — publish + deliverToClass để học sinh xem/làm được. */
    private ReviewVideoResponse createPublishedSetWithVideo(int durationSeconds, Integer completionThresholdPercent, Integer requiredViewCount) {
        ReviewVideoSetResponse set = createClassScopedSet();
        reviewVideoService.updateSet(set.id(), new UpdateReviewVideoSetRequest(set.title(), null, 1, "PUBLISHED"), teacher.getId());
        reviewVideoService.deliverToClass(set.id(), schoolClass.id(), null, teacher.getId());
        return reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_VIDEO", "Video", "https://media.pps.edu.vn/lms/review-videos/video/x.mp4", 1_000_000L,
                        durationSeconds, 1, completionThresholdPercent, requiredViewCount),
                teacher.getId());
    }

    private ReviewVideoSetResponse createClassScopedSet() {
        return reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Bài 1: Video TKN", "CONNECTION", null, schoolClass.id(), null, 1),
                teacher.getId());
    }

    private Long startSession(Long videoId, Long actorUserId) {
        return reviewVideoService.startWatchSession(videoId, actorUserId).sessionId();
    }

    private ReviewVideoProgressResponse reportProgress(Long videoId, Long sessionId, int watchedSeconds, Long actorUserId) {
        return reviewVideoService.reportProgress(videoId, new ReportVideoProgressRequest(sessionId, watchedSeconds), actorUserId);
    }

    private Student enrollStudent(Long classId) {
        User studentUser = newUser("student.video");
        Student student = newStudent(studentUser);
        classService.enroll(classId, new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        return student;
    }

    private Student newStudent(User studentUser) {
        Student student = new Student();
        student.setUser(studentUser);
        student.setStudentCode("HS-VID-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(student);
    }

    private String curriculumCode() {
        return "CUR-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-" + SEQ.incrementAndGet();
    }

    private String setCode() {
        return "RVS-" + SEQ.incrementAndGet();
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
    }

    private Site newSite() {
        Site s = new Site();
        s.setCode("SITE-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
