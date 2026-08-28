package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.*;
import vn.com.pps.education.dto.*;
import vn.com.pps.education.dto.UpsertEntranceAssessmentResultRequest.EntranceScoreInput;
import vn.com.pps.education.repository.*;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-18c: Đánh giá đầu vào & đề xuất xếp lớp — NHẬP điểm & kết quả (bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28). Xem
 * docs/uc/phan-he-06-hoc-thuat.md UC-18c.
 */
@Transactional
class EntranceAssessmentResultServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired private EntranceAssessmentSetupService setupService;
    @Autowired private EntranceAssessmentResultService resultService;
    @Autowired private SiteRepository siteRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private LeadSourceRepository leadSourceRepository;

    @Test
    void upsertResult_UC18c_MainFlow_createsThenUpdatesResultWithScores() {
        Ctx c = ctx();

        EntranceAssessmentResultResponse created = resultService.upsertResult(c.setupId,
                new UpsertEntranceAssessmentResultRequest(null, c.studentId, "Trần Thị D", LocalDate.now(),
                        new BigDecimal("7.0"), "Pre-Intermediate", c.recommendedClassId, "ổn",
                        List.of(new EntranceScoreInput(c.componentId, new BigDecimal("7.5"), false))),
                c.actorId);

        assertThat(created.id()).isNotNull();
        assertThat(created.studentId()).isEqualTo(c.studentId);
        assertThat(created.recommendedLevel()).isEqualTo("Pre-Intermediate");
        assertThat(created.recommendedClassId()).isEqualTo(c.recommendedClassId);
        assertThat(created.placedFlag()).isFalse();
        assertThat(created.scores()).singleElement().satisfies(s -> {
            assertThat(s.componentId()).isEqualTo(c.componentId);
            assertThat(s.score()).isEqualByComparingTo("7.5");
        });

        // Upsert lần 2 cho cùng thí sinh → cập nhật, không tạo bản ghi mới
        EntranceAssessmentResultResponse updated = resultService.upsertResult(c.setupId,
                new UpsertEntranceAssessmentResultRequest(null, c.studentId, "Trần Thị D", LocalDate.now(),
                        new BigDecimal("8.0"), "Intermediate", null, null,
                        List.of(new EntranceScoreInput(c.componentId, new BigDecimal("8.0"), false))),
                c.actorId);

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.overallScore()).isEqualByComparingTo("8.0");
        assertThat(updated.recommendedLevel()).isEqualTo("Intermediate");
        assertThat(resultService.listResults(c.setupId)).hasSize(1);
    }

    @Test
    void upsertResult_UC18c_MainFlow_acceptsLeadSubject() {
        Ctx c = ctx();
        Lead lead = newLead();

        EntranceAssessmentResultResponse created = resultService.upsertResult(c.setupId,
                new UpsertEntranceAssessmentResultRequest(lead.getId(), null, "Lê Văn E", LocalDate.now(),
                        new BigDecimal("6.0"), null, null, null, List.of()),
                c.actorId);

        assertThat(created.leadId()).isEqualTo(lead.getId());
        assertThat(created.studentId()).isNull();
    }

    @Test
    void upsertResult_UC18c_A1_rejectsWhenBothLeadAndStudent() {
        Ctx c = ctx();
        Lead lead = newLead();

        assertThatThrownBy(() -> resultService.upsertResult(c.setupId,
                new UpsertEntranceAssessmentResultRequest(lead.getId(), c.studentId, "Sai", LocalDate.now(),
                        null, null, null, null, List.of()),
                c.actorId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upsertResult_UC18c_A1_rejectsWhenNeitherLeadNorStudent() {
        Ctx c = ctx();

        assertThatThrownBy(() -> resultService.upsertResult(c.setupId,
                new UpsertEntranceAssessmentResultRequest(null, null, "Sai", LocalDate.now(),
                        null, null, null, null, List.of()),
                c.actorId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void upsertResult_UC18c_A2_rejectsScoreAboveComponentMax() {
        Ctx c = ctx();

        assertThatThrownBy(() -> resultService.upsertResult(c.setupId,
                new UpsertEntranceAssessmentResultRequest(null, c.studentId, "Quá điểm", LocalDate.now(),
                        null, null, null, null,
                        List.of(new EntranceScoreInput(c.componentId, new BigDecimal("11"), false))),
                c.actorId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markPlaced_UC18c_MainFlow_setsPlacedFlag() {
        Ctx c = ctx();
        EntranceAssessmentResultResponse created = resultService.upsertResult(c.setupId,
                new UpsertEntranceAssessmentResultRequest(null, c.studentId, "Đã xếp lớp", LocalDate.now(),
                        new BigDecimal("9"), null, null, null, List.of()),
                c.actorId);

        EntranceAssessmentResultResponse placed = resultService.markPlaced(created.id());

        assertThat(placed.placedFlag()).isTrue();
    }

    // ===================== Fixtures =====================

    private record Ctx(Long setupId, Long componentId, Long studentId, Long recommendedClassId, Long actorId) {}

    private Ctx ctx() {
        Site site = newSite();
        AcademicYear year = newAcademicYear();
        User actor = newUser("head.entrance.res");
        EntranceAssessmentSetupResponse setup = setupService.createSetup(
                new CreateEntranceAssessmentSetupRequest(site.getId(), year.getId(), "Đề " + SEQ.incrementAndGet(), "POINT_10"),
                actor.getId());
        EntranceAssessmentComponentResponse component = setupService.addComponent(setup.id(),
                new CreateEntranceAssessmentComponentRequest("GRAMMAR", "Ngữ pháp", new BigDecimal("10.00"), null, 1));
        Student student = newStudent();
        return new Ctx(setup.id(), component.id(), student.getId(), null, actor.getId());
    }

    private Site newSite() {
        Site site = new Site();
        site.setCode("SITE-EAR-" + SEQ.incrementAndGet());
        site.setName("Test Site EAR");
        site.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(site);
    }

    private AcademicYear newAcademicYear() {
        AcademicYear year = new AcademicYear();
        year.setCode("AY-EAR-" + SEQ.incrementAndGet());
        year.setName("Năm học test");
        return academicYearRepository.save(year);
    }

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }

    private Student newStudent() {
        Student student = new Student();
        student.setUser(newUser("student.ear"));
        student.setStudentCode("HS-EAR-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setStatus(Student.Status.ACTIVE);
        student.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(student);
    }

    private Lead newLead() {
        LeadSource source = new LeadSource();
        source.setCode("LS-EAR-" + SEQ.incrementAndGet());
        source.setName("Nguồn test");
        source.setChannelType(LeadSource.ChannelType.OTHER);
        source = leadSourceRepository.save(source);

        Lead lead = new Lead();
        lead.setLeadCode("LEAD-EAR-" + SEQ.incrementAndGet());
        lead.setFullName("Phụ huynh test");
        lead.setPhone("0900000000");
        lead.setLeadSource(source);
        return leadRepository.save(lead);
    }
}
