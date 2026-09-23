-- Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22: lưu kết quả chấm Key Grammar (filter 2,
-- gói key-grammar) của TỪNG lượt chấm Writing "v3" — đi cùng V186 (exercises.key_grammar, mã cấu trúc
-- được GIAO cho Bài) nhưng bảng khác: cột này là KẾT QUẢ chấm được (đúng/sai bao nhiêu lần, Đạt/Chưa đạt)
-- của 1 bài làm cụ thể, không phải cấu hình của Bài.
--
-- key_grammar: {"status":"pass"|"fail"|"unparsed","correct":N,"attempts":N,"redoRequired":true|false} —
-- NULL khi Bài không gắn Key Grammar hoặc lượt chấm chưa lên rubric "v3". FE dùng redoRequired để hiện
-- dải cảnh báo "cần viết lại bài" (xem WritingAiGradingService/KeyGrammarOutcome).
ALTER TABLE student_answer_grading ADD COLUMN key_grammar JSONB;
