package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.domain.Question;
import vn.com.pps.education.domain.QuestionBank;
import vn.com.pps.education.domain.QuestionChoice;
import vn.com.pps.education.domain.QuestionHistory;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateQuestionBankRequest;
import vn.com.pps.education.dto.CreateQuestionRequest;
import vn.com.pps.education.dto.QuestionBankResponse;
import vn.com.pps.education.dto.QuestionChoiceRequest;
import vn.com.pps.education.dto.QuestionChoiceResponse;
import vn.com.pps.education.dto.QuestionResponse;
import vn.com.pps.education.dto.UpdateQuestionRequest;
import vn.com.pps.education.exception.NotTeacherRoleException;
import vn.com.pps.education.exception.QuestionLockedException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.CurriculumSubjectRepository;
import vn.com.pps.education.repository.QuestionBankRepository;
import vn.com.pps.education.repository.QuestionChoiceRepository;
import vn.com.pps.education.repository.QuestionHistoryRepository;
import vn.com.pps.education.repository.QuestionRepository;
import vn.com.pps.education.repository.StudentAnswerRepository;
import vn.com.pps.education.repository.UserRepository;
import vn.com.pps.education.repository.UserRoleRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UC-40: Soạn & giao đề kiểm tra (FR-LMS-10) — phần Ngân hàng câu hỏi.
 * Xem docs/uc/phan-he-07-lms-portal.md. Tách khỏi ExerciseService (soạn
 * đề/giao đề) theo SRP — ngân hàng câu hỏi là tài nguyên dùng chung, có
 * thể tái sử dụng ngoài phạm vi 1 đề cụ thể (xem .claude/rules/solid.md).
 *
 * SDD "Bảo vệ khi sửa": câu hỏi đã có student_answers thì cấm sửa
 * content/đáp án đúng — chỉ còn sửa được các trường không ảnh hưởng bài
 * đã làm (status...). Muốn đổi nội dung phải tạo câu hỏi mới (createQuestion)
 * rồi tự archive câu cũ.
 */
@Service
public class QuestionBankService {

    private final QuestionBankRepository questionBankRepository;
    private final QuestionRepository questionRepository;
    private final QuestionChoiceRepository questionChoiceRepository;
    private final QuestionHistoryRepository questionHistoryRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final CurriculumRepository curriculumRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public QuestionBankService(QuestionBankRepository questionBankRepository,
                                QuestionRepository questionRepository,
                                QuestionChoiceRepository questionChoiceRepository,
                                QuestionHistoryRepository questionHistoryRepository,
                                StudentAnswerRepository studentAnswerRepository,
                                CurriculumRepository curriculumRepository,
                                CurriculumSubjectRepository curriculumSubjectRepository,
                                UserRepository userRepository,
                                UserRoleRepository userRoleRepository) {
        this.questionBankRepository = questionBankRepository;
        this.questionRepository = questionRepository;
        this.questionChoiceRepository = questionChoiceRepository;
        this.questionHistoryRepository = questionHistoryRepository;
        this.studentAnswerRepository = studentAnswerRepository;
        this.curriculumRepository = curriculumRepository;
        this.curriculumSubjectRepository = curriculumSubjectRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional
    public QuestionBankResponse createBank(CreateQuestionBankRequest request, Long actorUserId) {
        requireTeacher(actorUserId);
        QuestionBank bank = new QuestionBank();
        bank.setCode(request.code());
        bank.setName(request.name());
        if (request.curriculumId() != null) {
            bank.setCurriculum(curriculumOrThrow(request.curriculumId()));
        }
        if (request.subjectId() != null) {
            bank.setSubject(curriculumSubjectOrThrow(request.subjectId()));
        }
        bank.setLevel(request.level());
        bank = questionBankRepository.save(bank);
        return toResponse(bank);
    }

    @Transactional(readOnly = true)
    public List<QuestionBankResponse> listBanksByCurriculum(Long curriculumId) {
        return questionBankRepository.findByCurriculumId(curriculumId).stream().map(this::toResponse).toList();
    }

    /** Main Flow bước 1: soạn câu hỏi mới, lưu vào ngân hàng. */
    @Transactional
    public QuestionResponse createQuestion(CreateQuestionRequest request, Long actorUserId) {
        requireTeacher(actorUserId);
        User actor = getUserOrThrow(actorUserId);
        QuestionBank bank = questionBankRepository.findById(request.questionBankId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngân hàng câu hỏi id=" + request.questionBankId()));

        Question question = new Question();
        question.setQuestionBank(bank);
        question.setQuestionType(Question.QuestionType.valueOf(request.questionType()));
        if (request.skill() != null) {
            question.setSkill(Question.Skill.valueOf(request.skill()));
        }
        if (request.difficulty() != null) {
            question.setDifficulty(Question.Difficulty.valueOf(request.difficulty()));
        }
        question.setContent(request.content());
        question.setAudioUrl(request.audioUrl());
        question.setImageUrl(request.imageUrl());
        question.setReferencePassage(request.referencePassage());
        question.setExplanation(request.explanation());
        if (request.defaultPoints() != null) {
            question.setDefaultPoints(request.defaultPoints());
        }
        question.setTags(request.tags());
        question.setCreatedBy(actor);
        question = questionRepository.save(question);
        saveChoices(question, request.choices());

        writeHistory(question, actor, QuestionHistory.Action.CREATED);
        return toResponse(question);
    }

    /**
     * Sửa câu hỏi — chặn nếu đã có student_answers VÀ request đổi
     * content/choices (SDD "Bảo vệ khi sửa"); các trường khác (status...)
     * luôn sửa được.
     */
    @Transactional
    public QuestionResponse updateQuestion(Long id, UpdateQuestionRequest request, Long actorUserId) {
        requireTeacher(actorUserId);
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy câu hỏi id=" + id));
        User actor = getUserOrThrow(actorUserId);

        boolean changesContent = !Objects.equals(question.getContent(), request.content()) || request.choices() != null;
        if (changesContent && studentAnswerRepository.existsByQuestionId(id)) {
            throw new QuestionLockedException(
                    "Câu hỏi id=" + id + " đã có học sinh trả lời — không sửa được nội dung/đáp án. Hãy tạo câu hỏi mới rồi archive câu này.");
        }

        question.setContent(request.content());
        question.setAudioUrl(request.audioUrl());
        question.setImageUrl(request.imageUrl());
        question.setReferencePassage(request.referencePassage());
        question.setExplanation(request.explanation());
        if (request.defaultPoints() != null) {
            question.setDefaultPoints(request.defaultPoints());
        }
        question.setTags(request.tags());
        if (request.status() != null) {
            question.setStatus(Question.Status.valueOf(request.status()));
        }
        question = questionRepository.save(question);
        if (request.choices() != null) {
            questionChoiceRepository.deleteAll(questionChoiceRepository.findByQuestionIdOrderByDisplayOrder(id));
            saveChoices(question, request.choices());
        }

        writeHistory(question, actor, QuestionHistory.Action.UPDATED);
        return toResponse(question);
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> listQuestions(Long questionBankId) {
        return questionRepository.findByQuestionBankIdAndStatus(questionBankId, Question.Status.ACTIVE)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public QuestionResponse getQuestion(Long id) {
        return toResponse(questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy câu hỏi id=" + id)));
    }

    // ===================== Helpers =====================

    private void saveChoices(Question question, List<QuestionChoiceRequest> choices) {
        if (choices == null) {
            return;
        }
        for (QuestionChoiceRequest c : choices) {
            QuestionChoice choice = new QuestionChoice();
            choice.setQuestion(question);
            choice.setChoiceLabel(c.choiceLabel());
            choice.setContent(c.content());
            choice.setCorrect(c.isCorrect());
            choice.setDisplayOrder(c.displayOrder());
            questionChoiceRepository.save(choice);
        }
    }

    private void requireTeacher(Long actorUserId) {
        if (!roleCodesOf(actorUserId).contains("TEACHER")) {
            throw new NotTeacherRoleException(
                    "Tài khoản id=" + actorUserId + " không có role TEACHER để soạn ngân hàng câu hỏi.");
        }
    }

    private Set<String> roleCodesOf(Long userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toSet());
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    private Curriculum curriculumOrThrow(Long id) {
        return curriculumRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung chương trình id=" + id));
    }

    private CurriculumSubject curriculumSubjectOrThrow(Long id) {
        return curriculumSubjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học phần id=" + id));
    }

    private void writeHistory(Question question, User actor, QuestionHistory.Action action) {
        QuestionHistory history = new QuestionHistory();
        history.setQuestion(question);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("questionType", question.getQuestionType().name());
        snapshot.put("content", question.getContent());
        snapshot.put("status", question.getStatus().name());
        history.setDetails(snapshot);
        questionHistoryRepository.save(history);
    }

    private QuestionBankResponse toResponse(QuestionBank b) {
        return new QuestionBankResponse(
                b.getId(), b.getCode(), b.getName(),
                b.getCurriculum() == null ? null : b.getCurriculum().getId(),
                b.getSubject() == null ? null : b.getSubject().getId(),
                b.getLevel(), b.isActive());
    }

    private QuestionResponse toResponse(Question q) {
        List<QuestionChoiceResponse> choices = questionChoiceRepository.findByQuestionIdOrderByDisplayOrder(q.getId())
                .stream().map(this::toResponse).toList();
        return new QuestionResponse(
                q.getId(), q.getQuestionBank().getId(), q.getQuestionType().name(),
                q.getSkill() == null ? null : q.getSkill().name(),
                q.getDifficulty() == null ? null : q.getDifficulty().name(),
                q.getContent(), q.getAudioUrl(), q.getImageUrl(), q.getReferencePassage(), q.getExplanation(),
                q.getDefaultPoints(), q.getTags(), q.getStatus().name(), q.getCreatedBy().getId(), choices);
    }

    private QuestionChoiceResponse toResponse(QuestionChoice c) {
        return new QuestionChoiceResponse(c.getId(), c.getChoiceLabel(), c.getContent(), c.isCorrect(), c.getDisplayOrder());
    }
}
