package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.*;
import vn.com.pps.education.dto.EntranceAssessmentResultResponse;
import vn.com.pps.education.dto.EntranceAssessmentResultResponse.EntranceScoreResponse;
import vn.com.pps.education.dto.UpsertEntranceAssessmentResultRequest;
import vn.com.pps.education.dto.UpsertEntranceAssessmentResultRequest.EntranceScoreInput;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-18c: Đánh giá đầu vào & đề xuất xếp lớp — NHẬP điểm & kết quả (bổ sung
 * ngoài SDD gốc, đã xác nhận với người dùng 2026-08-28). Xem
 * docs/uc/phan-he-06-hoc-thuat.md UC-18c. KHÔNG có quy trình duyệt — nhập
 * trực tiếp, audit qua {@code enteredBy} + timestamps. Đối tượng chấm là
 * Lead HOẶC Student (đúng 1 trong 2).
 */
@Service
public class EntranceAssessmentResultService {

    private final EntranceAssessmentSetupRepository setupRepository;
    private final EntranceAssessmentComponentRepository componentRepository;
    private final EntranceAssessmentResultRepository resultRepository;
    private final EntranceAssessmentScoreRepository scoreRepository;
    private final LeadRepository leadRepository;
    private final StudentRepository studentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final UserRepository userRepository;

    public EntranceAssessmentResultService(EntranceAssessmentSetupRepository setupRepository,
                                           EntranceAssessmentComponentRepository componentRepository,
                                           EntranceAssessmentResultRepository resultRepository,
                                           EntranceAssessmentScoreRepository scoreRepository,
                                           LeadRepository leadRepository,
                                           StudentRepository studentRepository,
                                           SchoolClassRepository schoolClassRepository,
                                           UserRepository userRepository) {
        this.setupRepository = setupRepository;
        this.componentRepository = componentRepository;
        this.resultRepository = resultRepository;
        this.scoreRepository = scoreRepository;
        this.leadRepository = leadRepository;
        this.studentRepository = studentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.userRepository = userRepository;
    }

    /**
     * UC-18c Main Flow: tạo mới hoặc cập nhật kết quả đánh giá đầu vào của
     * 1 thí sinh trong 1 bộ đề (1 giao dịch).
     * <ul>
     *   <li>A1 — thiếu/thừa đối tượng: phải có đúng 1 trong leadId/studentId.</li>
     *   <li>A2 — điểm ngoài [0, maxScore] của đầu điểm → chặn lưu.</li>
     * </ul>
     */
    @Transactional
    public EntranceAssessmentResultResponse upsertResult(Long setupId, UpsertEntranceAssessmentResultRequest request, Long actorUserId) {
        EntranceAssessmentSetup setup = setupRepository.findByIdAndDeletedAtIsNull(setupId)
                .orElseThrow(() -> new ResourceNotFoundException("error.entranceAssessment.setupNotFound",
                        new Object[]{setupId}, "Không tìm thấy bộ đề đánh giá đầu vào id=" + setupId));

        boolean hasLead = request.leadId() != null;
        boolean hasStudent = request.studentId() != null;
        if (hasLead == hasStudent) {
            throw new IllegalArgumentException("Phải chọn đúng 1 trong: khách hàng tiềm năng (lead) HOẶC học sinh.");
        }

        Lead lead = hasLead ? leadRepository.findById(request.leadId())
                .orElseThrow(() -> new ResourceNotFoundException("error.entranceAssessment.leadNotFound",
                        new Object[]{request.leadId()}, "Không tìm thấy lead id=" + request.leadId())) : null;
        Student student = hasStudent ? studentRepository.findById(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("error.entranceAssessment.studentNotFound",
                        new Object[]{request.studentId()}, "Không tìm thấy học sinh id=" + request.studentId())) : null;

        if (request.overallScore() != null && request.overallScore().signum() < 0) {
            throw new IllegalArgumentException("Điểm tổng không được âm.");
        }

        EntranceAssessmentResult result = (hasLead
                ? resultRepository.findBySetupIdAndLeadId(setupId, request.leadId())
                : resultRepository.findBySetupIdAndStudentId(setupId, request.studentId()))
                .orElseGet(EntranceAssessmentResult::new);

        result.setSetup(setup);
        result.setLead(lead);
        result.setStudent(student);
        result.setCandidateName(request.candidateName().trim());
        result.setAssessedDate(request.assessedDate());
        result.setOverallScore(request.overallScore());
        result.setRecommendedLevel(trimToNull(request.recommendedLevel()));
        result.setRecommendedClass(resolveClass(request.recommendedClassId()));
        result.setNote(trimToNull(request.note()));
        if (result.getEnteredBy() == null) {
            result.setEnteredBy(userRepository.findById(actorUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.entranceAssessment.actorNotFound",
                            new Object[]{actorUserId}, "Không tìm thấy tài khoản id=" + actorUserId)));
        }
        result = resultRepository.save(result);

        applyScores(setup, result, request.scores());
        return toResultResponse(result);
    }

    /** UC-18c: đánh dấu đã chuyển thí sinh sang luồng xếp lớp (UC-18). */
    @Transactional
    public EntranceAssessmentResultResponse markPlaced(Long resultId) {
        EntranceAssessmentResult result = getResultOrThrow(resultId);
        result.setPlacedFlag(true);
        result = resultRepository.save(result);
        return toResultResponse(result);
    }

    @Transactional(readOnly = true)
    public List<EntranceAssessmentResultResponse> listResults(Long setupId) {
        return resultRepository.findBySetupIdOrderByAssessedDateDescIdDesc(setupId).stream()
                .map(this::toResultResponse).toList();
    }

    @Transactional(readOnly = true)
    public EntranceAssessmentResultResponse getResult(Long resultId) {
        return toResultResponse(getResultOrThrow(resultId));
    }

    @Transactional
    public void deleteResult(Long resultId) {
        EntranceAssessmentResult result = getResultOrThrow(resultId);
        scoreRepository.deleteByResultId(resultId);
        resultRepository.delete(result);
    }

    // ===================== Helpers =====================

    private void applyScores(EntranceAssessmentSetup setup, EntranceAssessmentResult result, List<EntranceScoreInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return;
        }
        Map<Long, EntranceAssessmentComponent> components = new LinkedHashMap<>();
        componentRepository.findBySetupIdOrderByDisplayOrderAscIdAsc(setup.getId())
                .forEach(c -> components.put(c.getId(), c));

        Map<Long, EntranceAssessmentScore> existing = new LinkedHashMap<>();
        scoreRepository.findByResultId(result.getId()).forEach(s -> existing.put(s.getComponent().getId(), s));

        for (EntranceScoreInput input : inputs) {
            EntranceAssessmentComponent component = components.get(input.componentId());
            if (component == null) {
                throw new IllegalArgumentException("Đầu điểm id=" + input.componentId() + " không thuộc bộ đề này.");
            }
            BigDecimal score = input.score();
            if (score != null && !input.absenceFlag()
                    && (score.signum() < 0 || score.compareTo(component.getMaxScore()) > 0)) {
                throw new IllegalArgumentException(
                        "Điểm '" + component.getName() + "' phải trong khoảng [0, " + component.getMaxScore() + "].");
            }
            EntranceAssessmentScore row = existing.getOrDefault(input.componentId(), new EntranceAssessmentScore());
            row.setResult(result);
            row.setComponent(component);
            row.setScore(input.absenceFlag() ? null : score);
            row.setAbsenceFlag(input.absenceFlag());
            scoreRepository.save(row);
        }
    }

    private EntranceAssessmentResult getResultOrThrow(Long id) {
        return resultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.entranceAssessment.resultNotFound",
                        new Object[]{id}, "Không tìm thấy kết quả đánh giá đầu vào id=" + id));
    }

    private SchoolClass resolveClass(Long classId) {
        if (classId == null) {
            return null;
        }
        return schoolClassRepository.findById(classId)
                .orElseThrow(() -> new ResourceNotFoundException("error.entranceAssessment.classNotFound",
                        new Object[]{classId}, "Không tìm thấy lớp id=" + classId));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private EntranceAssessmentResultResponse toResultResponse(EntranceAssessmentResult r) {
        Map<Long, EntranceAssessmentComponent> components = new LinkedHashMap<>();
        componentRepository.findBySetupIdOrderByDisplayOrderAscIdAsc(r.getSetup().getId())
                .forEach(c -> components.put(c.getId(), c));

        Map<Long, EntranceAssessmentScore> scoreByComponent = new LinkedHashMap<>();
        scoreRepository.findByResultId(r.getId()).forEach(s -> scoreByComponent.put(s.getComponent().getId(), s));

        List<EntranceScoreResponse> scores = new ArrayList<>();
        for (EntranceAssessmentComponent c : components.values()) {
            EntranceAssessmentScore s = scoreByComponent.get(c.getId());
            scores.add(new EntranceScoreResponse(c.getId(), c.getCode(), c.getName(), c.getMaxScore(),
                    s == null ? null : s.getScore(), s != null && s.isAbsenceFlag()));
        }

        SchoolClass recClass = r.getRecommendedClass();
        return new EntranceAssessmentResultResponse(
                r.getId(), r.getSetup().getId(),
                r.getLead() == null ? null : r.getLead().getId(),
                r.getStudent() == null ? null : r.getStudent().getId(),
                r.getCandidateName(), r.getAssessedDate(), r.getOverallScore(),
                r.getRecommendedLevel(),
                recClass == null ? null : recClass.getId(),
                recClass == null ? null : recClass.getName(),
                r.isPlacedFlag(), r.getNote(),
                r.getEnteredBy() == null ? null : r.getEnteredBy().getFullName(),
                scores);
    }
}
