package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.GradeEditWindowResponse;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.support.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UC-19/20 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): cấu hình số
 * ngày X GV toàn quyền sửa điểm (system_settings.academic.grade_edit_window_days,
 * V39). Xem docs/uc/phan-he-06-hoc-thuat.md.
 */
@Transactional
class AcademicSettingsServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AcademicSettingsService academicSettingsService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void gradeEditWindowDays_MainFlow_defaultsToSevenDays() {
        assertThat(academicSettingsService.gradeEditWindowDays()).isEqualTo(7);
        assertThat(academicSettingsService.getGradeEditWindow()).isEqualTo(new GradeEditWindowResponse(7));
    }

    @Test
    void updateGradeEditWindowDays_MainFlow_persistsNewValue() {
        User actor = newUser("head.academic");

        GradeEditWindowResponse updated = academicSettingsService.updateGradeEditWindowDays(10, actor.getId());

        assertThat(updated.days()).isEqualTo(10);
        assertThat(academicSettingsService.gradeEditWindowDays()).isEqualTo(10);
    }

    @Test
    void updateGradeEditWindowDays_A1_rejectsNonPositiveDays() {
        User actor = newUser("head.academic");

        assertThatThrownBy(() -> academicSettingsService.updateGradeEditWindowDays(0, actor.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> academicSettingsService.updateGradeEditWindowDays(-1, actor.getId()))
                .isInstanceOf(IllegalArgumentException.class);
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
