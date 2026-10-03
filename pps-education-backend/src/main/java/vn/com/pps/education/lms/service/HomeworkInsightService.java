package vn.com.pps.education.lms.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.common.CriteriaScoreItem;
import vn.com.pps.education.common.HomeworkHistoryInsight;
import vn.com.pps.education.common.HomeworkScoreInsight;
import vn.com.pps.education.academic.domain.ClassSession;
import vn.com.pps.education.lms.domain.Exercise;
import vn.com.pps.education.lms.domain.ExerciseAssignment;
import vn.com.pps.education.lms.domain.ExerciseAttempt;
import vn.com.pps.education.lms.domain.HomeworkSkillBatch;
import vn.com.pps.education.lms.domain.Question;
import vn.com.pps.education.lms.domain.StudentAnswer;
import vn.com.pps.education.lms.domain.StudentAnswerGrading;
import vn.com.pps.education.student.domain.StudentComment;
import vn.com.pps.education.lms.dto.HomeworkScoreInput;
import vn.com.pps.education.academic.repository.ClassSessionRepository;
import vn.com.pps.education.lms.repository.ExerciseAssignmentRepository;
import vn.com.pps.education.lms.repository.ExerciseAttemptRepository;
import vn.com.pps.education.lms.repository.StudentAnswerGradingRepository;
import vn.com.pps.education.lms.repository.StudentAnswerRepository;
import vn.com.pps.education.student.repository.StudentCommentRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * UC-74 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-30) — gom dữ liệu BTVN NHIỀU buổi của cả lớp
 * cho trợ lý nhận xét, rồi quy ra lời bằng {@link HomeworkHistoryInsight}: xu hướng theo kỹ năng, điểm yếu/mạnh cụ
 * thể (dạng câu hỏi, độ khó, tiêu chí chấm Writing/Speaking) và thói quen làm bài. Chỉ đọc, không ghi DB; chỉ dùng
 * cho trợ lý (giáo viên không xem trực tiếp số liệu này).
 *
 * <p>"Lần BTVN thứ k" = bài giao ở buổi thứ k trước buổi đang nhận xét (cùng lớp, cùng Loại giáo viên — mirror
 * {@code StudentCommentService#previousSession}), gồm % tự động các Lô online (kênh chính/Reading/Writing — tính theo
 * Bài như {@code HomeworkProgressService#grammarProgressLabel}) và điểm nhập tay ghi ở buổi sau đó. Lần gần nhất
 * dùng điểm nhập tay giáo viên đang gõ trên bảng (frontend gửi kèm, có thể chưa lưu). Mọi truy vấn chạy theo lô cho
 * cả lớp, không theo từng học sinh.</p>
 */
@Service
public class HomeworkInsightService {

    static final int ROUNDS = 4;
    static final int MIN_QUESTIONS_PER_GROUP = 3;
    private static final List<ClassSession.Status> EXCLUDED_STATUSES =
            List.of(ClassSession.Status.CANCELLED, ClassSession.Status.RESCHEDULED);
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Map<Question.QuestionType, String> TYPE_LABELS = Map.of(
            Question.QuestionType.MULTIPLE_CHOICE, "trắc nghiệm",
            Question.QuestionType.MULTIPLE_ANSWER, "chọn nhiều đáp án",
            Question.QuestionType.TRUE_FALSE, "đúng/sai",
            Question.QuestionType.FILL_IN_BLANK, "điền từ",
            Question.QuestionType.WORD_BANK, "chọn từ điền vào chỗ trống",
            Question.QuestionType.SENTENCE_BUILDING, "sắp xếp câu",
            Question.QuestionType.ESSAY, "tự luận",
            Question.QuestionType.SPEAKING, "nói");
    private static final Map<Question.Difficulty, String> DIFFICULTY_LABELS = Map.of(
            Question.Difficulty.EASY, "câu dễ", Question.Difficulty.MEDIUM, "câu mức trung bình", Question.Difficulty.HARD, "câu khó");

    private final ClassSessionRepository classSessionRepository;
    private final StudentCommentRepository studentCommentRepository;
    private final ExerciseAssignmentRepository exerciseAssignmentRepository;
    private final ExerciseAttemptRepository exerciseAttemptRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final StudentAnswerGradingRepository studentAnswerGradingRepository;

    public HomeworkInsightService(ClassSessionRepository classSessionRepository,
                                  StudentCommentRepository studentCommentRepository,
                                  ExerciseAssignmentRepository exerciseAssignmentRepository,
                                  ExerciseAttemptRepository exerciseAttemptRepository,
                                  StudentAnswerRepository studentAnswerRepository,
                                  StudentAnswerGradingRepository studentAnswerGradingRepository) {
        this.classSessionRepository = classSessionRepository;
        this.studentCommentRepository = studentCommentRepository;
        this.exerciseAssignmentRepository = exerciseAssignmentRepository;
        this.exerciseAttemptRepository = exerciseAttemptRepository;
        this.studentAnswerRepository = studentAnswerRepository;
        this.studentAnswerGradingRepository = studentAnswerGradingRepository;
    }

    /** 1 kênh BTVN của 1 lần: kỹ năng + Lô online (có thể null) + điểm nhập tay (có thể null). */
    private record ChannelData(String skill, HomeworkSkillBatch batch, String manual) {
    }

    /** Kết quả tự động của 1 Lô cho 1 học sinh — mirror {@code HomeworkProgressService#grammarProgressLabel(List, Long)}. */
    private record BatchOutcome(Integer percent, boolean notDone, boolean late) {
    }

    /**
     * UC-74 bước 6 (bổ sung 2026-09-30): các ý BTVN bằng lời cho từng học sinh, theo thứ tự ưu tiên.
     *
     * @param manualCurrent điểm BTVN buổi trước giáo viên đang nhập trên bảng (theo studentId), có thể rỗng.
     * @return studentId → danh sách ý (chỉ chứa học sinh có ít nhất 1 ý).
     */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> describe(ClassSession session, List<Long> studentIds, Map<Long, HomeworkScoreInput> manualCurrent) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        List<ClassSession> before = previousSessions(session);
        if (before.isEmpty()) {
            return Map.of();
        }
        boolean foreign = session.getTeacherType() == ClassSession.TeacherType.FOREIGN;
        // comments.get(0) = buổi đang nhận xét; comments.get(k) = buổi thứ k trước đó.
        List<Map<Long, StudentComment>> comments = new ArrayList<>();
        comments.add(commentsOf(session.getId(), studentIds));
        before.forEach(s -> comments.add(commentsOf(s.getId(), studentIds)));

        Map<Long, List<Exercise>> exercisesByBatch = new HashMap<>();
        for (int k = 1; k < comments.size(); k++) {
            for (StudentComment comment : comments.get(k).values()) {
                for (HomeworkSkillBatch batch : java.util.Arrays.asList(comment.getHomeworkNextGrammarBatch(),
                        comment.getHomeworkNextReadingBatch(), comment.getHomeworkNextWritingBatch())) {
                    if (batch != null) {
                        exercisesByBatch.computeIfAbsent(batch.getId(), id -> exerciseAssignmentRepository.findByHomeworkBatchId(id).stream()
                                .map(ExerciseAssignment::getExercise).distinct().toList());
                    }
                }
            }
        }
        Set<Long> exerciseIds = exercisesByBatch.values().stream().flatMap(List::stream).map(Exercise::getId).collect(Collectors.toSet());
        // studentId → exerciseId → các lượt làm, lượt MỚI NHẤT trước.
        Map<Long, Map<Long, List<ExerciseAttempt>>> attempts = new HashMap<>();
        if (!exerciseIds.isEmpty()) {
            for (ExerciseAttempt attempt : exerciseAttemptRepository.findByExerciseIdInAndStudentIdIn(exerciseIds, studentIds)) {
                attempts.computeIfAbsent(attempt.getStudent().getId(), k -> new HashMap<>())
                        .computeIfAbsent(attempt.getExercise().getId(), k -> new ArrayList<>()).add(attempt);
            }
            attempts.values().forEach(byExercise -> byExercise.values()
                    .forEach(list -> list.sort(Comparator.comparingInt(ExerciseAttempt::getAttemptNumber).reversed())));
        }
        Map<Long, List<HomeworkHistoryInsight.Point>> points = points(comments.get(1), exercisesByBatch, attempts);

        Map<Long, List<String>> result = new HashMap<>();
        for (Long studentId : studentIds) {
            Map<Long, List<ExerciseAttempt>> studentAttempts = attempts.getOrDefault(studentId, Map.of());
            List<HomeworkHistoryInsight.Round> rounds = new ArrayList<>();
            for (int k = 1; k < comments.size(); k++) {
                rounds.add(round(channels(comments.get(k).get(studentId), manualOf(k, studentId, comments, manualCurrent), foreign),
                        exercisesByBatch, studentAttempts));
            }
            boolean retryImproved = retryImproved(comments.get(1).get(studentId), exercisesByBatch, studentAttempts);
            List<String> insights = HomeworkHistoryInsight.describe(new HomeworkHistoryInsight.Input(rounds, retryImproved,
                    points.getOrDefault(studentId, List.of())));
            if (!insights.isEmpty()) {
                result.put(studentId, insights);
            }
        }
        return result;
    }

    private List<ClassSession> previousSessions(ClassSession session) {
        List<ClassSession> candidates = session.getTeacherType() != null
                ? classSessionRepository.findSessionsBeforeWithTeacherTypeOrderedDesc(session.getSchoolClass().getId(),
                session.getSessionDate(), session.getId(), session.getTeacherType(), EXCLUDED_STATUSES)
                : classSessionRepository.findSessionsBeforeOrderedDesc(session.getSchoolClass().getId(),
                session.getSessionDate(), session.getId(), EXCLUDED_STATUSES);
        return candidates.stream().limit(ROUNDS).toList();
    }

    private Map<Long, StudentComment> commentsOf(Long classSessionId, List<Long> studentIds) {
        return studentCommentRepository.findByClassSessionIdAndStudentIdIn(classSessionId, studentIds).stream()
                .collect(Collectors.toMap(c -> c.getStudent().getId(), c -> c, (a, b) -> a));
    }

    /**
     * Điểm nhập tay của lần BTVN thứ k nằm ở nhận xét buổi SAU đó (buổi k-1): lần gần nhất (k=1) ưu tiên giá trị giáo
     * viên đang gõ trên bảng, không có thì lấy bản đã lưu. Trả mảng [kênh chính, video, Reading, Writing].
     */
    private static String[] manualOf(int k, Long studentId, List<Map<Long, StudentComment>> comments,
                                     Map<Long, HomeworkScoreInput> manualCurrent) {
        if (k == 1 && manualCurrent != null && manualCurrent.containsKey(studentId)) {
            HomeworkScoreInput input = manualCurrent.get(studentId);
            return new String[]{input.offline(), input.speaking(), input.reading(), input.writing()};
        }
        StudentComment comment = comments.get(k - 1).get(studentId);
        return comment == null ? new String[4] : new String[]{comment.getHomeworkPreviousScore(),
                comment.getHomeworkPreviousSpeakingScore(), comment.getHomeworkPreviousReadingScore(), comment.getHomeworkPreviousWritingScore()};
    }

    private static List<ChannelData> channels(StudentComment assigned, String[] manual, boolean foreign) {
        return List.of(
                new ChannelData(HomeworkScoreInsight.mainChannelSkill(foreign), assigned == null ? null : assigned.getHomeworkNextGrammarBatch(), manual[0]),
                new ChannelData(HomeworkScoreInsight.videoChannelSkill(foreign), null, manual[1]),
                new ChannelData(HomeworkScoreInsight.SKILL_READING, assigned == null ? null : assigned.getHomeworkNextReadingBatch(), manual[2]),
                new ChannelData(HomeworkScoreInsight.SKILL_WRITING, assigned == null ? null : assigned.getHomeworkNextWritingBatch(), manual[3]));
    }

    private static HomeworkHistoryInsight.Round round(List<ChannelData> channels, Map<Long, List<Exercise>> exercisesByBatch,
                                                      Map<Long, List<ExerciseAttempt>> studentAttempts) {
        List<HomeworkHistoryInsight.SkillResult> skills = new ArrayList<>();
        boolean late = false;
        for (ChannelData channel : channels) {
            List<Integer> percents = new ArrayList<>();
            boolean notDone = false;
            boolean assigned = false;
            if (channel.batch() != null) {
                BatchOutcome outcome = outcome(exercisesByBatch.getOrDefault(channel.batch().getId(), List.of()), studentAttempts);
                if (outcome != null) {
                    assigned = true;
                    notDone |= outcome.notDone();
                    late |= outcome.late();
                    if (outcome.percent() != null) {
                        percents.add(outcome.percent());
                    }
                }
            }
            HomeworkScoreInsight.Level manualLevel = HomeworkScoreInsight.levelOf(channel.manual());
            if (manualLevel != null) {
                assigned = true;
                if (manualLevel == HomeworkScoreInsight.Level.NOT_DONE) {
                    notDone = true;
                } else {
                    HomeworkScoreInsight.percentOf(channel.manual()).ifPresent(percents::add);
                }
            }
            if (assigned) {
                Integer percent = percents.isEmpty() ? null
                        : (int) Math.round(percents.stream().mapToInt(Integer::intValue).average().orElse(0));
                skills.add(new HomeworkHistoryInsight.SkillResult(channel.skill(), percent, notDone));
            }
        }
        return new HomeworkHistoryInsight.Round(skills, late);
    }

    /** {@code null} nếu Lô không có Bài nào; "chưa làm" nếu không Bài nào có lượt làm; % = tổng điểm / tổng điểm tối đa. */
    private static BatchOutcome outcome(List<Exercise> exercises, Map<Long, List<ExerciseAttempt>> studentAttempts) {
        if (exercises.isEmpty()) {
            return null;
        }
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalPoints = BigDecimal.ZERO;
        boolean anyAttempted = false;
        boolean pending = false;
        boolean late = false;
        for (Exercise exercise : exercises) {
            List<ExerciseAttempt> list = studentAttempts.getOrDefault(exercise.getId(), List.of());
            if (list.isEmpty()) {
                continue;
            }
            anyAttempted = true;
            ExerciseAttempt latest = list.get(0);
            late |= latest.isLateSubmission();
            if (latest.getTotalScore() == null) {
                pending = true;
                continue;
            }
            totalScore = totalScore.add(latest.getTotalScore());
            totalPoints = totalPoints.add(exercise.getTotalPoints() == null ? BigDecimal.ZERO : exercise.getTotalPoints());
        }
        if (!anyAttempted) {
            return new BatchOutcome(null, true, false);
        }
        if (pending || totalPoints.signum() <= 0) {
            return new BatchOutcome(null, false, late);
        }
        int percent = totalScore.divide(totalPoints, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).intValue();
        return new BatchOutcome(percent, false, late);
    }

    /** Có Bài làm ≥ 2 lượt đã chấm và lượt mới nhất cao điểm hơn lượt đầu. */
    private static boolean retryImproved(StudentComment assigned, Map<Long, List<Exercise>> exercisesByBatch,
                                         Map<Long, List<ExerciseAttempt>> studentAttempts) {
        for (Exercise exercise : exercisesOf(assigned, exercisesByBatch).keySet()) {
            List<ExerciseAttempt> graded = studentAttempts.getOrDefault(exercise.getId(), List.of()).stream()
                    .filter(a -> a.getTotalScore() != null).toList();
            if (graded.size() >= 2 && graded.get(0).getTotalScore().compareTo(graded.get(graded.size() - 1).getTotalScore()) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Bài của các Lô giao ở 1 buổi, kèm kỹ năng của kênh. */
    private static Map<Exercise, String> exercisesOf(StudentComment assigned, Map<Long, List<Exercise>> exercisesByBatch) {
        Map<Exercise, String> result = new LinkedHashMap<>();
        if (assigned == null) {
            return result;
        }
        boolean foreign = assigned.getClassSession() != null
                && assigned.getClassSession().getTeacherType() == ClassSession.TeacherType.FOREIGN;
        addBatch(result, assigned.getHomeworkNextGrammarBatch(), HomeworkScoreInsight.mainChannelSkill(foreign), exercisesByBatch);
        addBatch(result, assigned.getHomeworkNextReadingBatch(), HomeworkScoreInsight.SKILL_READING, exercisesByBatch);
        addBatch(result, assigned.getHomeworkNextWritingBatch(), HomeworkScoreInsight.SKILL_WRITING, exercisesByBatch);
        return result;
    }

    private static void addBatch(Map<Exercise, String> result, HomeworkSkillBatch batch, String skill, Map<Long, List<Exercise>> exercisesByBatch) {
        if (batch != null) {
            exercisesByBatch.getOrDefault(batch.getId(), List.of()).forEach(e -> result.putIfAbsent(e, skill));
        }
    }

    /**
     * Điểm cụ thể của BTVN buổi trước (lượt mới nhất mỗi Bài): tỷ lệ đúng theo dạng câu và theo độ khó (câu chấm tự
     * động, mỗi nhóm ≥ {@link #MIN_QUESTIONS_PER_GROUP} câu), và % từng tiêu chí chấm tự luận/nói (bản chấm hiện hành).
     */
    private Map<Long, List<HomeworkHistoryInsight.Point>> points(Map<Long, StudentComment> assignedComments,
                                                                 Map<Long, List<Exercise>> exercisesByBatch,
                                                                 Map<Long, Map<Long, List<ExerciseAttempt>>> attempts) {
        Map<Long, Long> studentByAttempt = new HashMap<>();
        Map<Long, String> skillByAttempt = new HashMap<>();
        assignedComments.forEach((studentId, comment) -> exercisesOf(comment, exercisesByBatch).forEach((exercise, skill) -> {
            List<ExerciseAttempt> list = attempts.getOrDefault(studentId, Map.of()).getOrDefault(exercise.getId(), List.of());
            if (!list.isEmpty()) {
                studentByAttempt.put(list.get(0).getId(), studentId);
                skillByAttempt.put(list.get(0).getId(), skill);
            }
        }));
        if (studentByAttempt.isEmpty()) {
            return Map.of();
        }
        List<StudentAnswer> answers = studentAnswerRepository.findWithQuestionByExerciseAttemptIdIn(studentByAttempt.keySet());
        Map<Long, List<CriteriaScoreItem>> criteriaByAnswer = new HashMap<>();
        List<Long> manualAnswerIds = answers.stream().filter(a -> !a.isAutoGradable()).map(StudentAnswer::getId).toList();
        if (!manualAnswerIds.isEmpty()) {
            for (StudentAnswerGrading grading : studentAnswerGradingRepository.findByStudentAnswerIdInAndLatestIsTrue(manualAnswerIds)) {
                if (grading.getCriteriaScores() != null) {
                    criteriaByAnswer.put(grading.getStudentAnswer().getId(), grading.getCriteriaScores());
                }
            }
        }

        // studentId → nhãn nhóm → [số đúng, tổng] (dạng câu / độ khó) hoặc danh sách % (tiêu chí).
        Map<Long, Map<String, int[]>> typeGroups = new HashMap<>();
        Map<Long, Map<String, int[]>> difficultyGroups = new HashMap<>();
        Map<Long, Map<String, List<Integer>>> criteriaGroups = new HashMap<>();
        for (StudentAnswer answer : answers) {
            Long attemptId = answer.getExerciseAttempt().getId();
            Long studentId = studentByAttempt.get(attemptId);
            String skill = skillByAttempt.get(attemptId);
            Question question = answer.getQuestion();
            if (answer.isAutoGradable() && answer.getCorrect() != null) {
                int correct = Boolean.TRUE.equals(answer.getCorrect()) ? 1 : 0;
                String typeLabel = TYPE_LABELS.get(question.getQuestionType());
                if (typeLabel != null) {
                    add(typeGroups, studentId, "dạng câu " + typeLabel + " trong bài " + skill, correct);
                }
                String difficultyLabel = question.getDifficulty() == null ? null : DIFFICULTY_LABELS.get(question.getDifficulty());
                if (difficultyLabel != null) {
                    add(difficultyGroups, studentId, difficultyLabel + " trong bài " + skill, correct);
                }
            }
            for (CriteriaScoreItem item : criteriaByAnswer.getOrDefault(answer.getId(), List.of())) {
                if (item.criterion() != null && !item.criterion().isBlank() && !DIGIT.matcher(item.criterion()).find()) {
                    criteriaGroups.computeIfAbsent(studentId, k -> new LinkedHashMap<>())
                            .computeIfAbsent("tiêu chí \"" + item.criterion().trim() + "\" trong bài " + skill, k -> new ArrayList<>())
                            .add(item.percent());
                }
            }
        }

        Map<Long, List<HomeworkHistoryInsight.Point>> result = new HashMap<>();
        Set<Long> studentIds = new HashSet<>(typeGroups.keySet());
        studentIds.addAll(difficultyGroups.keySet());
        studentIds.addAll(criteriaGroups.keySet());
        for (Long studentId : studentIds) {
            List<HomeworkHistoryInsight.Point> list = new ArrayList<>();
            addRatioPoints(list, typeGroups.get(studentId));
            addRatioPoints(list, difficultyGroups.get(studentId));
            criteriaGroups.getOrDefault(studentId, Map.of()).forEach((label, values) -> list.add(new HomeworkHistoryInsight.Point(label,
                    (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0)), true)));
            result.put(studentId, list);
        }
        return result;
    }

    private static void add(Map<Long, Map<String, int[]>> groups, Long studentId, String label, int correct) {
        int[] counts = groups.computeIfAbsent(studentId, k -> new LinkedHashMap<>()).computeIfAbsent(label, k -> new int[2]);
        counts[0] += correct;
        counts[1]++;
    }

    /** Nhóm đủ số câu mới xét; chỉ cho khen khi học sinh có ≥ 2 nhóm cùng loại (khen 1 nhóm duy nhất = khen cả bài). */
    private static void addRatioPoints(List<HomeworkHistoryInsight.Point> list, Map<String, int[]> groups) {
        if (groups == null) {
            return;
        }
        Map<String, int[]> eligible = new LinkedHashMap<>();
        groups.forEach((label, counts) -> {
            if (counts[1] >= MIN_QUESTIONS_PER_GROUP) {
                eligible.put(label, counts);
            }
        });
        boolean praisable = eligible.size() >= 2;
        eligible.forEach((label, counts) -> list.add(new HomeworkHistoryInsight.Point(label,
                (int) Math.round(counts[0] * 100.0 / counts[1]), praisable)));
    }
}
