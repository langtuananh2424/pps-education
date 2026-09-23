package vn.com.pps.education.dto;

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22 — 1 mã cấu trúc khả dụng để giáo viên
 * chọn Key Grammar (filter 2) cho 1 câu hỏi ESSAY, nguồn cho GET /api/questions/{id}/key-grammar-options
 * và GET /api/exams/{examId}/questions/{questionId}/key-grammar-options (dropdown chọn tay ở modal "Sửa
 * câu hỏi", mọi Khối cùng 1 kiểu UI — xem QuestionBankService#listKeyGrammarOptions).
 */
public record KeyGrammarStructureResponse(String id, String name, boolean base) {}
