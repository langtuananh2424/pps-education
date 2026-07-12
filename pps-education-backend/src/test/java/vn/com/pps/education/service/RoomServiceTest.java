package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Role;
import vn.com.pps.education.domain.Site;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.domain.UserRole;
import vn.com.pps.education.dto.CreateEquipmentRequest;
import vn.com.pps.education.dto.CreateRoomRequest;
import vn.com.pps.education.dto.EquipmentResponse;
import vn.com.pps.education.dto.RoomResponse;
import vn.com.pps.education.dto.UpdateEquipmentStatusRequest;
import vn.com.pps.education.dto.UpdateRoomRequest;
import vn.com.pps.education.exception.DuplicateRoomCodeException;
import vn.com.pps.education.exception.NotAuthorizedForFacilityManagementException;
import vn.com.pps.education.repository.RoleRepository;
import vn.com.pps.education.repository.SiteRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-37: Quản lý phòng học & thiết bị — Main Flow (bước 1-4), A1 (thiết bị
 * hỏng/bảo trì). Xem docs/uc/phan-he-10-co-so-vat-chat.md.
 */
@Transactional
class RoomServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private RoomService roomService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private SiteRepository siteRepository;

    private User staff;
    private Site site;

    @BeforeEach
    void setUp() {
        staff = newUser("giaovu");
        assignRole(staff, "STAFF");
        site = newSite();
    }

    @Test
    void createRoom_UC37_MainFlow_savesRoomWithFlexibleFlag() {
        RoomResponse room = roomService.createRoom(new CreateRoomRequest(site.getId(), "P101", "Phòng 101",
                "THEORY", 30, true, false), staff.getId());

        assertThat(room.status()).isEqualTo("AVAILABLE");
        assertThat(room.flexible()).isTrue();
        assertThat(room.managedByCenter()).isFalse();
    }

    @Test
    void createRoom_rejectsDuplicateCodeWithinSameSite() {
        roomService.createRoom(new CreateRoomRequest(site.getId(), "P101", "Phòng 101", "THEORY", 30, false, true), staff.getId());

        assertThatThrownBy(() -> roomService.createRoom(
                new CreateRoomRequest(site.getId(), "P101", "Phòng khác", "LAB", 20, false, true), staff.getId()))
                .isInstanceOf(DuplicateRoomCodeException.class);
    }

    @Test
    void createRoom_rejectsWhenActorNotAuthorized() {
        User parent = newUser("parent");
        assignRole(parent, "PARENT");

        assertThatThrownBy(() -> roomService.createRoom(
                new CreateRoomRequest(site.getId(), "P101", "Phòng 101", "THEORY", 30, false, true), parent.getId()))
                .isInstanceOf(NotAuthorizedForFacilityManagementException.class);
    }

    @Test
    void updateRoom_UC37_MainFlow_updatesCapacityAndStatus() {
        RoomResponse room = roomService.createRoom(new CreateRoomRequest(site.getId(), "P102", "Phòng 102",
                "COMPUTER", 25, false, true), staff.getId());

        RoomResponse updated = roomService.updateRoom(room.id(),
                new UpdateRoomRequest("Phòng 102 (mới)", 20, true, true, "MAINTENANCE", "Đang sửa điều hòa"), staff.getId());

        assertThat(updated.capacity()).isEqualTo(20);
        assertThat(updated.status()).isEqualTo("MAINTENANCE");
    }

    @Test
    void createEquipment_UC37_MainFlow_attachesEquipmentToRoom() {
        RoomResponse room = roomService.createRoom(new CreateRoomRequest(site.getId(), "P103", "Phòng 103",
                "LAB", 25, false, true), staff.getId());

        EquipmentResponse equipment = roomService.createEquipment(
                new CreateEquipmentRequest(room.id(), "TB-001", "Máy chiếu Epson", "PROJECTOR"), staff.getId());

        assertThat(equipment.status()).isEqualTo("AVAILABLE");
        assertThat(roomService.listByRoom(room.id())).extracting(EquipmentResponse::id).contains(equipment.id());
    }

    @Test
    void updateEquipmentStatus_UC37_A1_maintenanceExcludesFromAvailableStatus() {
        RoomResponse room = roomService.createRoom(new CreateRoomRequest(site.getId(), "P104", "Phòng 104",
                "THEORY", 30, false, true), staff.getId());
        EquipmentResponse equipment = roomService.createEquipment(
                new CreateEquipmentRequest(room.id(), "TB-002", "Loa JBL", "SPEAKER"), staff.getId());

        EquipmentResponse broken = roomService.updateEquipmentStatus(equipment.id(),
                new UpdateEquipmentStatusRequest("BROKEN", "Hỏng loa, cần thay mới"), staff.getId());

        assertThat(broken.status()).isEqualTo("BROKEN");
    }

    private Site newSite() {
        Site s = new Site();
        s.setCode("SITE-" + SEQ.incrementAndGet());
        s.setName("Test Site");
        s.setSiteType(Site.SiteType.OWNED);
        return siteRepository.save(s);
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedBy(user);
        userRoleRepository.save(userRole);
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
