package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.common.ReflexQuestionFormat;
import vn.com.pps.education.common.ReflexV2Task;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.ReviewVideoQuestion;
import vn.com.pps.education.dto.ReflexQuestionFormatOptionResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.CurriculumRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * V200 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — UC-23b: dạng đề của câu hỏi Video phản xạ.
 * Nguồn chân lý DUY NHẤT của "khối/tuyến nào được chọn dạng đề nào" là {@link ReflexV2Task#allowedFormats} (cùng bảng
 * dùng để chấm) — FE hỏi danh sách qua {@link #optionsForCurriculum}, lúc lưu câu hỏi thì kiểm tra lại ở
 * {@link #applyTo}. Xem docs/uc/phan-he-07-lms-portal.md (UC-23b).
 */
@Service
public class ReflexQuestionFormatService {

    /** Mô tả tranh 2-3 dòng; chặn trên để không ai dán nguyên bài văn vào prompt chấm. */
    static final int MAX_PICTURE_BRIEF_CHARS = 1000;

    private final CurriculumRepository curriculumRepository;
    private final MediaStorageService mediaStorageService;

    public ReflexQuestionFormatService(CurriculumRepository curriculumRepository, MediaStorageService mediaStorageService) {
        this.curriculumRepository = curriculumRepository;
        this.mediaStorageService = mediaStorageService;
    }

    /** Các dạng đề chọn được cho chương trình; rỗng = chương trình chưa có bộ tiêu chí (chấm bằng luồng cũ). */
    @Transactional(readOnly = true)
    public List<ReflexQuestionFormatOptionResponse> optionsForCurriculum(Long curriculumId) {
        Curriculum curriculum = curriculumRepository.findById(curriculumId)
                .orElseThrow(() -> new ResourceNotFoundException("error.reviewVideo.curriculumNotFound", new Object[]{curriculumId},
                        "Không tìm thấy chương trình id=" + curriculumId));
        return ReflexV2Task.allowedFormats(curriculum.getGradeLevel(), curriculum.getTrack()).entrySet().stream()
                .map(e -> new ReflexQuestionFormatOptionResponse(e.getKey().name(), e.getValue().formatLabel(),
                        e.getValue().seconds(), e.getKey() == ReflexQuestionFormat.PICTURE))
                .toList();
    }

    /**
     * Kiểm tra + gán dạng đề và dữ liệu tranh vào câu hỏi (gọi trong giao dịch thêm/sửa câu hỏi).
     * <ul>
     *   <li>Không chọn dạng đề → câu hỏi suy dạng theo thời lượng như trước; xoá dữ liệu tranh.</li>
     *   <li>Chương trình chưa có bộ tiêu chí, hoặc dạng đề không hợp lệ với khối/tuyến → 400.</li>
     *   <li>Tả tranh: bắt buộc mô tả tranh; ảnh (nếu có) phải là file hệ thống đã lưu.</li>
     *   <li>Dạng khác: xoá dữ liệu tranh (đổi từ tả tranh sang dạng khác không để sót mô tả cũ vào prompt).</li>
     * </ul>
     */
    public void applyTo(ReviewVideoQuestion question, Curriculum curriculum, String format, String pictureImageUrl,
                        String pictureBrief) {
        if (format == null || format.isBlank()) {
            question.setQuestionFormat(null);
            question.setPictureImageUrl(null);
            question.setPictureBrief(null);
            return;
        }
        ReflexQuestionFormat parsed;
        try {
            parsed = ReflexQuestionFormat.valueOf(format.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Dạng đề không hợp lệ: " + format);
        }
        Map<ReflexQuestionFormat, ReflexV2Task> allowed = ReflexV2Task.allowedFormats(curriculum.getGradeLevel(), curriculum.getTrack());
        if (allowed.isEmpty()) {
            throw new IllegalArgumentException("Chương trình của bộ video chưa có bộ tiêu chí chấm theo dạng đề — hãy để trống dạng đề.");
        }
        if (!allowed.containsKey(parsed)) {
            throw new IllegalArgumentException("Dạng đề " + parsed + " không dùng được cho chương trình này. Dạng đề hợp lệ: "
                    + allowed.keySet().stream().map(Enum::name).collect(Collectors.joining(", ")) + ".");
        }
        question.setQuestionFormat(parsed);
        if (parsed != ReflexQuestionFormat.PICTURE) {
            question.setPictureImageUrl(null);
            question.setPictureBrief(null);
            return;
        }
        String brief = pictureBrief == null ? "" : pictureBrief.strip();
        if (brief.isEmpty()) {
            throw new IllegalArgumentException("Dạng tả tranh bắt buộc có mô tả tranh (2–3 dòng) để AI xét đúng/lạc đề.");
        }
        if (brief.length() > MAX_PICTURE_BRIEF_CHARS) {
            throw new IllegalArgumentException("Mô tả tranh tối đa " + MAX_PICTURE_BRIEF_CHARS + " ký tự (chỉ cần 2–3 dòng).");
        }
        String imageUrl = pictureImageUrl == null || pictureImageUrl.isBlank() ? null : pictureImageUrl.trim();
        if (imageUrl != null) {
            mediaStorageService.requireStoredUrl(imageUrl);
        }
        question.setPictureImageUrl(imageUrl);
        question.setPictureBrief(brief);
    }
}
