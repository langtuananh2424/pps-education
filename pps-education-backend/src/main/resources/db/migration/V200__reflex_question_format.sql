-- Bổ sung ngoài SDD gốc (UC-23b Video phản xạ, rubric v3), đã xác nhận với người dùng 2026-09-29: DẠNG ĐỀ tường minh
-- cho từng câu hỏi.
--
-- Trước đây hệ thống SUY dạng đề từ khối/tuyến của chương trình + thời gian ghi âm tối đa (≥60 giây = Part 2, chỉ
-- ở Khối 8-9 IELTS). Rubric v3 có những cặp dạng đề không phân biệt được bằng thời lượng (Khối 8 Cambridge: PET
-- Part 4 và tả tranh đều 60 giây) và dạng mới (Part 2 Khối 7, tả tranh Khối 7-8 Cambridge).
--
-- question_format: SHORT (câu hỏi ngắn) / PART2 (cue card IELTS) / PET4 (câu thảo luận PET Part 4) / PICTURE (tả
--   tranh, PET Speaking Task 2). NULL = câu hỏi cũ — tiếp tục suy theo thời lượng như trước.
-- picture_image_url: ảnh tranh (khung hình chụp từ video tại mốc câu hỏi, hoặc giáo viên tải lên) — chỉ để giáo
--   viên đối chiếu khi soạn mô tả; KHÔNG gửi vào AI chấm.
-- picture_brief: 2-3 dòng mô tả tranh bằng chữ, giáo viên đã duyệt — gửi vào lượt chấm VIẾT và chấm NÓI, chỉ để xét
--   đúng/lạc đề (rubric §C1 dạng PICTURE). KHÔNG bao giờ gửi vào lượt phiên âm, KHÔNG trả cho học sinh.
ALTER TABLE review_video_questions ADD COLUMN question_format VARCHAR(20)
    CHECK (question_format IN ('SHORT', 'PART2', 'PET4', 'PICTURE'));
ALTER TABLE review_video_questions ADD COLUMN picture_image_url VARCHAR(1000);
ALTER TABLE review_video_questions ADD COLUMN picture_brief TEXT;
