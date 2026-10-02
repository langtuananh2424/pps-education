-- Bổ sung ngoài SDD gốc (UC-74 Trợ lý AI soạn nháp nhận xét), đã xác nhận với người dùng 2026-10-01.
--
-- Lưu NGUYÊN VĂN bản nhận xét trợ lý AI soạn tại lúc giáo viên áp dụng vào bảng (lần áp dụng gần nhất). So với
-- content cuối cùng giáo viên gửi duyệt sẽ biết giáo viên giữ nguyên hay phải sửa nhiều — chỉ số chất lượng sát nhất
-- của trợ lý (ai_drafted ở V201 chỉ cho biết có xuất phát từ AI hay không). Xem scripts/comment-ai-approved-metrics.sql.
-- NULL = không xuất phát từ AI, hoặc dữ liệu trước V208. Không hiển thị cho phụ huynh, không dùng trong luồng duyệt.
ALTER TABLE student_comments ADD COLUMN ai_draft_content TEXT NULL;

COMMENT ON COLUMN student_comments.ai_draft_content IS
    'Nguyên văn nhận xét trợ lý AI soạn lúc giáo viên áp dụng (UC-74, V208) — để đo mức giáo viên sửa bản AI; NULL nếu không từ AI hoặc trước V208.';
