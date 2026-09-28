# Label trong mẫu báo cáo tự động (UC-67 / UC-68)

Tài liệu tra cứu cho người **tạo file mẫu báo cáo** (Word `.docx`, HTML, PDF
Form) và cho dev khi bổ sung trường mới. Liệt kê toàn bộ label (placeholder)
mà hệ thống điền dữ liệu tự động, theo từng loại báo cáo.

- Đặc tả nghiệp vụ: `docs/uc/phan-he-06-hoc-thuat.md` (UC-67 Quản lý mẫu báo
  cáo, UC-68 Xuất báo cáo tự động).
- Nguồn chân lý danh sách label: bảng `report_template_published_fields`
  (seed ở migration `V113__report_published_fields.sql`,
  `V122__report_published_fields_semester2.sql` và
  `V196__daily_report_attendance_homework_fields.sql`).
- API trả danh sách label đang active (frontend dùng ở màn hình cấu hình
  field mapping): `GET /api/report-templates/available-fields`.

---

## 1. Cú pháp placeholder

| Dạng | Cú pháp | Định dạng hỗ trợ | Ví dụ |
|---|---|---|---|
| Trường đơn (FIELD) | `[TEN_KEY]`, key viết HOA, chỉ gồm chữ/số/`_`, bắt đầu bằng chữ | DOCX, HTML | `[STUDENT_NAME]` |
| Công thức (FORMULA) | Dấu `[ ]` vừa đánh dấu trường vừa gom nhóm thay cho `( )` | DOCX, HTML | `[[[READING_MID1]+[WRITING_MID1]]/2]` |
| Bảng động (TABLE) | `[[TABLE:TEN]]` … `[[/TABLE:TEN]]` bao quanh dòng mẫu | DOCX, HTML | xem mục 3.1 |
| PDF Form | Tên field AcroForm chính là key, **không** có dấu ngoặc | PDF | `STUDENT_NAME` |

Quy tắc chung:

- **Phân biệt hoa thường.** `[student_name]` không được nhận là placeholder.
- **Dấu ngoặc phải cân bằng.** Nếu thiếu hoặc thừa `[` hay `]` ở bất kỳ đâu
  trong file, hệ thống sẽ chặn lưu mẫu (UC-67 A3). Nếu nội dung mẫu cần in
  dấu ngoặc vuông thật, hãy tránh ký tự `[ ]`.
- **Công thức** chỉ nhận biến kiểu số (điểm). Mọi biến trong công thức đều
  phải có dữ liệu, nếu không việc xuất bị chặn.
- **PDF Form** chỉ hỗ trợ trường đơn, không hỗ trợ công thức hay bảng động.
- **Mẫu HTML** luôn được xuất ra PDF. Mẫu DOCX xuất ra DOCX.
- **Word hay tách chữ thành nhiều "run"** (do định dạng hoặc gạch chân chính
  tả). Engine gộp các run trong một đoạn văn trước khi thay thế, vì vậy định
  dạng riêng của phần chữ trong đoạn có placeholder (trừ phần đầu đoạn) có thể
  bị mất. Nên định dạng cả đoạn thống nhất.

### Field mapping (UC-67 bước 3)

Mỗi placeholder `[X]` trong mẫu được ánh xạ tới một **data_path** (là một key
trong các bảng dưới đây).

- Nếu mẫu **chưa cấu hình mapping**, hệ thống tự hiểu `[X]` tương ứng
  data_path `X`. Cách đơn giản nhất là đặt placeholder **trùng tên key** trong
  tài liệu này.
- Nếu muốn đặt tên placeholder khác (ví dụ `[HO_TEN]`), phải vào màn hình cấu
  hình field mapping và chọn data_path `STUDENT_NAME`.

---

## 2. Tổng quan các loại báo cáo

| `template_type` | Tên | Phạm vi xuất | Số label |
|---|---|---|---|
| `DAILY_REPORT` | Báo cáo ngày | 1 file / 1 buổi học | 13 |
| `STUDENT_PROFILE` | Hồ sơ học sinh | 1 file / 1 học sinh | 12 |
| `STUDENT_COMMENT` | Nhận xét học sinh | 1 file / 1 học sinh | 4 |
| `TRANSCRIPT` | Phiếu kết quả lộ trình | 1 file / học sinh, hoặc ZIP toàn lớp | 39 |
| `GRADE_REPORT` | Bảng điểm | 1 file / học sinh, hoặc ZIP toàn lớp | 38 |

Mọi ngày tháng đều ở định dạng `dd/MM/yyyy`.

---

## 3. Chi tiết label theo loại báo cáo

### 3.1. `DAILY_REPORT` — Báo cáo ngày

Nguồn dữ liệu: `class_sessions`, `attendance_marks`, `student_comments`,
`class_enrollments`. Resolver: `DailyReportDataResolver`.

| Key | Label | Mô tả |
|---|---|---|
| `CLASS_NAME` | Tên lớp học | Tên lớp của buổi học |
| `CLASS_DATE` | Ngày học | Ngày diễn ra buổi học |
| `TEACHER_NAME` | Tên giáo viên | GV dạy thực tế; nếu không có thì lấy GV chính |
| `ASSISTANT_TEACHER_NAME` | Tên trợ giảng | Trợ giảng của buổi học; **để trống** nếu buổi không có trợ giảng (không chặn xuất) |
| `LESSON_TOPIC` | Nội dung bài học | Nội dung/chủ đề buổi học (rỗng nếu chưa nhập) |
| `HOMEWORK_CONTENT` | Bài tập về nhà | BTVN giao cho buổi sau, **nhiều dòng** (xem bên dưới) |
| `TOTAL_STUDENTS` | Tổng số học sinh | Số HS đang học (ACTIVE) trong lớp |
| `PRESENT_COUNT` | Số lượng HS có mặt | Có mặt + Đi trễ + Về sớm |
| `ABSENT_COUNT` | Số lượng HS vắng mặt | Vắng + Vắng có phép |
| `ABSENT_STUDENT_NAMES` | Danh sách HS vắng | Họ tên đầy đủ đúng các HS trong `ABSENT_COUNT`, A-Z, cách nhau `, ` |
| `MISSING_HOMEWORK_STUDENT_NAMES` | Danh sách HS thiếu BTVN | Họ tên HS "Chưa làm bài" BTVN online Ngữ pháp/Nghe của buổi trước, A-Z, cách nhau `, ` |
| `GENERATED_DATE` | Ngày xuất báo cáo | Ngày tạo file |
| `[[TABLE:STUDENTS]]` | Bảng học sinh | Danh sách HS ACTIVE của lớp, sắp theo tên A-Z |

**Trường con trong bảng `STUDENTS`** (chỉ dùng được giữa 2 marker):

| Key | Nội dung |
|---|---|
| `STUDENT_NAME` | Họ tên học sinh |
| `ATTENDANCE_STATUS` | Có mặt / Vắng / Vắng có phép / Đi trễ / Về sớm / Chưa điểm danh |
| `STUDENT_COMMENT` | Nhận xét của GV trong buổi (rỗng nếu chưa có) |
| `STUDENT_CODE` | Mã học sinh (*có dữ liệu nhưng chưa công bố trong bảng label*) |

HS **chưa được điểm danh** không nằm trong `PRESENT_COUNT` lẫn
`ABSENT_COUNT`, nên nếu buổi học chưa điểm danh đủ thì
`PRESENT_COUNT + ABSENT_COUNT` sẽ nhỏ hơn `TOTAL_STUDENTS`. Ví dụ ô Sĩ số
dạng "27/30": `[PRESENT_COUNT]/[TOTAL_STUDENTS]`.

`ASSISTANT_TEACHER_NAME`, `HOMEWORK_CONTENT`, `ABSENT_STUDENT_NAMES`,
`MISSING_HOMEWORK_STUDENT_NAMES` **để trống** khi không có gì để hiện (không
có trợ giảng, chưa giao BTVN, không ai vắng…). Đây là trường hợp bình thường
nên không chặn xuất.

**`HOMEWORK_CONTENT`** gộp BTVN của mọi học sinh trong buổi, bỏ các dòng trùng
nhau, mỗi mục một dòng (xuống dòng ngay trong cùng ô):

| Nguồn | Dạng dòng |
|---|---|
| BTVN offline chữ tự do (Ngữ pháp / chung) | `- Unit 8 trang 1 & 2` |
| BTVN offline Reading / Writing | `- Reading: …` / `- Writing: …` |
| BTVN online Ngữ pháp/Nghe | `- Online: <tên đề>` |
| BTVN online Reading / Writing | `- Online Reading: <tên đề>` / `- Online Writing: <tên đề>` |
| Video ôn tập | `- Video: <tên bộ video>` |

BTVN online chỉ xuất hiện **sau khi GV bấm "Gửi nhận xét"**. Khi còn nháp,
bài online chưa thực sự được giao.

**`MISSING_HOMEWORK_STUDENT_NAMES`** dùng đúng giá trị cột "BTVN buổi trước"
(kênh online Ngữ pháp/Nghe) trên bảng Nhận xét hàng ngày: nếu GV đã nhập tay
thì lấy giá trị nhập tay, nếu không thì hệ thống tự tính theo lượt làm bài của
đề giao ở buổi liền trước. Học sinh bị liệt kê khi giá trị đó là
"Chưa làm bài". Các kênh Video, online Reading/Writing và offline **chưa** được
tính.

Bảng động bắt buộc tên là `STUDENTS`. Trong Word, mỗi marker nằm trên **một
dòng riêng** của bảng:

```
| STT | Họ tên            | Điểm danh             | Nhận xét            |
|-----|-------------------|-----------------------|---------------------|
| [[TABLE:STUDENTS]]                                                    |
|     | [STUDENT_NAME]    | [ATTENDANCE_STATUS]   | [STUDENT_COMMENT]   |
| [[/TABLE:STUDENTS]]                                                   |
```

Khi xuất, dòng ở giữa được nhân bản cho từng học sinh, còn 2 dòng marker bị
xoá.

### 3.2. `STUDENT_PROFILE` — Hồ sơ học sinh

Nguồn dữ liệu: `students`, `users`, `parents`, `parent_student`. Resolver:
`StudentProfileReportDataResolver`.

| Key | Label | Mô tả |
|---|---|---|
| `STUDENT_NAME` | Họ và tên học sinh | |
| `STUDENT_CODE` | Mã học sinh | |
| `DATE_OF_BIRTH` | Ngày sinh | |
| `GENDER` | Giới tính | Giá trị thô `MALE` / `FEMALE` |
| `PRIMARY_SITE` | Điểm trường chính | Tên cơ sở |
| `STATUS` | Trạng thái | Giá trị thô của trạng thái hồ sơ (enum) |
| `ENROLLMENT_DATE` | Ngày nhập học | |
| `PRIMARY_PARENT_NAME` | Họ tên phụ huynh chính | PHHS được đánh dấu liên hệ chính |
| `PRIMARY_PARENT_PHONE` | SĐT phụ huynh | |
| `PRIMARY_PARENT_EMAIL` | Email phụ huynh | |
| `FINANCIAL_GUARDIAN_NAME` | Người bảo trợ tài chính | PHHS được đánh dấu chịu trách nhiệm tài chính |
| `GENERATED_DATE` | Ngày xuất báo cáo | |

### 3.3. `STUDENT_COMMENT` — Nhận xét học sinh

Resolver: `StudentCommentReportDataResolver`.

| Key | Label | Mô tả |
|---|---|---|
| `STUDENT_NAME` | Họ và tên học sinh | |
| `STUDENT_CODE` | Mã học sinh | |
| `STUDENT_COMMENT` | Nội dung nhận xét | Nhận xét của GV cho HS trong buổi học được chọn |
| `GENERATED_DATE` | Ngày xuất báo cáo | |

### 3.4. `TRANSCRIPT` — Phiếu kết quả lộ trình

Nguồn dữ liệu: `students`, `classes`, `grade_entries`,
`grade_evaluation_results`, `class_teachers`. Resolver:
`TranscriptReportDataResolver`.

**Thông tin chung (7 label):**

| Key | Label |
|---|---|
| `STUDENT_NAME` | Họ tên học sinh |
| `STUDENT_CODE` | Mã học sinh |
| `CLASS_NAME` | Tên lớp học |
| `SCHOOL_NAME` | Tên cơ sở (điểm trường của lớp) |
| `ACADEMIC_YEAR` | Năm học (VD `2025-2026`) |
| `PRIMARY_TEACHER_NAME` | Giáo viên chính |
| `GENERATED_DATE` | Ngày xuất báo cáo |

**Điểm theo đợt (32 label):** key có dạng `{TRƯỜNG}_{ĐỢT}`, tức 8 trường
nhân với 4 đợt.

| Trường | Label | Nguồn |
|---|---|---|
| `LISTENING` | Điểm Nghe | `grade_entries` |
| `READING` | Điểm Đọc | `grade_entries` |
| `SPEAKING` | Điểm Nói | `grade_entries` |
| `WRITING` | Điểm Viết | `grade_entries` |
| `GRAMMAR` | Điểm Ngữ pháp | `grade_entries` |
| `OVERALL` | Điểm Tổng kết | `grade_evaluation_results` (GV nhập/import, hệ thống không tự tính) |
| `LEVEL` | Xếp loại | `grade_evaluation_results` |
| `COMMENT` | Nhận xét | `grade_evaluation_results` |

| Đợt | Label |
|---|---|
| `MID1` | Giữa kỳ 1 |
| `END1` | Cuối kỳ 1 |
| `MID2` | Giữa kỳ 2 |
| `END2` | Cuối kỳ 2 |

Ví dụ: `[READING_MID1]` là "Điểm Đọc - Giữa kỳ 1", `[COMMENT_END2]` là
"Nhận xét - Cuối kỳ 2", `[OVERALL_END1]` là "Điểm Tổng kết - Cuối kỳ 1".

<details>
<summary>Danh sách đầy đủ 32 key điểm</summary>

| | MID1 | END1 | MID2 | END2 |
|---|---|---|---|---|
| Nghe | `LISTENING_MID1` | `LISTENING_END1` | `LISTENING_MID2` | `LISTENING_END2` |
| Đọc | `READING_MID1` | `READING_END1` | `READING_MID2` | `READING_END2` |
| Nói | `SPEAKING_MID1` | `SPEAKING_END1` | `SPEAKING_MID2` | `SPEAKING_END2` |
| Viết | `WRITING_MID1` | `WRITING_END1` | `WRITING_MID2` | `WRITING_END2` |
| Ngữ pháp | `GRAMMAR_MID1` | `GRAMMAR_END1` | `GRAMMAR_MID2` | `GRAMMAR_END2` |
| Tổng kết | `OVERALL_MID1` | `OVERALL_END1` | `OVERALL_MID2` | `OVERALL_END2` |
| Xếp loại | `LEVEL_MID1` | `LEVEL_END1` | `LEVEL_MID2` | `LEVEL_END2` |
| Nhận xét | `COMMENT_MID1` | `COMMENT_END1` | `COMMENT_MID2` | `COMMENT_END2` |

</details>

### 3.5. `GRADE_REPORT` — Bảng điểm

Resolver: `GradeReportDataResolver`. Giống hệt `TRANSCRIPT` (cả cơ chế chọn
đợt ở mục 4), chỉ **không có `SCHOOL_NAME`**.

- Thông tin chung (6 label): `STUDENT_NAME`, `STUDENT_CODE`, `CLASS_NAME`,
  `ACADEMIC_YEAR`, `PRIMARY_TEACHER_NAME`, `GENERATED_DATE`.
- Điểm theo đợt (32 label): giống bảng ở mục 3.4.

---

## 4. Hậu tố đợt (`MID1`, `END1`, …) hoạt động thế nào

Hậu tố **không cố định trong code**. Khi xuất `TRANSCRIPT` hoặc
`GRADE_REPORT`, người xuất chọn một hoặc nhiều **đợt** (period selector). Mỗi
đợt gồm:

- `academicTermId`: học kỳ.
- `evaluationType`: `MID_TERM` (giữa kỳ) hoặc `END_TERM` (cuối kỳ).
- `label`: hậu tố gắn vào key.

Hệ thống sinh key theo mẫu `{TRƯỜNG}_{label}`. Vì vậy **label chọn lúc xuất
phải khớp hậu tố đã dùng trong mẫu.**

| Muốn xuất | Selector cần chọn |
|---|---|
| Chỉ giữa kỳ 1 | 1 selector: HK1 + `MID_TERM` + label `MID1` |
| Cả kỳ 1 | 2 selector: HK1 + `MID_TERM` + `MID1`, và HK1 + `END_TERM` + `END1` |
| Cả năm học | 4 selector: `MID1`, `END1`, `MID2`, `END2` |

`MID1/END1/MID2/END2` là quy ước **được công bố sẵn** (có label hiển thị trên
màn hình cấu hình). Bạn vẫn có thể dùng nhãn khác, ví dụ `[READING_S1]` với
label `S1`, nhưng key đó sẽ không xuất hiện trong danh sách gợi ý.

---

## 5. Khi nào xuất báo cáo bị lỗi thiếu dữ liệu (UC-68 A1)

Hệ thống **không tự điền rỗng hoặc 0**. Nếu một placeholder trong mẫu không có
dữ liệu, việc xuất sẽ bị chặn và báo rõ tên trường thiếu. Các trường hợp
thường gặp:

| Label | Thiếu khi |
|---|---|
| `ACADEMIC_YEAR` | Lớp chưa gắn năm học |
| `PRIMARY_TEACHER_NAME` | Lớp chưa phân công GV chính (vai trò `PRIMARY`) |
| `PRIMARY_PARENT_*` | Học sinh chưa có PHHS được đánh dấu liên hệ chính |
| `FINANCIAL_GUARDIAN_NAME` | Chưa có PHHS được đánh dấu chịu trách nhiệm tài chính |
| `GENDER`, `PRIMARY_SITE` | Hồ sơ học sinh để trống |
| `STUDENT_COMMENT` (STUDENT_COMMENT) | GV chưa nhận xét HS trong buổi học được chọn |
| `{SKILL}_{ĐỢT}` | GV chưa nhập điểm kỹ năng đó trong đợt đó |
| `OVERALL_/LEVEL_/COMMENT_{ĐỢT}` | Chưa nhập hoặc import kết quả đánh giá tổng của đợt |
| Biến trong công thức | Một biến bất kỳ thiếu dữ liệu hoặc không phải số |

Chỉ đặt vào mẫu những trường mà mọi học sinh cần xuất đều chắc chắn có dữ
liệu. Ví dụ, mẫu dùng cho giữa kỳ thì đừng chứa `[OVERALL_END1]`.

---

## 6. Key có dữ liệu nhưng chưa công bố

Resolver vẫn sinh các key sau, nhưng chúng **chưa có trong bảng label**, nên
không hiện ở danh sách gợi ý. Nếu gõ tay đúng tên vào mẫu thì vẫn dùng được.

- `PROJECT_{ĐỢT}` và `OTHER_{ĐỢT}` (TRANSCRIPT, GRADE_REPORT): điểm thành
  phần `PROJECT`/`OTHER` của enum `GradeEvaluationComponent.ComponentCode`.
- `STUDENT_CODE` trong bảng `[[TABLE:STUDENTS]]` (DAILY_REPORT).

---

## 7. Dành cho dev: thêm label mới

1. Bổ sung `context.put("KEY", value)` vào resolver tương ứng
   (`service/*ReportDataResolver.java`). Tuân thủ quy ước: nếu không có dữ
   liệu thì **không put key** (không put rỗng hay 0). Nếu key dùng trong công
   thức thì value phải là `Number`.
2. Thêm migration Flyway **mới** chèn dòng vào `report_template_published_fields`
   (`template_type`, `field_key`, `field_label`, `description`, `field_type`,
   `display_order`). **Không sửa V113/V122.**
3. Cập nhật tài liệu này.
4. Thêm loại báo cáo mới: thêm giá trị vào `ReportTemplate.TemplateType` và
   một implementation `ReportDataResolver` mới. Không sửa resolver cũ
   (Open/Closed, xem `ReportGeneratorFactory`).
