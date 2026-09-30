---
target: Toàn bộ màn PPS Admin 2.0 (canvas) — lần 2
total_score: 25
max_score: 40
na_heuristics: 
p0_count: 1
p1_count: 2
target_identity: "url:https://claude.ai/artifact/7e8UTj9Cb8ecxjqbyLBezn"
timestamp: 2026-09-30T04-26-01Z
slug: claude-ai-artifact-7e8utj9cb8ecxjqbylbezn
---
# Critique 2: PPS Admin 2.0 — 58 màn (canvas v42; typeset v43–44 đã sửa SVG 11px và 22px)
Method: dual-agent (A: design review · B: detector). Source-only.

## Design Health Score: 25/40 (Acceptable)
|#|Heuristic|Score|Key issue|
|1|Status|3|Dải "đang gửi" tự vẽ thay NoticeBanner|
|2|Real world|3|Quản lý thấy "Lớp của tôi"|
|3|Control|2|Undo chỉ ở Inbox/duyệt hàng loạt; AttitudeWarnings, MeetingInvites gửi ngay|
|4|Consistency|2|4 bố cục duyệt, 3 kiểu bảng, 6 độ rộng sheet, 22 link sang shell khác|
|5|Error prevention|2|Grades "Duyệt tất cả (n)" công bố cả lớp không xác nhận|
|6|Recognition|3|CountBadge + "Phụ huynh sẽ nhận"|
|7|Flexibility|2|J/K/A/R chỉ Inbox; không multi-select|
|8|Minimalism|3|HrProfiles, Lectures quá tải|
|9|Recovery|3|Grades lý do từ chối "không bắt buộc"|
|10|Help|2|Không giải thích phạm vi điểm trường|

## Priority issues
- [P0] An toàn duyệt không đồng nhất (Grades, AttitudeWarnings, MeetingInvites): 1 controller chung confirm+undo 10s+lý do bắt buộc → harden
- [P1] 22 link menu sang shell vai trò khác; 5 vai trò thiếu trang chủ; menu quản lý trùng Inbox → shape, layout
- [P1] Accent vẽ dữ liệu (ActualPeriods, Roles, Crm); tone=brand trên tiền (Billing, Expenses, Payroll) chưa có quy tắc; info note dùng homework-soft; pending counter info thay amber; ~40 hex lệch token (EmployeeSchedule, AttendanceSites, Comments tint, GradeAnalytics violet ramp) → colorize
- [P2] Bảng 3 kỹ thuật; side sheet 6 độ rộng (520–720) → polish
- [P2] Điều khiển 36px vs 40; sidebar overflow hidden cắt mục; scope Select 12px; 1.177 giá trị 6/10/14px → layout

## Detector
123: cramped-padding 120 (≈báo nhầm), em-dash 2, thin-border-wide-shadow 1 (modal). Không đọc bundle.css.

## Persona
Alex: phím tắt chỉ Inbox. Sam: 22 file outline:none không thay thế; scrim div; sidebar cắt; chấm màu không chữ. Chị Lan: đếm ba lần, không so sánh cơ sở, Lớp học mở màn GV, Select nhỏ. Chị Hương: CTA <a> tự vẽ, 2 nút xuất ngang nhau.

## Đã loại (A sai)
#1a7f37/#9a5b00/#a3141f là token; Grades duyệt cả lớp chứ không 1 học sinh.
