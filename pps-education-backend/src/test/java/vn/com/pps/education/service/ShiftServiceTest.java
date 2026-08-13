package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Shift;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateShiftRequest;
import vn.com.pps.education.dto.ShiftResponse;
import vn.com.pps.education.dto.UpdateShiftRequest;
import vn.com.pps.education.exception.DuplicateShiftCodeException;
import vn.com.pps.education.repository.ShiftHistoryRepository;
import vn.com.pps.education.repository.ShiftRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Quản lý danh mục ca làm việc — bổ sung 2026-08-13, dưới UC-09/FR-HRM-02.
 * Xem docs/uc/phan-he-04-nhan-su.md (khối bổ sung sau UC-09).
 */
@Transactional
class ShiftServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private ShiftService shiftService;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private ShiftHistoryRepository shiftHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    private User actor;

    @BeforeEach
    void setUp() {
        actor = newUser();
    }

    @Test
    void create_UC09Extension_MainFlow_savesShiftAndWritesHistory() {
        CreateShiftRequest request = new CreateShiftRequest(
                "SHIFT-CREATE-" + SEQ.incrementAndGet(), "Ca sáng", LocalTime.of(8, 0), LocalTime.of(17, 0),
                30, 30, 30, 60, "1,2,3,4,5,6", Shift.WeekParity.ALL);

        ShiftResponse response = shiftService.create(request, actor.getId());

        assertThat(response.id()).isNotNull();
        assertThat(response.code()).isEqualTo(request.code());
        assertThat(response.active()).isTrue();
        assertThat(shiftHistoryRepository.findAll()).anyMatch(h -> h.getShift().getId().equals(response.id())
                && h.getAction() == vn.com.pps.education.domain.ShiftHistory.Action.CREATED);
    }

    @Test
    void create_UC09Extension_A1_rejectsDuplicateCode() {
        String code = "SHIFT-DUP-" + SEQ.incrementAndGet();
        shiftService.create(new CreateShiftRequest(code, "Ca sáng", LocalTime.of(8, 0), LocalTime.of(17, 0),
                30, 30, 30, 60, "1,2,3,4,5,6", Shift.WeekParity.ALL), actor.getId());

        assertThatThrownBy(() -> shiftService.create(new CreateShiftRequest(code, "Ca chiều",
                LocalTime.of(13, 0), LocalTime.of(21, 0), 30, 30, 30, 60, "1,2,3,4,5,6", Shift.WeekParity.ALL), actor.getId()))
                .isInstanceOf(DuplicateShiftCodeException.class);
    }

    @Test
    void update_UC09Extension_MainFlow_changesFieldsAndWritesHistory() {
        Shift shift = newShift();

        ShiftResponse response = shiftService.update(shift.getId(), new UpdateShiftRequest(
                "Ca sáng (đổi tên)", LocalTime.of(7, 30), LocalTime.of(16, 30),
                15, 15, 15, 45, "1,2,3,4,5", Shift.WeekParity.ODD, true), actor.getId());

        assertThat(response.name()).isEqualTo("Ca sáng (đổi tên)");
        assertThat(response.weekParity()).isEqualTo(Shift.WeekParity.ODD);
        assertThat(shiftHistoryRepository.findAll()).anyMatch(h -> h.getShift().getId().equals(shift.getId())
                && h.getAction() == vn.com.pps.education.domain.ShiftHistory.Action.UPDATED);
    }

    @Test
    void deactivate_UC09Extension_setsActiveFalseWithoutHardDelete() {
        Shift shift = newShift();

        shiftService.deactivate(shift.getId(), actor.getId());

        Shift reloaded = shiftRepository.findById(shift.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
    }

    private Shift newShift() {
        Shift shift = new Shift();
        shift.setCode("SHIFT-" + SEQ.incrementAndGet());
        shift.setName("Ca sáng");
        shift.setCheckInTime(LocalTime.of(8, 0));
        shift.setCheckOutTime(LocalTime.of(17, 0));
        shift.setAppliesToWeekdays("1,2,3,4,5,6");
        shift.setWeekParity(Shift.WeekParity.ALL);
        shift.setActive(true);
        return shiftRepository.save(shift);
    }

    private User newUser() {
        User user = new User();
        user.setUsername("shift.actor." + SEQ.incrementAndGet());
        user.setEmail("shift.actor." + SEQ.incrementAndGet() + "@pps.edu.vn");
        user.setFullName("Shift Actor");
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
