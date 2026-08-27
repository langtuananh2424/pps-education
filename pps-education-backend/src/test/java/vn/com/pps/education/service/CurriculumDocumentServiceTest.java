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
import vn.com.pps.education.dto.CreateCurriculumDocumentRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CurriculumDocumentResponse;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.UpdateCurriculumDocumentRequest;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
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

/** UC-60: Kho tài liệu tham khảo — Main Flow (upload/publish/xem), A1 (học viên chưa ghi danh curriculum). */
@Transactional
class CurriculumDocumentServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private CurriculumDocumentService curriculumDocumentService;

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
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());

        Site site = newSite();
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", site.getId(), activeCurriculum.id(), "OPEN", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
    }

    @Test
    void createDocument_UC60_MainFlow_savesAsDraft() {
        CurriculumDocumentResponse document = curriculumDocumentService.createDocument(
                new CreateCurriculumDocumentRequest(activeCurriculum.id(), "Tài liệu ngữ pháp", "Mô tả", "PDF",
                        "https://cdn.pps.edu.vn/docs/1.pdf", null, null),
                teacher.getId());

        assertThat(document.status()).isEqualTo("DRAFT");
        assertThat(document.curriculumId()).isEqualTo(activeCurriculum.id());
    }

    @Test
    void updateDocument_UC60_MainFlow_publishMakesVisibleToStudents() {
        CurriculumDocumentResponse document = curriculumDocumentService.createDocument(
                new CreateCurriculumDocumentRequest(activeCurriculum.id(), "Tài liệu ngữ pháp", null, "PDF",
                        "https://cdn.pps.edu.vn/docs/1.pdf", null, null),
                teacher.getId());
        Student student = enrollStudent();

        assertThat(curriculumDocumentService.listMyDocuments(student.getUser().getId(), null)).isEmpty();

        CurriculumDocumentResponse published = curriculumDocumentService.updateDocument(document.id(),
                new UpdateCurriculumDocumentRequest("Tài liệu ngữ pháp", null, null, "PUBLISHED", null), teacher.getId());

        assertThat(published.status()).isEqualTo("PUBLISHED");
        List<CurriculumDocumentResponse> visible = curriculumDocumentService.listMyDocuments(student.getUser().getId(), null);
        assertThat(visible).extracting(CurriculumDocumentResponse::id).contains(document.id());
    }

    @Test
    void listMyDocuments_UC60_A1_returnsEmptyWhenStudentNotEnrolledInCurriculum() {
        CurriculumDocumentResponse document = curriculumDocumentService.createDocument(
                new CreateCurriculumDocumentRequest(activeCurriculum.id(), "Tài liệu ngữ pháp", null, "PDF",
                        "https://cdn.pps.edu.vn/docs/1.pdf", null, null),
                teacher.getId());
        curriculumDocumentService.updateDocument(document.id(),
                new UpdateCurriculumDocumentRequest("Tài liệu ngữ pháp", null, null, "PUBLISHED", null), teacher.getId());

        User outsiderStudentUser = newUser("student.outsider.doc");
        Student outsider = new Student();
        outsider.setUser(outsiderStudentUser);
        outsider.setStudentCode("HS-DOC-OUT-" + SEQ.incrementAndGet());
        outsider.setDateOfBirth(LocalDate.of(2012, 5, 1));
        outsider.setEnrollmentDate(LocalDate.now());
        studentRepository.save(outsider);

        assertThat(curriculumDocumentService.listMyDocuments(outsiderStudentUser.getId(), null)).isEmpty();
    }

    @Test
    void listByCurriculum_boSung_staffSeesDraftDocuments() {
        CurriculumDocumentResponse document = curriculumDocumentService.createDocument(
                new CreateCurriculumDocumentRequest(activeCurriculum.id(), "Tài liệu nháp", null, "VIDEO",
                        "https://cdn.pps.edu.vn/docs/2.mp4", null, null),
                teacher.getId());

        List<CurriculumDocumentResponse> all = curriculumDocumentService.listByCurriculum(activeCurriculum.id());

        assertThat(all).extracting(CurriculumDocumentResponse::id).contains(document.id());
        assertThat(all).filteredOn(d -> d.id().equals(document.id())).first()
                .satisfies(d -> assertThat(d.status()).isEqualTo("DRAFT"));
    }

    private Student enrollStudent() {
        User studentUser = newUser("student.doc");
        Student student = new Student();
        student.setUser(studentUser);
        student.setStudentCode("HS-DOC-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        student = studentRepository.save(student);
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
        return student;
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
