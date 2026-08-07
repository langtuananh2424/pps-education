package vn.com.pps.education.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AcademicYearResponse;
import vn.com.pps.education.dto.CreateAcademicYearRequest;
import vn.com.pps.education.dto.UpdateAcademicYearRequest;
import vn.com.pps.education.exception.DuplicateAcademicYearCodeException;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V103 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-07) —
 * CRUD danh mục "Năm học" dùng chung toàn hệ thống.
 */
@Transactional
class AcademicYearServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private AcademicYearService academicYearService;

    @Autowired
    private UserRepository userRepository;

    private User actor;

    @BeforeEach
    void setUp() {
        actor = newUser("head.academic");
    }

    @Test
    void create_MainFlow_persistsAcademicYearWithPlannedStatus() {
        AcademicYearResponse response = academicYearService.create(
                new CreateAcademicYearRequest(code(), "Năm học 2026-2027", LocalDate.of(2026, 8, 1), LocalDate.of(2027, 6, 30)),
                actor.getId());

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo("PLANNED");
    }

    @Test
    void create_A_rejectsDuplicateCode() {
        String code = code();
        academicYearService.create(new CreateAcademicYearRequest(code, "A", null, null), actor.getId());

        assertThatThrownBy(() -> academicYearService.create(
                new CreateAcademicYearRequest(code, "B", null, null), actor.getId()))
                .isInstanceOf(DuplicateAcademicYearCodeException.class);
    }

    @Test
    void create_A_rejectsEndDateBeforeStartDate() {
        assertThatThrownBy(() -> academicYearService.create(
                new CreateAcademicYearRequest(code(), "Sai ngày", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 1, 1)),
                actor.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_MainFlow_changesStatusToActive() {
        AcademicYearResponse created = academicYearService.create(
                new CreateAcademicYearRequest(code(), "2026-2027", null, null), actor.getId());

        AcademicYearResponse updated = academicYearService.update(created.id(),
                new UpdateAcademicYearRequest("2026-2027", LocalDate.of(2026, 8, 1), LocalDate.of(2027, 6, 30), "ACTIVE"),
                actor.getId());

        assertThat(updated.status()).isEqualTo("ACTIVE");
        assertThat(updated.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void list_MainFlow_returnsAllAcademicYears() {
        academicYearService.create(new CreateAcademicYearRequest(code(), "A", null, null), actor.getId());
        academicYearService.create(new CreateAcademicYearRequest(code(), "B", null, null), actor.getId());

        assertThat(academicYearService.list().size()).isGreaterThanOrEqualTo(2);
    }

    private String code() {
        return "AY-" + SEQ.incrementAndGet();
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
