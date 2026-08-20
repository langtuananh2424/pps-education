package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Skill;
import vn.com.pps.education.dto.CreateSkillRequest;
import vn.com.pps.education.dto.SkillResponse;
import vn.com.pps.education.dto.UpdateSkillRequest;
import vn.com.pps.education.exception.DuplicateSkillCodeException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.SkillRepository;

import java.util.Comparator;
import java.util.List;

/**
 * UC-54: Quản lý danh mục kỹ năng (FR-ACA-06, bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng). Xem docs/uc/phan-he-06-hoc-thuat.md.
 *
 * Không xoá cứng (Main Flow bước 2) — kỹ năng có thể đã được tham chiếu
 * bởi curriculum_subjects/grade_components qua skill_id; vô hiệu hoá
 * bằng is_active=FALSE.
 */
@Service
public class SkillService {

    private final SkillRepository skillRepository;

    public SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    @Transactional(readOnly = true)
    public List<SkillResponse> list(boolean includeInactive) {
        List<Skill> skills = includeInactive
                ? skillRepository.findAll().stream().sorted(Comparator.comparing(Skill::getName)).toList()
                : skillRepository.findByActiveTrueOrderByName();
        return skills.stream().map(this::toResponse).toList();
    }

    /** Main Flow bước 2 (Thêm mới). A1 — trùng mã, chặn lưu. */
    @Transactional
    public SkillResponse create(CreateSkillRequest request) {
        String code = request.code().trim().toUpperCase();
        if (skillRepository.findByCode(code).isPresent()) {
            throw new DuplicateSkillCodeException("Mã kỹ năng đã tồn tại: " + code);
        }
        Skill skill = new Skill();
        skill.setCode(code);
        skill.setName(request.name().trim());
        skill.setDescription(request.description());
        skill = skillRepository.save(skill);
        return toResponse(skill);
    }

    /** Main Flow bước 2 (Sửa/Vô hiệu hoá) — code bất biến sau khi tạo. */
    @Transactional
    public SkillResponse update(Long id, UpdateSkillRequest request) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.skill.notFoundById", new Object[]{id}, "Không tìm thấy kỹ năng id=" + id));
        skill.setName(request.name().trim());
        skill.setDescription(request.description());
        skill.setActive(request.active());
        skill = skillRepository.save(skill);
        return toResponse(skill);
    }

    private SkillResponse toResponse(Skill skill) {
        return new SkillResponse(skill.getId(), skill.getCode(), skill.getName(),
                skill.getDescription(), skill.isActive());
    }
}
