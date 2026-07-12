package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AddLessonMaterialRequest;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateLessonRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.LessonMaterialResponse;
import vn.com.pps.education.dto.LessonResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateLessonRequest;
import vn.com.pps.education.exception.InvalidLessonScopeException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC-23: Quản lý bài giảng — Main Flow, A1 (không áp dụng ở tầng Service — upload CDN nằm ngoài phạm vi). */
@Transactional
class LessonServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private LessonService lessonService;

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
    void createLesson_UC23_MainFlow_savesClassScopedLessonAsDraft() {
        LessonResponse lesson = lessonService.createLesson(
                new CreateLessonRequest(lessonCode(), "Bài 1: Chào hỏi", null, schoolClass.id(), null, 1, "VIDEO_LECTURE", 15),
                teacher.getId());

        assertThat(lesson.status()).isEqualTo("DRAFT");
        assertThat(lesson.classId()).isEqualTo(schoolClass.id());
        assertThat(lesson.curriculumId()).isNull();
    }

    @Test
    void createLesson_UC23_MainFlow_savesCurriculumScopedSharedLesson() {
        LessonResponse lesson = lessonService.createLesson(
                new CreateLessonRequest(lessonCode(), "Bài chung: Ngữ pháp", activeCurriculum.id(), null, null, 1, "PDF_DOCUMENT", null),
                teacher.getId());

        assertThat(lesson.curriculumId()).isEqualTo(activeCurriculum.id());
        assertThat(lesson.classId()).isNull();
    }

    @Test
    void createLesson_rejectsWhenBothScopesGiven() {
        assertThatThrownBy(() -> lessonService.createLesson(
                new CreateLessonRequest(lessonCode(), "X", activeCurriculum.id(), schoolClass.id(), null, null, "VIDEO_LECTURE", null),
                teacher.getId()))
                .isInstanceOf(InvalidLessonScopeException.class);
    }

    @Test
    void createLesson_rejectsWhenNoScopeGiven() {
        assertThatThrownBy(() -> lessonService.createLesson(
                new CreateLessonRequest(lessonCode(), "X", null, null, null, null, "VIDEO_LECTURE", null),
                teacher.getId()))
                .isInstanceOf(InvalidLessonScopeException.class);
    }

    @Test
    void createLesson_rejectsWhenActorNotAssignedTeacherForClass() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> lessonService.createLesson(
                new CreateLessonRequest(lessonCode(), "X", null, schoolClass.id(), null, null, "VIDEO_LECTURE", null),
                outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void createLesson_rejectsWhenActorNotAssignedTeacherForCurriculum() {
        User outsider = newUser("outsider.teacher");
        assignRole(outsider, "TEACHER");

        assertThatThrownBy(() -> lessonService.createLesson(
                new CreateLessonRequest(lessonCode(), "X", activeCurriculum.id(), null, null, null, "PDF_DOCUMENT", null),
                outsider.getId()))
                .isInstanceOf(NotAssignedTeacherForClassException.class);
    }

    @Test
    void updateLesson_UC23_MainFlow_publishingSetsPublishedAt() {
        LessonResponse lesson = lessonService.createLesson(
                new CreateLessonRequest(lessonCode(), "Bài 1", null, schoolClass.id(), null, 1, "VIDEO_LECTURE", 15),
                teacher.getId());

        LessonResponse published = lessonService.updateLesson(lesson.id(),
                new UpdateLessonRequest("Bài 1 - đã cập nhật", null, 1, 20, "PUBLISHED"), teacher.getId());

        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.publishedAt()).isNotNull();
        assertThat(published.durationMinutes()).isEqualTo(20);
    }

    @Test
    void updateLesson_UC23_MainFlow_archivingRemovesFromKho() {
        LessonResponse lesson = lessonService.createLesson(
                new CreateLessonRequest(lessonCode(), "Bài 1", null, schoolClass.id(), null, 1, "VIDEO_LECTURE", 15),
                teacher.getId());

        LessonResponse archived = lessonService.updateLesson(lesson.id(),
                new UpdateLessonRequest("Bài 1", null, 1, 15, "ARCHIVED"), teacher.getId());

        assertThat(archived.status()).isEqualTo("ARCHIVED");
    }

    @Test
    void addMaterial_UC23_MainFlow_attachesCdnFileToLesson() {
        LessonResponse lesson = lessonService.createLesson(
                new CreateLessonRequest(lessonCode(), "Bài 1", null, schoolClass.id(), null, 1, "VIDEO_LECTURE", 15),
                teacher.getId());

        LessonMaterialResponse material = lessonService.addMaterial(lesson.id(),
                new AddLessonMaterialRequest("VIDEO", "Video bài 1", "https://cdn.pps.edu.vn/lessons/1/video.mp4",
                        10_000_000L, 900, 1, true),
                teacher.getId());

        assertThat(material.fileUrl()).isEqualTo("https://cdn.pps.edu.vn/lessons/1/video.mp4");
        List<LessonMaterialResponse> materials = lessonService.listMaterials(lesson.id());
        assertThat(materials).extracting(LessonMaterialResponse::id).contains(material.id());
    }

    private String curriculumCode() {
        return "CUR-" + SEQ.incrementAndGet();
    }

    private String classCode() {
        return "CLS-" + SEQ.incrementAndGet();
    }

    private String lessonCode() {
        return "LES-" + SEQ.incrementAndGet();
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
