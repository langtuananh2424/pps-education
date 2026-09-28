-- Lay REFLEX_ASSIGNMENT_ID + REFLEX_QUESTION_IDS cho loadtest_reflex_writing_speaking.py
-- tu Bo Video phan xa "G7 (IELTS)_CLIP PHAN XA_UNIT 1 HOBBIES_SUB 1" (ma
-- g7_pxa_lesson_2_1) vua giao cho lop 7E. Chay trong pgAdmin, nham DB STAGING.
--
-- Luu y: "Da gan 2 lop" tren man Kho Video On tap la
-- review_video_set_class_assignments (chi danh dau BO du dieu kien hien thi/
-- dung lam nguon cho lop, KHONG phai lan giao that) -- REFLEX_ASSIGNMENT_ID
-- thuc su dung cho script phai la review_video_assignments (sinh ra khi Giao
-- vien chon Bo nay lam "BTVN buoi sau" o Nhan xet hoc vien, UC-21) -- xem
-- comment trong anh man hinh Kho Video On tap ban gui.

WITH target_set AS (
    SELECT id, code, title
    FROM review_video_sets
    WHERE code = 'g7_pxa_lesson_2_1'
),
target_assignment AS (
    SELECT
        rva.id          AS assignment_id,
        rva.status,
        rva.due_at,
        c.class_code
    FROM review_video_assignments rva
    JOIN classes c ON c.id = rva.class_id
    WHERE rva.review_video_set_id IN (SELECT id FROM target_set)
      AND c.class_code = 'ND-2627-7E'
),
target_questions AS (
    SELECT
        rvq.id AS question_id,
        rvq.prompt,
        rvq.max_recording_seconds,
        rvq.max_attempts,
        rvq.display_order,
        rv.title AS video_title
    FROM review_video_questions rvq
    JOIN review_videos rv ON rv.id = rvq.review_video_id
    WHERE rv.review_video_set_id IN (SELECT id FROM target_set)
    ORDER BY rvq.display_order
)
SELECT
    ta.assignment_id  AS "REFLEX_ASSIGNMENT_ID",
    tq.question_id    AS "REFLEX_QUESTION_ID",
    tq.display_order,
    tq.prompt,
    tq.max_attempts,
    tq.max_recording_seconds,
    ta.status          AS assignment_status,
    ta.due_at
FROM target_assignment ta
CROSS JOIN target_questions tq
ORDER BY tq.display_order;

-- Doc ket qua:
--   - 0 dong -> hoac chua tim thay review_video_assignments cho lop ND-2627-7E
--     (kiem tra lai da giao qua Nhan xet hoc vien/UC-21 that su chua, hay moi
--     chi "gan lop" o Kho Video On tap), hoac sai ma "g7_pxa_lesson_2_1"/
--     ma lop -- doi lai va chay lai 2 cau do bang ILIKE ben duoi.
--   - Nhieu dong -> moi dong 1 cau hoi (dung), ghep het REFLEX_QUESTION_ID
--     thanh 1 chuoi phay ngan cach cho REFLEX_QUESTION_IDS.
--   - Cot max_attempts: NULL = khong gioi han (script se dung MAX_WRITING_ATTEMPTS/
--     MAX_SPEAKING_ATTEMPTS mac dinh 3); co so -> doi bien moi truong cho khop.

-- Do lai neu khong chac ma Bo/ma lop:
-- SELECT id, code, title FROM review_video_sets WHERE code ILIKE '%pxa%' OR title ILIKE '%HOBBIES%';
-- SELECT id, class_code, name FROM classes WHERE class_code ILIKE '%2627%' OR class_code ILIKE '%7E%';
