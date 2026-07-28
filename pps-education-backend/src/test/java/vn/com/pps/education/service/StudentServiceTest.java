package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateParentRequest;
import vn.com.pps.education.dto.CreateStudentRequest;
import vn.com.pps.education.dto.CreateUserRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.LinkParentRequest;
import vn.com.pps.education.dto.ParentResponse;
import vn.com.pps.education.dto.RecordTransferRequest;
import vn.com.pps.education.dto.StudentResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.exception.ClassEnrollmentAlreadyActiveException;
import vn.com.pps.education.exception.NotSiteManagerForSiteException;
import vn.com.pps.education.exception.ParentStudentLinkAlreadyExistsException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.StudentAlreadyExistsException;
import vn.com.pps.education.exception.StudentContactRoleConflictException;
import vn.com.pps.education.repository.ParentHistoryRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
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

    @Autowired
    private vn.com.pps.education.repository.UserRoleRepository userRoleRepository;

    @Autowired
    private SiteManagerRepository siteManagerRepository;

    private User staff;

    @BeforeEach
    void setUp() {
        staff = newUser("giao.vu");
    }

    @Test
    void create_UC13_MainFlow_usesUserProvidedStudentCodeAndWritesHistory() {
        User target = newUser("student.new");
        String code = studentCode();

        StudentResponse response = studentService.create(
                new CreateStudentRequest(target.getId(), null, code, LocalDate.of(2012, 5, 1), "MALE", null, null,
                        "THCS Nguyễn Du", "8A2", LocalDate.of(2026, 8, 1), null),
                staff.getId());

        assertThat(response.id()).isNotNull();
        assertThat(response.studentCode()).isEqualTo(code);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(studentHistoryRepository.findByStudentIdOrderByCreatedAtDesc(response.id()))
                .hasSize(1)
                .allMatch(h -> h.getAction() == vn.com.pps.education.domain.StudentHistory.Action.CREATED);
    }

    @Test
    void create_UC13_A_rejectsDuplicateStudentCode() {
        User target1 = newUser("student.dup.code.1");
        User target2 = newUser("student.dup.code.2");
        String code = studentCode();

        studentService.create(
                new CreateStudentRequest(target1.getId(), null, code, LocalDate.of(2012, 5, 1), "MALE", null, null,
                        null, null, LocalDate.of(2027, 1, 1), null),
                staff.getId());

        assertThatThrownBy(() -> studentService.create(
                new CreateStudentRequest(target2.getId(), null, code, LocalDate.of(2012, 5, 1), "MALE", null, null,
                        null, null, LocalDate.of(2027, 1, 1), null),
                staff.getId()))
                .isInstanceOf(vn.com.pps.education.exception.DuplicateStudentCodeException.class);
    }

    @Test
    void create_UC13_MainFlow_withNewAccount_createsUserAndStudentInOneTransactionAndAssignsStudentRole() {
        CreateUserRequest newAccount = new CreateUserRequest(
                "hs.moi." + System.nanoTime(), "hs.moi." + System.nanoTime() + "@pps.edu.vn",
                "Học Sinh Mới", null, "MatKhau@8kytu");

        StudentResponse response = studentService.create(
                new CreateStudentRequest(null, newAccount, studentCode(), LocalDate.of(2012, 5, 1), "MALE", null, null,
                        null, null, LocalDate.of(2026, 8, 1), null),
                staff.getId());

        assertThat(response.id()).isNotNull();
        User created = userRepository.findByUsername(newAccount.username()).orElseThrow();
        assertThat(created.getEmail()).isEqualTo(newAccount.email());
        assertThat(created.getPasswordHash()).startsWith("$2"); // BCrypt (NFR-SEC-01)
        assertThat(userRoleRepository.findByUserId(created.getId()))
                .extracting(ur -> ur.getRole().getCode())
                .containsExactly("STUDENT");
    }

    @Test
    void create_UC13_A_rejectsWhenBothUserIdAndNewAccountProvided() {
        User target = newUser("student.both");
        CreateUserRequest newAccount = new CreateUserRequest(
                "hs.both." + System.nanoTime(), "hs.both." + System.nanoTime() + "@pps.edu.vn", "Học Sinh Both", null, null);

        assertThatThrownBy(() -> studentService.create(
                new CreateStudentRequest(target.getId(), newAccount, studentCode(), LocalDate.of(2012, 5, 1), "MALE", null, null,
                        null, null, LocalDate.of(2026, 8, 1), null),
                staff.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_UC13_A_rejectsWhenNeitherUserIdNorNewAccountProvided() {
        assertThatThrownBy(() -> studentService.create(
                new CreateStudentRequest(null, null, studentCode(), LocalDate.of(2012, 5, 1), "MALE", null, null,
                        null, null, LocalDate.of(2026, 8, 1), null),
                staff.getId()))
                .isInstanceOf(IllegalArgumentException.class);
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
    void getMyStudentProfile_UC63_MainFlow_returnsOwnProfileByUserId() {
        User target = newUser("student.self.view");
        StudentResponse created = studentService.create(
                baseStudentRequest(target.getId(), LocalDate.of(2026, 1, 1)), staff.getId());

        StudentResponse mine = studentService.getMyStudentProfile(target.getId());

        assertThat(mine.id()).isEqualTo(created.id());
        assertThat(mine.userId()).isEqualTo(target.getId());
    }

    @Test
    void getMyStudentProfile_UC63_A1_rejectsWhenAccountHasNoStudentProfile() {
        User noProfile = newUser("student.self.view.noprofile");

        assertThatThrownBy(() -> studentService.getMyStudentProfile(noProfile.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateMyStudentProfile_UC63_MainFlow_updatesOnlyPortraitForOwnAccount() {
        User target = newUser("student.self.update");
        StudentResponse created = studentService.create(
                baseStudentRequest(target.getId(), LocalDate.of(2026, 1, 1)), staff.getId());

        StudentResponse updated = studentService.updateMyStudentProfile(target.getId(),
                new vn.com.pps.education.dto.UpdateOwnStudentProfileRequest("https://cdn.pps.edu.vn/self.jpg"));

        assertThat(updated.portraitUrl()).isEqualTo("https://cdn.pps.edu.vn/self.jpg");
        // UC-63 Postcondition -- field học vụ/hành chính khác giữ nguyên không đổi.
        assertThat(updated.studentCode()).isEqualTo(created.studentCode());
        assertThat(updated.status()).isEqualTo(created.status());
        assertThat(updated.gender()).isEqualTo(created.gender());
        assertThat(studentHistoryRepository.findByStudentIdOrderByCreatedAtDesc(created.id())).hasSize(2);
    }

    @Test
    void updateMyStudentProfile_UC63_A1_rejectsWhenAccountHasNoStudentProfile() {
        User noProfile = newUser("student.self.update.noprofile");

        assertThatThrownBy(() -> studentService.updateMyStudentProfile(noProfile.getId(),
                new vn.com.pps.education.dto.UpdateOwnStudentProfileRequest("https://cdn.pps.edu.vn/self.jpg")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getMyParentProfile_UC63_MainFlow_returnsOwnProfileByUserId() {
        User parentUser = newUser("parent.self.view");
        ParentResponse created = studentService.createParent(
                new CreateParentRequest(parentUser.getId(), null, "Kỹ sư", "FPT Software", "Hà Nội", null, null), staff.getId());

        ParentResponse mine = studentService.getMyParentProfile(parentUser.getId());

        assertThat(mine.id()).isEqualTo(created.id());
        assertThat(mine.userId()).isEqualTo(parentUser.getId());
    }

    @Test
    void getMyParentProfile_UC63_A1_rejectsWhenAccountHasNoParentProfile() {
        User noProfile = newUser("parent.self.view.noprofile");

        assertThatThrownBy(() -> studentService.getMyParentProfile(noProfile.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateMyParentProfile_UC63_MainFlow_updatesPortraitAndContactInfoForOwnAccountOnly() {
        User parentUser = newUser("parent.self.update");
        ParentResponse created = studentService.createParent(
                new CreateParentRequest(parentUser.getId(), null, "Kỹ sư", "FPT Software", "Hà Nội", "Ghi chú nội bộ", null), staff.getId());

        ParentResponse updated = studentService.updateMyParentProfile(parentUser.getId(),
                new vn.com.pps.education.dto.UpdateOwnParentProfileRequest(
                        "https://cdn.pps.edu.vn/parent-self.jpg", "Bác sĩ", "Bệnh viện Bạch Mai", "Hà Nội mới"));

        assertThat(updated.portraitUrl()).isEqualTo("https://cdn.pps.edu.vn/parent-self.jpg");
        assertThat(updated.occupation()).isEqualTo("Bác sĩ");
        assertThat(updated.workplace()).isEqualTo("Bệnh viện Bạch Mai");
        assertThat(updated.address()).isEqualTo("Hà Nội mới");
        // UC-63 Postcondition -- notes (ghi chú nội bộ do Nhân viên quản lý) giữ nguyên không đổi.
        assertThat(updated.notes()).isEqualTo(created.notes());
        assertThat(parentHistoryRepository.findByParentIdOrderByCreatedAtDesc(created.id()))
                .filteredOn(h -> h.getAction() == vn.com.pps.education.domain.ParentHistory.Action.UPDATED)
                .hasSize(1);
    }

    @Test
    void updateMyParentProfile_UC63_A1_rejectsWhenAccountHasNoParentProfile() {
        User noProfile = newUser("parent.self.update.noprofile");

        assertThatThrownBy(() -> studentService.updateMyParentProfile(noProfile.getId(),
                new vn.com.pps.education.dto.UpdateOwnParentProfileRequest("https://cdn.pps.edu.vn/x.jpg", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createParent_UC13_MainFlow_withNewAccount_createsUserAndParentInOneTransactionAndAssignsParentRole() {
        CreateUserRequest newAccount = new CreateUserRequest(
                "ph.moi." + System.nanoTime(), "ph.moi." + System.nanoTime() + "@pps.edu.vn",
                "Phụ Huynh Mới", null, "MatKhau@8kytu");

        ParentResponse response = studentService.createParent(
                new CreateParentRequest(null, newAccount, "Kỹ sư", "FPT Software", "Hà Nội", null, null), staff.getId());

        assertThat(response.id()).isNotNull();
        User created = userRepository.findByUsername(newAccount.username()).orElseThrow();
        assertThat(created.getEmail()).isEqualTo(newAccount.email());
        assertThat(created.getPasswordHash()).startsWith("$2"); // BCrypt (NFR-SEC-01)
        assertThat(userRoleRepository.findByUserId(created.getId()))
                .extracting(ur -> ur.getRole().getCode())
                .containsExactly("PARENT");
    }

    @Test
    void createParent_UC13_A_rejectsWhenBothUserIdAndNewAccountProvided() {
        User target = newUser("parent.both");
        CreateUserRequest newAccount = new CreateUserRequest(
                "ph.both." + System.nanoTime(), "ph.both." + System.nanoTime() + "@pps.edu.vn", "Phụ Huynh Both", null, null);

        assertThatThrownBy(() -> studentService.createParent(
                new CreateParentRequest(target.getId(), newAccount, null, null, null, null, null), staff.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createParent_UC13_A_rejectsWhenNeitherUserIdNorNewAccountProvided() {
        assertThatThrownBy(() -> studentService.createParent(
                new CreateParentRequest(null, null, null, null, null, null, null), staff.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linkParent_UC13_MainFlow_createsParentAndLinksToStudent() {
        User studentUser = newUser("student.withparent");
        StudentResponse student = studentService.create(
                baseStudentRequest(studentUser.getId(), LocalDate.of(2026, 1, 1)), staff.getId());

        User parentUser = newUser("parent.of.student");
        ParentResponse parent = studentService.createParent(
                new CreateParentRequest(parentUser.getId(), null, "Kỹ sư", "FPT Software", "Hà Nội", null, null), staff.getId());

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
                new CreateParentRequest(parentUser.getId(), null, null, null, null, null, null), staff.getId());
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
                new CreateParentRequest(parentUser1.getId(), null, null, null, null, null, null), staff.getId());
        User parentUser2 = newUser("parent.primary.2");
        ParentResponse parent2 = studentService.createParent(
                new CreateParentRequest(parentUser2.getId(), null, null, null, null, null, null), staff.getId());
        studentService.linkParent(student.id(), new LinkParentRequest(parent1.id(), "FATHER", true, false, null));

        assertThatThrownBy(() -> studentService.linkParent(student.id(),
                new LinkParentRequest(parent2.id(), "MOTHER", true, false, null)))
                .isInstanceOf(StudentContactRoleConflictException.class);
    }

    @Test
    void searchParents_returnsAllParents_whenQueryBlank() {
        User parentUser1 = newUser("parent.searchall.1");
        ParentResponse parent1 = studentService.createParent(
                new CreateParentRequest(parentUser1.getId(), null, null, null, null, null, null), staff.getId());
        User parentUser2 = newUser("parent.searchall.2");
        ParentResponse parent2 = studentService.createParent(
                new CreateParentRequest(parentUser2.getId(), null, null, null, null, null, null), staff.getId());

        assertThat(studentService.searchParents(null))
                .extracting(ParentResponse::id)
                .contains(parent1.id(), parent2.id());
    }

    @Test
    void searchParents_filtersByFullNameQuery() {
        User parentUser = newUser("parent.uniquename." + System.nanoTime());
        ParentResponse parent = studentService.createParent(
                new CreateParentRequest(parentUser.getId(), null, null, null, null, null, null), staff.getId());

        assertThat(studentService.searchParents(parentUser.getFullName()))
                .extracting(ParentResponse::id)
                .containsExactly(parent.id());
    }

    @Test
    void getParentById_returnsParentDetails() {
        User parentUser = newUser("parent.getbyid");
        ParentResponse created = studentService.createParent(
                new CreateParentRequest(parentUser.getId(), null, "Kỹ sư", "FPT Software", "Hà Nội", null, null), staff.getId());

        ParentResponse fetched = studentService.getParentById(created.id());

        assertThat(fetched).isEqualTo(created);
    }

    @Test
    void getParentById_throwsWhenNotFound() {
        assertThatThrownBy(() -> studentService.getParentById(-1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void recordTransfer_UC13_A1_updatesPrimarySiteImmediately() {
        User studentUser = newUser("student.transfer");
        Site oldSite = newSite("SITE-OLD");
        StudentResponse created = studentService.create(
                new CreateStudentRequest(studentUser.getId(), null, studentCode(), LocalDate.of(2012, 5, 1), null, null, oldSite.getId(),
                        null, null, LocalDate.of(2026, 1, 1), null),
                staff.getId());
        Site newSite = newSite("SITE-NEW");

        var transfer = studentService.recordTransfer(created.id(),
                new RecordTransferRequest("SITE_CHANGE", null, null, newSite.getId(), LocalDate.now(), "Chuyển nhà"),
                staff.getId());

        assertThat(transfer.fromSiteId()).isEqualTo(oldSite.getId());
        assertThat(transfer.toSiteId()).isEqualTo(newSite.getId());
        // A1: primary_site_id được cập nhật ngay khi giao dịch hoàn tất.
        assertThat(studentService.getById(created.id(), staff.getId()).primarySiteId()).isEqualTo(newSite.getId());
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

    @Test
    void search_UC13_A1_scopesToSiteManagerAssignedSite() {
        Site ownSite = newSite("SITE-SM-OWN");
        Site otherSite = newSite("SITE-SM-OTHER");
        User siteManagerUser = newUser("site.manager.search");
        newSiteManager(siteManagerUser, ownSite);
        StudentResponse ownStudent = studentService.create(
                new CreateStudentRequest(newUser("student.own").getId(), null, studentCode(), LocalDate.of(2012, 5, 1),
                        "MALE", null, ownSite.getId(), null, null, LocalDate.of(2026, 1, 1), null),
                staff.getId());
        StudentResponse otherStudent = studentService.create(
                new CreateStudentRequest(newUser("student.other").getId(), null, studentCode(), LocalDate.of(2012, 5, 1),
                        "MALE", null, otherSite.getId(), null, null, LocalDate.of(2026, 1, 1), null),
                staff.getId());

        assertThat(studentService.search(null, null, siteManagerUser.getId()))
                .extracting(StudentResponse::id)
                .contains(ownStudent.id())
                .doesNotContain(otherStudent.id());
    }

    @Test
    void getById_UC13_A1_rejectsSiteManagerReadingStudentOfOtherSite() {
        Site ownSite = newSite("SITE-SM-GETOWN");
        Site otherSite = newSite("SITE-SM-GETOTHER");
        User siteManagerUser = newUser("site.manager.getbyid");
        newSiteManager(siteManagerUser, ownSite);
        StudentResponse otherStudent = studentService.create(
                new CreateStudentRequest(newUser("student.getother").getId(), null, studentCode(), LocalDate.of(2012, 5, 1),
                        "MALE", null, otherSite.getId(), null, null, LocalDate.of(2026, 1, 1), null),
                staff.getId());

        assertThatThrownBy(() -> studentService.getById(otherStudent.id(), siteManagerUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_UC13_A1_rejectsSiteManagerUpdatingStudentOfOtherSite() {
        Site ownSite = newSite("SITE-SM-UPDOWN");
        Site otherSite = newSite("SITE-SM-UPDOTHER");
        User siteManagerUser = newUser("site.manager.update");
        newSiteManager(siteManagerUser, ownSite);
        StudentResponse otherStudent = studentService.create(
                new CreateStudentRequest(newUser("student.updother").getId(), null, studentCode(), LocalDate.of(2012, 5, 1),
                        "MALE", null, otherSite.getId(), null, null, LocalDate.of(2026, 1, 1), null),
                staff.getId());

        assertThatThrownBy(() -> studentService.update(otherStudent.id(),
                new vn.com.pps.education.dto.UpdateStudentRequest(
                        LocalDate.of(2012, 5, 1), "MALE", null, null, null, null),
                siteManagerUser.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_UC13_A1_rejectsSiteManagerCreatingStudentForOtherSite() {
        Site ownSite = newSite("SITE-SM-CREATEOWN");
        Site otherSite = newSite("SITE-SM-CREATEOTHER");
        User siteManagerUser = newUser("site.manager.create");
        newSiteManager(siteManagerUser, ownSite);

        assertThatThrownBy(() -> studentService.create(
                new CreateStudentRequest(newUser("student.createother").getId(), null, studentCode(), LocalDate.of(2012, 5, 1),
                        "MALE", null, otherSite.getId(), null, null, LocalDate.of(2026, 1, 1), null),
                siteManagerUser.getId()))
                .isInstanceOf(NotSiteManagerForSiteException.class);
    }

    @Test
    void create_UC13_A1_allowsSiteManagerCreatingStudentForOwnSite() {
        Site ownSite = newSite("SITE-SM-CREATEOK");
        User siteManagerUser = newUser("site.manager.createok");
        newSiteManager(siteManagerUser, ownSite);

        StudentResponse created = studentService.create(
                new CreateStudentRequest(newUser("student.createok").getId(), null, studentCode(), LocalDate.of(2012, 5, 1),
                        "MALE", null, ownSite.getId(), null, null, LocalDate.of(2026, 1, 1), null),
                siteManagerUser.getId());

        assertThat(created.primarySiteId()).isEqualTo(ownSite.getId());
    }

    private SiteManager newSiteManager(User user, Site site) {
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(site);
        siteManager.setUser(user);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(user);
        return siteManagerRepository.save(siteManager);
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
        return new CreateStudentRequest(userId, null, studentCode(), LocalDate.of(2012, 5, 1), "MALE", null, null,
                null, null, enrollmentDate, null);
    }

    private String studentCode() {
        return "HSTEST" + SEQ.incrementAndGet();
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
