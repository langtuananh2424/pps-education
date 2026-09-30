---
target: Toàn bộ màn PPS Admin 2.0 (canvas) — lần 3
total_score: 27
max_score: 40
na_heuristics: 
p0_count: 1
p1_count: 2
target_identity: "url:https://claude.ai/artifact/7e8UTj9Cb8ecxjqbyLBezn"
timestamp: 2026-09-30T04-50-49Z
slug: claude-ai-artifact-7e8utj9cb8ecxjqbylbezn
---
# Critique 3: PPS Admin 2.0 — 63 màn (canvas v50)
Method: dual-agent (A: design review · B: detector). Source-only.

## Design Health Score: 27/40 (Acceptable, sát ngưỡng Good)
|#|Heuristic|Score|Key issue|
|1|Status|3|Màn duyệt riêng không tô sáng mục nào trên sidebar quản lý|
|2|Real world|3|Trang chủ mới ghi 30/09, TopBar 29/09; chào buổi sáng cạnh "chấm công 17:52"|
|3|Control|2|Hoàn tác bị ghi đè khi duyệt liên tiếp; sheet không đóng bằng Esc|
|4|Consistency|2|6 màn duyệt, 4 cách xác nhận|
|5|Error prevention|3|CommentApproval từ chối không kiểm tra lý do|
|6|Recognition|3|Inbox xem trước nội dung người nhận|
|7|Flexibility|3|Phím tắt chỉ ở Inbox|
|8|Minimalism|3|Trang chủ không xếp ưu tiên|
|9|Recovery|3|Lý do bắt buộc ở 5/6 màn|
|10|Help|2|Quy tắc thư mời mâu thuẫn dữ liệu Inbox|

## Priority issues
- [P0] Hoàn tác mất khi duyệt nhanh liên tiếp (clearInterval ghi đè) ở Inbox, CommentApproval, AttitudeWarnings, MeetingInvites; CommentApproval từ chối không lý do; Grades vs Inbox xác nhận khác nhau; không Esc cho sheet → harden
- [P1] TopBar theo pps_view_scope không kiểm tra; chip chấm công hiện với trường liên kết → harden
- [P1] 16/18 màn đích cross-role giữ nội dung vai trò gốc (Payroll "Chốt kỳ lương" cho BGĐ, Grades cho TPĐT, Feedback "chị" cho Vận hành); menu quản lý thiếu Nghỉ phép; mục đang ở không tô sáng → adapt
- [P2] Trang chủ không có nút chính, đĩa icon xám đồng loạt, ngày lệch, Main 1 StatCard, Exec không có việc; 8 màn có bộ chọn cơ sở trùng TopBar → layout
- [P2] Billing/FinanceReports thẻ tiền warning/danger; Comments "Chờ duyệt" info; chấm danh mục Expenses 1,26:1; Inbox thời gian chờ 4,04:1; nút 22–30px; 24 hover chết; 4 cặp leading lệch; 3 padding 18px; label onClick SendNotification → colorize, polish

## Detector
128: cramped-padding 125 (báo nhầm), em-dash 2, thin-border-wide-shadow 1 (panel AI). Màu/cỡ chữ/focus/sheet/accent-data: sạch. A nhầm #1a7f37/#9a5b00/#a3141f là lệch token (đều là token).
