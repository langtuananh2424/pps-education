package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.*;
import vn.com.pps.education.dto.*;
import vn.com.pps.education.dto.UpsertEntranceAssessmentResultRequest.EntranceScoreInput;
import vn.com.pps.education.exception.EntranceAssessmentNotDeletableException;
import vn.com.pps.education.exception.EntranceComponentLockedException;
import vn.com.pps.education.repository.*;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-18c: Đánh giá đầu vào & đề xuất xếp lớp — CẤU HÌNH bộ đề (bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28). Xem
 * docs/uc/phan-he-06-hoc-thuat.md UC-18c.
 */
@Transactional
class EntranceAssessmentSetupServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired private EntranceAssessmentSetupService setupService;
    @Autowired private EntranceAssessmentResultService resultService;
    @Autowired private SiteRepository siteRepository;
    @Autowired private AcademicYearRepository academicYearRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;

    @Test
    void createSetup_UC18c_MainFlow_persistsSetup() {
        Fixture f = fixture();

        EntranceAssessmentSetupResponse setup = setupService.createSetup(
                new CreateEntranceAssessmentSetupRequest(f.site.getId(), f.year.getId(), "Đề đầu vào THCS", "POINT_10"),
                f.actor.getId());

        assertThat(setup.id()).isNotNull();
        assertThat(setup.siteId()).isEqualTo(f.site.getId());
        assertThat(setup.academicYearId()).isEqualTo(f.year.getId());
        assertThat(setup.scaleType()).isEqualTo("POINT_10");
        assertThat(setup.components()).isEmpty();
    }

    @Test
    void addComponent_UC18c_MainFlow_addsComponent() {
        Fixture f = fixture();
        EntranceAssessmentSetupResponse setup = setupService.createSetup(
                new CreateEntranceAssessmentSetupRequest(f.site.getId(), f.year.getId(), "Đề " + SEQ.incrementAndGet(), "POINT_10"),
                f.actor.getId());

        EntranceAssessmentComponentResponse component = setupService.addComponent(setup.id(),
                new CreateEntranceAssessmentComponentRequest("SPEAKING", "Nói", new BigDecimal("10.00"), null, 1));

        assertThat(component.id()).isNotNull();
        assertThat(component.code()).isEqualTo("SPEAKING");
        assertThat(setupService.getSetup(setup.id()).components()).hasSize(1);
    }

    @Test
    void updateComponent_UC18c_A3_rejectsMaxScoreChangeWhenScoresExist() {
        Fixture f = fixture();
        EntranceAssessmentSetupResponse setup = setupService.createSetup(
                new CreateEntranceAssessmentSetupRequest(f.site.getId(), f.year.getId(), "Đề " + SEQ.incrementAndGet(), "POINT_10"),
                f.actor.getId());
        EntranceAssessmentComponentResponse component = setupService.addComponent(setup.id(),
                new CreateEntranceAssessmentComponentRequest("READING", "Đọc", new BigDecimal("10.00"), null, 1));
        Student student = newStudent();
        resultService.upsertResult(setup.id(), new UpsertEntranceAssessmentResultRequest(
                null, student.getId(), "Nguyễn Văn A", LocalDate.now(), null, null, null, null,
                List.of(new EntranceScoreInput(component.id(), new BigDecimal("7.5"), false))), f.actor.getId());

        assertThatThrownBy(() -> setupService.updateComponent(component.id(),
                new UpdateEntranceAssessmentComponentRequest("Đọc", new BigDecimal("20.00"), null, 1)))
                .isInstanceOf(EntranceComponentLockedException.class);
    }

    @Test
    void deleteComponent_UC18c_A4_rejectsWhenScoresExist() {
        Fixture f = fixture();
        EntranceAssessmentSetupResponse setup = setupService.createSetup(
                new CreateEntranceAssessmentSetupRequest(f.site.getId(), f.year.getId(), "Đề " + SEQ.incrementAndGet(), "POINT_10"),
                f.actor.getId());
        EntranceAssessmentComponentResponse component = setupService.addComponent(setup.id(),
                new CreateEntranceAssessmentComponentRequest("WRITING", "Viết", new BigDecimal("10.00"), null, 1));
        Student student = newStudent();
        resultService.upsertResult(setup.id(), new UpsertEntranceAssessmentResultRequest(
                null, student.getId(), "Nguyễn Văn B", LocalDate.now(), null, null, null, null,
                List.of(new EntranceScoreInput(component.id(), new BigDecimal("6"), false))), f.actor.getId());

        assertThatThrownBy(() -> setupService.deleteComponent(component.id()))
                .isInstanceOf(EntranceAssessmentNotDeletableException.class);
    }

    @Test
    void deleteSetup_UC18c_A4_rejectsWhenResultsExist() {
        Fixture f = fixture();
        EntranceAssessmentSetupResponse setup = setupService.createSetup(
                new CreateEntranceAssessmentSetupRequest(f.site.getId(), f.year.getId(), "Đề " + SEQ.incrementAndGet(), "POINT_10"),
                f.actor.getId());
        setupService.addComponent(setup.id(),
                new CreateEntranceAssessmentComponentRequest("LISTENING", "Nghe", new BigDecimal("10.00"), null, 1));
        Student student = newStudent();
        resultService.upsertResult(setup.id(), new UpsertEntranceAssessmentResultRequest(
                null, student.getId(), "Nguyễn Văn C", LocalDate.now(), new BigDecimal("8"), null, null, null,
                List.of()), f.actor.getId());

        assertThatThrownBy(() -> setupService.deleteSetup(setup.id()))
                .isInstanceOf(EntranceAssessmentNotDeletableException.class);
    }

    @Test
    void createSetup_UC18c_A_rejectsInvalidScaleType() {
        Fixture f = fixture();
        assertThatThrownBy(() -> setupService.createSetup(
                new CreateEntranceAssessmentSetupRequest(f.site.getId(), f.year.getId(), "Đề sai thang", "BAND_9"),
                f.actor.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===================== Fixtures =====================

    private record Fixture(Site site, AcademicYear year, User actor) {}

    private Fixture fixture() {
        return new Fixture(newSite(), newAcademicYear(), newUser("head.entrance"));
    }

    private Site newSite() {
        Site site = new Site();
        site.setCode("SITE-EA-" + SEQ.incrementAndGet());
        site.setName("Test Site EA");
        site.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(site);
    }

    private AcademicYear newAcademicYear() {
        AcademicYear year = new AcademicYear();
        year.setCode("AY-EA-" + SEQ.incrementAndGet());
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
        student.setUser(newUser("student.ea"));
        student.setStudentCode("HS-EA-" + SEQ.incrementAndGet());
        student.setDateOfBirth(LocalDate.of(2012, 5, 1));
        student.setStatus(Student.Status.ACTIVE);
        student.setEnrollmentDate(LocalDate.now());
        return studentRepository.save(student);
    }
}
