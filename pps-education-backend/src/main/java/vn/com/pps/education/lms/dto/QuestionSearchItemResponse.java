package vn.com.pps.education.lms.dto;

/**
 * 1 dòng kết quả tìm kiếm câu hỏi xuyên nhiều ngân hàng/khung chương trình
 * (QuestionBankService#searchQuestions, bổ sung 2026-10-19, đã xác nhận với
 * người dùng khi review hiệu năng trang "Ngân hàng câu hỏi") — gộp sẵn
 * thông tin ngân hàng/khung chương trình để FE không cần tự ghép từ danh
 * sách riêng như trước (loadAll flatten nested).
 */
public record QuestionSearchItemResponse(
        QuestionResponse question,
        Long bankId,
        String bankCode,
        String bankName,
        String bankLevel,
        Long curriculumId,
        String curriculumCode,
        String curriculumName
) {}
