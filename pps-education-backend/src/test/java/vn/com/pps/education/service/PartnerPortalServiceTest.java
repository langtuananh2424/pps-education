package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicTerm;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.SitePeriodTemplate;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.AssignTeacherRequest;
import vn.com.pps.education.dto.ClassResponse;
import vn.com.pps.education.dto.CreateAcademicYearRequest;
import vn.com.pps.education.dto.ClassSessionResponse;
import vn.com.pps.education.dto.CreateClassRequest;
import vn.com.pps.education.dto.CreateClassSessionRequest;
import vn.com.pps.education.dto.CreateCurriculumRequest;
import vn.com.pps.education.dto.CreateGradeComponentSetupRequest;
import vn.com.pps.education.dto.CreateGradeEvaluationComponentRequest;
import vn.com.pps.education.dto.CreateStudentCommentRequest;
import vn.com.pps.education.dto.CreateTeachingPlanRequest;
import vn.com.pps.education.dto.CurriculumResponse;
import vn.com.pps.education.dto.DecideCommentsRequest;
import vn.com.pps.education.dto.EnrollStudentRequest;
import vn.com.pps.education.dto.EnterAttendanceMarkRequest;
import vn.com.pps.education.dto.EnterGradeRequest;
import vn.com.pps.education.dto.GradeComponentSetupResponse;
import vn.com.pps.education.dto.GradeEntryResponse;
import vn.com.pps.education.dto.GradeEvaluationComponentResponse;
import vn.com.pps.education.dto.MarkAttendanceRequest;
import vn.com.pps.education.dto.PartnerAttendanceSummaryResponse;
import vn.com.pps.education.dto.PartnerSiteResponse;
import vn.com.pps.education.dto.PublishGradesRequest;
import vn.com.pps.education.dto.SubmitGradesRequest;
import vn.com.pps.education.dto.StudentCommentResponse;
import vn.com.pps.education.dto.SubmitCommentsRequest;
import vn.com.pps.education.dto.TeachingPlanResponse;
import vn.com.pps.education.dto.UpdateCurriculumRequest;
import vn.com.pps.education.dto.UpdateTeachingPlanRequest;
import vn.com.pps.education.exception.NotAuthorizedForPortalAccessException;
import vn.com.pps.education.repository.AcademicTermRepository;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC-29: Xem báo cáo Portal trường liên kết — Main Flow (bước 1-2), A1 (chưa có dữ liệu). */
@Transactional
class PartnerPortalServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private PartnerPortalService partnerPortalService;

    @Autowired
    private ClassService classService;

    @Autowired
    private ClassSessionService classSessionService;

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private GradeService gradeService;

    @Autowired
    private StudentAttendanceService studentAttendanceService;

    @Autowired
    private TeachingPlanService teachingPlanService;

    @Autowired
    private AcademicYearService academicYearService;

    @Autowired
    private StudentCommentService studentCommentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private vn.com.pps.education.repository.SitePeriodTemplateRepository sitePeriodTemplateRepository;

    @Autowired
    private SiteManagerRepository siteManagerRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AcademicTermRepository academicTermRepository;

    private User headAcademic;
    private User teacher;
    private User siteManagerUser;
    private User partnerRepUser;
    private Site partnerSite;
    private ClassResponse schoolClass;
    private Student student;
    private CurriculumResponse activeCurriculum;

    @BeforeEach
    void setUp() {
        headAcademic = newUser("head.academic");
        assignRole(headAcademic, "HEAD_ACADEMIC");
        CurriculumResponse curriculum = curriculumService.create(
                new CreateCurriculumRequest(curriculumCode(), "Chuẩn", "MAIN", null, null, null, null, null), headAcademic.getId());
        activeCurriculum = curriculumService.update(curriculum.id(),
                new UpdateCurriculumRequest("Chuẩn", null, null, null, null, null, "ACTIVE", false), headAcademic.getId());

        partnerSite = newSite(Site.SiteType.PARTNER);
        schoolClass = classService.create(
                new CreateClassRequest(classCode(), "8A2", partnerSite.getId(), activeCurriculum.id(), "LINKED", 20, null,
                        LocalDate.now(), null, null), headAcademic.getId());

        teacher = newUser("teacher");
        assignRole(teacher, "TEACHER");
        classService.assignTeacher(schoolClass.id(),
                new AssignTeacherRequest(teacher.getId(), "PRIMARY", null, LocalDate.now(), "VIETNAMESE"), headAcademic.getId());

        siteManagerUser = newUser("site.manager");
        assignRole(siteManagerUser, "SITE_MANAGER");
        assignSiteManager(partnerSite, siteManagerUser, SiteManager.RoleType.SITE_MANAGER);

        partnerRepUser = newUser("partner.rep");
        assignRole(partnerRepUser, "PARTNER_REP");
        assignSiteManager(partnerSite, partnerRepUser, SiteManager.RoleType.PARTNER_REP);

        student = newStudent();
        classService.enroll(schoolClass.id(), new EnrollStudentRequest(student.getId(), LocalDate.now()), headAcademic.getId());
    }

    @Test
    void getMySite_UC29_MainFlow_returnsAssignedPartnerSite() {
        PartnerSiteResponse site = partnerPortalService.getMySite(partnerRepUser.getId());

        assertThat(site.siteId()).isEqualTo(partnerSite.getId());
    }

    @Test
    void getMySite_rejectsWhenActorNotAssignedToPartnerSite() {
        User outsider = newUser("outsider.rep");
        assignRole(outsider, "PARTNER_REP");

        assertThatThrownBy(() -> partnerPortalService.getMySite(outsider.getId()))
                .isInstanceOf(NotAuthorizedForPortalAccessException.class);
    }

    @Test
    void getMySite_rejectsWhenAssignedSiteIsOwnedNotPartner() {
        Site ownedSite = newSite(Site.SiteType.OWNED);
        User outsider = newUser("owned.rep");
        assignRole(outsider, "PARTNER_REP");
        assignSiteManager(ownedSite, outsider, SiteManager.RoleType.PARTNER_REP);

        assertThatThrownBy(() -> partnerPortalService.getMySite(outsider.getId()))
                .isInstanceOf(NotAuthorizedForPortalAccessException.class);
    }

    @Test
    void getAttendanceSummary_UC29_MainFlow_computesAttendanceRate() {
        // Cửa sổ bao quanh NGAY LÚC NÀY (không phải giờ cố định 08:00-09:40) -- bổ sung 2026-08-18:
        // StudentAttendanceService.markAttendance() giờ chặn cả ở API khi ngoài khung giờ buổi học
        // [startTime, endTime] (xem UC-15, sửa đổi nghiệp vụ 2026-08-18), giờ cố định làm test flaky
        // theo giờ chạy CI.
        seedPeriod(partnerSite, 1, LocalTime.now().minusMinutes(1), LocalTime.now().plusHours(1));
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now(), "MORNING", List.of(1), null, "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null),
                headAcademic.getId());
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(
                        new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());

        List<PartnerAttendanceSummaryResponse> summary = partnerPortalService.getAttendanceSummary(partnerRepUser.getId());

        assertThat(summary).hasSize(1);
        assertThat(summary.get(0).studentId()).isEqualTo(student.getId());
        assertThat(summary.get(0).presentCount()).isEqualTo(1);
        assertThat(summary.get(0).totalMarks()).isEqualTo(1);
        assertThat(summary.get(0).attendanceRatePercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void getAttendanceSummary_UC29_A1_emptyWhenNoAttendanceDataYet() {
        List<PartnerAttendanceSummaryResponse> summary = partnerPortalService.getAttendanceSummary(partnerRepUser.getId());

        assertThat(summary).isEmpty();
    }

    @Test
    void getPublishedGrades_UC29_MainFlow_returnsOnlyOfficialGrades() {
        AcademicTerm academicTerm = newAcademicTerm(partnerSite);
        GradeComponentSetupResponse setup = gradeService.createGradeComponentSetup(schoolClass.id(),
                new CreateGradeComponentSetupRequest(academicTerm.getId(), "MID_TERM", "POINT_10", LocalDate.now(), false),
                headAcademic.getId());
        GradeEvaluationComponentResponse component = gradeService.addGradeEvaluationComponent(setup.id(),
                new CreateGradeEvaluationComponentRequest(null, null, "SPEAKING", "Nói", new BigDecimal("10.00"), null, null, 1),
                headAcademic.getId());
        GradeEntryResponse entry = gradeService.enterGrade(schoolClass.id(), component.id(),
                new EnterGradeRequest(student.getId(), new BigDecimal("9"), false, null), teacher.getId());
        gradeService.submitGradesForApproval(new SubmitGradesRequest(List.of(entry.id()), null), teacher.getId());
        gradeService.publishGrades(new PublishGradesRequest("APPROVE", List.of(entry.id()), null, null, null, null), siteManagerUser.getId());

        List<GradeEntryResponse> grades = partnerPortalService.getPublishedGrades(partnerRepUser.getId());

        assertThat(grades).hasSize(1);
        assertThat(grades.get(0).status()).isEqualTo("OFFICIAL");
    }

    @Test
    void getTeachingPlans_UC29_MainFlow_returnsPublishedVisiblePlans() {
        Long academicYearId = academicYearService.create(
                new CreateAcademicYearRequest("AY-" + SEQ.incrementAndGet(), "2026-2027", null, null), headAcademic.getId()).id();
        Long nextAcademicYearId = academicYearService.create(
                new CreateAcademicYearRequest("AY-" + SEQ.incrementAndGet(), "2027-2028", null, null), headAcademic.getId()).id();
        TeachingPlanResponse plan = teachingPlanService.createPlan(
                new CreateTeachingPlanRequest(schoolClass.id(), "YEARLY", academicYearId, null, null, null, "Kế hoạch năm", "Mục tiêu", true),
                teacher.getId());
        teachingPlanService.updatePlan(plan.id(),
                new UpdateTeachingPlanRequest("Kế hoạch năm", "Mục tiêu", "PUBLISHED", true), teacher.getId());

        // Ke hoach khac chua publish -- khong duoc hien thi.
        teachingPlanService.createPlan(
                new CreateTeachingPlanRequest(schoolClass.id(), "YEARLY", nextAcademicYearId, null, null, null, "Nam sau", null, true),
                teacher.getId());

        List<TeachingPlanResponse> plans = partnerPortalService.getTeachingPlans(partnerRepUser.getId());

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).status()).isEqualTo("PUBLISHED");
    }

    @Test
    void getApprovedComments_UC29_MainFlow_returnsOnlyApprovedComments() {
        // Cửa sổ bao quanh NGAY LÚC NÀY (không phải giờ cố định, không dùng LocalTime.MIN) -- bổ
        // sung 2026-08-14, SỬA LẠI 2026-08-18: trước đây đặt buổi ĐÃ KẾT THÚC (now-1h..now-1min) vì
        // requireSessionEndedAndAttendanceTaken đòi buổi đã kết thúc + đã điểm danh xong -- điều kiện
        // đó đã bị BỎ HẲN 2026-08-18 (xem docs/uc/phan-he-06-hoc-thuat.md UC-21), nhưng đồng thời
        // StudentAttendanceService.markAttendance() giờ lại đòi buổi đang TRONG khung giờ diễn ra
        // (UC-15, sửa đổi nghiệp vụ 2026-08-18) nên buổi phải bao quanh "now" thay vì đã kết thúc.
        // LocalTime.MIN vẫn không dùng được vì bị hibernate.jdbc.time_zone=UTC quy đổi lệch, vi phạm
        // CHECK chk_session_time.
        seedPeriod(partnerSite, 1, LocalTime.now().minusMinutes(1), LocalTime.now().plusHours(1));
        seedPeriod(partnerSite, 2, LocalTime.of(8, 0), LocalTime.of(9, 40));
        ClassSessionResponse session = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now(), "MORNING", List.of(1), null, "REGULAR", "VIETNAMESE",
                        teacher.getId(), null, null, null),
                headAcademic.getId());
        studentCommentService.updateLessonContent(session.id(), "Unit 1: Present simple tense.", teacher.getId());
        studentAttendanceService.markAttendance(session.id(),
                new MarkAttendanceRequest("SESSION_LEVEL", List.of(new EnterAttendanceMarkRequest(student.getId(), "PRESENT", null, null, null))),
                teacher.getId());
        studentAttendanceService.submitAttendance(session.id(), teacher.getId());
        StudentCommentResponse approvedComment = studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), session.id(),
                        LocalDate.now(), "Chăm chỉ, tiến bộ rõ rệt.", null, "POSITIVE", false, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
                teacher.getId());
        studentCommentService.submitComments(schoolClass.id(), new SubmitCommentsRequest(List.of(approvedComment.id())), teacher.getId());
        studentCommentService.decideComments(
                new DecideCommentsRequest(List.of(approvedComment.id()), "APPROVED", "Tốt"), siteManagerUser.getId());

        // Nhan xet con DRAFT (chua submit/duyet) -- khong duoc hien thi cho Doi tac. Phải ở 1 buổi
        // KHÁC session gốc — từ 2026-08-19 writeComment chặn tạo thêm nhận xét thứ 2 cho cùng 1 buổi
        // đã APPROVED (StudentCommentNotEditableException, xem StudentCommentService#writeComment).
        ClassSessionResponse otherSession = classSessionService.createSession(schoolClass.id(),
                new CreateClassSessionRequest(LocalDate.now().plusDays(1), "MORNING", List.of(2), null, "REGULAR", "VIETNAMESE",
                        headAcademic.getId(), null, null, null),
                headAcademic.getId());
        studentCommentService.writeComment(schoolClass.id(),
                new CreateStudentCommentRequest(student.getId(), otherSession.id(),
                        LocalDate.now(), "Nhận xét nháp chưa gửi duyệt.", null, "NORMAL", false, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
                teacher.getId());

        List<StudentCommentResponse> comments = partnerPortalService.getApprovedComments(partnerRepUser.getId());

        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).status()).isEqualTo("APPROVED");
        assertThat(comments.get(0).studentId()).isEqualTo(student.getId());
    }

    @Test
    void getApprovedComments_UC29_A1_emptyWhenNoApprovedCommentsYet() {
        List<StudentCommentResponse> comments = partnerPortalService.getApprovedComments(partnerRepUser.getId());

        assertThat(comments).isEmpty();
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

    private void assignSiteManager(Site site, User user, SiteManager.RoleType roleType) {
        SiteManager siteManager = new SiteManager();
        siteManager.setSite(site);
        siteManager.setUser(user);
        siteManager.setRoleType(roleType);
        siteManager.setAssignedFrom(LocalDate.now().minusMonths(1));
        siteManager.setAssignedBy(user);
        siteManagerRepository.save(siteManager);
    }

    private Site newSite(Site.SiteType siteType) {
        Site s = new Site();
        s.setCode("SITE-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(siteType);
        return siteRepository.save(s);
    }

    /** Bổ sung ngoài SDD gốc, xác nhận 2026-08-19 — session_periods giờ sinh từ site_period_templates thay vì chia đều theo phút. */
    private void seedPeriod(Site site, int periodNumber, LocalTime start, LocalTime end) {
        SitePeriodTemplate template = new SitePeriodTemplate();
        template.setSite(site);
        template.setPeriodNumber(periodNumber);
        template.setDayPart(SitePeriodTemplate.DayPart.MORNING);
        template.setStartTime(start);
        template.setEndTime(end);
        template.setCreatedBy(headAcademic);
        sitePeriodTemplateRepository.save(template);
    }

    private AcademicTerm newAcademicTerm(Site site) {
        AcademicTerm term = new AcademicTerm();
        term.setSite(site);
        term.setCode("TERM-" + SEQ.incrementAndGet());
        term.setName("Kỳ test");
        term.setStartDate(LocalDate.now().minusMonths(1));
        term.setEndDate(LocalDate.now().plusMonths(2));
        term.setCreatedBy(headAcademic);
        return academicTermRepository.save(term);
    }

    private Student newStudent() {
        User user = newUser("student");
        Student s = new Student();
        s.setUser(user);
        s.setStudentCode("HS-TEST-" + SEQ.incrementAndGet());
        s.setDateOfBirth(LocalDate.of(2012, 5, 1));
        s.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(s);
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
