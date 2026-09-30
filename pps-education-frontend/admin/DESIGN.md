---
name: PPS Admin 2.0
description: Cổng nội bộ cho giáo viên và nhân sự PPS Education — đơn giản và vừa đủ.
colors:
  accent: "#cc4e00"
  accent-hover: "#a84000"
  accent-soft: "#fdf0e6"
  accent-ink: "#a84000"
  highlight: "#cc4e00"
  page-grey: "#f5f5f7"
  white: "#ffffff"
  slate-100: "#efeff2"
  hairline: "#e5e5ea"
  field-border: "#d1d1d6"
  text-tertiary: "#76767b"
  text-secondary: "#6e6e73"
  text-muted: "#515154"
  text-dense: "#3a3a3c"
  text-primary: "#1d1d1f"
  success-soft: "#e8f5ec"
  success: "#166c2f"
  warning-soft: "#fdf3e2"
  warning: "#8a5100"
  danger-soft: "#fdecec"
  danger: "#c01c28"
  info-soft: "#e8f1fb"
  info: "#0a5fb4"
  attendance-soft: "#e3f4f1"
  attendance: "#0f6b63"
  homework-soft: "#e7f0fb"
  homework: "#0a5fb4"
  scores-soft: "#efebfb"
  scores: "#5b3db5"
  comments-soft: "#fbe9f1"
  comments: "#a8235d"
typography:
  large-title:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', Inter, Roboto, 'Helvetica Neue', Arial, sans-serif"
    fontSize: "34px"
    fontWeight: 600
    lineHeight: "40px"
    letterSpacing: "-0.02em"
  title-1:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', Inter, Roboto, 'Helvetica Neue', Arial, sans-serif"
    fontSize: "28px"
    fontWeight: 600
    lineHeight: "34px"
  title-2:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', Inter, Roboto, 'Helvetica Neue', Arial, sans-serif"
    fontSize: "24px"
    fontWeight: 600
    lineHeight: "30px"
    letterSpacing: "-0.015em"
  title-3:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'Segoe UI', Inter, Roboto, 'Helvetica Neue', Arial, sans-serif"
    fontSize: "19px"
    fontWeight: 600
    lineHeight: "26px"
  headline:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Text', 'Segoe UI', Inter, Roboto, 'Helvetica Neue', Arial, sans-serif"
    fontSize: "17px"
    fontWeight: 600
    lineHeight: "24px"
  body:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Text', 'Segoe UI', Inter, Roboto, 'Helvetica Neue', Arial, sans-serif"
    fontSize: "15px"
    fontWeight: 400
    lineHeight: "22px"
  callout:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Text', 'Segoe UI', Inter, Roboto, 'Helvetica Neue', Arial, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "20px"
  footnote:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Text', 'Segoe UI', Inter, Roboto, 'Helvetica Neue', Arial, sans-serif"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: "18px"
  caption:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'SF Pro Text', 'Segoe UI', Inter, Roboto, 'Helvetica Neue', Arial, sans-serif"
    fontSize: "12px"
    fontWeight: 400
    lineHeight: "16px"
  mono:
    fontFamily: "ui-monospace, 'SF Mono', 'JetBrains Mono', Menlo, Consolas, monospace"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: "18px"
rounded:
  lg: "8px"
  xl: "12px"
  2xl: "16px"
  full: "9999px"
spacing:
  unit: "4px"
  space-2: "8px"
  space-3: "12px"
  space-4: "16px"
  space-6: "24px"
  space-8: "32px"
  control-sm: "32px"
  control-md: "40px"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.white}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    padding: "0 16px"
    height: "{spacing.control-md}"
  button-primary-hover:
    backgroundColor: "{colors.accent-hover}"
  button-secondary:
    backgroundColor: "{colors.slate-100}"
    textColor: "{colors.text-primary}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    padding: "0 16px"
    height: "{spacing.control-md}"
  button-secondary-hover:
    backgroundColor: "{colors.hairline}"
  button-danger:
    backgroundColor: "{colors.danger-soft}"
    textColor: "{colors.danger}"
    rounded: "{rounded.lg}"
    height: "{spacing.control-md}"
  button-sm:
    typography: "{typography.footnote}"
    height: "{spacing.control-sm}"
    padding: "0 12px"
  input:
    backgroundColor: "{colors.white}"
    textColor: "{colors.text-primary}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    padding: "0 12px"
    height: "{spacing.control-md}"
  input-readonly:
    backgroundColor: "{colors.slate-100}"
  card:
    backgroundColor: "{colors.white}"
    rounded: "{rounded.2xl}"
    padding: "24px"
  content-panel:
    backgroundColor: "{colors.white}"
    rounded: "{rounded.2xl}"
    padding: "32px"
  nav-item:
    textColor: "{colors.text-muted}"
    typography: "{typography.callout}"
    rounded: "{rounded.lg}"
    padding: "0 12px"
    height: "{spacing.control-md}"
  nav-item-active:
    backgroundColor: "{colors.accent-soft}"
    textColor: "{colors.accent-ink}"
  badge-success:
    backgroundColor: "{colors.success-soft}"
    textColor: "{colors.success}"
    typography: "{typography.footnote}"
    rounded: "{rounded.full}"
    height: "24px"
    padding: "0 10px"
  badge-warning:
    backgroundColor: "{colors.warning-soft}"
    textColor: "{colors.warning}"
    rounded: "{rounded.full}"
  badge-danger:
    backgroundColor: "{colors.danger-soft}"
    textColor: "{colors.danger}"
    rounded: "{rounded.full}"
  badge-info:
    backgroundColor: "{colors.info-soft}"
    textColor: "{colors.info}"
    rounded: "{rounded.full}"
  stat-tile-attendance:
    backgroundColor: "{colors.attendance-soft}"
    textColor: "{colors.attendance}"
    rounded: "{rounded.2xl}"
    padding: "20px"
---

# Design System: PPS Admin 2.0

<!-- Nguồn: design system "PPS Admin 2.0" (https://claude.ai/artifact/JrkadaS3Wvc2x1Gz2rN7aJ).
     Code trong src/ hiện vẫn là giao diện 1.0 (Plus Jakarta Sans, nút gradient cam, nền kem
     #fff4ea); file này là đích chuyển đổi, không phải mô tả code hiện tại. -->

## Overview

**Creative North Star: "The Academic Ledger" (Sổ tay học vụ)**

PPS Admin 2.0 đọc như một cuốn sổ ghi chép học vụ chuẩn mực: chính xác, dễ dò, số liệu
rõ ràng. Mỗi trang là một tờ sổ — một tiêu đề, một dòng mô tả, rồi các bảng và thẻ
trắng xếp gọn trên nền giấy xám. Người dùng mở sổ để tra và ghi, không để ngắm; giao
diện lùi lại để nội dung (tên học sinh, điểm, buổi học, trạng thái duyệt) dẫn dắt.

Tinh thần là "đơn giản và vừa đủ", theo nguyên tắc giao diện của Apple — rõ ràng,
nhường chỗ, chiều sâu bằng mặt phẳng. Mật độ thông tin cao nhưng không chật: chữ 15px
cho nội dung, 14px cho bảng dày, không gì dưới 12px; điều khiển cao 40px để chạm được
trên iPad. Màu nhấn chỉ dùng một lần cho hành động quan trọng nhất; màu dữ liệu gắn cố
định với từng loại con số để người đọc nhận ra ngay "đây là chuyên cần", "đây là điểm".

Từ chối rõ ràng: gradient, glow, khối trang trí, chữ IN HOA, emoji và minh họa — tất cả
những gì bản 1.0 từng dùng để "làm đẹp" nay coi là nhiễu.

**Key Characteristics:**
- Nền xám, nội dung trắng, một đường viền mảnh duy nhất.
- Một màu nhấn theo bảng màu người dùng chọn (4 bảng), mọi thứ khác trung tính.
- Font hệ thống (SF Pro / Segoe UI / Inter), tối đa semibold 600.
- Màu dữ liệu cố định theo loại số liệu; màu trạng thái luôn đi kèm chữ.
- Bóng đổ chỉ cho lớp nổi (menu, modal, toast); thẻ phẳng.
- Điều khiển 40px, hàng ≈ 44px — dùng tốt bằng tay trên máy tính bảng.

## Colors

Bảng trung tính lạnh kiểu macOS, một màu nhấn đổi theo lựa chọn của người dùng, và hai
họ màu phụ có nhiệm vụ cố định: trạng thái và dữ liệu.

### Primary
- **PPS Ember** (accent): nút chính, ngày được chọn trên lịch, phân đoạn được chọn, link
  trên nền trắng. Đây là giá trị của bảng mặc định **Cam PPS**; ba bảng khác thay bằng
  Xanh ngọc `#0f766e`, Xanh Apple `#0066cc`, Mực `#1d1d1f`. Chữ trắng trên accent luôn
  ≥ 4.5:1 ở cả bốn bảng.
- **Ember Pressed** (accent-hover): trạng thái hover/nhấn của vùng tô accent.
- **Ember Wash** (accent-soft) + **Ember Ink** (accent-ink): nền mục sidebar đang chọn,
  option đang chọn, avatar, badge thương hiệu. Trên nền wash luôn dùng chữ ink, không
  bao giờ dùng accent.
- **PPS Signal** (highlight): CountBadge, link, chữ nhấn, chữ "VIETNAM" trong wordmark.
  Bằng accent, trừ bảng Mực + cam nơi nó giữ nguyên cam PPS.

### Secondary — màu trạng thái
- **Done Green** (success / success-soft): hoàn thành, đang học, xu hướng tăng.
- **Attention Amber** (warning / warning-soft): chờ duyệt, bảo lưu, nhắc nhở.
- **Overdue Rose** (danger / danger-soft): quá hạn, nợ học phí, lỗi, hành động phá hủy.
- **Notice Sky** (info / info-soft): thông tin.

### Tertiary — màu dữ liệu
- **Attendance Teal**: chuyên cần.
- **Homework Blue**: bài tập về nhà.
- **Scores Violet**: điểm số, thi thử.
- **Comments Pink**: nhận xét và phản hồi.

Mỗi màu dữ liệu có cặp `-soft` (tô nền thẻ) và đậm (nhãn, icon, thanh tiến độ). Giá
trị số trên thẻ luôn là text-primary. Màu kỹ năng trong Kho đề: Reading = blue,
Writing = violet, Listening = teal, Speaking = pink, Từ vựng + Ngữ pháp = amber,
Video TKN = xám.

### Neutral
- **Paper Grey** (page-grey): nền trang; cũng là hàng tiêu đề bảng và panel tĩnh.
- **Sheet White** (white): thẻ, panel nội dung, modal, menu và *mọi* ô nhập liệu.
- **Quiet Fill** (slate-100): nền nút phụ, rãnh segmented control, hover hàng, đĩa icon,
  giá trị chỉ đọc/khóa.
- **Ruled Line** (hairline): đường viền duy nhất — thẻ, bảng, menu, vạch chia.
- **Field Edge** (field-border): viền ô nhập liệu; không bao giờ dùng cho chữ.
- **Ink ramp**: text-primary (tiêu đề, giá trị), text-dense (chữ trong vùng dày),
  text-muted (tab và mục điều hướng chưa chọn), text-secondary (mô tả, nhãn, header
  bảng), text-tertiary (thời gian, placeholder).

### Named Rules
**The One Accent Rule.** Accent đánh dấu đúng một hành động quan trọng nhất và lựa chọn
hiện tại trên màn hình. Một trang có đúng một nút primary.

**The Numbers Have Colours Rule.** Mỗi loại số liệu có một màu cố định ở mọi nơi. Màu dữ
liệu chỉ dành cho số, thẻ số, biểu đồ, thanh — không bao giờ cho nút, link, hay để nói
thành công/lỗi.

**The Status Speaks Rule.** Màu trạng thái luôn đi kèm một từ; không có chấm màu đứng một mình.

## Typography

**Display Font:** SF Pro Display (với -apple-system, Segoe UI, Inter, Roboto)
**Body Font:** SF Pro Text (với cùng chuỗi dự phòng)
**Label/Mono Font:** SF Mono (với JetBrains Mono, Menlo, Consolas)

**Character:** Font hệ thống của chính thiết bị — quen tay, sắc nét, trung tính; để nội
dung tiếng Việt đủ dấu nổi lên, không để kiểu chữ tranh chỗ.

### Hierarchy
- **Large title** (600, 34/40px, -0.02em): lời chào trang chủ giáo viên, số KPI lớn. Tối đa một lần mỗi màn.
- **Title 1** (600, 28/34px): giá trị trong StatCard.
- **Title 2** (600, 24/30px, -0.015em): tiêu đề trang — mọi trang.
- **Title 3** (600, 19/26px): tiêu đề modal, tiêu đề thẻ mở đầu một phần.
- **Headline** (600, 17/24px): empty state, dòng nhấn.
- **Body** (400, 15/22px): form, nút cỡ md, mô tả, nội dung modal.
- **Callout** (400, 14/20px): bảng, danh sách, mục sidebar — dữ liệu dày.
- **Footnote** (400, 13/18px): nhãn, header bảng, badge, tab, nút nhỏ, phân trang.
- **Caption** (400, 12/16px): cỡ nhỏ nhất — thời gian, bộ đếm, chú thích lưới.
- **Mono** (400, 13/18px): giờ, mã, bộ đếm.

### Named Rules
**The Twelve Floor Rule.** Không có chữ nào dưới 12px.

**The Sentence Case Rule.** Viết hoa kiểu câu ở mọi nơi ("Quản lý lớp học", "Tạo lớp");
không IN HOA, không giãn chữ, không đậm hơn 600.

**The Ledger Figures Rule.** Số trong thẻ thống kê và bảng dùng chữ số đều độ rộng
(`font-variant-numeric: tabular-nums`).

## Layout

Khung ứng dụng gồm sidebar trắng mờ rộng 256px trên nền xám, header trong suốt dính trên
cùng (64px), và một panel nội dung trắng bo 16px với padding 32px trên desktop. Nhịp
8px trên lưới 4px của Tailwind: 16px giữa các thẻ, 24px padding thẻ và modal, 24–32px
giữa các phần của trang. Điều khiển cao 40px (nhỏ 32px), hàng bảng ≈ 44–56px.

Mỗi trang: tiêu đề (title-2) + một dòng mô tả, rồi đến thẻ. Danh sách dùng TableContainer
+ Th/Td, Badge cho trạng thái, Tabs để lọc, Pagination bên dưới. Form một cột, nhãn
footnote phía trên ô, hành động chính ở góc dưới bên phải. Canvas thiết kế ở 1440px;
trên di động sidebar thu vào, form và bảng xếp một cột (NFR-UI-01).

## Elevation & Depth

Chiều sâu bằng mặt phẳng, không bằng trang trí: nền xám → tờ trắng → một đường viền
mảnh. Thẻ và panel không có bóng. Bóng đổ chỉ xuất hiện khi một lớp thật sự nổi lên trên
nội dung.

### Shadow Vocabulary
- **Segment lift** (`box-shadow: 0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1)`): phân đoạn đang chọn trong Tabs và LanguageSwitcher.
- **Menu float** (`box-shadow: 0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.1)`): Dropdown, Select, DatePicker, ContextMenu.
- **Dialog float** (`box-shadow: 0 25px 50px -12px rgb(0 0 0 / 0.25)`): modal và toast.

### Named Rules
**The Only Floaters Cast Shadows Rule.** Nếu một phần tử không đè lên nội dung khác, nó
không có bóng.

## Shapes

Góc bo mềm, tăng theo kích thước bề mặt: điều khiển (nút, ô nhập, hàng danh sách, rãnh
segmented) 8px; bảng và menu 12px; thẻ, modal, panel chính và sidebar 16px; badge,
avatar, toast, đĩa icon và ngày lịch là hình viên thuốc/tròn. Viền luôn 1px. Logo chữ P
là nơi duy nhất còn gradient cam.

## Components

### Buttons
Chắc tay và kiệm lời — động từ nói rõ kết quả ("Lưu nhận xét", "Điểm danh", "Giao bài").
- **Shape:** bo nhẹ (8px), cao 40px (nhỏ: 32px, chữ footnote).
- **Primary:** nền accent, chữ trắng, weight 500. Một nút mỗi trang.
- **Secondary:** nền Quiet Fill, chữ text-primary; hover sang Ruled Line.
- **Danger:** nền danger-soft, chữ danger.
- **Ghost:** trong suốt, chữ text-muted, hover Quiet Fill.
- **Focus:** viền 2px accent cách 2px trên mọi nút và link khi dùng bàn phím.
- **Disabled:** 40% opacity.

### Chips / Badges
- **Style:** viên thuốc cao 24px, chữ footnote, nền `-soft` + chữ đậm cùng họ màu.
- **State:** success / warning / danger / info / neutral / brand (accent-soft + accent-ink).
- **Skill chips:** tô `-soft` với chữ `-700`; khi là bộ lọc được chọn, tô `-700` chữ trắng.

### Cards / Containers
- **Corner Style:** 16px.
- **Background:** Sheet White trên Paper Grey.
- **Shadow Strategy:** không có (xem Elevation & Depth).
- **Border:** 1px Ruled Line.
- **Internal Padding:** 24px (panel chính 32px).

### Inputs / Fields
- **Style:** nền trắng, viền 1px Field Edge, bo 8px, cao 40px; hover viền text-tertiary.
- **Focus:** viền accent kèm quầng accent mềm 3px.
- **Error:** viền danger, thông báo footnote màu danger ngay dưới ô.
- **Read-only / khóa:** nền Quiet Fill — ô xám nghĩa là không sửa được.
- **Pickers:** luôn dùng Select, DatePicker, MonthPicker, Time24Input của hệ thống; không dùng picker gốc của trình duyệt.

### Navigation
- **Sidebar:** trắng mờ, 256px, nhóm theo phân hệ có thể thu gọn; mục chữ callout
  text-muted với icon 18px text-secondary; mục đang chọn tô accent-soft, chữ và icon
  accent-ink, weight 600. Logo P 32px + wordmark "PPS VIETNAM" / "Caring Individuals".
- **Header:** trong suốt, dính trên cùng; trạng thái chấm công, ngày (mono), PaletteSwitcher, LanguageSwitcher, chuông thông báo.

### Feedback
- **NoticeBanner:** lỗi và cảnh báo cấp trang/thao tác rơi xuống từ giữa trên cùng màn
  hình, ở lại tới khi đóng, có thể kèm một hành động ("Thử lại"); xác nhận thành công tự
  ẩn sau 4 giây.
- **Toast:** sau khi lưu; nền text-primary 90%, bo tròn, Dialog float.
- **EmptyState:** icon 28px, tiêu đề headline, một câu nói việc cần làm tiếp.

### StatCard (signature)
Thẻ số liệu tô màu dữ liệu `-soft`; nhãn, icon và thanh tiến độ màu dữ liệu đậm; giá trị
title-1 text-primary, chữ số đều độ rộng. Màu thẻ do loại số liệu quyết định, không do
thẩm mỹ.

## Do's and Don'ts

### Do:
- **Do** đặt nội dung trên thẻ trắng bo 16px, viền 1px `#e5e5ea`, trên nền `#f5f5f7`.
- **Do** dùng accent cho đúng một hành động chính mỗi trang và cho lựa chọn hiện tại.
- **Do** tô thẻ số liệu bằng màu dữ liệu theo loại: teal chuyên cần, blue BTVN, violet điểm, pink nhận xét.
- **Do** giữ mọi ô nhập liệu nền trắng; nền xám chỉ cho giá trị chỉ đọc.
- **Do** ghi ngày DD/MM/YYYY, giờ 24h HH:mm, phân cách hàng nghìn bằng dấu chấm (1.248).
- **Do** dùng icon Lucide nét 2px, `currentColor`: 18px sidebar, 16px nút và menu, 20px đĩa icon, 28px empty state.
- **Do** giới hạn chuyển động ở chuyển màu 150ms.

### Don't:
- **Don't** dùng gradient, glow, khối trang trí (`bg-brand-gradient`, `shadow-glow` của bản 1.0), trừ trong logo P.
- **Don't** đặt bóng đổ lên thẻ hay panel.
- **Don't** viết IN HOA, giãn chữ, hay dùng weight trên 600.
- **Don't** để chữ dưới 12px.
- **Don't** dùng màu dữ liệu cho nút, link, hay để báo thành công/lỗi.
- **Don't** dùng emoji hoặc hình minh họa.
- **Don't** hiện lỗi cấp trang thành một hộp trên đầu bảng — dùng NoticeBanner.
- **Don't** nhảy, nhấp nháy hay nảy — ngoại lệ duy nhất là chấm nhắc chấm công.
