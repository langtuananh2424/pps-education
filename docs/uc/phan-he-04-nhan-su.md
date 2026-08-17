# phan-he-04-nhan-su

UC-08: Quản lý hồ sơ nhân sự

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-08                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Quản lý hồ sơ nhân sự                              |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 4                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-HRM-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản lý nhân sự                                    |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Quản lý nhân sự lưu trữ và duy trì thông tin cá    |
| tắt**           | nhân, bằng cấp, chứng chỉ sư phạm, lịch sử hợp     |
|                 | đồng lao động, khen thưởng/kỷ luật cho toàn bộ     |
|                 | nhân sự đa cơ sở.                                  |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Quản lý nhân sự cần khởi tạo hồ sơ nhân sự mới     |
| hoạt**          | hoặc cập nhật hồ sơ hiện có.                       |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người dùng có role HR_MANAGER và quyền         |
| tiên quyết      |     hrm.employee.view/create/update tương ứng theo |
| (               |     thao tác (V51, tách từ hrm.manage — bổ sung    |
| Precondition)** |     ngoài SDD gốc, đã xác nhận với người dùng      |
|                 |     2026-07-24).                                   |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản lý nhân sự mở màn hình Hồ sơ nhân sự,     |
| chính (Main     |     chọn Thêm mới hoặc tìm một nhân sự hiện có.    |
| Flow)**         |     Khi Thêm mới cho nhân sự CHƯA có tài khoản     |
|                 |     đăng nhập, hệ thống cho phép khởi tạo tài      |
|                 |     khoản người dùng kèm hồ sơ trong cùng một      |
|                 |     giao dịch (cơ chế khởi tạo tài khoản theo      |
|                 |     UC-43/FR-USR-01, dưới thẩm quyền                |
|                 |     hrm.employee.create của luồng này).            |
|                 |                                                    |
|                 | 2.  Nhập/cập nhật thông tin cá nhân, bằng cấp,     |
|                 |     chứng chỉ sư phạm, phòng ban (tùy chọn) và cờ  |
|                 |     miễn trừ chấm công/duyệt đơn is_management     |
|                 |     (mặc định FALSE).                              |
|                 |                                                    |
|                 | 3.  Thêm/chỉnh sửa lịch sử hợp đồng lao động (loại |
|                 |     hợp đồng, thời hạn, mức lương cơ bản).         |
|                 |                                                    |
|                 | 4.  Ghi nhận sự kiện khen thưởng/kỷ luật kèm mô    |
|                 |     tả, ngày phát sinh.                            |
|                 |                                                    |
|                 | 5.  Hệ thống lưu thay đổi, giữ lịch sử phiên bản   |
|                 |     (employees_history/tương tự) phục vụ tra soát. |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Nhân sự làm việc tại nhiều điểm          |
| thế / ngoại lệ  | trường***                                          |
| (Alternate      |                                                    |
| Flow)**         | 1.  Hệ thống cho phép gán 1 nhân sự (đặc biệt Giáo |
|                 |     viên) vào nhiều điểm trường; hồ sơ vẫn là 1    |
|                 |     bản ghi duy nhất, phần phân công điểm trường   |
|                 |     được quản lý riêng ở Phân hệ Cơ sở vật         |
|                 |     chất/Học thuật.                                |
|                 |                                                    |
|                 | ***A2 --- Hợp đồng hết hạn***                      |
|                 |                                                    |
|                 | 1.  Hệ thống cảnh báo cho Quản lý nhân sự khi hợp  |
|                 |     đồng lao động sắp/đã hết hạn để xử lý gia hạn  |
|                 |     hoặc chấm dứt.                                 |
|                 |                                                    |
|                 | ***A3 --- Bỏ trống phòng ban***                    |
|                 |                                                    |
|                 | 1.  Quản lý nhân sự để trống phòng ban; hệ thống   |
|                 |     lưu department_id = NULL (nhân sự chưa gán     |
|                 |     phòng ban nào).                                |
|                 |                                                    |
|                 | ***A4 --- Phòng ban không tồn tại***               |
|                 |                                                    |
|                 | 1.  department_id gửi lên không khớp bản ghi nào   |
|                 |     trong bảng departments; hệ thống từ chối lưu,  |
|                 |     báo không tìm thấy phòng ban.                  |
|                 |                                                    |
|                 | ***A5 --- Tự động gán vai trò theo chức vụ         |
|                 | (FR-HRM-06, xem UC-52)***                          |
|                 |                                                    |
|                 | 1.  Khi tạo mới hoặc đổi chức vụ (position_id) của |
|                 |     hồ sơ, hệ thống tự động gán cho tài khoản toàn |
|                 |     bộ vai trò mặc định của chức vụ đó             |
|                 |     (position_default_roles — UC-52), và thu hồi   |
|                 |     vai trò do CHÍNH cơ chế này từng gán theo chức |
|                 |     vụ cũ nếu chức vụ mới không còn liệt kê vai trò|
|                 |     đó. Vai trò đã gán tay qua UC-46 không bị đụng.|
|                 |                                                    |
|                 | 2.  Để trống chức vụ (position_id = NULL): không   |
|                 |     gán vai trò nào mới; vai trò từng tự gán theo  |
|                 |     chức vụ cũ (nếu có) bị thu hồi.                |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Hồ sơ nhân sự phản ánh đầy đủ, chính xác thông |
| (P              |     tin mới nhất; dữ liệu này được dùng làm đầu    |
| ostcondition)** |     vào cho chấm công, tính lương (UC-09, UC-12).  |
+-----------------+----------------------------------------------------+

---

UC-09: Chấm công

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-09                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Chấm công                                          |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 4                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-HRM-02                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Giáo viên, Nhân viên                               |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Hệ thống (đối chiếu             |
|                 | work_calendar/shifts))                             |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Giáo viên/Nhân viên thực hiện chấm công qua máy    |
| tắt**           | vân tay, nhận diện khuôn mặt hoặc định vị GPS; cấp |
|                 | quản lý được miễn trừ.                             |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Nhân sự thực hiện thao tác chấm công vào/ra tại    |
| hoạt**          | điểm trường hoặc trên ứng dụng di động.            |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người dùng có is_management = FALSE (không     |
| tiên quyết      |     thuộc diện miễn trừ).                          |
| (               |                                                    |
| Precondition)** | -   Ít nhất 1 phương thức chấm công (vân tay/khuôn |
|                 |     mặt/GPS) đang bật trong system_settings.       |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Nhân sự thực hiện chấm công qua 1 trong 3      |
| chính (Main     |     phương thức: quét vân tay/khuôn mặt tại điểm   |
| Flow)**         |     trường, hoặc bấm chấm công GPS trên ứng dụng.  |
|                 |                                                    |
|                 | 2.  Hệ thống kiểm tra is_management: nếu TRUE, từ  |
|                 |     chối ngay và kết thúc (cấp quản lý miễn trừ    |
|                 |     hoàn toàn).                                    |
|                 |                                                    |
|                 | 3.  Hệ thống xác định ngày D có phải ngày làm việc:|
|                 |     đối chiếu work_calendar (override) trước, sau  |
|                 |     đó shifts (pattern mặc định + week_parity); nếu|
|                 |     cả 2 đều không xác định được (không có         |
|                 |     override, không có ca khớp), Giáo viên có tiết |
|                 |     dạy hôm nay cũng được coi là ngày làm việc     |
|                 |     (không áp dụng nếu đã có override tường minh). |
|                 |                                                    |
|                 | 4.  Nếu là Giáo viên có tiết dạy hôm nay, hệ thống |
|                 |     tính thêm cửa sổ theo lịch dạy; nếu            |
|                 |     is_default_shift_required = TRUE, tính thêm    |
|                 |     cửa sổ theo ca cố định.                        |
|                 |                                                    |
|                 | 5.  Hệ thống kiểm tra thời điểm chấm công T có     |
|                 |     thuộc 1 trong các cửa sổ hợp lệ đã xác định    |
|                 |     hay không.                                     |
|                 |                                                    |
|                 | 6.  Với phương thức GPS: kiểm tra vị trí có nằm    |
|                 |     trong bán kính cho phép quanh điểm trường      |
|                 |     không.                                         |
|                 |                                                    |
|                 | 7.  Với phương thức vân tay/khuôn mặt: kiểm tra    |
|                 |     xác thực sinh trắc học thành công.             |
|                 |                                                    |
|                 | 8.  Nếu hợp lệ, hệ thống ghi nhận bản ghi chấm     |
|                 |     công thành công cho nhân sự tại thời điểm T.   |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Ngoài cửa sổ hợp lệ***                   |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu T không thuộc cửa sổ nào, hệ thống từ chối |
| Flow)**         |     chấm công và thông báo lý do (ngoài ca/ngoài   |
|                 |     lịch dạy).                                     |
|                 |                                                    |
|                 | ***A2 --- GPS ngoài bán kính***                    |
|                 |                                                    |
|                 | 1.  Hệ thống từ chối, yêu cầu nhân sự di chuyển    |
|                 |     vào phạm vi cho phép hoặc liên hệ Quản lý điểm |
|                 |     trường để xử lý thủ công.                      |
|                 |                                                    |
|                 | ***A3 --- Xác thực sinh trắc thất bại***           |
|                 |                                                    |
|                 | 1.  Hệ thống từ chối và cho phép thử lại; nếu tất  |
|                 |     cả phương thức tự động đều tắt (theo           |
|                 |     system_settings), cho phép chấm công thủ công  |
|                 |     theo cấu hình quản trị.                        |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bản ghi chấm công hợp lệ được lưu, làm đầu vào |
| (P              |     cho tính lương (FR-HRM-04).                    |
| ostcondition)** |                                                    |
|                 | -   Trường hợp bị từ chối: không có bản ghi chấm   |
|                 |     công nào được tạo.                             |
+-----------------+----------------------------------------------------+

> **Bổ sung 2026-08-12 (ngoài Main Flow gốc, đã xác nhận với người dùng):**
> quyền `hrm.attendance.view-all` (GET /api/attendance/records) cho phép
> HR/Điều hành xem tổng hợp log chấm công toàn trung tâm theo khoảng
> ngày/nhân sự/điểm trường — không thay đổi Main Flow/Alternate Flow tự
> phục vụ ở trên.

---

UC-70: Quản lý Ca làm việc & Lịch làm việc/nghỉ lễ (bổ sung HOÀN TOÀN
ngoài SDD/SRS gốc, đã xác nhận với người dùng 2026-08-13)

Bối cảnh: `shifts`/`employee_shifts`/`work_calendar` (V7) chỉ được
`AttendanceService` (UC-09, Main Flow bước 3-4) **đọc** để xác định ngày
làm việc/cửa sổ chấm công — SRS gốc chưa từng đặc tả UC/FR nào quản trị
(tạo/sửa) 3 bảng này, khiến không có ca làm việc nào có thể được gán cho
nhân sự và mọi lượt chấm công thật đều bị từ chối "không phải ngày làm
việc". UC này bổ sung 3 nhóm API quản trị:

- **Ca làm việc** (`POST/PUT /api/shifts`, `PUT /api/shifts/{id}/deactivate`)
  — quyền `hrm.shift.create`/`hrm.shift.update`. Vô hiệu hoá là soft-delete
  (`is_active=false`), không xoá cứng.
- **Gán ca cho nhân sự** (`POST /api/employee-shifts`) — quyền
  `hrm.employee-shift.assign`. Gán ca mới tự đóng bản ghi active cũ (nếu
  có) theo đúng ràng buộc "1 nhân sự chỉ 1 ca active tại 1 thời điểm".
- **Lịch làm việc/nghỉ lễ** (`POST/DELETE /api/work-calendar`) — quyền
  `hrm.work-calendar.create`/`hrm.work-calendar.delete`. Kèm vá 1 thiếu
  sót thiết kế gốc: `work_calendar` (V7) không có ràng buộc unique, có thể
  tạo trùng override cùng ngày/phạm vi — đã thêm unique index (V120) chặn
  từ DB.

GET (danh sách) của cả 3 nhóm không gate quyền — dữ liệu tra cứu dùng
chung, đúng pattern `SiteController`/`DepartmentController`.

**"T7 xen kẽ" — 1 nhân sự có nhiều ca active cùng lúc** (bổ sung ngoài
SDD/SRS gốc, xác nhận với người dùng 2026-08-14, thay thế hướng thiết kế
ban đầu "1 ca có ngày xen kẽ"): ràng buộc gốc (V7) chỉ cho 1 nhân sự có
tối đa 1 bản ghi `employee_shifts` active tại 1 thời điểm (unique partial
index `idx_employee_shifts_active`). V124 gỡ bỏ ràng buộc này, cho phép
gán NHIỀU ca active song song cho cùng 1 nhân sự — mỗi ca dùng lại nguyên
cặp `applies_to_weekdays`/`week_parity` đã có (không thêm cột mới):

- VD "T2-T6 mọi tuần + T7 chỉ tuần chẵn": gán 2 ca cho cùng nhân sự — ca A
  (`applies_to_weekdays="1,2,3,4,5,6"`, `week_parity=EVEN`) áp dụng các
  tuần ISO số chẵn (kể cả T7), ca B (`applies_to_weekdays="1,2,3,4,5"`,
  `week_parity=ODD`) áp dụng các tuần ISO số lẻ (không có T7). Parity vẫn
  tính theo tuần ISO của năm (`WeekFields.ISO.weekOfWeekBasedYear()`) như
  cũ.
- `EmployeeShiftService.assignShift` không còn tự đóng ca active cũ khi
  gán ca mới — thay vào đó validate KHÔNG cho gán nếu ca mới chồng chéo
  lịch (cùng ngày trong tuần + parity giao nhau, ALL giao với mọi parity)
  với bất kỳ ca nào đang active của nhân sự đó
  (`ShiftAssignmentOverlapException`). Muốn đổi ca phải chủ động kết thúc
  ca cũ trước qua `PUT /api/employee-shifts/{id}/end` (mới, cùng quyền
  `hrm.employee-shift.assign`) rồi mới gán ca mới.
- `AttendanceService`: đọc TẤT CẢ ca active của nhân sự (không còn 1 ca
  duy nhất); mỗi lượt chấm công, `isWorkingDay`/cửa sổ chấm công dùng ca
  nào trong số đó khớp `applies_to_weekdays`/`week_parity` với ngày hôm
  nay (nhờ validate chống chồng chéo ở trên, tối đa 1 ca khớp mỗi ngày).

---

UC-10: Nộp đơn từ

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-10                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Nộp đơn từ                                         |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 4                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-HRM-03                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Giáo viên, Nhân viên, cấp Quản lý (Quản lý điểm    |
|                 | trường, Trưởng phòng đào tạo, Quản lý vận hành,    |
|                 | Quản lý nhân sự)                                   |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Nhân sự nộp đơn nghỉ phép/đi muộn/về sớm trực      |
| tắt**           | tuyến; Ban giám đốc được miễn trừ không thể nộp    |
|                 | đơn qua hệ thống. Giáo viên có buổi dạy trong       |
|                 | khoảng thời gian xin nghỉ phải chọn giáo viên dạy   |
|                 | thay cho từng buổi học bị ảnh hưởng.                |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Nhân sự cần xin nghỉ phép, đi muộn hoặc về sớm.    |
| hoạt**          |                                                    |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người dùng không thuộc vai trò Ban giám đốc    |
| tiên quyết      |     (được miễn trừ hoàn toàn).                     |
| (               |                                                    |
| Precondition)** | -   Mẫu đơn hiển thị được hệ thống xác định theo   |
|                 |     chức vụ (position) của người nộp: Giáo viên có |
|                 |     thêm bước chọn lớp/buổi học/giáo viên dạy thay; |
|                 |     nhân sự khác dùng mẫu đơn thường, không có      |
|                 |     bước này (bổ sung ngoài SDD gốc, đã xác nhận    |
|                 |     với người dùng 2026-08-05).                     |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người dùng mở màn hình Nộp đơn từ, kiểm tra    |
| chính (Main     |     is_management và role: nếu là Ban giám đốc, hệ |
| Flow)**         |     thống chặn ngay và không hiển thị chức năng    |
|                 |     nộp đơn.                                       |
|                 |                                                    |
|                 | 2.  Người dùng chọn loại đơn (nghỉ phép/đi muộn/về |
|                 |     sớm), nhập thời gian xin nghỉ (khoảng ngày,    |
|                 |     hoặc khung giờ cụ thể nếu nghỉ nửa buổi/đi      |
|                 |     muộn/về sớm --- dùng start_time/end_time có     |
|                 |     sẵn), lý do.                                   |
|                 |                                                    |
|                 | 3.  Nếu người nộp có chức vụ Giáo viên: hệ thống   |
|                 |     kiểm tra trong khoảng [start_date, end_date] có |
|                 |     buổi dạy nào không (class_sessions có           |
|                 |     primary_teacher_id = người nộp, status không   |
|                 |     phải CANCELLED/RESCHEDULED). Nếu có, hệ thống  |
|                 |     hiển thị danh sách buổi học đó theo TỪNG lớp;   |
|                 |     giáo viên chọn 1 lớp cho đơn hiện tại, sau đó   |
|                 |     chọn giáo viên dạy thay cho từng buổi học của   |
|                 |     lớp đó (bắt buộc chọn đủ, không được bỏ trống). |
|                 |     Nếu giáo viên có buổi dạy thuộc nhiều lớp khác  |
|                 |     nhau trong cùng khoảng nghỉ, chỉ xử lý 1 lớp    |
|                 |     trong đơn này; các lớp còn lại nộp đơn riêng    |
|                 |     (xem A3). (bổ sung ngoài SDD gốc, đã xác nhận   |
|                 |     với người dùng 2026-08-05).                     |
|                 |                                                    |
|                 | 4.  Hệ thống xác định quy trình duyệt phù hợp: (a) |
|                 |     nhân sự thuộc phòng ban thường → duyệt 2 cấp   |
|                 |     (Trưởng phòng ban rồi Quản lý vận hành), hoặc  |
|                 |     1 cấp nếu phòng ban chưa có trưởng phòng       |
|                 |     (chuyển thẳng Quản lý vận hành); (b) nhân sự   |
|                 |     là cấp Quản lý (Quản lý điểm trường/Trưởng     |
|                 |     phòng đào tạo/Quản lý vận hành/Quản lý nhân    |
|                 |     sự) → duyệt 1 cấp bởi Ban giám đốc.            |
|                 |                                                    |
|                 | 5.  Người dùng xác nhận Gửi đơn; hệ thống khởi tạo |
|                 |     workflow duyệt nhiều bước tương ứng và gửi     |
|                 |     thông báo tới người duyệt đầu tiên. Nếu đơn có |
|                 |     kèm lựa chọn dạy thay (bước 3), hệ thống ÁP     |
|                 |     DỤNG NGAY --- không đợi duyệt xong --- vì buổi  |
|                 |     dạy có thể diễn ra trước khi quy trình duyệt    |
|                 |     hoàn tất: với mỗi buổi học đã chọn, (a) ghi 1   |
|                 |     bản ghi leave_substitutions (buổi học, giáo     |
|                 |     viên chính hiện tại, giáo viên dạy thay); (b)   |
|                 |     cập nhật class_sessions.primary_teacher_id của  |
|                 |     buổi đó thành giáo viên dạy thay; (c) với mỗi   |
|                 |     cặp (lớp, giáo viên dạy thay) xuất hiện trong    |
|                 |     đơn, tạo/cập nhật 1 bản ghi class_teachers        |
|                 |     (teacher_role=SUBSTITUTE, assigned_from=         |
|                 |     start_date, assigned_to=NULL --- NULL = đang     |
|                 |     phụ trách, cùng quy ước với PRIMARY/ASSISTANT;   |
|                 |     chỉ set assigned_to khi thu hồi, xem UC-11 A2/   |
|                 |     Mở rộng). Giáo viên                              |
|                 |     dạy thay có quyền điểm danh/nhận xét/giao bài    |
|                 |     cho buổi đó ngay (UC-15), kể cả khi đơn đang ở   |
|                 |     trạng thái Chờ duyệt. Nếu đơn sau đó bị Từ chối, |
|                 |     việc dạy thay bị thu hồi ngay lập tức (xem       |
|                 |     UC-11 A2). (bổ sung ngoài SDD gốc, đã xác nhận   |
|                 |     với người dùng 2026-08-05).                      |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Ban giám đốc***                          |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Use case bị chặn ngay từ bước 1; hệ thống      |
| Flow)**         |     không cho phép Ban giám đốc tạo đơn qua hệ     |
|                 |     thống.                                         |
|                 |                                                    |
|                 | ***A2 --- Phòng ban không có trưởng phòng***       |
|                 |                                                    |
|                 | 1.  Hệ thống bỏ qua bước duyệt Trưởng phòng ban,   |
|                 |     chuyển thẳng đơn lên Quản lý vận hành duyệt.   |
|                 |                                                    |
|                 | ***A3 --- Giáo viên nghỉ trùng nhiều lớp (bổ sung  |
|                 | ngoài SDD gốc, đã xác nhận với người dùng           |
|                 | 2026-08-05)***                                      |
|                 |                                                    |
|                 | 1.  Giáo viên có buổi dạy thuộc nhiều lớp khác nhau |
|                 |     trong khoảng nghỉ đã chọn --- hệ thống chỉ cho  |
|                 |     xử lý dạy thay cho 1 lớp/đơn; giáo viên phải    |
|                 |     nộp thêm đơn riêng cho từng lớp còn lại.        |
|                 |                                                    |
|                 | ***A4 --- Giáo viên không có lịch dạy trong khoảng  |
|                 | nghỉ (bổ sung ngoài SDD gốc, đã xác nhận với người  |
|                 | dùng 2026-08-05)***                                 |
|                 |                                                    |
|                 | 1.  Không có buổi dạy nào của người nộp rơi vào     |
|                 |     khoảng [start_date, end_date] --- hệ thống bỏ   |
|                 |     qua bước 3, xử lý như mẫu đơn thường (không có  |
|                 |     bước chọn dạy thay).                            |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Đơn từ được tạo với trạng thái Chờ duyệt và    |
| (P              |     đúng số bước duyệt theo nhóm nhân sự.          |
| ostcondition)** |                                                    |
|                 | -   Người duyệt bước đầu tiên nhận được thông báo  |
|                 |     cần xử lý.                                     |
|                 |                                                    |
|                 | -   Nếu đơn có chọn giáo viên dạy thay: buổi học    |
|                 |     liên quan đã được gán đúng giáo viên dạy thay    |
|                 |     NGAY (không đợi duyệt --- xem bước 5); việc gán  |
|                 |     này bị thu hồi ngay nếu đơn sau đó bị Từ chối    |
|                 |     (UC-11 A2), hoặc tự động thu hồi sau 2 ngày kể   |
|                 |     từ end_date nếu đơn Đã duyệt (UC-11, mục Mở      |
|                 |     rộng).                                           |
+-----------------+----------------------------------------------------+

---

UC-11: Duyệt đơn từ

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-11                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Duyệt đơn từ                                       |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 4                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-HRM-03                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Trưởng phòng ban, Quản lý vận hành, Ban giám đốc   |
|                 | (tùy nhóm nhân sự và bước duyệt)                   |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Người có thẩm quyền xem xét và ra quyết định       |
| tắt**           | duyệt/từ chối đơn từ theo đúng bước trong quy      |
|                 | trình đã xác định ở UC-10.                         |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Có đơn từ đang chờ duyệt ở bước thuộc thẩm quyền   |
| hoạt**          | người duyệt.                                       |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Đơn từ đã được nộp (UC-10) và đang ở trạng     |
| tiên quyết      |     thái Chờ duyệt tại bước của người duyệt hiện   |
| (               |     tại.                                           |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người duyệt mở danh sách đơn từ chờ duyệt      |
| chính (Main     |     thuộc thẩm quyền.                              |
| Flow)**         |                                                    |
|                 | 2.  Người duyệt xem chi tiết đơn (loại, thời gian, |
|                 |     lý do, người nộp).                             |
|                 |                                                    |
|                 | 3.  Người duyệt ra quyết định: APPROVED hoặc       |
|                 |     REJECTED, có thể kèm ghi chú.                  |
|                 |                                                    |
|                 | 4.  Nếu APPROVED và còn bước tiếp theo trong       |
|                 |     workflow (đơn 2 bước), hệ thống chuyển đơn     |
|                 |     sang bước kế tiếp và thông báo người duyệt     |
|                 |     tiếp theo (lặp lại bước 1-3).                  |
|                 |                                                    |
|                 | 5.  Nếu APPROVED và là bước cuối cùng, hệ thống    |
|                 |     cập nhật trạng thái đơn thành Đã duyệt, thông  |
|                 |     báo cho người nộp đơn.                         |
|                 |                                                    |
|                 | 6.  Nếu REJECTED ở bất kỳ bước nào, hệ thống kết   |
|                 |     thúc ngay quy trình duyệt (không cần đi hết    |
|                 |     các bước còn lại), cập nhật trạng thái Từ chối |
|                 |     và thông báo người nộp đơn kèm lý do.          |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Từ chối giữa chừng (đơn 2 bước)***       |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu Trưởng phòng ban từ chối ở bước 1, đơn     |
| Flow)**         |     không được chuyển tiếp lên Quản lý vận hành;   |
|                 |     quy trình kết thúc ngay với trạng thái Từ      |
|                 |     chối.                                          |
|                 |                                                    |
|                 | ***A2 --- Từ chối đơn đã có giáo viên dạy thay (bổ  |
|                 | sung ngoài SDD gốc, đã xác nhận với người dùng      |
|                 | 2026-08-05, xem UC-10 bước 5)***                    |
|                 |                                                    |
|                 | 1.  Khi đơn chuyển sang Từ chối (bước 6 Main Flow,  |
|                 |     ở BẤT KỲ bước duyệt nào) VÀ đơn có bản ghi       |
|                 |     leave_substitutions đang mở (revoked_at IS      |
|                 |     NULL, đã được gán ngay từ lúc nộp đơn --- UC-10  |
|                 |     bước 5): hệ thống thu hồi NGAY LẬP TỨC, không    |
|                 |     đợi tới chu kỳ 2 ngày --- với mỗi bản ghi: (a)   |
|                 |     trả class_sessions.primary_teacher_id về đúng    |
|                 |     giáo viên chính ban đầu (original_teacher_id);   |
|                 |     (b) đóng bản ghi class_teachers tương ứng         |
|                 |     (assigned_to = ngày từ chối) nếu không còn        |
|                 |     leave_substitutions nào khác đang mở cho cùng     |
|                 |     cặp (lớp, giáo viên dạy thay); (c) ghi nhận        |
|                 |     revoked_at = thời điểm từ chối --- đây chính là   |
|                 |     log thu hồi phục vụ tra vết.                      |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Trạng thái đơn từ được cập nhật chính xác theo |
| (P              |     quyết định của người duyệt ở từng bước.        |
| ostcondition)** |                                                    |
|                 | -   Người nộp đơn nhận được thông báo kết quả cuối |
|                 |     cùng.                                          |
|                 |                                                    |
|                 | -   Đơn nghỉ phép đã duyệt được dùng làm dữ liệu   |
|                 |     trừ công khi tính lương (UC-12).               |
|                 |                                                    |
|                 | -   Với đơn có dạy thay: nếu APPROVED, việc dạy      |
|                 |     thay (đã áp dụng từ lúc nộp đơn --- UC-10 bước 5) |
|                 |     tiếp tục hiệu lực và tự động thu hồi sau 2 ngày   |
|                 |     kể từ end_date (xem Mở rộng bên dưới); nếu        |
|                 |     REJECTED, việc dạy thay đã bị thu hồi ngay (A2).  |
+-----------------+----------------------------------------------------+

Mở rộng --- Tự động thu hồi giáo viên dạy thay + trang lịch sử dạy thay
(bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-05)

-   Tác nhân: Hệ thống (scheduled job chạy hàng ngày).
-   Điều kiện kích hoạt: bản ghi leave_substitutions có revoked_at IS NULL,
    thuộc 1 đơn có status = APPROVED (đơn PENDING/REJECTED không thuộc
    diện job này --- REJECTED đã thu hồi ngay theo A2, PENDING chưa tới
    hạn), và leave_requests.end_date + 2 ngày <= ngày hiện tại.
-   Với mỗi bản ghi thỏa điều kiện: (a) trả
    class_sessions.primary_teacher_id về đúng giáo viên chính ban đầu
    (original_teacher_id) --- không ảnh hưởng dữ liệu điểm danh/nhận xét
    giáo viên dạy thay đã ghi nhận trước đó vì các bảng đó độc lập, không
    phụ thuộc primary_teacher_id tại thời điểm đọc lại; (b) đóng bản ghi
    class_teachers tương ứng (assigned_to = ngày thu hồi) nếu không còn
    leave_substitutions nào khác đang mở cho cùng cặp (lớp, giáo viên dạy
    thay); (c) ghi nhận revoked_at = thời điểm job chạy --- đây chính là
    log thu hồi phục vụ tra vết.
-   Trang lịch sử dạy thay: liệt kê leave_substitutions kèm thông tin đơn
    nghỉ/buổi học/giáo viên, lọc theo lớp/giáo viên/khoảng thời gian, hiển
    thị trạng thái Đang dạy thay (revoked_at NULL) hoặc Đã thu hồi
    (revoked_at có giá trị) --- phục vụ Quản lý điểm trường/Trưởng phòng
    đào tạo/Quản lý nhân sự tra soát.

---

UC-12: Xem bảng lương

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-12                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xem bảng lương                                     |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 4                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-HRM-04                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Giáo viên, Nhân viên (xem bảng lương của bản thân) |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Quản lý nhân sự (xem/quản lý    |
|                 | bảng lương toàn hệ thống))                         |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Xem bảng lương được hệ thống tự động tổng hợp dựa  |
| tắt**           | trên ngày công/số tiết dạy thực tế, trừ đi các     |
|                 | khoản phạt, thuế, bảo hiểm.                        |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Đến kỳ lương hoặc người dùng cần tra cứu bảng      |
| hoạt**          | lương của mình/của nhân sự.                        |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Kỳ lương đã được hệ thống tính toán (dựa trên  |
| tiên quyết      |     dữ liệu chấm công UC-09 và số tiết dạy thực tế |
| (               |     lấy từ Phân hệ Học thuật).                     |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người dùng mở màn hình Bảng lương, chọn kỳ     |
| chính (Main     |     lương cần xem.                                 |
| Flow)**         |                                                    |
|                 | 2.  Trường hợp Giáo viên/Nhân viên: hệ thống chỉ   |
|                 |     hiển thị bảng lương của chính người dùng (kiểm |
|                 |     soát ở tầng Service --- NFR-SEC-03).           |
|                 |                                                    |
|                 | 3.  Trường hợp Quản lý nhân sự: hệ thống hiển thị  |
|                 |     danh sách bảng lương toàn hệ thống, có thể lọc |
|                 |     theo điểm trường/phòng ban/nhân sự.            |
|                 |                                                    |
|                 | 4.  Hệ thống hiển thị chi tiết: công thức tính     |
|                 |     (ngày công × đơn giá hoặc số tiết dạy × đơn    |
|                 |     giá tiết), các khoản trừ (phạt, thuế, bảo      |
|                 |     hiểm), lương thực nhận.                        |
|                 |                                                    |
|                 | 5.  Quản lý nhân sự có thể xuất báo cáo bảng lương |
|                 |     theo kỳ.                                       |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Kỳ lương chưa được tính toán***          |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống thông báo bảng lương của kỳ hiện tại  |
| Flow)**         |     chưa sẵn sàng và hiển thị kỳ gần nhất đã có dữ |
|                 |     liệu.                                          |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Người dùng xem được đúng thông tin bảng lương  |
| (P              |     trong phạm vi quyền hạn của mình; dữ liệu      |
| ostcondition)** |     không bị thay đổi (use case chỉ đọc).          |
+-----------------+----------------------------------------------------+

UC-51: Nhập nhân sự theo lô

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-51                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Nhập nhân sự theo lô                               |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 4                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-HRM-05                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản lý nhân sự                                    |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Quản lý nhân sự nhập file Excel danh sách nhân sự  |
| tắt**           | để tạo tài khoản + hồ sơ hàng loạt, thay vì nhập   |
|                 | tay từng người (UC-08). Hệ thống tự sinh mật khẩu  |
|                 | tạm cho từng tài khoản, không đặt mật khẩu qua     |
|                 | file Excel (rủi ro bảo mật khi file bị chuyển tay/ |
|                 | email).                                            |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Quản lý nhân sự cần khởi tạo hàng loạt tài khoản + |
| hoạt**          | hồ sơ nhân sự (VD chuyển dữ liệu từ hệ thống cũ).  |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người dùng có quyền hrm.employee.import (V51,  |
| tiên quyết      |     tách từ hrm.manage).                           |
| (               | -   Có file Excel danh sách nhân sự đúng định dạng |
| Precondition)** |     mẫu.                                           |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản lý nhân sự tải file Excel lên.            |
| chính (Main     |                                                    |
| Flow)**         | 2.  Hệ thống tạo bản ghi import_jobs               |
|                 |     (import_type=EMPLOYEES), đọc và xác thực định |
|                 |     dạng file.                                     |
|                 |                                                    |
|                 | 3.  Với từng dòng: kiểm tra trùng lặp theo mã nhân |
|                 |     sự/username/email.                             |
|                 |                                                    |
|                 | 4.  Tạo tài khoản (username bắt buộc, email tùy    |
|                 |     chọn --- trống thì hệ thống tự sinh placeholder |
|                 |     để thỏa ràng buộc email NOT NULL) kèm mật khẩu |
|                 |     tạm sinh ngẫu nhiên, và hồ sơ nhân sự (mã nhân |
|                 |     sự, loại nhân sự, phòng ban nếu có, chức vụ    |
|                 |     nếu có, cờ miễn trừ is_management) cho từng    |
|                 |     dòng hợp lệ. Nếu dòng có chức vụ (mã chức vụ), |
|                 |     áp dụng luôn A5 của UC-08 (FR-HRM-06, xem       |
|                 |     UC-52) --- tự gán vai trò mặc định của chức vụ |
|                 |     đó; không để chức vụ thì không tự gán role nào |
|                 |     (Quản lý nhân sự gán tay sau qua UC-46).        |
|                 |                                                    |
|                 | 5.  Hệ thống cập nhật total_rows/success_rows/     |
|                 |     failed_rows/error_summary, trạng thái COMPLETED|
|                 |     hoặc PARTIAL_SUCCESS.                          |
|                 |                                                    |
|                 | 6.  Quản lý nhân sự xem kết quả, nhận danh sách    |
|                 |     username + mật khẩu tạm (chỉ hiển thị 1 lần    |
|                 |     ngay trong kết quả của bước tải lên, không tra |
|                 |     cứu lại được sau đó) để gửi riêng từng người,  |
|                 |     và tải danh sách dòng lỗi (nếu có).            |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- File sai định dạng***                    |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  File rỗng, thiếu dòng tiêu đề, hoặc không mở   |
| Flow)**         |     được như file Excel (.xlsx) hợp lệ --- hệ      |
|                 |     thống từ chối xử lý toàn bộ, đánh dấu          |
|                 |     import_jobs.status=FAILED ngay, không tạo bản  |
|                 |     ghi nào.                                       |
|                 |                                                    |
|                 | ***A2 --- Một phần dòng lỗi/trùng lặp***           |
|                 |                                                    |
|                 | 1.  1 hoặc nhiều dòng lỗi (thiếu trường bắt buộc,  |
|                 |     mã nhân sự/username/email trùng, mã phòng ban  |
|                 |     hoặc mã chức vụ không tồn tại, sai định dạng   |
|                 |     ngày) --- hệ thống vẫn tạo tài khoản + hồ sơ   |
|                 |     cho các dòng hợp lệ, bỏ qua dòng lỗi, đánh dấu |
|                 |     status=PARTIAL_SUCCESS, liệt kê chi tiết từng  |
|                 |     dòng lỗi trong error_summary.                  |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Hồ sơ nhân sự hợp lệ được tạo hàng loạt kèm    |
| (P              |     tài khoản đăng nhập (username/password), vai   |
| ostcondition)** |     trò mặc định của chức vụ (nếu dòng có chỉ định |
|                 |     chức vụ — FR-HRM-06/UC-52) đã được gán sẵn; dòng|
|                 |     không có chức vụ thì sẵn sàng để Quản lý nhân   |
|                 |     sự gán role tay sau qua UC-46. Kết quả import   |
|                 |     (bao gồm mật khẩu tạm của lần import đó) được   |
|                 |     trả về ngay cho người thực hiện; tra cứu lại job|
|                 |     sau đó (UC tương tự UC-35 getJob) KHÔNG còn     |
|                 |     thấy mật khẩu tạm.                              |
+-----------------+----------------------------------------------------+

Mở rộng --- File mẫu + xuất danh sách tài khoản (bổ sung ngoài SDD gốc, đã
xác nhận với người dùng 2026-07-24)

-   Trước bước 1, Quản lý nhân sự có thể gọi `GET
    /api/employee-imports/template` để tải file Excel mẫu đầy đủ 10 cột
    theo đúng thứ tự Main Flow đọc, trường bắt buộc đánh dấu `*` cuối tên
    cột, không có cột mật khẩu.
-   Sau bước 6, có thể gọi `POST /api/employee-imports/accounts-export`
    với đúng danh sách username + mật khẩu tạm vừa nhận được để lấy file
    Excel giao lại cho từng nhân sự — chỉ dùng được trong cùng phiên vừa
    import (không lưu mật khẩu tạm lại để tra cứu sau).

---

UC-52: Danh mục chức vụ & tự động gán vai trò theo chức vụ

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-52                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Danh mục chức vụ & tự động gán vai trò theo chức   |
| Case**          | vụ                                                 |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 4                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-HRM-06                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản lý nhân sự                                    |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Quản lý nhân sự duy trì danh mục chức vụ (positions)|
| tắt**           | và cấu hình vai trò (role) mặc định cho từng chức  |
|                 | vụ (position_default_roles, 1 chức vụ có thể ánh   |
|                 | xạ nhiều role). Khi hồ sơ nhân sự (UC-08/UC-51)    |
|                 | được gán 1 chức vụ, hệ thống tự động đồng bộ vai   |
|                 | trò của tài khoản theo đúng danh sách mặc định đó. |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Quản lý nhân sự cần thêm/sửa chức vụ, hoặc cấu     |
| hoạt**          | hình lại vai trò mặc định của 1 chức vụ.           |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người dùng có quyền hrm.position.create/update/ |
| tiên quyết      |     delete tương ứng theo thao tác (đã có sẵn từ    |
| (               |     V49, tách từ hrm.manage).                       |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản lý nhân sự tạo/sửa 1 chức vụ (mã, tên).   |
| chính (Main     |     Mã chức vụ bất biến sau khi tạo.                |
| Flow)**         |                                                    |
|                 | 2.  Quản lý nhân sự chọn danh sách vai trò (role)  |
|                 |     hệ thống làm mặc định cho chức vụ đó (0..N,    |
|                 |     thay thế toàn bộ danh sách cũ mỗi lần lưu).    |
|                 |                                                    |
|                 | 3.  Hệ thống lưu lại — danh sách chức vụ dùng làm  |
|                 |     dropdown khi tạo/sửa hồ sơ nhân sự (UC-08) và  |
|                 |     nhập nhân sự theo lô (UC-51).                  |
|                 |                                                    |
|                 | 4.  Xem UC-08 A5 / UC-51 bước 4 cho cơ chế tự động |
|                 |     gán/thu hồi vai trò khi hồ sơ nhân sự được gán |
|                 |     chức vụ.                                       |
|                 |                                                    |
|                 | 5.  Nếu chức vụ đang có sẵn nhân sự nắm giữ, hệ    |
|                 |     thống ngay lập tức rà lại toàn bộ nhân sự đó   |
|                 |     và đồng bộ vai trò theo danh sách mặc định vừa |
|                 |     lưu ở bước 2 (cùng cơ chế UC-08 A5) — không    |
|                 |     chỉ áp dụng cho các lần gán chức vụ sau này.   |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Xóa chức vụ đang được sử dụng***         |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Chức vụ đang được gán cho ít nhất 1 hồ sơ nhân |
| Flow)**         |     sự --- hệ thống từ chối xóa, báo chuyển nhân   |
|                 |     sự sang chức vụ khác trước.                     |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Danh mục chức vụ và ánh xạ vai trò mặc định    |
| (P              |     phản ánh đúng cấu hình mới nhất; mọi lần tạo/  |
| ostcondition)** |     đổi chức vụ của hồ sơ nhân sự sau đó áp dụng   |
|                 |     đúng danh sách vai trò mặc định hiện hành.     |
|                 | -   Toàn bộ nhân sự ĐANG giữ chức vụ vừa cấu hình  |
|                 |     lại cũng được đồng bộ vai trò ngay (không phải |
|                 |     chờ tới lần sửa hồ sơ tiếp theo).              |
+-----------------+----------------------------------------------------+

Phân hệ 5 --- Quản lý học sinh