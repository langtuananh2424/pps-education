package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Parent;
import vn.com.pps.education.domain.ParentStudent;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.PortalClassOptionResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.WithdrawEnrollmentRequest;
import vn.com.pps.education.exception.NotAuthorizedForPortalAccessException;
import vn.com.pps.education.repository.ParentRepository;
import vn.com.pps.education.repository.ParentStudentRepository;
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
 * UC-42: Chọn lớp đang xem — Main Flow (bước 1-3), A1 (chưa từng xếp
 * lớp), A2 (không còn lớp ACTIVE), A3 (phụ huynh đổi con xem). Xem
 * docs/uc/phan-he-07-lms-portal.md.
 */
@Transactional
class PortalClassSelectionServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private PortalClassSelectionService portalClassSelectionService;

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
    private ParentRepository parentRepository;

    @Autowired
    private ParentStudentRepository parentStudentRepository;

    private User headAcademic;
    private CurriculumResponse activeCurriculum;
    private Site site;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());
        site = newSite();
    }

    @Test
    void listClassOptions_UC42_MainFlow_singleEnrollmentIsRecommended() {
        Student student = newStudent();
        ClassResponse schoolClass = newOpenClass();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        List<PortalClassOptionResponse> options = portalClassSelectionService.listClassOptions(student.getId(), student.getUser().getId());

        assertThat(options).hasSize(1);
        assertThat(options.get(0).recommended()).isTrue();
        assertThat(options.get(0).status()).isEqualTo("ACTIVE");
    }

    @Test
    void listClassOptions_UC42_MainFlow_recommendsActiveClassAmongMultiple() {
        Student student = newStudent();
        ClassResponse oldClass = newOpenClass();
        var oldEnrollment = classService.enroll(oldClass.id(),
                new EnrollStudentRequest(student.getId(), LocalDate.now().minusMonths(6)), headAcademic.getId());
        classService.withdraw(oldClass.id(), oldEnrollment.id(),
                new WithdrawEnrollmentRequest(LocalDate.now().minusMonths(1), "Chuyển lớp"), headAcademic.getId());
        ClassResponse newClass = newOpenClass();
        classService.enroll(newClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());

        List<PortalClassOptionResponse> options = portalClassSelectionService.listClassOptions(student.getId(), student.getUser().getId());

        assertThat(options).hasSize(2);
        assertThat(options.get(0).classId()).isEqualTo(newClass.id()); // sort enrolled_date DESC
        PortalClassOptionResponse recommended = options.stream().filter(PortalClassOptionResponse::recommended).findFirst().orElseThrow();
        assertThat(recommended.status()).isEqualTo("ACTIVE");
        assertThat(recommended.classId()).isEqualTo(newClass.id());
    }

    @Test
    void listClassOptions_UC42_A1_returnsEmptyListWhenNeverEnrolled() {
        Student student = newStudent();

        assertThat(portalClassSelectionService.listClassOptions(student.getId(), student.getUser().getId())).isEmpty();
    }

    @Test
    void listClassOptions_UC42_A2_recommendsMostRecentWhenNoActiveClassLeft() {
        Student student = newStudent();
        ClassResponse olderClass = newOpenClass();
        var olderEnrollment = classService.enroll(olderClass.id(),
                new EnrollStudentRequest(student.getId(), LocalDate.now().minusMonths(6)), headAcademic.getId());
        classService.withdraw(olderClass.id(), olderEnrollment.id(),
                new WithdrawEnrollmentRequest(LocalDate.now().minusMonths(4), "Tốt nghiệp"), headAcademic.getId());
        ClassResponse recentClass = newOpenClass();
        var recentEnrollment = classService.enroll(recentClass.id(),
                new EnrollStudentRequest(student.getId(), LocalDate.now().minusMonths(1)), headAcademic.getId());
        classService.withdraw(recentClass.id(), recentEnrollment.id(),
                new WithdrawEnrollmentRequest(LocalDate.now(), "Tốt nghiệp"), headAcademic.getId());

        List<PortalClassOptionResponse> options = portalClassSelectionService.listClassOptions(student.getId(), student.getUser().getId());

        assertThat(options).hasSize(2);
        assertThat(options).allMatch(o -> !o.status().equals("ACTIVE"));
        PortalClassOptionResponse recommended = options.stream().filter(PortalClassOptionResponse::recommended).findFirst().orElseThrow();
        assertThat(recommended.classId()).isEqualTo(recentClass.id());
    }

    @Test
    void listClassOptions_rejectsStudentViewingAnotherStudent() {
        Student self = newStudent();
        Student other = newStudent();

        assertThatThrownBy(() -> portalClassSelectionService.listClassOptions(other.getId(), self.getUser().getId()))
                .isInstanceOf(NotAuthorizedForPortalAccessException.class);
    }

    @Test
    void listClassOptions_allowsLinkedParentAndRejectsUnlinkedParent() {
        Student student = newStudent();
        User linkedParentUser = newUser("parent.linked");
        assignRole(linkedParentUser, "PARENT");
        Parent linkedParent = new Parent();
        linkedParent.setUser(linkedParentUser);
        linkedParent = parentRepository.save(linkedParent);
        ParentStudent link = new ParentStudent();
        link.setParent(linkedParent);
        link.setStudent(student);
        link.setRelationship(ParentStudent.Relationship.MOTHER);
        parentStudentRepository.save(link);

        User unlinkedParentUser = newUser("parent.unlinked");
        assignRole(unlinkedParentUser, "PARENT");

        assertThat(portalClassSelectionService.listClassOptions(student.getId(), linkedParentUser.getId())).isEmpty();
        assertThatThrownBy(() -> portalClassSelectionService.listClassOptions(student.getId(), unlinkedParentUser.getId()))
                .isInstanceOf(NotAuthorizedForPortalAccessException.class);
    }

    @Test
    void listClassOptions_UC42_A3_parentSwitchingChildrenGetsIndependentResults() {
        Student childA = newStudent();
        Student childB = newStudent();
        ClassResponse classA = newOpenClass();
        classService.enroll(classA.id(), new EnrollStudentRequest(childA.getId(), LocalDate.now()), headAcademic.getId());
        User parentUser = newUser("parent.two.children");
        assignRole(parentUser, "PARENT");
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent = parentRepository.save(parent);
        linkParent(parent, childA, ParentStudent.Relationship.MOTHER);
        linkParent(parent, childB, ParentStudent.Relationship.MOTHER);

        List<PortalClassOptionResponse> forChildA = portalClassSelectionService.listClassOptions(childA.getId(), parentUser.getId());
        List<PortalClassOptionResponse> forChildB = portalClassSelectionService.listClassOptions(childB.getId(), parentUser.getId());

        assertThat(forChildA).hasSize(1);
        assertThat(forChildB).isEmpty();
    }

    private void linkParent(Parent parent, Student student, ParentStudent.Relationship relationship) {
        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationship(relationship);
        parentStudentRepository.save(link);
    }

    private ClassResponse newOpenClass() {
        return classService.create(
                new CreateClassRequest(classCode(), "Lớp test", site.getId(), activeCurriculum.id(), "OPEN",
                        20, null, LocalDate.now().minusYears(1), null, null),
                headAcademic.getId());
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

    private Student newStudent() {
        User user = newUser("student.portal");
        assignRole(user, "STUDENT");
        Student student = new Student();
        student.setUser(user);
        student.setStudentCode("HS-TEST-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(student);
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
