package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.AcademicYear;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AcademicTermResponse;
import vn.com.pps.education.dto.CreateAcademicTermRequest;
import vn.com.pps.education.dto.UpdateAcademicTermRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.AcademicYearRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-18 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31) —
 * "Giai đoạn/Học kỳ" giới hạn theo điểm trường, độc lập với lớp học. Xem
 * docs/uc/phan-he-06-hoc-thuat.md.
 *
 * <p>V157 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28):
 * mỗi kỳ học bắt buộc thuộc 1 năm học; khoảng thời gian kỳ phải nằm trong
 * năm học khi năm học đã khai báo đủ ngày.
 */
@Transactional
class AcademicTermServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private AcademicTermService academicTermService;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void create_boSung_MainFlow_persistsTermForSite() {
        Site site = newSite();
        AcademicYear year = newAcademicYear(null, null);
        User actor = newUser("head.academic");

        AcademicTermResponse term = academicTermService.create(
                new CreateAcademicTermRequest(site.getId(), year.getId(), "GK1-2627", "Giữa kỳ 1 (2026-2027)",
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31)),
                actor.getId());

        assertThat(term.id()).isNotNull();
        assertThat(term.siteId()).isEqualTo(site.getId());
        assertThat(term.academicYearId()).isEqualTo(year.getId());
        assertThat(term.academicYearCode()).isEqualTo(year.getCode());
        assertThat(term.code()).isEqualTo("GK1-2627");
        assertThat(academicTermService.listBySite(site.getId())).containsExactly(term);
    }

    @Test
    void create_boSung_A1_rejectsWhenEndDateBeforeStartDate() {
        Site site = newSite();
        AcademicYear year = newAcademicYear(null, null);
        User actor = newUser("head.academic2");

        assertThatThrownBy(() -> academicTermService.create(
                new CreateAcademicTermRequest(site.getId(), year.getId(), "CK1-2627", "Cuối kỳ 1",
                        LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 1)),
                actor.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_V157_rejectsWhenAcademicYearNotFound() {
        Site site = newSite();
        User actor = newUser("head.academic.v157a");

        assertThatThrownBy(() -> academicTermService.create(
                new CreateAcademicTermRequest(site.getId(), 999_999_999L, "GK1-X", "Kỳ thiếu năm học",
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31)),
                actor.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_V157_rejectsWhenTermOutsideAcademicYearRange() {
        Site site = newSite();
        AcademicYear year = newAcademicYear(LocalDate.of(2026, 8, 1), LocalDate.of(2027, 6, 30));
        User actor = newUser("head.academic.v157b");

        assertThatThrownBy(() -> academicTermService.create(
                new CreateAcademicTermRequest(site.getId(), year.getId(), "GK1-OOR", "Kỳ vượt năm học",
                        LocalDate.of(2027, 6, 1), LocalDate.of(2027, 7, 15)),
                actor.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_boSung_MainFlow_updatesNameDatesAndYearKeepsCodeAndSite() {
        Site site = newSite();
        AcademicYear year = newAcademicYear(null, null);
        AcademicYear otherYear = newAcademicYear(null, null);
        User actor = newUser("head.academic3");
        AcademicTermResponse created = academicTermService.create(
                new CreateAcademicTermRequest(site.getId(), year.getId(), "GK2-2627", "Giữa kỳ 2 (nháp)",
                        LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 31)),
                actor.getId());

        AcademicTermResponse updated = academicTermService.update(created.id(),
                new UpdateAcademicTermRequest(otherYear.getId(), "Giữa kỳ 2 (2026-2027)",
                        LocalDate.of(2027, 2, 15), LocalDate.of(2027, 4, 15)),
                actor.getId());

        assertThat(updated.name()).isEqualTo("Giữa kỳ 2 (2026-2027)");
        assertThat(updated.startDate()).isEqualTo(LocalDate.of(2027, 2, 15));
        assertThat(updated.endDate()).isEqualTo(LocalDate.of(2027, 4, 15));
        assertThat(updated.code()).isEqualTo("GK2-2627");
        assertThat(updated.siteId()).isEqualTo(site.getId());
        assertThat(updated.academicYearId()).isEqualTo(otherYear.getId());
    }

    @Test
    void listBySite_boSung_returnsOnlyTermsOfThatSite() {
        Site siteA = newSite();
        Site siteB = newSite();
        AcademicYear year = newAcademicYear(null, null);
        User actor = newUser("head.academic4");
        AcademicTermResponse termA = academicTermService.create(
                new CreateAcademicTermRequest(siteA.getId(), year.getId(), "T-A", "Kỳ site A",
                        LocalDate.now(), LocalDate.now().plusMonths(1)),
                actor.getId());
        academicTermService.create(
                new CreateAcademicTermRequest(siteB.getId(), year.getId(), "T-B", "Kỳ site B",
                        LocalDate.now(), LocalDate.now().plusMonths(1)),
                actor.getId());

        assertThat(academicTermService.listBySite(siteA.getId())).containsExactly(termA);
    }

    private Site newSite() {
        Site site = new Site();
        site.setCode("SITE-TERM-" + SEQ.incrementAndGet());
        site.setName("Test Site Term");
        site.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(site);
    }

    private AcademicYear newAcademicYear(LocalDate start, LocalDate end) {
        AcademicYear year = new AcademicYear();
        year.setCode("AY-TERM-" + SEQ.incrementAndGet());
        year.setName("Năm học test");
        year.setStartDate(start);
        year.setEndDate(end);
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
}
