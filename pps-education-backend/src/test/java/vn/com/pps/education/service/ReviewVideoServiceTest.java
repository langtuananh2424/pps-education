package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AddReviewVideoConnectionQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoQuestionRequest;
import vn.com.pps.education.dto.AddReviewVideoRequest;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.ConnectionAnswerItem;
import vn.com.pps.education.dto.ConnectionChoiceRequest;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateReviewVideoSetRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.domain.ReviewVideoAssignment;
import vn.com.pps.education.dto.GradeReviewVideoSubmissionRequest;
import vn.com.pps.education.dto.MyReviewVideoAssignmentResponse;
import vn.com.pps.education.dto.ReportVideoProgressRequest;
import vn.com.pps.education.dto.ReviewVideoAssignmentResponse;
import vn.com.pps.education.dto.ReviewVideoConnectionQuestionResponse;
import vn.com.pps.education.dto.ReviewVideoConnectionQuizResultResponse;
import vn.com.pps.education.dto.ReviewVideoProgressResponse;
import vn.com.pps.education.dto.ReviewVideoQuestionResponse;
import vn.com.pps.education.dto.ReviewVideoResponse;
import vn.com.pps.education.dto.ReviewVideoSetResponse;
import vn.com.pps.education.dto.ReviewVideoSetStatsResponse;
import vn.com.pps.education.dto.ReviewVideoSubmissionResponse;
import vn.com.pps.education.dto.SubmitConnectionAnswersRequest;
import vn.com.pps.education.dto.SubmitReviewVideoAudioRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateReviewVideoSetRequest;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.QuizAlreadyCompletedException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.RetakeNotAllowedException;
import vn.com.pps.education.exception.VideoNotYetQualifiedException;
import vn.com.pps.education.repository.NotificationRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
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
 *
 * V98 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06): mô
 * hình gán lớp đổi giống hệt Kho đề (mirror ExamServiceTest) — curriculum
 * trên "bộ" giờ CHỈ dùng lọc/tìm kiếm (createSet không còn nhận classId),
 * điều kiện hiển thị DUY NHẤT cho học sinh của 1 lớp là
 * ReviewVideoSetClassAssignment (gán tường minh qua assignToClass/
 * unassignFromClass) — các test cũ về "bộ riêng lớp" XOR "bộ chung khung
 * tự động hiển thị" (InvalidReviewVideoSetScopeException) được thay bằng
 * test cho assignToClass/unassignFromClass/listAssignedClasses/listSets.
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

    @Autowired
    private NotificationRepository notificationRepository;

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
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());
    }

    // ===================== createSet (UC-23) =====================

    @Test
    void createSet_UC23_MainFlow_savesSetWithCurriculumForFiltering() {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Video TKN Unit 1", "CONNECTION", activeCurriculum.id(), "VIETNAMESE", null, 1),
                teacher.getId());

        assertThat(set.status()).isEqualTo("DRAFT");
        assertThat(set.videoType()).isEqualTo("CONNECTION");
        assertThat(set.curriculumId()).isEqualTo(activeCurriculum.id());
        assertThat(set.curriculumCode()).isEqualTo(activeCurriculum.code());
        assertThat(set.teacherType()).isEqualTo("VIETNAMESE");
    }

    /** V98, mirror createExam_boSung_savesTeacherTypeAndExamType: teacherType bắt buộc chọn 1 trong 2, lưu đúng vào Bộ. */
    @Test
    void createSet_boSung_savesTeacherType() {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Video phản xạ GV nước ngoài", "REFLEX", activeCurriculum.id(), "FOREIGN", null, 1),
                teacher.getId());

        assertThat(set.teacherType()).isEqualTo("FOREIGN");
        assertThat(set.videoType()).isEqualTo("REFLEX");
    }

    @Test
    void createSet_boSung_rejectsInvalidTeacherType() {
        assertThatThrownBy(() -> reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "X", "CONNECTION", activeCurriculum.id(), "KHONG_HOP_LE", null, null),
                teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** V98: curriculumId giờ bắt buộc (@NotNull ở DTO) VÀ service tự tra cứu — id không tồn tại thì 404, không tạo được bộ "vô chủ". */
    @Test
    void createSet_rejectsWhenCurriculumNotFound() {
        assertThatThrownBy(() -> reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "X", "CONNECTION", 999_999L, "VIETNAMESE", null, null),
                teacher.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createSet_rejectsWhenActorNotAssignedTeacherForCurriculum() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "X", "REFLEX", activeCurriculum.id(), "VIETNAMESE", null, null),
                outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    // ===================== updateSet (UC-23) =====================

    @Test
    void updateSet_UC23_MainFlow_publishingSetsPublishedAtOnce() {
        ReviewVideoSetResponse set = createSet();

        ReviewVideoSetResponse published = reviewVideoService.updateSet(set.id(),
                new UpdateReviewVideoSetRequest(set.title(), "VIETNAMESE", null, 1, "PUBLISHED"), teacher.getId());
        var firstPublishedAt = published.publishedAt();

        ReviewVideoSetResponse republished = reviewVideoService.updateSet(set.id(),
                new UpdateReviewVideoSetRequest("Đổi tiêu đề", "VIETNAMESE", null, 1, "PUBLISHED"), teacher.getId());

        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(firstPublishedAt).isNotNull();
        assertThat(republished.publishedAt()).isEqualTo(firstPublishedAt);
    }

    /** V98, mirror updateExam_boSung_changesTeacherTypeAndExamTypeAlongWithTitle: teacherType sửa được cùng lúc với title. */
    @Test
    void updateSet_boSung_changesTeacherTypeAlongWithTitle() {
        ReviewVideoSetResponse set = createSet();

        ReviewVideoSetResponse updated = reviewVideoService.updateSet(set.id(),
                new UpdateReviewVideoSetRequest("Tên mới", "FOREIGN", null, 1, "DRAFT"), teacher.getId());

        assertThat(updated.title()).isEqualTo("Tên mới");
        assertThat(updated.teacherType()).isEqualTo("FOREIGN");
    }

    @Test
    void updateSet_UC23_MainFlow_archivingDoesNotHardDelete() {
        ReviewVideoSetResponse set = createSet();
        reviewVideoService.assignToClass(set.id(), schoolClass.id(), teacher.getId());

        ReviewVideoSetResponse archived = reviewVideoService.updateSet(set.id(),
                new UpdateReviewVideoSetRequest(set.title(), "VIETNAMESE", null, 1, "ARCHIVED"), teacher.getId());

        assertThat(archived.status()).isEqualTo("ARCHIVED");
        // Postcondition: soft-remove, không xóa cứng -- bản ghi vẫn tồn tại và đọc lại được (GV vẫn thấy qua listByClass).
        assertThat(reviewVideoService.listByClass(schoolClass.id(), teacher.getId()))
                .extracting(ReviewVideoSetResponse::id).contains(archived.id());
    }

    @Test
    void addVideo_UC23_MainFlow_savesSourceTypeAndDuration() {
        ReviewVideoSetResponse set = createSet();

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

    // ===================== listSets (V98, mirror ExamService#listExams) =====================

    @Test
    void listSets_UC95_MainFlow_filtersByCurriculum() {
        ReviewVideoSetResponse setA = createSet();
        CurriculumResponse curriculumB = createActiveCurriculum();
        newClassUnderCurriculum(curriculumB.id());
        ReviewVideoSetResponse setB = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Bộ khung B", "CONNECTION", curriculumB.id(), "VIETNAMESE", null, 1),
                teacher.getId());

        List<ReviewVideoSetResponse> filtered = reviewVideoService.listSets(activeCurriculum.id(), null, teacher.getId());

        assertThat(filtered).extracting(ReviewVideoSetResponse::id).contains(setA.id()).doesNotContain(setB.id());
    }

    @Test
    void listSets_boSung_returnsAllWhenFiltersOmitted() {
        ReviewVideoSetResponse setA = createSet();

        List<ReviewVideoSetResponse> all = reviewVideoService.listSets(null, null, teacher.getId());

        assertThat(all).extracting(ReviewVideoSetResponse::id).contains(setA.id());
    }

    @Test
    void listSets_boSung_filtersByTeacherType() {
        ReviewVideoSetResponse setVn = createSet();
        ReviewVideoSetResponse setForeign = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Bộ GV nước ngoài", "CONNECTION", activeCurriculum.id(), "FOREIGN", null, 1),
                teacher.getId());

        List<ReviewVideoSetResponse> filtered = reviewVideoService.listSets(null, "FOREIGN", teacher.getId());

        assertThat(filtered).extracting(ReviewVideoSetResponse::id).contains(setForeign.id()).doesNotContain(setVn.id());
    }

    @Test
    void listSets_boSung_filtersByCurriculumAndTeacherTypeCombined() {
        ReviewVideoSetResponse matching = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Khớp cả 2", "CONNECTION", activeCurriculum.id(), "FOREIGN", null, 1),
                teacher.getId());
        ReviewVideoSetResponse wrongTeacherType = createSet();

        List<ReviewVideoSetResponse> filtered = reviewVideoService.listSets(activeCurriculum.id(), "FOREIGN", teacher.getId());

        assertThat(filtered).extracting(ReviewVideoSetResponse::id).contains(matching.id()).doesNotContain(wrongTeacherType.id());
    }

    // ===================== assignToClass / unassignFromClass / listAssignedClasses (V98, mirror ExamService) =====================

    @Test
    void assignToClass_UC95_MainFlow_addsClassVisibility() {
        ReviewVideoSetResponse set = createSet();

        reviewVideoService.assignToClass(set.id(), schoolClass.id(), teacher.getId());

        assertThat(reviewVideoService.listAssignedClasses(set.id(), teacher.getId()))
                .extracting(ClassResponse::id).containsExactly(schoolClass.id());
    }

    @Test
    void assignToClass_rejectsWhenActorNotAssignedTeacherForClass() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");
        ReviewVideoSetResponse set = createSet();

        assertThatThrownBy(() -> reviewVideoService.assignToClass(set.id(), schoolClass.id(), outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    /** Idempotent: gán lại lớp đã gán rồi không lỗi, không tạo dòng trùng (mirror ExamServiceTest). */
    @Test
    void assignToClass_boSung_isIdempotentWhenAlreadyAssigned() {
        ReviewVideoSetResponse set = createSet();

        reviewVideoService.assignToClass(set.id(), schoolClass.id(), teacher.getId());
        reviewVideoService.assignToClass(set.id(), schoolClass.id(), teacher.getId());

        assertThat(reviewVideoService.listAssignedClasses(set.id(), teacher.getId())).hasSize(1);
    }

    @Test
    void unassignFromClass_UC95_MainFlow_removesClassAssignment() {
        ReviewVideoSetResponse set = createSet();
        reviewVideoService.assignToClass(set.id(), schoolClass.id(), teacher.getId());

        reviewVideoService.unassignFromClass(set.id(), schoolClass.id(), teacher.getId());

        assertThat(reviewVideoService.listAssignedClasses(set.id(), teacher.getId())).isEmpty();
    }

    // ===================== listByClass (UC-23a) =====================

    @Test
    void listByClass_UC95_MainFlow_showsOnlySetsExplicitlyAssignedToClass() {
        ReviewVideoSetResponse assignedSet = createSet();
        reviewVideoService.assignToClass(assignedSet.id(), schoolClass.id(), teacher.getId());
        ReviewVideoSetResponse notAssignedSet = createSet(); // cùng khung chương trình nhưng chưa gán lớp nào

        List<ReviewVideoSetResponse> visible = reviewVideoService.listByClass(schoolClass.id(), teacher.getId());

        assertThat(visible).extracting(ReviewVideoSetResponse::id)
                .contains(assignedSet.id())
                .doesNotContain(notAssignedSet.id());
    }

    /**
     * V98: khác hành vi CŨ (curriculum dùng chung tự động hiển thị mọi lớp) — nay curriculum CHỈ
     * dùng lọc/tìm kiếm, 1 lớp khác dưới CÙNG khung chương trình KHÔNG tự thấy bộ nếu chưa được
     * assignToClass tường minh cho ĐÚNG lớp đó.
     */
    @Test
    void listByClass_UC95_A_excludesSetsAssignedOnlyToAnotherClassUnderSameCurriculum() {
        ClassResponse otherClass = newClassUnderCurriculum(activeCurriculum.id());
        ReviewVideoSetResponse set = createSet();
        reviewVideoService.assignToClass(set.id(), schoolClass.id(), teacher.getId());

        List<ReviewVideoSetResponse> visibleToOtherClass = reviewVideoService.listByClass(otherClass.id(), teacher.getId());

        assertThat(visibleToOtherClass).extracting(ReviewVideoSetResponse::id).doesNotContain(set.id());
    }

    @Test
    void listByClass_UC23a_MainFlow_studentOnlySeesPublishedSets() {
        ReviewVideoSetResponse draft = createSet();
        reviewVideoService.assignToClass(draft.id(), schoolClass.id(), teacher.getId());
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
        ReviewVideoSetResponse set = createSet();
        reviewVideoService.updateSet(set.id(), new UpdateReviewVideoSetRequest(set.title(), "VIETNAMESE", null, 1, "PUBLISHED"), teacher.getId());
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
        assertThat(atThreshold.watchedPercent()).isEqualTo(80);
        // CONNECTION (V83/V93/V101): xem đạt ngưỡng chỉ làm session "qualified" — 1 lượt chỉ tính
        // vào viewCount/completed sau khi CŨNG nộp đủ câu hỏi (quizCompletedAt khác NULL). Video ở
        // đây chưa thêm câu hỏi nào (addConnectionQuestion không được gọi) nên nộp danh sách rỗng
        // vẫn khớp đúng bộ câu hỏi (rỗng = rỗng), đủ để đánh dấu quizCompletedAt.
        ReviewVideoConnectionQuizResultResponse quizResult = reviewVideoService.submitConnectionAnswers(
                sessionId, new SubmitConnectionAnswersRequest(List.of()), student.getUser().getId());
        assertThat(quizResult.progress().completed()).isTrue();
        assertThat(quizResult.progress().viewCount()).isEqualTo(1);
    }

    @Test
    void reportProgress_UC23a_MainFlow_watchedSecondsNeverDecreases() {
        ReviewVideoResponse video = createPublishedSetWithVideo(100);
        Student student = enrollStudent(schoolClass.id());
        Long sessionId = startSession(video.id(), student.getUser().getId());
        reportProgress(video.id(), sessionId, 90, student.getUser().getId());

        ReviewVideoProgressResponse afterLowerReport = reportProgress(video.id(), sessionId, 30, student.getUser().getId());

        assertThat(afterLowerReport.watchedSeconds()).isEqualTo(90);
        // CONNECTION (V83/V93/V101): "đạt" chỉ tính sau khi nộp đủ câu hỏi (rỗng ở đây) cho lượt đã qualified.
        ReviewVideoConnectionQuizResultResponse quizResult = reviewVideoService.submitConnectionAnswers(
                sessionId, new SubmitConnectionAnswersRequest(List.of()), student.getUser().getId());
        assertThat(quizResult.progress().completed()).isTrue();
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

        // CONNECTION (V83/V93/V101): mỗi lượt chỉ tính vào viewCount sau khi CŨNG nộp đủ câu hỏi
        // (rỗng ở đây, video chưa thêm câu hỏi) cho đúng session đã qualified (xem ghi chú tại
        // reportProgress_UC23a_MainFlow_upsertsAndComputesCompletionAt80Percent).
        Long firstSessionId = startSession(video.id(), student.getUser().getId());
        reportProgress(video.id(), firstSessionId, 90, student.getUser().getId());
        ReviewVideoConnectionQuizResultResponse afterFirstSession = reviewVideoService.submitConnectionAnswers(
                firstSessionId, new SubmitConnectionAnswersRequest(List.of()), student.getUser().getId());
        assertThat(afterFirstSession.progress().viewCount()).isEqualTo(1);
        assertThat(afterFirstSession.progress().completed()).isFalse();

        Long secondSessionId = startSession(video.id(), student.getUser().getId());
        reportProgress(video.id(), secondSessionId, 90, student.getUser().getId());
        ReviewVideoConnectionQuizResultResponse afterSecondSession = reviewVideoService.submitConnectionAnswers(
                secondSessionId, new SubmitConnectionAnswersRequest(List.of()), student.getUser().getId());
        assertThat(afterSecondSession.progress().viewCount()).isEqualTo(2);
        assertThat(afterSecondSession.progress().completed()).isTrue();
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
        ReviewVideoSetResponse setB = createSet();
        reviewVideoService.assignToClass(setB.id(), schoolClass.id(), teacher.getId());
        reviewVideoService.updateSet(setB.id(), new UpdateReviewVideoSetRequest(setB.title(), "VIETNAMESE", null, 1, "PUBLISHED"), teacher.getId());
        // V71: deliverToClass dùng PROPAGATION_REQUIRES_NEW — phải commit set vừa tạo trước.
        commitCurrentTransactionAndStartNew();
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
    void getStats_A_rejectsWhenClassIdNotProvided() {
        ReviewVideoSetResponse set = createSet();

        assertThatThrownBy(() -> reviewVideoService.getStats(set.id(), null, teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** V98: classId truyền vào phải là 1 lớp ĐÃ được gán tường minh cho bộ (ReviewVideoSetClassAssignment), không chỉ cần cùng khung chương trình. */
    @Test
    void getStats_A_rejectsWhenClassNotAssignedToSet() {
        ReviewVideoSetResponse set = createSet(); // curriculum đúng nhưng chưa assignToClass cho lớp nào

        assertThatThrownBy(() -> reviewVideoService.getStats(set.id(), schoolClass.id(), teacher.getId()))
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
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/lms/review-video-submissions/audio/a.mp3", null),
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
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/a.mp3", null), outsiderStudentUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submitQuestionAudio_UC23b_MainFlow_resubmitCreatesNewAttemptKeepingHistory() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        ReviewVideoQuestionResponse question = addQuestion(video.id(), 53, 15, null);
        Student student = enrollStudent(schoolClass.id());
        ReviewVideoSubmissionResponse first = reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/first.mp3", null), student.getUser().getId());
        reviewVideoService.gradeSubmission(first.id(),
                new GradeReviewVideoSubmissionRequest(new BigDecimal("8.00"), new BigDecimal("10.00"), "Tốt"), teacher.getId());

        ReviewVideoSubmissionResponse second = reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/second.mp3", null), student.getUser().getId());

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
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/first.mp3", null), student.getUser().getId());

        assertThatThrownBy(() -> reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/second.mp3", null), student.getUser().getId()))
                .isInstanceOf(RetakeNotAllowedException.class);
    }

    @Test
    void getMyLatestSubmission_UC23b_MainFlow_returnsLatestAttempt() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        ReviewVideoQuestionResponse question = addQuestion(video.id(), 53, 15, null);
        Student student = enrollStudent(schoolClass.id());
        reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/first.mp3", null), student.getUser().getId());
        reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/second.mp3", null), student.getUser().getId());

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
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/first.mp3", null), submitted.getUser().getId());
        reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/second.mp3", null), submitted.getUser().getId());

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
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/a.mp3", null), student.getUser().getId());

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
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/a.mp3", null), student.getUser().getId());
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

    /**
     * V98: bộ đã Publish nhưng CHƯA assignToClass cho lớp nào — không giao (deliverToClass) được cho
     * lớp đó, dù teacher phụ trách đúng lớp. Precondition MỚI, không tồn tại trước V98 (trước đây
     * curriculum dùng chung hoặc classId lúc tạo là đủ).
     */
    @Test
    void deliverToClass_UC95_A_rejectsWhenSetNotAssignedToClass() {
        ReviewVideoSetResponse set = createSet();
        reviewVideoService.updateSet(set.id(), new UpdateReviewVideoSetRequest(set.title(), "VIETNAMESE", null, 1, "PUBLISHED"), teacher.getId());
        commitCurrentTransactionAndStartNew();

        assertThatThrownBy(() -> reviewVideoService.deliverToClass(set.id(), schoolClass.id(), null, teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * V69 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31) —
     * fix bug thật: "Đã nộp bài" hiện sai khi giao lại đúng bộ REFLEX cho
     * lớp đã từng làm rồi (VD buổi 3 giao, học sinh đã trả lời xong; buổi
     * 7 giao LẠI đúng bộ đó — hệ thống vẫn thấy "đã có câu trả lời cũ" nên
     * hiện nhầm "Đã nộp bài"). Giao lại phải là 1 lượt HOÀN TOÀN MỚI.
     */
    @Test
    void deliverToClass_V69_MainFlow_redeliveringResetsSubmissionStatusToNotSubmitted() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        ReviewVideoQuestionResponse question = addQuestion(video.id(), 53, 15, null);
        Student student = enrollStudent(schoolClass.id());
        // Buổi 3: học sinh đã nộp và được chấm xong.
        ReviewVideoSubmissionResponse oldSubmission = reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/old.mp3", null), student.getUser().getId());
        reviewVideoService.gradeSubmission(oldSubmission.id(),
                new GradeReviewVideoSubmissionRequest(new BigDecimal("9.00"), new BigDecimal("10.00"), "Tốt"), teacher.getId());

        // Buổi 7: giao LẠI đúng bộ này cho đúng lớp này ở 1 buổi KHÁC (dueAt khác) — lượt giao mới (V70: dueAt
        // trùng nhau mới coi là request trùng lặp cùng 1 buổi, dueAt khác nhau vẫn là redeliver thật).
        reviewVideoService.deliverToClass(video.reviewVideoSetId(), schoolClass.id(), OffsetDateTime.now().plusDays(7), teacher.getId());

        ReviewVideoSubmissionResponse mine = reviewVideoService.getMyLatestSubmission(question.id(), student.getUser().getId());
        assertThat(mine).as("chưa nộp gì cho lượt giao MỚI dù đã nộp ở lượt giao cũ").isNull();
        assertThat(reviewVideoService.listMySubmissionHistory(question.id(), student.getUser().getId()))
                .as("lịch sử của lượt giao MỚI phải rỗng, không kéo theo lịch sử lượt giao cũ")
                .isEmpty();
    }

    /** V69: maxAttempts áp dụng lại từ đầu ở lượt giao mới — không bị tính dồn từ lượt giao cũ. */
    @Test
    void submitQuestionAudio_V69_MainFlow_maxAttemptsResetsOnRedelivery() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        ReviewVideoQuestionResponse question = addQuestion(video.id(), 53, 15, 1);
        Student student = enrollStudent(schoolClass.id());
        reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/old.mp3", null), student.getUser().getId());
        // Đã hết lượt (maxAttempts=1) ở lượt giao cũ.
        assertThatThrownBy(() -> reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/old2.mp3", null), student.getUser().getId()))
                .isInstanceOf(RetakeNotAllowedException.class);

        // Redeliver ở buổi KHÁC (dueAt khác) — V70 chỉ tái dùng khi dueAt trùng (cùng 1 đợt gửi).
        reviewVideoService.deliverToClass(video.reviewVideoSetId(), schoolClass.id(), OffsetDateTime.now().plusDays(7), teacher.getId());

        ReviewVideoSubmissionResponse afterRedeliver = reviewVideoService.submitQuestionAudio(question.id(),
                new SubmitReviewVideoAudioRequest("https://media.pps.edu.vn/new.mp3", null), student.getUser().getId());
        assertThat(afterRedeliver.attemptNumber()).isEqualTo(1);
    }

    /**
     * V69: giao lại cùng (bộ, lớp) hủy (CANCELLED) lần giao ACTIVE cũ — tại mọi thời điểm chỉ tối đa 1 lần giao
     * ACTIVE cho 1 (bộ, lớp). V70: điều này chỉ đúng khi 2 lần giao khác buổi (dueAt khác) — dueAt trùng nhau
     * (cùng 1 buổi) nay được coi là request trùng lặp trong cùng 1 đợt gửi, xem
     * deliverToClass_V70_boSung_reusesExistingAssignmentForSameSessionInsteadOfDuplicating.
     */
    @Test
    void deliverToClass_V69_MainFlow_cancelsPreviousActiveAssignmentForSameSetAndClass() {
        ReviewVideoSetResponse set = createSet();
        reviewVideoService.assignToClass(set.id(), schoolClass.id(), teacher.getId());
        reviewVideoService.updateSet(set.id(), new UpdateReviewVideoSetRequest(set.title(), "VIETNAMESE", null, 1, "PUBLISHED"), teacher.getId());
        // V71: deliverToClass dùng PROPAGATION_REQUIRES_NEW — phải commit set vừa tạo trước.
        commitCurrentTransactionAndStartNew();

        ReviewVideoAssignment first = reviewVideoService.deliverToClass(set.id(), schoolClass.id(), OffsetDateTime.now().plusDays(3), teacher.getId());
        ReviewVideoAssignment second = reviewVideoService.deliverToClass(set.id(), schoolClass.id(), OffsetDateTime.now().plusDays(7), teacher.getId());

        List<ReviewVideoAssignmentResponse> active = reviewVideoService.listAssignmentsForClass(schoolClass.id(), teacher.getId());
        assertThat(active).extracting(ReviewVideoAssignmentResponse::id).containsExactly(second.getId());
        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    /**
     * V70 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31) —
     * fix bug thật: "Gửi nhận xét" hàng loạt cho N học sinh CÙNG buổi,
     * CÙNG chọn 1 video ôn tập → StudentCommentService gọi deliverToClass
     * N lần với CÙNG (setId, classId, dueAt) → trước đây tạo N
     * ReviewVideoAssignment trùng lặp, mỗi bản ghi lại thông báo lại cho
     * TOÀN BỘ học sinh lớp → 1 học sinh nhận N thông báo giống hệt nhau.
     */
    @Test
    void deliverToClass_V70_boSung_reusesExistingAssignmentForSameSessionInsteadOfDuplicating() {
        ReviewVideoSetResponse set = createSet();
        reviewVideoService.assignToClass(set.id(), schoolClass.id(), teacher.getId());
        reviewVideoService.updateSet(set.id(), new UpdateReviewVideoSetRequest(set.title(), "VIETNAMESE", null, 1, "PUBLISHED"), teacher.getId());
        Student student = enrollStudent(schoolClass.id());
        OffsetDateTime dueAt = OffsetDateTime.now().plusDays(2);
        // V71: deliverToClass dùng PROPAGATION_REQUIRES_NEW — phải commit set vừa tạo trước.
        commitCurrentTransactionAndStartNew();

        // Mô phỏng N=3 request riêng biệt (3 học sinh khác nhau CÙNG chọn video này CÙNG buổi).
        ReviewVideoAssignment first = reviewVideoService.deliverToClass(set.id(), schoolClass.id(), dueAt, teacher.getId());
        ReviewVideoAssignment second = reviewVideoService.deliverToClass(set.id(), schoolClass.id(), dueAt, teacher.getId());
        ReviewVideoAssignment third = reviewVideoService.deliverToClass(set.id(), schoolClass.id(), dueAt, teacher.getId());

        assertThat(second.getId()).as("tái dùng đúng bản ghi cũ, không tạo mới").isEqualTo(first.getId());
        assertThat(third.getId()).isEqualTo(first.getId());
        assertThat(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(student.getUser().getId(), PageRequest.of(0, 10)))
                .as("chỉ nhận đúng 1 thông báo dù deliverToClass bị gọi 3 lần cho cùng buổi")
                .hasSize(1);
    }

    /**
     * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31 — fix
     * bug thật: Portal không hiển thị hạn nộp (dueAt) cho BTVN Video vì
     * chưa có API self-service nào cho Học sinh đọc field này
     * (listAssignmentsForClass bị khoá requireAssignedTeacher).
     */
    @Test
    void listMyAssignments_boSung_MainFlow_returnsDueAtForAssignedSet() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);
        Student student = enrollStudent(schoolClass.id());
        OffsetDateTime dueAt = OffsetDateTime.now().plusDays(3);
        reviewVideoService.deliverToClass(video.reviewVideoSetId(), schoolClass.id(), dueAt, teacher.getId());

        List<MyReviewVideoAssignmentResponse> mine = reviewVideoService.listMyAssignments(student.getUser().getId(), null);

        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).reviewVideoSetId()).isEqualTo(video.reviewVideoSetId());
        assertThat(mine.get(0).videoType()).isEqualTo("REFLEX");
        assertThat(mine.get(0).classId()).isEqualTo(schoolClass.id());
        // So sánh theo Instant (không phải field giờ/phút thô) — isEqualToIgnoringNanos so trực tiếp
        // offset+field, chỉ đúng khi JVM chạy cùng múi giờ với offset Hibernate trả về (UTC, xem
        // hibernate.jdbc.time_zone ở application.yml). CI (Ubuntu runner) mặc định UTC nên không lộ
        // ra, nhưng máy dev múi giờ +07:00 (VN) luôn fail dù giá trị đúng cùng 1 thời điểm thực.
        assertThat(mine.get(0).dueAt().toInstant().truncatedTo(ChronoUnit.SECONDS))
                .isEqualTo(dueAt.toInstant().truncatedTo(ChronoUnit.SECONDS));
        assertThat(mine.get(0).availableFrom()).isNotNull();
    }

    /** Bổ sung: bộ đã Publish nhưng CHƯA deliverToClass cho lớp nào — học sinh chưa thấy gì (Publish đơn thuần không đủ, đúng V65). */
    @Test
    void listMyAssignments_boSung_A_returnsEmptyWhenSetPublishedButNotYetDelivered() {
        Student student = enrollStudent(schoolClass.id());
        ReviewVideoSetResponse set = createSet();
        reviewVideoService.updateSet(set.id(), new UpdateReviewVideoSetRequest(set.title(), "VIETNAMESE", null, 1, "PUBLISHED"), teacher.getId());

        List<MyReviewVideoAssignmentResponse> mine = reviewVideoService.listMyAssignments(student.getUser().getId(), null);

        assertThat(mine).isEmpty();
    }

    /**
     * V76 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-04):
     * Video Kết nối (CONNECTION) giờ bắt buộc có câu hỏi trắc nghiệm tự
     * chấm — không gắn mã UC cụ thể (tính năng mới), đặt tên test theo mô
     * tả luồng.
     */
    @Test
    void addConnectionQuestion_MainFlow_savesQuestionWithChoices() {
        ReviewVideoSetResponse set = createSet();
        ReviewVideoResponse video = reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_VIDEO", "Video", "https://media.pps.edu.vn/lms/review-videos/video/x.mp4",
                        1_000_000L, 100, 1, null, null),
                teacher.getId());

        ReviewVideoConnectionQuestionResponse question = reviewVideoService.addConnectionQuestion(video.id(),
                new AddReviewVideoConnectionQuestionRequest("2+2 = ?", 1, List.of(
                        new ConnectionChoiceRequest("A", "3", false, 1),
                        new ConnectionChoiceRequest("B", "4", true, 2))),
                teacher.getId());

        assertThat(question.prompt()).isEqualTo("2+2 = ?");
        assertThat(question.choices()).hasSize(2);
        assertThat(question.choices()).filteredOn(c -> c.isCorrect() != null && c.isCorrect())
                .extracting("content").containsExactly("4");
    }

    @Test
    void addConnectionQuestion_A_rejectsWhenVideoTypeIsReflex() {
        ReviewVideoResponse video = createPublishedReflexSetWithVideo(100);

        assertThatThrownBy(() -> reviewVideoService.addConnectionQuestion(video.id(),
                new AddReviewVideoConnectionQuestionRequest("Câu hỏi?", 1, List.of(
                        new ConnectionChoiceRequest("A", "X", true, 1),
                        new ConnectionChoiceRequest("B", "Y", false, 2))),
                teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Lõi nghiệp vụ khớp cặp 1-1: "1 lượt hoàn thành" = xem đạt ngưỡng VÀ
     * trả lời hết bộ câu hỏi CHO ĐÚNG lượt xem đó — viewCount chỉ tăng khi
     * CẢ 2 điều kiện cùng thoả cho CÙNG 1 watchSessionId.
     */
    @Test
    void submitConnectionAnswers_MainFlow_pairsWithQualifiedSessionAndIncrementsViewCount() {
        ReviewVideoResponse video = createPublishedSetWithVideo(100, 80, 1);
        ReviewVideoConnectionQuestionResponse question = reviewVideoService.addConnectionQuestion(video.id(),
                new AddReviewVideoConnectionQuestionRequest("2+2 = ?", 1, List.of(
                        new ConnectionChoiceRequest("A", "3", false, 1),
                        new ConnectionChoiceRequest("B", "4", true, 2))),
                teacher.getId());
        Long correctChoiceId = question.choices().stream().filter(c -> Boolean.TRUE.equals(c.isCorrect()))
                .findFirst().orElseThrow().id();
        Student student = enrollStudent(schoolClass.id());
        Long sessionId = startSession(video.id(), student.getUser().getId());
        reportProgress(video.id(), sessionId, 100, student.getUser().getId());

        ReviewVideoConnectionQuizResultResponse result = reviewVideoService.submitConnectionAnswers(sessionId,
                new SubmitConnectionAnswersRequest(List.of(new ConnectionAnswerItem(question.id(), correctChoiceId))),
                student.getUser().getId());

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).correct()).isTrue();
        assertThat(result.progress().viewCount()).isEqualTo(1);
        assertThat(result.progress().completed()).isTrue();
    }

    @Test
    void submitConnectionAnswers_A_rejectsWhenSessionNotYetQualified() {
        ReviewVideoResponse video = createPublishedSetWithVideo(100, 80, 1);
        ReviewVideoConnectionQuestionResponse question = reviewVideoService.addConnectionQuestion(video.id(),
                new AddReviewVideoConnectionQuestionRequest("2+2 = ?", 1, List.of(
                        new ConnectionChoiceRequest("A", "3", false, 1),
                        new ConnectionChoiceRequest("B", "4", true, 2))),
                teacher.getId());
        Long correctChoiceId = question.choices().stream().filter(c -> Boolean.TRUE.equals(c.isCorrect()))
                .findFirst().orElseThrow().id();
        Student student = enrollStudent(schoolClass.id());
        Long sessionId = startSession(video.id(), student.getUser().getId());
        reportProgress(video.id(), sessionId, 10, student.getUser().getId()); // 10% < ngưỡng 80%

        assertThatThrownBy(() -> reviewVideoService.submitConnectionAnswers(sessionId,
                new SubmitConnectionAnswersRequest(List.of(new ConnectionAnswerItem(question.id(), correctChoiceId))),
                student.getUser().getId()))
                .isInstanceOf(VideoNotYetQualifiedException.class);
    }

    @Test
    void submitConnectionAnswers_A_rejectsWhenSessionAlreadyCompleted() {
        ReviewVideoResponse video = createPublishedSetWithVideo(100, 80, 2);
        ReviewVideoConnectionQuestionResponse question = reviewVideoService.addConnectionQuestion(video.id(),
                new AddReviewVideoConnectionQuestionRequest("2+2 = ?", 1, List.of(
                        new ConnectionChoiceRequest("A", "3", false, 1),
                        new ConnectionChoiceRequest("B", "4", true, 2))),
                teacher.getId());
        Long correctChoiceId = question.choices().stream().filter(c -> Boolean.TRUE.equals(c.isCorrect()))
                .findFirst().orElseThrow().id();
        Student student = enrollStudent(schoolClass.id());
        Long sessionId = startSession(video.id(), student.getUser().getId());
        reportProgress(video.id(), sessionId, 100, student.getUser().getId());
        reviewVideoService.submitConnectionAnswers(sessionId,
                new SubmitConnectionAnswersRequest(List.of(new ConnectionAnswerItem(question.id(), correctChoiceId))),
                student.getUser().getId());

        assertThatThrownBy(() -> reviewVideoService.submitConnectionAnswers(sessionId,
                new SubmitConnectionAnswersRequest(List.of(new ConnectionAnswerItem(question.id(), correctChoiceId))),
                student.getUser().getId()))
                .isInstanceOf(QuizAlreadyCompletedException.class);
    }

    @Test
    void updateSet_A_rejectsPublishWhenConnectionVideoMissingQuestions() {
        ReviewVideoSetResponse set = createSet();
        reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_VIDEO", "Video", "https://media.pps.edu.vn/lms/review-videos/video/x.mp4",
                        1_000_000L, 100, 1, null, null),
                teacher.getId());

        assertThatThrownBy(() -> reviewVideoService.updateSet(set.id(),
                new UpdateReviewVideoSetRequest(set.title(), "VIETNAMESE", null, 1, "PUBLISHED"), teacher.getId()))
                .isInstanceOf(IllegalArgumentException.class);
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
     * chọn làm "BTVN buổi sau"). V98: deliverToClass giờ ĐÒI HỎI bộ đã
     * được assignToClass tường minh cho lớp đó trước — gọi assignToClass ở
     * đây trước khi publish/deliver, mô phỏng "đã được giao" mà không cần
     * dựng lại toàn bộ luồng nhận xét.
     */
    private ReviewVideoResponse createPublishedReflexSetWithVideo(int durationSeconds) {
        ReviewVideoSetResponse set = reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Bài 1: Video phản xạ", "REFLEX", activeCurriculum.id(), "VIETNAMESE", null, 1),
                teacher.getId());
        reviewVideoService.assignToClass(set.id(), schoolClass.id(), teacher.getId());
        reviewVideoService.updateSet(set.id(), new UpdateReviewVideoSetRequest(set.title(), "VIETNAMESE", null, 1, "PUBLISHED"), teacher.getId());
        // V71: deliverToClass dùng PROPAGATION_REQUIRES_NEW — phải commit set vừa tạo trước.
        commitCurrentTransactionAndStartNew();
        reviewVideoService.deliverToClass(set.id(), schoolClass.id(), null, teacher.getId());
        return reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_AUDIO", "Audio", "https://media.pps.edu.vn/lms/review-videos/audio/x.mp3", 1_000_000L, durationSeconds, 1, null, null),
                teacher.getId());
    }

    private ReviewVideoResponse createPublishedSetWithVideo(int durationSeconds) {
        return createPublishedSetWithVideo(durationSeconds, null, null);
    }

    /** V98: xem Javadoc createPublishedReflexSetWithVideo — assignToClass + publish + deliverToClass để học sinh xem/làm được. */
    private ReviewVideoResponse createPublishedSetWithVideo(int durationSeconds, Integer completionThresholdPercent, Integer requiredViewCount) {
        ReviewVideoSetResponse set = createSet();
        reviewVideoService.assignToClass(set.id(), schoolClass.id(), teacher.getId());
        reviewVideoService.updateSet(set.id(), new UpdateReviewVideoSetRequest(set.title(), "VIETNAMESE", null, 1, "PUBLISHED"), teacher.getId());
        // V71: deliverToClass dùng PROPAGATION_REQUIRES_NEW — phải commit set vừa tạo trước.
        commitCurrentTransactionAndStartNew();
        reviewVideoService.deliverToClass(set.id(), schoolClass.id(), null, teacher.getId());
        return reviewVideoService.addVideo(set.id(),
                new AddReviewVideoRequest("R2_VIDEO", "Video", "https://media.pps.edu.vn/lms/review-videos/video/x.mp4", 1_000_000L,
                        durationSeconds, 1, completionThresholdPercent, requiredViewCount),
                teacher.getId());
    }

    /** V98: tạo "bộ" trơn — curriculum bắt buộc + teacherType mặc định VIETNAMESE, CHƯA gán lớp nào (gán qua assignToClass riêng khi cần). */
    private ReviewVideoSetResponse createSet() {
        return reviewVideoService.createSet(
                new CreateReviewVideoSetRequest(setCode(), "Bài 1: Video TKN", "CONNECTION", activeCurriculum.id(), "VIETNAMESE", null, 1),
                teacher.getId());
    }

    private CurriculumResponse createActiveCurriculum() {
        CurriculumResponse raw = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Khung khác", "MAIN", null, null, null), headAcademic.getId());
        return curriculumService.update(raw.id(),
                new UpdateCurriculumRequest("Khung khác", null, null, null, "ACTIVE", false), headAcademic.getId());
    }

    /** Tạo 1 lớp mới dưới khung chương trình cho trước + gán `teacher` (fixture) làm GV chủ nhiệm — dùng cho test cần lớp thứ 2. */
    private ClassResponse newClassUnderCurriculum(Long curriculumId) {
        ClassResponse cls = classService.create(
                new CreateClassRequest(classCode(), "Lớp phụ", newSite().getId(), curriculumId, "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());
        classService.assignTeacher(cls.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now()), headAcademic.getId());
        return cls;
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
