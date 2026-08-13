package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateShiftRequest;
import vn.com.pps.education.dto.ShiftResponse;
import vn.com.pps.education.dto.UpdateShiftRequest;
import vn.com.pps.education.exception.DuplicateShiftCodeException;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-70: Quản lý Ca làm việc (shifts) — bổ sung ngoài SDD gốc, xác nhận
 * với người dùng 2026-08-13. Xem docs/uc/phan-he-04-nhan-su.md.
 */
@Transactional
class ShiftServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ShiftService shiftService;

    @Autowired
    private UserRepository userRepository;

    private Long actorUserId() {
        User user = new User();
        user.setUsername("shift.actor." + SEQ.incrementAndGet());
        user.setEmail("shift.actor." + SEQ.incrementAndGet() + "@pps.edu.vn");
        user.setFullName("Shift Actor");
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user).getId();
    }

    private CreateShiftRequest validRequest(String code) {
        return new CreateShiftRequest(code, "Ca hành chính", LocalTime.of(8, 0), LocalTime.of(17, 0),
                null, null, null, null, "1,2,3,4,5,6", "ALL");
    }

    @Test
    void createShift_UC70_MainFlow_createsShiftWithDefaults() {
        ShiftResponse response = shiftService.createShift(validRequest("SHIFT-" + SEQ.incrementAndGet()), actorUserId());

        assertThat(response.id()).isNotNull();
        assertThat(response.active()).isTrue();
        assertThat(response.checkInWindowBeforeMinutes()).isEqualTo(30);
        assertThat(response.checkOutWindowAfterMinutes()).isEqualTo(60);
    }

    @Test
    void createShift_UC70_A1_rejectsDuplicateCode() {
        String code = "SHIFT-" + SEQ.incrementAndGet();
        Long actor = actorUserId();
        shiftService.createShift(validRequest(code), actor);

        assertThatThrownBy(() -> shiftService.createShift(validRequest(code), actor))
                .isInstanceOf(DuplicateShiftCodeException.class);
    }

    @Test
    void createShift_UC70_A2_rejectsInvalidWeekdaysFormat() {
        CreateShiftRequest request = new CreateShiftRequest("SHIFT-" + SEQ.incrementAndGet(), "Ca lỗi",
                LocalTime.of(8, 0), LocalTime.of(17, 0), null, null, null, null, "1,8,x", "ALL");

        assertThatThrownBy(() -> shiftService.createShift(request, actorUserId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createShift_UC70_A2_rejectsDuplicateWeekdayNumbers() {
        CreateShiftRequest request = new CreateShiftRequest("SHIFT-" + SEQ.incrementAndGet(), "Ca lỗi",
                LocalTime.of(8, 0), LocalTime.of(17, 0), null, null, null, null, "1,1,2", "ALL");

        assertThatThrownBy(() -> shiftService.createShift(request, actorUserId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateShift_UC70_MainFlow_updatesFields() {
        Long actor = actorUserId();
        ShiftResponse created = shiftService.createShift(validRequest("SHIFT-" + SEQ.incrementAndGet()), actor);

        UpdateShiftRequest update = new UpdateShiftRequest("Ca hành chính (đổi tên)", LocalTime.of(9, 0),
                LocalTime.of(18, 0), 15, 15, 15, 45, "1,2,3,4,5", "ODD");
        ShiftResponse updated = shiftService.updateShift(created.id(), update, actor);

        assertThat(updated.name()).isEqualTo("Ca hành chính (đổi tên)");
        assertThat(updated.checkInTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(updated.weekParity()).isEqualTo("ODD");
        assertThat(updated.appliesToWeekdays()).isEqualTo("1,2,3,4,5");
    }

    @Test
    void deactivateShift_UC70_doesNotHardDelete() {
        Long actor = actorUserId();
        ShiftResponse created = shiftService.createShift(validRequest("SHIFT-" + SEQ.incrementAndGet()), actor);

        ShiftResponse deactivated = shiftService.deactivateShift(created.id(), actor);

        assertThat(deactivated.active()).isFalse();
        assertThat(shiftService.listShifts()).anySatisfy(s -> assertThat(s.id()).isEqualTo(created.id()));
    }
}
