package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateParentRequest;
import vn.com.pps.education.dto.CreateStudentRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.LinkParentRequest;
import vn.com.pps.education.dto.ParentResponse;
import vn.com.pps.education.dto.RecordTransferRequest;
import vn.com.pps.education.dto.StudentResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.ClassEnrollmentAlreadyActiveException;
import vn.com.pps.education.exception.ParentStudentLinkAlreadyExistsException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.StudentAlreadyExistsException;
import vn.com.pps.education.exception.StudentContactRoleConflictException;
import vn.com.pps.education.repository.ParentHistoryRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentHistoryRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-13: Quản lý hồ sơ học sinh — Main Flow (bước 1-5), A1 (chuyển điểm
 * trường khác Quản lý điểm trường phụ trách). Xem docs/uc/phan-he-05-hoc-sinh.md.
 */
@Transactional
class StudentServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private StudentService studentService;

    @Autowired
    private ClassService classService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private StudentHistoryRepository studentHistoryRepository;

    @Autowired
    private ParentHistoryRepository parentHistoryRepository;

    private User staff;

    @BeforeEach
    void setUp() {
        staff = newUser("giao.vu");
    }

    @Test
    void create_UC13_MainFlow_generatesStudentCodeAndWritesHistory() {
        User target = newUser("student.new");

        StudentResponse response = studentService.create(
                new CreateStudentRequest(target.getId(), LocalDate.of(2012, 5, 1), "MALE", null, null,
                        "THCS Nguyễn Du", "8A2", LocalDate.of(2026, 8, 1), null),
                staff.getId());

        assertThat(response.id()).isNotNull();
        assertThat(response.studentCode()).matches("HS2026-\\d{4}");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(studentHistoryRepository.findByStudentIdOrderByCreatedAtDesc(response.id()))
                .hasSize(1)
                .allMatch(h -> h.getAction() == vn.com.pps.education.domain.StudentHistory.Action.CREATED);
    }

    @Test
    void create_generatesSequentialStudentCodesForSameEnrollmentYear() {
        User target1 = newUser("student.seq.1");
        User target2 = newUser("student.seq.2");
        LocalDate enrollmentDate = LocalDate.of(2027, 1, 1);

        StudentResponse first = studentService.create(baseStudentRequest(target1.getId(), enrollmentDate), staff.getId());
        StudentResponse second = studentService.create(baseStudentRequest(target2.getId(), enrollmentDate), staff.getId());

        assertThat(first.studentCode()).isNotEqualTo(second.studentCode());
        assertThat(first.studentCode()).startsWith("HS2027-");
        assertThat(second.studentCode()).startsWith("HS2027-");
    }

    @Test
    void create_rejectsWhenUserAlreadyHasStudentProfile() {
        User target = newUser("student.dup.user");
        studentService.create(baseStudentRequest(target.getId(), LocalDate.of(2026, 1, 1)), staff.getId());

        assertThatThrownBy(() -> studentService.create(
                baseStudentRequest(target.getId(), LocalDate.of(2026, 1, 1)), staff.getId()))
                .isInstanceOf(StudentAlreadyExistsException.class);
    }

    @Test
    void update_UC13_MainFlow_updatesProfileAndWritesHistory() {
        User target = newUser("student.update");
        StudentResponse created = studentService.create(
                baseStudentRequest(target.getId(), LocalDate.of(2026, 1, 1)), staff.getId());

        StudentResponse updated = studentService.update(created.id(),
                new vn.com.pps.education.dto.UpdateStudentRequest(
                        LocalDate.of(2012, 5, 1), "FEMALE", "https://cdn.pps.edu.vn/p.jpg", "THCS A", "8A1", "Dị ứng hải sản"),
                staff.getId());

        assertThat(updated.gender()).isEqualTo("FEMALE");
        assertThat(updated.notes()).isEqualTo("Dị ứng hải sản");
        assertThat(studentHistoryRepository.findByStudentIdOrderByCreatedAtDesc(created.id())).hasSize(2);
    }

    @Test
    void linkParent_UC13_MainFlow_createsParentAndLinksToStudent() {
        User studentUser = newUser("student.withparent");
        StudentResponse student = studentService.create(
                baseStudentRequest(studentUser.getId(), LocalDate.of(2026, 1, 1)), staff.getId());

        User parentUser = newUser("parent.of.student");
        ParentResponse parent = studentService.createParent(
                new CreateParentRequest(parentUser.getId(), "Kỹ sư", "FPT Software", "Hà Nội", null), staff.getId());

        var link = studentService.linkParent(student.id(),
                new LinkParentRequest(parent.id(), "MOTHER", true, true, null));

        assertThat(link.isPrimaryContact()).isTrue();
        assertThat(link.isFinancialResponsible()).isTrue();
        assertThat(studentService.listParents(student.id())).containsExactly(link);
        assertThat(parentHistoryRepository.findByParentIdOrderByCreatedAtDesc(parent.id())).hasSize(1);
    }

    @Test
    void linkParent_rejectsDuplicateLinkForSameParentAndStudent() {
        User studentUser = newUser("student.dup.link");
        StudentResponse student = studentService.create(
                baseStudentRequest(studentUser.getId(), LocalDate.of(2026, 1, 1)), staff.getId());
        User parentUser = newUser("parent.dup.link");
        ParentResponse parent = studentService.createParent(
                new CreateParentRequest(parentUser.getId(), null, null, null, null), staff.getId());
        studentService.linkParent(student.id(), new LinkParentRequest(parent.id(), "FATHER", false, false, null));

        assertThatThrownBy(() -> studentService.linkParent(student.id(),
                new LinkParentRequest(parent.id(), "FATHER", false, false, null)))
                .isInstanceOf(ParentStudentLinkAlreadyExistsException.class);
    }

    @Test
    void linkParent_rejectsSecondPrimaryContactForSameStudent() {
        User studentUser = newUser("student.two.primary");
        StudentResponse student = studentService.create(
                baseStudentRequest(studentUser.getId(), LocalDate.of(2026, 1, 1)), staff.getId());
        User parentUser1 = newUser("parent.primary.1");
        ParentResponse parent1 = studentService.createParent(
                new CreateParentRequest(parentUser1.getId(), null, null, null, null), staff.getId());
        User parentUser2 = newUser("parent.primary.2");
        ParentResponse parent2 = studentService.createParent(
                new CreateParentRequest(parentUser2.getId(), null, null, null, null), staff.getId());
        studentService.linkParent(student.id(), new LinkParentRequest(parent1.id(), "FATHER", true, false, null));

        assertThatThrownBy(() -> studentService.linkParent(student.id(),
                new LinkParentRequest(parent2.id(), "MOTHER", true, false, null)))
                .isInstanceOf(StudentContactRoleConflictException.class);
    }

    @Test
    void recordTransfer_UC13_A1_updatesPrimarySiteImmediately() {
        User studentUser = newUser("student.transfer");
        Site oldSite = newSite("SITE-OLD");
        StudentResponse created = studentService.create(
                new CreateStudentRequest(studentUser.getId(), LocalDate.of(2012, 5, 1), null, null, oldSite.getId(),
                        null, null, LocalDate.of(2026, 1, 1), null),
                staff.getId());
        Site newSite = newSite("SITE-NEW");

        var transfer = studentService.recordTransfer(created.id(),
                new RecordTransferRequest("SITE_CHANGE", null, null, newSite.getId(), LocalDate.now(), "Chuyển nhà"),
                staff.getId());

        assertThat(transfer.fromSiteId()).isEqualTo(oldSite.getId());
        assertThat(transfer.toSiteId()).isEqualTo(newSite.getId());
        // A1: primary_site_id được cập nhật ngay khi giao dịch hoàn tất.
        assertThat(studentService.getById(created.id()).primarySiteId()).isEqualTo(newSite.getId());
        assertThat(studentService.listTransferHistory(created.id())).containsExactly(transfer);
    }

    @Test
    void recordTransfer_UC13_CLASS_CHANGE_transfersEnrollmentAndSetsFromClassId() {
        Site site = newSite("SITE-CLASS");
        CurriculumResponse curriculum = newActiveCurriculum();
        ClassResponse oldClass = newClass(site, curriculum, "8A2-OLD");
        ClassResponse newClass = newClass(site, curriculum, "8A2-NEW");
        StudentResponse student = studentService.create(
                baseStudentRequest(newUser("student.classchange").getId(), LocalDate.of(2026, 1, 1)), staff.getId());
        classService.enroll(oldClass.id(), new EnrollStudentRequest(student.id(), LocalDate.now()), staff.getId());

        var transfer = studentService.recordTransfer(student.id(),
                new RecordTransferRequest("CLASS_CHANGE", oldClass.id(), newClass.id(), null, LocalDate.now(), "Đổi lớp"),
                staff.getId());

        assertThat(transfer.fromClassId()).isEqualTo(oldClass.id());
        assertThat(transfer.toClassId()).isEqualTo(newClass.id());
        assertThat(classService.listEnrollments(oldClass.id()))
                .allMatch(e -> e.status().equals("TRANSFERRED"));
        assertThat(classService.listEnrollments(newClass.id()))
                .allMatch(e -> e.status().equals("ACTIVE"));
    }

    @Test
    void recordTransfer_rejectsClassChangeWithoutFromClassId() {
        ClassResponse toClass = newClass(newSite("SITE-NOFROM"), newActiveCurriculum(), "8A2-NOFROM");
        StudentResponse student = studentService.create(
                baseStudentRequest(newUser("student.nofrom").getId(), LocalDate.of(2026, 1, 1)), staff.getId());

        assertThatThrownBy(() -> studentService.recordTransfer(student.id(),
                new RecordTransferRequest("CLASS_CHANGE", null, toClass.id(), null, LocalDate.now(), null),
                staff.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordTransfer_rejectsWhenFromClassIdHasNoActiveEnrollment() {
        Site site = newSite("SITE-NOENROLL");
        CurriculumResponse curriculum = newActiveCurriculum();
        ClassResponse fromClass = newClass(site, curriculum, "8A2-NOENROLL-FROM");
        ClassResponse toClass = newClass(site, curriculum, "8A2-NOENROLL-TO");
        StudentResponse student = studentService.create(
                baseStudentRequest(newUser("student.noenroll").getId(), LocalDate.of(2026, 1, 1)), staff.getId());

        assertThatThrownBy(() -> studentService.recordTransfer(student.id(),
                new RecordTransferRequest("CLASS_CHANGE", fromClass.id(), toClass.id(), null, LocalDate.now(), null),
                staff.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void recordTransfer_rejectsWhenAlreadyActiveInDestinationClass() {
        Site site = newSite("SITE-DUPACTIVE");
        CurriculumResponse curriculum = newActiveCurriculum();
        ClassResponse fromClass = newClass(site, curriculum, "8A2-DUP-FROM");
        ClassResponse toClass = newClass(site, curriculum, "8A2-DUP-TO");
        StudentResponse student = studentService.create(
                baseStudentRequest(newUser("student.dupactive").getId(), LocalDate.of(2026, 1, 1)), staff.getId());
        classService.enroll(fromClass.id(), new EnrollStudentRequest(student.id(), LocalDate.now()), staff.getId());
        classService.enroll(toClass.id(), new EnrollStudentRequest(student.id(), LocalDate.now()), staff.getId());

        assertThatThrownBy(() -> studentService.recordTransfer(student.id(),
                new RecordTransferRequest("CLASS_CHANGE", fromClass.id(), toClass.id(), null, LocalDate.now(), null),
                staff.getId()))
                .isInstanceOf(ClassEnrollmentAlreadyActiveException.class);
    }

    private CurriculumResponse newActiveCurriculum() {
        CurriculumResponse draft = curriculumService.create(
                new CreateCurriculumRequest("CUR-" + SEQ.incrementAndGet(), "Chuẩn", "MAIN", null, null, null),
                staff.getId());
        return curriculumService.update(draft.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, "ACTIVE", false), staff.getId());
    }

    private ClassResponse newClass(Site site, CurriculumResponse curriculum, String codePrefix) {
        return classService.create(
                new CreateClassRequest(codePrefix + "-" + SEQ.incrementAndGet(), codePrefix, site.getId(),
                        curriculum.id(), "OPEN", 25, 10, LocalDate.now(), null, "2026-2027", "S1"),
                staff.getId());
    }

    private CreateStudentRequest baseStudentRequest(Long userId, LocalDate enrollmentDate) {
        return new CreateStudentRequest(userId, LocalDate.of(2012, 5, 1), "MALE", null, null,
                null, null, enrollmentDate, null);
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }

    private Site newSite(String codePrefix) {
        Site site = new Site();
        site.setCode(codePrefix + "-" + SEQ.incrementAndGet());
        site.setName("Test " + codePrefix);
        site.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(site);
    }
}
