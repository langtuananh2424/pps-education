---
target: Toàn bộ 54 màn PPS Admin 2.0 (canvas)
total_score: 25
max_score: 40
na_heuristics: 
p0_count: 1
p1_count: 2
target_identity: "url:https://claude.ai/artifact/7e8UTj9Cb8ecxjqbyLBezn"
timestamp: 2026-09-30T03-42-46Z
slug: claude-ai-artifact-7e8utj9cb8ecxjqbylbezn
---
# Critique: PPS Admin 2.0 — toàn bộ 54 màn (canvas)
Method: dual-agent (A: design review · B: detector). Source-only; browser render skipped (runtime không có cục bộ).

## Design Health Score: 25/40 (Acceptable)
| # | Heuristic | Score | Key issue |
|---|---|---|---|
|1|Visibility of status|3|Sidebar 2 cảnh báo vs CampusDashboard 3|
|2|Match real world|3|"Điểm trường A/B/C" vs tên cơ sở thật|
|3|User control|2|"Duyệt tất cả" gửi phụ huynh không confirm/undo|
|4|Consistency|2|"Chờ duyệt" info vs warning; 11 độ rộng rail|
|5|Error prevention|2|Bulk approve, "Tất cả có mặt" không guard|
|6|Recognition|3|Tiêu đề trang lệch nhãn menu|
|7|Flexibility|2|Chỉ Grading có phím tắt; admin 44 mục không search|
|8|Aesthetic/minimalist|3|Bình tĩnh nhưng đơn điệu; CampusDashboard lặp số|
|9|Error recovery|3|NoticeBanner 24 màn, reject modal có lý do|
|10|Help|2|Mô tả trang dài thay cho trợ giúp tại chỗ|

## Priority issues
- [P0] Màu dữ liệu mang nghĩa mâu thuẫn (teal=doanh thu, pink=chi phí, màu điểm trường) + accent kiêm màu dữ liệu → phá 3/4 palette. Fix: giữ 4 hue cho 4 chỉ số học thuật; tiền dùng ink; điểm trường thang xám; chart không dùng accent. → colorize, document
- [P1] Duyệt hàng loạt tới phụ huynh không có lưới an toàn. Fix: confirm có số lượng/lớp/mục AI gắn cờ + undo 10s; "Chờ duyệt" = amber. → harden, clarify
- [P1] Kiến trúc vai trò/điểm trường: 32 màn dưới admin 44 mục; thiếu HR/TPĐT/kế toán/vận hành/Đại diện trường liên kết; TopBar không có campus scope. → shape
- [P2] Template lan tràn: 7 kiểu trang, 11 rail trái, 7 rail phải, bảng 3 kiểu, sidebar cao cố định 960. Fix: 5 template, rail 320/360, TableContainer, sidebar sticky, nhịp 8px. → layout, document
- [P2] Token màu & tương phản: 5.740 hex, 7 xanh thành công, #8e8e93/#aeaeb2 text fail, chữ 11px. → audit, polish

## Detector
124 findings / 53 files: cramped-padding 113 (≈93 false positive: artboard root, bordered table wrappers), gpt-thin-border-wide-shadow 5 (overlay, ok), em-dash-overuse 2, low-contrast 2 (Lectures #8e8e93 3.3:1), clipped-overflow 1 (Campuses aside), skipped-heading 1 (Documents h1→h3). Không đọc được bundle.css → bỏ sót màu trong PPS.*.

## Persona red flags
Alex: không phím tắt/không nav search/không approve-next. Sam: FAB nhãn chỉ hover, 9 div/span bấm được, tương phản, 11px, legend chỉ màu. Chị Lan: không lọc điểm trường, 4 hàng chờ rời, số lệch, tên giả.

## Minor
Main CTA là <a> tự vẽ; icon sai ở "Hoàn thành bài tập"; cảnh báo "vắng 2 buổi" sai quy tắc; sọc inset 3px ở 5 màn; weight 500 dày đặc; h1→h3 ở Documents.

## Questions
Thumbnail Payroll vs AuditLog? Tiền có nên chung hue với chỉ số học? Vì sao "Duyệt tất cả" dễ bấm nhất? Sidebar 44 mục cho ai? Màn đầu tiên của trường liên kết ở đâu?
