package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AssignSiteManagerRequest;
import vn.com.pps.education.dto.CreateSiteRequest;
import vn.com.pps.education.dto.PartnerSchoolInfoRequest;
import vn.com.pps.education.dto.SiteResponse;
import vn.com.pps.education.dto.UpdateSiteRequest;
import vn.com.pps.education.dto.AssignSiteTeacherRequest;
import vn.com.pps.education.dto.SiteTeacherResponse;
import vn.com.pps.education.exception.DuplicateSiteCodeException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.exception.SiteTeacherAlreadyAssignedException;
import vn.com.pps.education.repository.UserRepository;
import java.time.LocalDate;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-36: Quản lý điểm trường (FR-FAC-01).
 *
 * Covers:
 * - Main Flow: tạo điểm trường OWNED (không partnerInfo) + gán Quản lý điểm trường ngay lúc tạo
 * - Main Flow bước 3: tạo điểm trường PARTNER kèm partnerInfo
 * - Validate: partnerInfo chỉ hợp lệ khi siteType=PARTNER
 * - Validate: mã điểm trường trùng → DuplicateSiteCodeException
 * - A1: đổi Quản lý điểm trường phụ trách → phân công cũ đóng lại, phân công mới mở
 * - update: đổi PARTNER→OWNED xóa partnerInfo cũ
 * - get/list điểm trường không tồn tại → ResourceNotFoundException
 */
@Transactional
class SiteServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired private SiteService siteService;
    @Autowired private UserRepository userRepository;

    @Test
    void createSite_UC36_MainFlow_owned_createsSiteAndAssignsManager() {
        User actor = newUser("ops-manager");
        User manager = newUser("site-manager");

        SiteResponse response = siteService.createSite(
                new CreateSiteRequest(uniqueCode(), "Cơ sở Đống Đa", "OWNED",
                        "123 Đường ABC", "Đống Đa", "0900000000", null, manager.getId()),
                actor.getId());

        assertThat(response.id()).isNotNull();
        assertThat(response.siteType()).isEqualTo("OWNED");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.partnerInfo()).isNull();
        assertThat(response.currentManagerUserId()).isEqualTo(manager.getId());
        assertThat(response.currentManagerFullName()).isEqualTo(manager.getFullName());
    }

    @Test
    void createSite_UC36_MainFlow_partner_savesPartnerSchoolInfo() {
        User actor = newUser("ops-manager");

        SiteResponse response = siteService.createSite(
                new CreateSiteRequest(uniqueCode(), "Trường THCS Nguyễn Du", "PARTNER",
                        "456 Đường XYZ", "Ba Đình", "0911111111",
                        new PartnerSchoolInfoRequest("Nguyễn Văn A", "Hiệu trưởng", "0922222222",
                                "hieutruong@ndu.edu.vn", "Ghi chú thêm"),
                        null),
                actor.getId());

        assertThat(response.siteType()).isEqualTo("PARTNER");
        assertThat(response.partnerInfo()).isNotNull();
        assertThat(response.partnerInfo().contactPersonName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.partnerInfo().contactEmail()).isEqualTo("hieutruong@ndu.edu.vn");
        assertThat(response.currentManagerUserId()).isNull();
    }

    @Test
    void createSite_UC36_partnerInfoOnOwnedSite_rejectsWithIllegalArgument() {
        User actor = newUser("ops-manager");

        assertThatThrownBy(() -> siteService.createSite(
                new CreateSiteRequest(uniqueCode(), "Cơ sở lỗi", "OWNED", null, null, null,
                        new PartnerSchoolInfoRequest("A", "B", "C", "D", "E"), null),
                actor.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSite_UC36_duplicateCode_rejectsWithDuplicateSiteCodeException() {
        User actor = newUser("ops-manager");
        String code = uniqueCode();
        siteService.createSite(new CreateSiteRequest(code, "Cơ sở 1", "OWNED", null, null, null, null, null), actor.getId());

        assertThatThrownBy(() -> siteService.createSite(
                new CreateSiteRequest(code, "Cơ sở 2", "OWNED", null, null, null, null, null), actor.getId()))
                .isInstanceOf(DuplicateSiteCodeException.class);
    }

    @Test
    void assignManager_UC36_A1_replacesActiveManager() {
        User actor = newUser("ops-manager");
        User firstManager = newUser("manager-1");
        User secondManager = newUser("manager-2");

        SiteResponse created = siteService.createSite(
                new CreateSiteRequest(uniqueCode(), "Cơ sở đổi QL", "OWNED", null, null, null, null, firstManager.getId()),
                actor.getId());
        assertThat(created.currentManagerUserId()).isEqualTo(firstManager.getId());

        SiteResponse updated = siteService.assignManager(created.id(),
                new AssignSiteManagerRequest(secondManager.getId()), actor.getId());

        assertThat(updated.currentManagerUserId()).isEqualTo(secondManager.getId());
        assertThat(updated.currentManagerFullName()).isEqualTo(secondManager.getFullName());
    }

    @Test
    void updateSite_UC36_partnerToOwned_removesPartnerSchoolInfo() {
        User actor = newUser("ops-manager");
        SiteResponse created = siteService.createSite(
                new CreateSiteRequest(uniqueCode(), "Trường liên kết cũ", "PARTNER", null, null, null,
                        new PartnerSchoolInfoRequest("X", "Y", "Z", "x@y.z", null), null),
                actor.getId());
        assertThat(created.partnerInfo()).isNotNull();

        SiteResponse updated = siteService.updateSite(created.id(),
                new UpdateSiteRequest("Cơ sở tự vận hành mới", "OWNED", null, null, null, "ACTIVE", null),
                actor.getId());

        assertThat(updated.siteType()).isEqualTo("OWNED");
        assertThat(updated.partnerInfo()).isNull();
    }

    @Test
    void getSite_UC36_notFound_throwsResourceNotFoundException() {
        assertThatThrownBy(() -> siteService.getSite(-1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listSites_UC36_includesCreatedSite() {
        User actor = newUser("ops-manager");
        SiteResponse created = siteService.createSite(
                new CreateSiteRequest(uniqueCode(), "Cơ sở list", "OWNED", null, null, null, null, null),
                actor.getId());

        List<SiteResponse> sites = siteService.listSites();

        assertThat(sites).extracting(SiteResponse::id).contains(created.id());
    }

    @Test
    void assignTeacher_MainFlow_createsActiveAssignment() {
        User actor = newUser("ops-manager");
        User teacher = newUser("teacher-for-site");
        SiteResponse site = siteService.createSite(
                new CreateSiteRequest(uniqueCode(), "Cơ sở gán GV", "OWNED", null, null, null, null, null), actor.getId());

        SiteTeacherResponse response = siteService.assignTeacher(site.id(),
                new AssignSiteTeacherRequest(teacher.getId(), LocalDate.now(), "Ghi chú"), actor.getId());

        assertThat(response.id()).isNotNull();
        assertThat(response.siteId()).isEqualTo(site.id());
        assertThat(response.teacherUserId()).isEqualTo(teacher.getId());
        assertThat(response.assignedTo()).isNull();
        assertThat(siteService.listTeachers(site.id())).extracting(SiteTeacherResponse::teacherUserId).containsExactly(teacher.getId());
    }

    @Test
    void assignTeacher_rejectsDuplicateActiveAssignment() {
        User actor = newUser("ops-manager");
        User teacher = newUser("teacher-dup");
        SiteResponse site = siteService.createSite(
                new CreateSiteRequest(uniqueCode(), "Cơ sở dup GV", "OWNED", null, null, null, null, null), actor.getId());
        siteService.assignTeacher(site.id(), new AssignSiteTeacherRequest(teacher.getId(), LocalDate.now(), null), actor.getId());

        assertThatThrownBy(() -> siteService.assignTeacher(site.id(),
                new AssignSiteTeacherRequest(teacher.getId(), LocalDate.now(), null), actor.getId()))
                .isInstanceOf(SiteTeacherAlreadyAssignedException.class);
    }

    @Test
    void removeTeacherFromSite_closesAssignment_andListTeachersNoLongerReturnsIt() {
        User actor = newUser("ops-manager");
        User teacher = newUser("teacher-removed");
        SiteResponse site = siteService.createSite(
                new CreateSiteRequest(uniqueCode(), "Cơ sở gỡ GV", "OWNED", null, null, null, null, null), actor.getId());
        SiteTeacherResponse assignment = siteService.assignTeacher(site.id(),
                new AssignSiteTeacherRequest(teacher.getId(), LocalDate.now(), null), actor.getId());

        siteService.removeTeacherFromSite(site.id(), assignment.id(), actor.getId());

        assertThat(siteService.listTeachers(site.id())).isEmpty();
    }

    // ===================== Helpers =====================

    private String uniqueCode() {
        return "SITE-TEST-" + SEQ.incrementAndGet();
    }

    private User newUser(String prefix) {
        User user = new User();
        long seq = SEQ.incrementAndGet();
        user.setUsername(prefix + "." + seq);
        user.setEmail(prefix + "." + seq + "@pps.edu.vn");
        user.setFullName("Test " + prefix);
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
