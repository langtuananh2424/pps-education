-- Chỉ số chất lượng nhận xét hằng ngày trên dữ liệu THẬT (đã gửi duyệt), so sánh nhận xét do trợ lý AI soạn
-- (student_comments.ai_drafted = TRUE, có từ V201) với nhận xét giáo viên tự viết. Bổ sung 2026-09-29 (UC-74).
--
-- Khác scripts/comment-ai-metrics.py (đo bản nháp AI ngay lúc soạn, trước khi giáo viên sửa): script này đo bản
-- cuối cùng giáo viên đã gửi và kết quả duyệt của Quản lý. Chỉ đọc, không sửa dữ liệu.
--
-- Cách chạy (ví dụ trong docker compose):
--   docker compose exec -T postgres psql -U <user> -d <db> -v weeks=8 -f - < scripts/comment-ai-approved-metrics.sql
-- Tham số weeks: số tuần gần nhất cần xem (mặc định 8 nếu không truyền -v weeks=...).
--
-- Cột:
--   gui_duyet          số nhận xét đã gửi (Chờ duyệt / Đã duyệt / Bị từ chối)
--   da_duyet_pct       % đã duyệt trong số đã có quyết định
--   tung_bi_tu_choi_pct % từng bị Quản lý từ chối ít nhất 1 lần (rejection_reason không rỗng — cột này không bị xoá khi gửi lại)
--   do_dai_tb          số ký tự trung bình của Nhận xét
--   co_chu_so_pct      % nhận xét có chữ số (dễ là lộ điểm)
--   nhac_ten_bai_pct   % nhận xét chứa nguyên văn "Bài học hôm nay" của buổi
--   yeu_tb_pct         % Thái độ Yếu/Trung bình (theo dõi AI có đẩy mức Thái độ lệch không)
--   Bổ sung 2026-10-01 (V208 ai_draft_content — chỉ dòng AI soạn từ V208 trở đi mới có, dòng khác để trống):
--   co_ban_ai          số dòng có lưu bản AI gốc
--   giu_nguyen_pct     % giáo viên gửi y nguyên bản AI (bỏ khoảng trắng đầu/cuối)
--   giong_ban_ai_tb    độ giống trung bình giữa bản gửi và bản AI (0..1, Jaccard trên tập TỪ — thô hơn phép so
--                      cụm 3 từ của CommentSimilarity nhưng đủ để thấy giáo viên sửa ít hay nhiều)
--   sua_nhieu_pct      % dòng độ giống < 0.3 (giáo viên viết lại phần lớn bản AI; sửa vừa phải thường ~0.4-0.6)

\if :{?weeks}
\else
\set weeks 8
\endif

WITH base AS (
    SELECT
        to_char(date_trunc('week', sc.comment_date), 'IYYY-"W"IW') AS tuan,
        CASE WHEN sc.ai_drafted THEN 'AI soạn' ELSE 'Tự viết' END AS nguon,
        sc.status,
        sc.rejection_reason,
        sc.attitude,
        coalesce(sc.content, '') AS content,
        nullif(btrim(cs.lesson_content), '') AS lesson_content,
        sc.ai_draft_content,
        CASE WHEN sc.ai_draft_content IS NOT NULL THEN (
            SELECT count(*) FROM (SELECT unnest(w.a) INTERSECT SELECT unnest(w.b)) i
        )::numeric / nullif((
            SELECT count(*) FROM (SELECT unnest(w.a) UNION SELECT unnest(w.b)) u
        ), 0) END AS giong_ban_ai
    FROM student_comments sc
    CROSS JOIN LATERAL (
        SELECT regexp_split_to_array(btrim(regexp_replace(lower(coalesce(sc.content, '')), '[^[:alnum:]]+', ' ', 'g')), ' ') AS a,
               regexp_split_to_array(btrim(regexp_replace(lower(coalesce(sc.ai_draft_content, '')), '[^[:alnum:]]+', ' ', 'g')), ' ') AS b
    ) w
    JOIN class_sessions cs ON cs.id = sc.class_session_id
    WHERE sc.comment_type = 'DAILY'
      AND sc.status IN ('PENDING', 'APPROVED', 'REJECTED')
      AND sc.comment_date >= current_date - (:weeks * 7)
)
SELECT
    CASE WHEN GROUPING(tuan) = 1 THEN 'Tổng' ELSE tuan END AS tuan,
    nguon,
    count(*) AS gui_duyet,
    round(100.0 * count(*) FILTER (WHERE status = 'APPROVED')
          / nullif(count(*) FILTER (WHERE status IN ('APPROVED', 'REJECTED')), 0), 1) AS da_duyet_pct,
    round(100.0 * count(*) FILTER (WHERE rejection_reason IS NOT NULL AND btrim(rejection_reason) <> '') / count(*), 1) AS tung_bi_tu_choi_pct,
    round(avg(length(content)))::int AS do_dai_tb,
    round(100.0 * count(*) FILTER (WHERE content ~ '[0-9]') / count(*), 1) AS co_chu_so_pct,
    round(100.0 * count(*) FILTER (WHERE lesson_content IS NOT NULL
                                     AND position(lower(lesson_content) IN lower(content)) > 0) / count(*), 1) AS nhac_ten_bai_pct,
    round(100.0 * count(*) FILTER (WHERE attitude IN ('WEAK', 'AVERAGE')) / count(*), 1) AS yeu_tb_pct,
    count(ai_draft_content) AS co_ban_ai,
    round(100.0 * count(*) FILTER (WHERE btrim(content) = btrim(ai_draft_content)) / nullif(count(ai_draft_content), 0), 1) AS giu_nguyen_pct,
    round(avg(giong_ban_ai), 2) AS giong_ban_ai_tb,
    round(100.0 * count(*) FILTER (WHERE giong_ban_ai < 0.3) / nullif(count(ai_draft_content), 0), 1) AS sua_nhieu_pct
FROM base
GROUP BY ROLLUP (tuan), nguon
ORDER BY GROUPING(base.tuan), base.tuan, nguon;
