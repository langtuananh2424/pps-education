-- Bổ sung ngoài SDD gốc (UC-76 Trợ lý AI soạn nháp nhận xét Giữa kỳ/Cuối kỳ), đã xác nhận với người dùng 2026-10-05.
--
-- Cùng cách đo chất lượng với nhận xét hằng ngày của UC-74 (student_comments V201/V208), áp cho Nhận xét kỳ nằm trong
-- sổ điểm (grade_evaluation_results.comment, V95):
--   ai_drafted       — TRUE nếu Nhận xét xuất phát từ bản nháp của trợ lý AI (giáo viên bấm "Áp dụng" từ trợ lý;
--                      giáo viên vẫn có thể đã sửa tay sau đó). Chỉ bật lên, không tắt lại; dòng cũ = FALSE.
--   ai_draft_content — NGUYÊN VĂN bản trợ lý soạn ở lần áp dụng gần nhất; so với comment cuối cùng để biết giáo viên
--                      giữ nguyên hay phải sửa nhiều. NULL = không từ AI. Không trả ra API phụ huynh, không dùng khi duyệt.
ALTER TABLE grade_evaluation_results ADD COLUMN ai_drafted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE grade_evaluation_results ADD COLUMN ai_draft_content TEXT NULL;

COMMENT ON COLUMN grade_evaluation_results.ai_drafted IS
    'TRUE nếu Nhận xét kỳ xuất phát từ bản nháp của trợ lý AI (UC-76); FALSE = giáo viên tự viết/import hoặc dữ liệu trước V212.';
COMMENT ON COLUMN grade_evaluation_results.ai_draft_content IS
    'Nguyên văn Nhận xét kỳ trợ lý AI soạn lúc giáo viên áp dụng (UC-76, V212) — để đo mức giáo viên sửa bản AI; NULL nếu không từ AI.';
