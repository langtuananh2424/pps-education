package vn.com.pps.education.lms.dto;

import java.util.List;

/**
 * V226 (Nghe ép, đã xác nhận với người dùng 2026-11-10) — kết quả import Excel riêng cho Bài Nghe ép. Có
 * {@code errors} thì KHÔNG tạo gì cả (all-or-nothing); mỗi lỗi ghi rõ số dòng.
 */
public record ForcedListeningImportResponse(int createdSections, int createdQuestions, List<String> errors) {}
