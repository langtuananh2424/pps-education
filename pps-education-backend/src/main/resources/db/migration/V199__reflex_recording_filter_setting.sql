-- Bổ sung ngoài SDD gốc (UC-23b Video phản xạ), đã xác nhận với người dùng 2026-09-29: công tắc bộ lọc thu âm.
--
-- Mã tham chiếu bàn giao 29/9 của phòng đào tạo lọc bản ghi trước khi gửi (lọc thông cao 90 Hz, thông thấp 7800 Hz,
-- cổng dìm tiếng nền −7 dB giữa các đoạn nói). App PPS ghi âm THÔ từ V183 (đo được bộ lọc ồn của trình duyệt làm mất
-- dải <2000 Hz). Người dùng chọn: cài bộ lọc (KHÔNG bật lọc ồn của trình duyệt) sau một công tắc, MẶC ĐỊNH TẮT, để so
-- độ chính xác phiên âm giữa hai chế độ trên bài thật rồi mới quyết định. Bật/tắt ở Quản trị hệ thống → Cài đặt hệ
-- thống (nhóm Cờ tính năng), có hiệu lực từ lượt ghi âm kế tiếp, không cần deploy.
INSERT INTO system_settings (setting_key, setting_value, description, category) VALUES
('reflex.recording_filter_enabled', 'false',
 'Video phản xạ: lọc bản ghi âm của học sinh trước khi nộp (lọc tần 90–7800 Hz + dìm tiếng nền −7 dB, không bật lọc ồn trình duyệt). Tắt = gửi bản ghi thô như hiện nay. Dùng để so sánh độ chính xác phiên âm giữa hai chế độ.',
 'FEATURE_FLAG');

-- Chế độ thu âm THỰC TẾ của từng lần ghi âm (true = đã lọc, false = thô, NULL = lần ghi trước V199) — căn cứ để so
-- sánh hai chế độ; công tắc có thể đổi giữa chừng nên không suy ngược từ system_settings được.
ALTER TABLE reflex_question_progress_history ADD COLUMN recording_filter BOOLEAN;
