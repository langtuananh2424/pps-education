package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.SiteManager;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.SiteManagerRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hotline trong email gửi Phụ huynh = số điện thoại Quản lý điểm trường đang phụ trách điểm
 * trường chứa lớp ({@link SiteManagerRepository#findActiveSiteManagerPhonesByClassId}) — chạy
 * trên Postgres thật để kiểm tra đúng điều kiện lọc (đang phụ trách, đúng vai trò, có số).
 */
@Transactional
class SiteManagerHotlineQueryTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private SiteManagerRepository siteManagerRepository;
    @Autowired
    private CurriculumRepository curriculumRepository;
    @Autowired
    private SchoolClassRepository schoolClassRepository;

    private User admin;
    private Site site;
    private SchoolClass schoolClass;

    @BeforeEach
    void setUp() {
        admin = newUser(null);
        site = newSite();
        schoolClass = newSchoolClass(site);
    }

    @Test
    void returnsPhoneOfActiveSiteManagerOfClassSite() {
        assign(site, newUser("0912345678"), SiteManager.RoleType.SITE_MANAGER, null);

        assertThat(siteManagerRepository.findActiveSiteManagerPhonesByClassId(schoolClass.getId()))
                .containsExactly("0912345678");
    }

    @Test
    void ignoresEndedAssignmentsPartnerRepsManagersWithoutPhoneAndOtherSites() {
        assign(site, newUser("0900000001"), SiteManager.RoleType.SITE_MANAGER, LocalDate.now().minusDays(1));
        assign(site, newUser("0900000002"), SiteManager.RoleType.PARTNER_REP, null);
        assign(site, newUser(null), SiteManager.RoleType.SITE_MANAGER, null);
        assign(newSite(), newUser("0900000003"), SiteManager.RoleType.SITE_MANAGER, null);

        assertThat(siteManagerRepository.findActiveSiteManagerPhonesByClassId(schoolClass.getId())).isEmpty();
    }

    private void assign(Site targetSite, User user, SiteManager.RoleType roleType, LocalDate assignedTo) {
        SiteManager sm = new SiteManager();
        sm.setSite(targetSite);
        sm.setUser(user);
        sm.setRoleType(roleType);
        sm.setAssignedFrom(LocalDate.now().minusMonths(1));
        sm.setAssignedTo(assignedTo);
        sm.setAssignedBy(admin);
        siteManagerRepository.save(sm);
    }

    private User newUser(String phone) {
        long n = SEQ.incrementAndGet();
        User user = new User();
        user.setUsername("site.hotline." + n);
        user.setEmail("site.hotline." + n + "@pps.edu.vn");
        user.setFullName("Site Hotline " + n);
        user.setPhone(phone);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }

    private Site newSite() {
        Site s = new Site();
        s.setCode("SITE-HL-" + SEQ.incrementAndGet());
        s.setName("Hotline Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private SchoolClass newSchoolClass(Site classSite) {
        Curriculum curriculum = new Curriculum();
        curriculum.setCode("CUR-HL-" + SEQ.incrementAndGet());
        curriculum.setName("Test curriculum");
        curriculum.setClassCategory(Curriculum.ClassCategory.MAIN);
        curriculum.setCreatedBy(admin);
        curriculum = curriculumRepository.save(curriculum);

        SchoolClass sc = new SchoolClass();
        sc.setClassCode("CLS-HL-" + SEQ.incrementAndGet());
        sc.setName("Hotline class " + SEQ.get());
        sc.setSite(classSite);
        sc.setCurriculum(curriculum);
        sc.setClassType(SchoolClass.ClassType.OPEN);
        sc.setMaxStudents(20);
        sc.setStartDate(LocalDate.now());
        sc.setColor("#F97316");
        sc.setCreatedBy(admin);
        return schoolClassRepository.save(sc);
    }
}
