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
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateListeningPracticeItemRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.ListeningPracticeAttemptResponse;
import vn.com.pps.education.dto.ListeningPracticeItemResponse;
import vn.com.pps.education.dto.SubmitListeningPracticeAttemptRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateListeningPracticeItemRequest;
import vn.com.pps.education.exception.AttemptNotEditableException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-26: Luyện Nghe – Nói — Main Flow (3 chế độ Nghe/Chép chính tả/Nói),
 * A1 (tạm dừng giữa chừng). Domain riêng biệt, tự luyện không deadline.
 */
@Transactional
class ListeningPracticeServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ListeningPracticeService listeningPracticeService;

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
    private Student student;

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

        User studentUser = newUser("student.listening");
        student = new Student();
        student.setUser(studentUser);
        student.setStudentCode("HS-LST-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        student = studentRepository.save(student);
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
    }

    @Test
    void createItem_UC26_MainFlow_savesAsDraft() {
        ListeningPracticeItemResponse item = createItem("DICTATION", "The cat sat on the mat.");

        assertThat(item.status()).isEqualTo("DRAFT");
        assertThat(item.mode()).isEqualTo("DICTATION");
    }

    @Test
    void listMyPracticeItems_UC26_MainFlow_returnsPublishedItemsForEnrolledCurriculum() {
        ListeningPracticeItemResponse item = createPublishedItem("LISTENING", "Good morning everyone.");

        List<ListeningPracticeItemResponse> visible = listeningPracticeService.listMyPracticeItems(
                student.getUser().getId(), null, null);

        assertThat(visible).extracting(ListeningPracticeItemResponse::id).contains(item.id());
    }

    @Test
    void listMyPracticeItems_doesNotReturnDraftItems() {
        ListeningPracticeItemResponse item = createItem("LISTENING", "Good morning everyone.");

        List<ListeningPracticeItemResponse> visible = listeningPracticeService.listMyPracticeItems(
                student.getUser().getId(), null, null);

        assertThat(visible).extracting(ListeningPracticeItemResponse::id).doesNotContain(item.id());
    }

    @Test
    void submitAttempt_UC26_MainFlow_listeningModeJustMarksCompleted() {
        ListeningPracticeItemResponse item = createPublishedItem("LISTENING", "Good morning everyone.");
        ListeningPracticeAttemptResponse attempt = listeningPracticeService.startAttempt(item.id(), student.getUser().getId());

        ListeningPracticeAttemptResponse submitted = listeningPracticeService.submitAttempt(attempt.id(),
                new SubmitListeningPracticeAttemptRequest(null, null), student.getUser().getId());

        assertThat(submitted.status()).isEqualTo("GRADED");
        assertThat(submitted.submittedAt()).isNotNull();
    }

    @Test
    void submitAttempt_UC26_MainFlow_dictationModeAutoScoresAgainstScript() {
        ListeningPracticeItemResponse item = createPublishedItem("DICTATION", "The cat sat on the mat.");
        ListeningPracticeAttemptResponse attempt = listeningPracticeService.startAttempt(item.id(), student.getUser().getId());

        ListeningPracticeAttemptResponse submitted = listeningPracticeService.submitAttempt(attempt.id(),
                new SubmitListeningPracticeAttemptRequest("The cat sat on the mat.", null), student.getUser().getId());

        assertThat(submitted.status()).isEqualTo("GRADED");
        assertThat(submitted.dictationScore()).isEqualByComparingTo("100.00");
    }

    @Test
    void submitAttempt_UC26_dictationModePartialMatchScoresProportionally() {
        ListeningPracticeItemResponse item = createPublishedItem("DICTATION", "The cat sat on the mat.");
        ListeningPracticeAttemptResponse attempt = listeningPracticeService.startAttempt(item.id(), student.getUser().getId());

        ListeningPracticeAttemptResponse submitted = listeningPracticeService.submitAttempt(attempt.id(),
                new SubmitListeningPracticeAttemptRequest("The dog sat on the mat.", null), student.getUser().getId());

        assertThat(submitted.dictationScore()).isLessThan(java.math.BigDecimal.valueOf(100));
        assertThat(submitted.dictationScore()).isGreaterThan(java.math.BigDecimal.ZERO);
    }

    @Test
    void submitAttempt_UC26_MainFlow_speakingModeDoesNotAutoScoreAndAwaitsManualGrading() {
        ListeningPracticeItemResponse item = createPublishedItem("SPEAKING", "Practice pronunciation of 'through'.");
        ListeningPracticeAttemptResponse attempt = listeningPracticeService.startAttempt(item.id(), student.getUser().getId());

        ListeningPracticeAttemptResponse submitted = listeningPracticeService.submitAttempt(attempt.id(),
                new SubmitListeningPracticeAttemptRequest(null, "https://cdn.pps.edu.vn/recordings/1.mp3"),
                student.getUser().getId());

        assertThat(submitted.status()).isEqualTo("SUBMITTED");
        assertThat(submitted.audioAnswerUrl()).isEqualTo("https://cdn.pps.edu.vn/recordings/1.mp3");
    }

    @Test
    void pauseAttempt_UC26_A1_savesPositionForResumeLater() {
        ListeningPracticeItemResponse item = createPublishedItem("LISTENING", "Good morning everyone.");
        ListeningPracticeAttemptResponse attempt = listeningPracticeService.startAttempt(item.id(), student.getUser().getId());

        ListeningPracticeAttemptResponse paused = listeningPracticeService.pauseAttempt(attempt.id(), 42, student.getUser().getId());

        assertThat(paused.pausedPositionSeconds()).isEqualTo(42);
        assertThat(paused.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void submitAttempt_rejectsWhenAttemptAlreadySubmitted() {
        ListeningPracticeItemResponse item = createPublishedItem("LISTENING", "Good morning everyone.");
        ListeningPracticeAttemptResponse attempt = listeningPracticeService.startAttempt(item.id(), student.getUser().getId());
        listeningPracticeService.submitAttempt(attempt.id(),
                new SubmitListeningPracticeAttemptRequest(null, null), student.getUser().getId());

        assertThatThrownBy(() -> listeningPracticeService.submitAttempt(attempt.id(),
                new SubmitListeningPracticeAttemptRequest(null, null), student.getUser().getId()))
                .isInstanceOf(AttemptNotEditableException.class);
    }

    @Test
    void startAttempt_allowsUnlimitedRetakes() {
        ListeningPracticeItemResponse item = createPublishedItem("LISTENING", "Good morning everyone.");
        ListeningPracticeAttemptResponse first = listeningPracticeService.startAttempt(item.id(), student.getUser().getId());
        listeningPracticeService.submitAttempt(first.id(),
                new SubmitListeningPracticeAttemptRequest(null, null), student.getUser().getId());

        ListeningPracticeAttemptResponse second = listeningPracticeService.startAttempt(item.id(), student.getUser().getId());

        assertThat(second.attemptNumber()).isEqualTo(2);
    }

    @Test
    void getAttempt_rejectsWhenNotOwnedByActor() {
        ListeningPracticeItemResponse item = createPublishedItem("LISTENING", "Good morning everyone.");
        ListeningPracticeAttemptResponse attempt = listeningPracticeService.startAttempt(item.id(), student.getUser().getId());

        User otherStudentUser = newUser("student.other.listening");
        Student otherStudent = new Student();
        otherStudent.setUser(otherStudentUser);
        otherStudent.setStudentCode("HS-LST2-" + SEQ.incrementAndGet());
        otherStudent.setDateOfBirth(LocalDate.of(2012, 5, 1));
        otherStudent.setEnrollmentDate(LocalDate.now());
        studentRepository.save(otherStudent);

        assertThatThrownBy(() -> listeningPracticeService.getAttempt(attempt.id(), otherStudentUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private ListeningPracticeItemResponse createItem(String mode, String scriptText) {
        return listeningPracticeService.createItem(
                new CreateListeningPracticeItemRequest(activeCurriculum.id(), "Bài luyện " + mode, mode, null,
                        scriptText, null, null),
                teacher.getId());
    }

    private ListeningPracticeItemResponse createPublishedItem(String mode, String scriptText) {
        ListeningPracticeItemResponse item = createItem(mode, scriptText);
        ListeningPracticeItemResponse published = listeningPracticeService.updateItem(item.id(),
                new UpdateListeningPracticeItemRequest("Bài luyện " + mode, null, scriptText, null, null, "PUBLISHED"),
                teacher.getId());
        assertThat(published.status()).isEqualTo("PUBLISHED");
        return published;
    }

    private String curriculumCode() {
        return "CUR-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-" + SEQ.incrementAndGet();
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
