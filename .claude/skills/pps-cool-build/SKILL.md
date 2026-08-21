---
name: pps-cool-build
description: Chuyển sang Windows Power Plan giảm nhiệt trước khi chạy build/test nặng (mvn clean compile, mvn test, spring-boot:run cùng lúc với Docker Desktop) trên máy dev PPS Education, và chuyển lại Balanced sau khi xong. Dùng khi chuẩn bị 1 phiên compile/verify runtime kéo dài, hoặc khi người dùng báo máy nóng/CPU bị throttle xuống rất thấp (~0.4GHz) lúc build.
---

## Vì sao cần skill này

Máy dev (CPU Intel i7-8665U, ultrabook TDP 15W, 4 core/8 luồng) bị
**firmware/thermal throttling** khi build nặng kéo dài (mvn compile 471+
file, spring-boot:run, Docker Desktop/WSL2 chạy Postgres cùng lúc, cộng
thêm các app nền khác). Đã chẩn đoán ngày 2026-07-12: Windows Event Log
ghi nhận Event ID 37 (provider `Kernel-Processor-Power`) — *"The speed of
processor X is being limited by system firmware"* — 13 lần trong 7 ngày,
có lúc tụt xuống 19% của xung nominal 2112MHz (~400MHz), khớp với mức
người dùng quan sát được. Đã loại trừ nguyên nhân do cấu hình Windows
(Maximum processor state vốn đã 100%, không có Power Throttling/EcoQoS
nào ép) — đây là giới hạn vật lý của chip 15W dưới tải nặng kéo dài, không
phải bug code/project.

**Cách giảm (không đụng tới cơ chế bảo vệ nhiệt của CPU — an toàn):** hạ
trần Maximum Processor State xuống 90% + tắt Turbo Boost trong lúc build,
để tránh các đợt xung đột ngột lên gần 4.8GHz rồi bị firmware đập xuống
~400MHz. Đã tạo sẵn 1 Power Plan riêng cho việc này, không đụng vào plan
"Balanced" người dùng dùng hàng ngày.

## Power Plan đã tạo sẵn

- **"PPS Build - Giảm nhiệt"** — GUID `11bf1b85-1a23-4c27-8ed6-57affac20d5c`
  (tạo lại 2026-08-21, GUID cũ `11bf1b85-1a23-4c27-8ed6-57affac20d5c` bị
  Windows Update reset mất — không còn tồn tại)
  - Maximum processor state = 90% (AC + DC)
  - Turbo Boost (PerfBoostMode) = Disabled (AC + DC)
- **"Balanced"** (plan mặc định hàng ngày) — GUID
  `381b4222-f694-41f0-9685-ff5bb260df2e`

## Trước khi build/test nặng — bật plan giảm nhiệt

Bật trước khi chạy `mvn clean compile`, `mvn test`, `mvn spring-boot:run`
kéo dài, đặc biệt khi Docker Desktop đang chạy Postgres cùng lúc:

```powershell
powercfg /setactive 11bf1b85-1a23-4c27-8ed6-57affac20d5c
```

## Sau khi build/test xong — chuyển lại Balanced

```powershell
powercfg /setactive 381b4222-f694-41f0-9685-ff5bb260df2e
```

Đừng quên bước này — nếu để plan giảm nhiệt chạy thường trực ngoài lúc
build sẽ làm giảm hiệu năng máy không cần thiết cho việc khác.

## Kiểm tra nhanh

```powershell
# Plan nào đang active
powercfg /getactivescheme

# Xác nhận plan giảm nhiệt còn nguyên cấu hình (kỳ vọng thấy 0x0000005a = 90%)
powercfg /query 11bf1b85-1a23-4c27-8ed6-57affac20d5c SUB_PROCESSOR PROCTHROTTLEMAX
```

## Nếu GUID không còn tồn tại (Windows Update có thể reset power plan)

Nếu lệnh `powercfg /query 663f57f7-...` báo lỗi không tìm thấy scheme,
tạo lại từ đầu bằng đúng các lệnh đã dùng để tạo ban đầu:

```powershell
$balancedGuid = "381b4222-f694-41f0-9685-ff5bb260df2e"
$dup = powercfg /duplicatescheme $balancedGuid
$newGuid = ($dup -split "GUID: ")[1].Substring(0,36)

powercfg /changename $newGuid "PPS Build - Giam nhiet" "Ha Max Processor State xuong 90% + tat Turbo Boost, dung khi build/test nang (mvn compile, spring-boot:run, docker) de tranh cham nguong throttle nhiet firmware."

powercfg /setacvalueindex $newGuid SUB_PROCESSOR PROCTHROTTLEMAX 90
powercfg /setdcvalueindex $newGuid SUB_PROCESSOR PROCTHROTTLEMAX 90
powercfg /setacvalueindex $newGuid SUB_PROCESSOR PERFBOOSTMODE 0
powercfg /setdcvalueindex $newGuid SUB_PROCESSOR PERFBOOSTMODE 0
```

Ghi lại GUID mới (`$newGuid`) và cập nhật vào skill này (thay thế
`1f84fb99-8106-4099-93a5-28704da34582` ở các lệnh phía trên) vì GUID đổi
mỗi lần tạo lại.

## Dùng chủ động trong project này

Khi chuẩn bị chạy 1 loạt lệnh nặng liên tục cho backend PPS Education
(`mvn -o clean compile`, `mvn -o test`, `mvn -o spring-boot:run` để verify
runtime UC mới) — bật plan giảm nhiệt trước, chuyển lại Balanced khi xong
phiên làm việc đó (không cần bật/tắt cho từng lệnh lẻ tẻ, chỉ cần bật 1
lần đầu phiên build nặng và tắt cuối phiên).
