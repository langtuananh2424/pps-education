package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.dto.CreateSkillRequest;
import vn.com.pps.education.dto.SkillResponse;
import vn.com.pps.education.dto.UpdateSkillRequest;
import vn.com.pps.education.exception.DuplicateSkillCodeException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.support.AbstractIntegrationTest;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC-54: Quản lý danh mục kỹ năng — Main Flow (thêm/sửa/vô hiệu hoá), A1 (trùng mã). */
@Transactional
class SkillServiceTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private SkillService skillService;

    @Test
    void create_UC54_MainFlow_addsSkillToActiveList() {
        SkillResponse skill = skillService.create(new CreateSkillRequest(skillCode(), "Từ vựng", "Kiểm tra vốn từ"));

        assertThat(skill.active()).isTrue();
        assertThat(skillService.list(false)).extracting(SkillResponse::id).contains(skill.id());
    }

    @Test
    void create_UC54_A1_rejectsDuplicateCode() {
        String code = skillCode();
        skillService.create(new CreateSkillRequest(code, "Từ vựng", null));

        assertThatThrownBy(() -> skillService.create(new CreateSkillRequest(code, "Từ vựng khác", null)))
                .isInstanceOf(DuplicateSkillCodeException.class);
    }

    @Test
    void update_UC54_MainFlow_deactivateHidesFromActiveList() {
        SkillResponse skill = skillService.create(new CreateSkillRequest(skillCode(), "Phát âm", null));

        SkillResponse updated = skillService.update(skill.id(), new UpdateSkillRequest("Phát âm chuẩn", "Ghi chú", false));

        assertThat(updated.active()).isFalse();
        assertThat(skillService.list(false)).extracting(SkillResponse::id).doesNotContain(skill.id());
        assertThat(skillService.list(true)).extracting(SkillResponse::id).contains(skill.id());
    }

    @Test
    void update_rejectsWhenSkillNotFound() {
        assertThatThrownBy(() -> skillService.update(-1L, new UpdateSkillRequest("X", null, true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private String skillCode() {
        return "TEST-SKILL-" + SEQ.incrementAndGet();
    }
}
