package vn.com.pps.education.lms.dto;

import java.util.List;

/**
 * SPIKE/TEST riêng (2026-10-22, đã xác nhận với người dùng) — điểm phát âm THEO TỪNG TỪ do Azure
 * Pronunciation Assessment trả về (mảng {@code NBest[0].Words[]}), trước đây bị bỏ qua (chỉ đọc điểm
 * tổng câu). {@code errorType} theo đúng giá trị Azure trả ("None"/"Mispronunciation"/"Omission"/
 * "Insertion"...). {@code phonemes} liệt kê điểm TỪNG ÂM VỊ trong từ đó — chỗ phát âm sai cụ thể nhất.
 */
public record PronunciationWordDetail(
        String word,
        Double accuracyScore,
        String errorType,
        List<PronunciationPhonemeDetail> phonemes
) {
}
