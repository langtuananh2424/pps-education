package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.TaskCancelledRetentionResponse;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-06/07 (bổ sung): thiết lập số ngày giữ task CANCELLED trước khi xóa cứng.
 * Mirror AcademicSettingsServiceTest — get mặc định (seed V47) + update + validate.
 */
@Transactional
class TaskSettingsServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired private TaskSettingsService taskSettingsService;
    @Autowired private UserRepository userRepository;

    @Test
    void getCancelledRetention_defaultFromMigration_isPositive() {
        assertThat(taskSettingsService.getCancelledRetention().days()).isGreaterThan(0);
    }

    @Test
    void updateCancelledRetentionDays_persistsNewValue() {
        User actor = newUser();

        TaskCancelledRetentionResponse resp = taskSettingsService.updateCancelledRetentionDays(14, actor.getId());

        assertThat(resp.days()).isEqualTo(14);
        assertThat(taskSettingsService.cancelledRetentionDays()).isEqualTo(14);
    }

    @Test
    void updateCancelledRetentionDays_rejectsNonPositive() {
        User actor = newUser();

        assertThatThrownBy(() -> taskSettingsService.updateCancelledRetentionDays(0, actor.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private User newUser() {
        long seq = SEQ.incrementAndGet();
        User user = new User();
        user.setUsername("task.settings." + seq);
        user.setEmail("task.settings." + seq + "@pps.edu.vn");
        user.setFullName("Task Settings Test");
        user.setStatus(User.Status.ACTIVE);
        return userRepository.save(user);
    }
}
