package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AcademicTermResponse;
import vn.com.pps.education.dto.CreateAcademicTermRequest;
import vn.com.pps.education.dto.UpdateAcademicTermRequest;
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
 */
@Transactional
class AcademicTermServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private AcademicTermService academicTermService;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void create_boSung_MainFlow_persistsTermForSite() {
        Site site = newSite();
        User actor = newUser("head.academic");

        AcademicTermResponse term = academicTermService.create(
                new CreateAcademicTermRequest(site.getId(), "GK1-2627", "Giữa kỳ 1 (2026-2027)",
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31)),
                actor.getId());

        assertThat(term.id()).isNotNull();
        assertThat(term.siteId()).isEqualTo(site.getId());
        assertThat(term.code()).isEqualTo("GK1-2627");
        assertThat(academicTermService.listBySite(site.getId())).containsExactly(term);
    }

    @Test
    void create_boSung_A1_rejectsWhenEndDateBeforeStartDate() {
        Site site = newSite();
        User actor = newUser("head.academic2");

        assertThatThrownBy(() -> academicTermService.create(
                new CreateAcademicTermRequest(site.getId(), "CK1-2627", "Cuối kỳ 1",
                        LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 1)),
                actor.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_boSung_MainFlow_updatesNameAndDatesKeepsCodeAndSite() {
        Site site = newSite();
        User actor = newUser("head.academic3");
        AcademicTermResponse created = academicTermService.create(
                new CreateAcademicTermRequest(site.getId(), "GK2-2627", "Giữa kỳ 2 (nháp)",
                        LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 31)),
                actor.getId());

        AcademicTermResponse updated = academicTermService.update(created.id(),
                new UpdateAcademicTermRequest("Giữa kỳ 2 (2026-2027)", LocalDate.of(2027, 2, 15), LocalDate.of(2027, 4, 15)),
                actor.getId());

        assertThat(updated.name()).isEqualTo("Giữa kỳ 2 (2026-2027)");
        assertThat(updated.startDate()).isEqualTo(LocalDate.of(2027, 2, 15));
        assertThat(updated.endDate()).isEqualTo(LocalDate.of(2027, 4, 15));
        assertThat(updated.code()).isEqualTo("GK2-2627");
        assertThat(updated.siteId()).isEqualTo(site.getId());
    }

    @Test
    void listBySite_boSung_returnsOnlyTermsOfThatSite() {
        Site siteA = newSite();
        Site siteB = newSite();
        User actor = newUser("head.academic4");
        AcademicTermResponse termA = academicTermService.create(
                new CreateAcademicTermRequest(siteA.getId(), "T-A", "Kỳ site A", LocalDate.now(), LocalDate.now().plusMonths(1)),
                actor.getId());
        academicTermService.create(
                new CreateAcademicTermRequest(siteB.getId(), "T-B", "Kỳ site B", LocalDate.now(), LocalDate.now().plusMonths(1)),
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

    private User newUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "." + System.nanoTime());
        user.setEmail(prefix + "." + System.nanoTime() + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
