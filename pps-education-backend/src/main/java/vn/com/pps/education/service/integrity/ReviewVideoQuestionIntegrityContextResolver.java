package vn.com.pps.education.service.integrity;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.AttemptIntegrityEvent.AttemptType;
import vn.com.pps.education.domain.ReviewVideoQuestionSubmission;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ReviewVideoQuestionSubmissionRepository;
import vn.com.pps.education.repository.StudentRepository;

/**
 * UC-23b: không có "phiên bắt đầu ghi âm" ở backend — sự kiện được đệm ở
 * client trong lúc ghi âm rồi gửi kèm CÙNG LÚC với submitQuestionAudio
 * (xem ReviewVideoService.submitQuestionAudio), nên attemptId luôn đã tồn
 * tại (là submission vừa tạo) khi resolveForOwner được gọi — khác Exercise
 * (attempt tồn tại trước, sự kiện gửi real-time trong lúc làm).
 */
@Component
public class ReviewVideoQuestionIntegrityContextResolver implements AttemptIntegrityContextResolver {

    private final ReviewVideoQuestionSubmissionRepository reviewVideoQuestionSubmissionRepository;
    private final StudentRepository studentRepository;

    public ReviewVideoQuestionIntegrityContextResolver(ReviewVideoQuestionSubmissionRepository reviewVideoQuestionSubmissionRepository,
                                                         StudentRepository studentRepository) {
        this.reviewVideoQuestionSubmissionRepository = reviewVideoQuestionSubmissionRepository;
        this.studentRepository = studentRepository;
    }

    @Override
    public boolean supports(AttemptType type) {
        return type == AttemptType.REVIEW_VIDEO_QUESTION;
    }

    @Override
    public AttemptContext resolveForOwner(Long attemptId, Long actorUserId) {
        var student = studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideoQuestionIntegrityContext.studentProfileNotFound",
                        new Object[]{actorUserId}, "Tài khoản id=" + actorUserId + " không có hồ sơ học sinh."));
        ReviewVideoQuestionSubmission submission = reviewVideoQuestionSubmissionRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideoQuestionIntegrityContext.submissionNotFound",
                        new Object[]{attemptId}, "Không tìm thấy bài nộp id=" + attemptId));
        if (!submission.getStudent().getId().equals(student.getId())) {
            throw new ResourceNotFoundException("error.reviewVideoQuestionIntegrityContext.submissionNotFound",
                    new Object[]{attemptId}, "Không tìm thấy bài nộp id=" + attemptId);
        }
        return toContext(submission);
    }

    @Override
    public AttemptContext resolveForReading(Long attemptId) {
        ReviewVideoQuestionSubmission submission = reviewVideoQuestionSubmissionRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideoQuestionIntegrityContext.submissionNotFound",
                        new Object[]{attemptId}, "Không tìm thấy bài nộp id=" + attemptId));
        return toContext(submission);
    }

    private AttemptContext toContext(ReviewVideoQuestionSubmission submission) {
        // Legacy trước V69 có thể NULL (reviewVideoAssignment map thô lúc đó chưa bắt buộc) — an toàn hoá.
        var schoolClass = submission.getReviewVideoAssignment() == null ? null : submission.getReviewVideoAssignment().getSchoolClass();
        return new AttemptContext(submission.getStudent(), schoolClass, "câu hỏi video ôn tập");
    }
}
