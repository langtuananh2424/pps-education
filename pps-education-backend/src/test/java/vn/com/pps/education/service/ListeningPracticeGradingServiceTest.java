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
import vn.com.pps.education.dto.GradeListeningAttemptRequest;
import vn.com.pps.education.dto.ListeningPracticeAttemptResponse;
import vn.com.pps.education.dto.ListeningPracticeGradingResponse;
import vn.com.pps.education.dto.ListeningPracticeItemResponse;
import vn.com.pps.education.dto.PendingListeningGradingResponse;
import vn.com.pps.education.dto.SubmitListeningPracticeAttemptRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateListeningPracticeItemRequest;
import vn.com.pps.education.exception.AnswerNotManuallyGradableException;
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
 * UC-26: Chấm thủ công lượt luyện Nói — hàng chờ TÁCH RIÊNG khỏi
 * ManualGradingController (UC-41).
 */
@Transactional
class ListeningPracticeGradingServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ListeningPracticeService listeningPracticeService;

    @Autowired
    private ListeningPracticeGradingService listeningPracticeGradingService;

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
    private Student student;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        ClassResponse schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");

        User studentUser = newUser("student.listeninggrading");
        student = new Student();
        student.setUser(studentUser);
        student.setStudentCode("HS-LSG-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        student = studentRepository.save(student);
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
    }

    @Test
    void gradeAttempt_UC26_MainFlow_gradesSpeakingAttemptAndMarksGraded() {
        ListeningPracticeAttemptResponse submitted = submitSpeakingAttempt();
        assertThat(listeningPracticeGradingService.listPendingGrading())
                .extracting(PendingListeningGradingResponse::practiceAttemptId).contains(submitted.id());

        ListeningPracticeGradingResponse grading = listeningPracticeGradingService.gradeAttempt(submitted.id(),
                new GradeListeningAttemptRequest(new BigDecimal("8.5"), new BigDecimal("10"), "Phát âm khá tốt"),
                teacher.getId());

        assertThat(grading.score()).isEqualByComparingTo("8.5");
        ListeningPracticeAttemptResponse graded = listeningPracticeService.getAttempt(submitted.id(), student.getUser().getId());
        assertThat(graded.status()).isEqualTo("GRADED");
        assertThat(listeningPracticeGradingService.listPendingGrading())
                .extracting(PendingListeningGradingResponse::practiceAttemptId).doesNotContain(submitted.id());
    }

    @Test
    void listPendingGrading_boSung_onlyShowsSpeakingModeNotDictationOrListening() {
        ListeningPracticeAttemptResponse speakingAttempt = submitSpeakingAttempt();

        ListeningPracticeItemResponse dictationItem = createPublishedItem("DICTATION", "Hello world.");
        ListeningPracticeAttemptResponse dictationAttempt = listeningPracticeService.startAttempt(
                dictationItem.id(), student.getUser().getId());
        listeningPracticeService.submitAttempt(dictationAttempt.id(),
                new SubmitListeningPracticeAttemptRequest("Hello world.", null), student.getUser().getId());

        List<PendingListeningGradingResponse> pending = listeningPracticeGradingService.listPendingGrading();

        assertThat(pending).extracting(PendingListeningGradingResponse::practiceAttemptId)
                .contains(speakingAttempt.id())
                .doesNotContain(dictationAttempt.id());
    }

    @Test
    void gradeAttempt_rejectsWhenAttemptIsNotSpeakingMode() {
        ListeningPracticeItemResponse dictationItem = createPublishedItem("DICTATION", "Hello world.");
        ListeningPracticeAttemptResponse attempt = listeningPracticeService.startAttempt(dictationItem.id(), student.getUser().getId());
        listeningPracticeService.submitAttempt(attempt.id(),
                new SubmitListeningPracticeAttemptRequest("Hello world.", null), student.getUser().getId());

        assertThatThrownBy(() -> listeningPracticeGradingService.gradeAttempt(attempt.id(),
                new GradeListeningAttemptRequest(new BigDecimal("5"), new BigDecimal("10"), null), teacher.getId()))
                .isInstanceOf(AnswerNotManuallyGradableException.class);
    }

    @Test
    void gradeAttempt_boSung_regradingUpdatesInPlaceInsteadOfVersioning() {
        ListeningPracticeAttemptResponse submitted = submitSpeakingAttempt();
        listeningPracticeGradingService.gradeAttempt(submitted.id(),
                new GradeListeningAttemptRequest(new BigDecimal("6"), new BigDecimal("10"), "Lần chấm đầu"), teacher.getId());

        ListeningPracticeGradingResponse regraded = listeningPracticeGradingService.gradeAttempt(submitted.id(),
                new GradeListeningAttemptRequest(new BigDecimal("9"), new BigDecimal("10"), "Chấm lại, nghe kỹ hơn"), teacher.getId());

        assertThat(regraded.score()).isEqualByComparingTo("9");
        assertThat(regraded.feedback()).isEqualTo("Chấm lại, nghe kỹ hơn");
    }

    private ListeningPracticeAttemptResponse submitSpeakingAttempt() {
        ListeningPracticeItemResponse item = createPublishedItem("SPEAKING", "Practice pronunciation of 'through'.");
        ListeningPracticeAttemptResponse attempt = listeningPracticeService.startAttempt(item.id(), student.getUser().getId());
        return listeningPracticeService.submitAttempt(attempt.id(),
                new SubmitListeningPracticeAttemptRequest(null, "https://cdn.pps.edu.vn/recordings/1.mp3"),
                student.getUser().getId());
    }

    private ListeningPracticeItemResponse createPublishedItem(String mode, String scriptText) {
        ListeningPracticeItemResponse item = listeningPracticeService.createItem(
                new CreateListeningPracticeItemRequest(activeCurriculum.id(), "Bài luyện " + mode, mode, null,
                        scriptText, null, null),
                teacher.getId());
        return listeningPracticeService.updateItem(item.id(),
                new UpdateListeningPracticeItemRequest("Bài luyện " + mode, null, scriptText, null, null, "PUBLISHED"),
                teacher.getId());
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
