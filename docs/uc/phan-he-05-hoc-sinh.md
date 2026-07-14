# phan-he-05-hoc-sinh

UC-13: Quản lý hồ sơ học sinh

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-13                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Quản lý hồ sơ học sinh                             |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 5                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-STU-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Nhân viên (Giáo vụ)                                |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Quản lý điểm trường)            |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Lưu trữ và duy trì thông tin cá nhân, mã số học    |
| tắt**           | sinh, ảnh chân dung, thông tin liên hệ phụ huynh,  |
|                 | lịch sử chuyển lớp/chuyển điểm trường.             |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Cần tạo hồ sơ học sinh mới hoặc cập nhật hồ sơ     |
| hoạt**          | hiện có (không thuộc luồng chuyển đổi lead ---     |
|                 | UC-34).                                            |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người dùng có quyền student.manage (Nhân viên  |
| tiên quyết      |     giáo vụ hoặc Quản lý điểm trường tại điểm      |
| (               |     trường của học sinh).                          |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người dùng mở màn hình Hồ sơ học sinh, chọn    |
| chính (Main     |     Thêm mới hoặc tìm 1 học sinh hiện có.          |
| Flow)**         |                                                    |
|                 | 2.  Nhập/cập nhật thông tin cá nhân, tải ảnh chân  |
|                 |     dung, thông tin liên hệ của Phụ huynh (liên    |
|                 |     kết tài khoản Phụ huynh nếu đã có, hoặc khởi   |
|                 |     tạo mới).                                      |
|                 |                                                    |
|                 | 3.  Hệ thống sinh/giữ nguyên mã số học sinh (ID    |
|                 |     duy nhất).                                     |
|                 |                                                    |
|                 | 4.  Nếu có yêu cầu chuyển lớp/chuyển điểm trường,  |
|                 |     người dùng ghi nhận sự kiện chuyển đổi; hệ     |
|                 |     thống lưu vào lịch sử chuyển lớp/chuyển điểm   |
|                 |     trường của học sinh.                           |
|                 |                                                    |
|                 | 5.  Hệ thống lưu thay đổi và cập nhật hồ sơ.       |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Chuyển điểm trường khác Quản lý điểm     |
| thế / ngoại lệ  | trường phụ trách***                                |
| (Alternate      |                                                    |
| Flow)**         | 1.  Nếu học sinh chuyển sang điểm trường do Quản   |
|                 |     lý điểm trường khác phụ trách, hệ thống cập    |
|                 |     nhật lại phạm vi truy cập dữ liệu (row-level   |
|                 |     theo điểm trường) ngay khi giao dịch hoàn tất. |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Hồ sơ học sinh được cập nhật chính xác, đầy    |
| (P              |     đủ; lịch sử chuyển lớp/chuyển điểm trường được |
| ostcondition)** |     lưu vết phục vụ tra cứu.                       |
+-----------------+----------------------------------------------------+

---

UC-14: Cập nhật trạng thái học tập

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-14                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Cập nhật trạng thái học tập                        |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 5                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-STU-02                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản lý điểm trường                                |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Nhân viên (Giáo vụ))            |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Cập nhật và theo dõi trạng thái học tập của học    |
| tắt**           | sinh theo thời gian thực: Đang học, Bảo lưu, Đình  |
|                 | chỉ, Đã tốt nghiệp.                                |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Phát sinh sự kiện thay đổi trạng thái học tập của  |
| hoạt**          | học sinh (bảo lưu, đình chỉ, tốt nghiệp\...).      |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Học sinh đã có hồ sơ trong hệ thống (UC-13)    |
| tiên quyết      |     với trạng thái hiện tại xác định.              |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người dùng mở hồ sơ học sinh, chọn chức năng   |
| chính (Main     |     Cập nhật trạng thái học tập.                   |
| Flow)**         |                                                    |
|                 | 2.  Người dùng chọn trạng thái mới (Đang học/Bảo   |
|                 |     lưu/Đình chỉ/Đã tốt nghiệp) và nhập lý do/ghi  |
|                 |     chú.                                           |
|                 |                                                    |
|                 | 3.  Hệ thống kiểm tra tính hợp lệ của chuyển trạng |
|                 |     thái (ví dụ: không cho chuyển thẳng từ Đã tốt  |
|                 |     nghiệp về Đang học mà không có xác nhận đặc    |
|                 |     biệt).                                         |
|                 |                                                    |
|                 | 4.  Hệ thống lưu trạng thái mới kèm thời điểm hiệu |
|                 |     lực, ghi lịch sử thay đổi trạng thái.          |
|                 |                                                    |
|                 | 5.  Hệ thống cập nhật hiển thị trạng thái tại mọi  |
|                 |     nơi liên quan (Portal Phụ huynh, danh sách     |
|                 |     lớp, báo cáo).                                 |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Chuyển trạng thái không hợp lệ***        |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu chuyển đổi không nằm trong tập hợp các     |
| Flow)**         |     chuyển đổi trạng thái hợp lệ, hệ thống từ chối |
|                 |     và giải thích lý do.                           |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Trạng thái học tập của học sinh được cập nhật  |
| (P              |     theo thời gian thực và phản ánh nhất quán trên |
| ostcondition)** |     toàn hệ thống.                                 |
+-----------------+----------------------------------------------------+

---

UC-15: Điểm danh học sinh

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-15                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Điểm danh học sinh                                 |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 5                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-STU-03                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Giáo viên                                          |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Hệ thống gửi thông báo          |
|                 | (background job))                                  |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Giáo viên điểm danh học sinh đầu mỗi tiết học; hệ  |
| tắt**           | thống tự động tổng hợp tỷ lệ nghỉ học và gửi thông |
|                 | báo vắng mặt ngay lập tức cho Phụ huynh.           |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Đến giờ bắt đầu tiết học, Giáo viên thực hiện điểm |
| hoạt**          | danh.                                              |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Buổi học/tiết học đã được xếp lịch (UC-48) và  |
| tiên quyết      |     Giáo viên được phân công giảng dạy tiết đó.    |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Giáo viên mở màn hình điểm danh của lớp, chọn  |
| chính (Main     |     chế độ điểm danh: SESSION_LEVEL (1 lần cho cả  |
| Flow)**         |     buổi) hoặc PERIOD_LEVEL (theo từng tiết).      |
|                 |                                                    |
|                 | 2.  Giáo viên đánh dấu trạng thái từng học sinh:   |
|                 |     Có mặt/Vắng (ABSENT)/Muộn (LATE).              |
|                 |                                                    |
|                 | 3.  Giáo viên có thể tùy chọn vào sửa lại chi tiết |
|                 |     theo từng tiết cho 1 học sinh cụ thể sau khi   |
|                 |     đã điểm danh nhanh cả buổi.                    |
|                 |                                                    |
|                 | 4.  Giáo viên xác nhận Lưu điểm danh; hệ thống ghi |
|                 |     nhận bản ghi điểm danh.                        |
|                 |                                                    |
|                 | 5.  Nếu có học sinh ABSENT hoặc LATE, hệ thống     |
|                 |     kích hoạt background job gửi thông báo         |
|                 |     (Email/thông báo trong Portal) cho Phụ huynh   |
|                 |     --- tiến trình này chạy bất đồng bộ, độc lập   |
|                 |     với thao tác của Giáo viên.                    |
|                 |                                                    |
|                 | 6.  Background job gửi thông báo; nếu thất bại, tự |
|                 |     động thử lại (retry) tối đa số lần cấu hình    |
|                 |     (max retry) trước khi dừng.                    |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Gửi thông báo thất bại vượt quá số lần   |
| thế / ngoại lệ  | retry***                                           |
| (Alternate      |                                                    |
| Flow)**         | 1.  Hệ thống dừng retry, đánh dấu thông báo gửi    |
|                 |     thất bại để Quản lý điểm trường có thể xử lý   |
|                 |     thủ công hoặc tra soát sau.                    |
|                 |                                                    |
|                 | ***A2 --- Toàn bộ học sinh có mặt***               |
|                 |                                                    |
|                 | 1.  Không có học sinh ABSENT/LATE nào, hệ thống    |
|                 |     không kích hoạt luồng gửi thông báo, chỉ lưu   |
|                 |     bản ghi điểm danh.                             |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bản ghi điểm danh của tiết/buổi học được lưu   |
| (P              |     chính xác.                                     |
| ostcondition)** |                                                    |
|                 | -   Tỷ lệ chuyên cần của học sinh được cập nhật,   |
|                 |     dùng cho báo cáo (UC-25, UC-29).               |
|                 |                                                    |
|                 | -   Thông báo vắng mặt (nếu có) được gửi hoặc ghi  |
|                 |     nhận trạng thái gửi.                           |
+-----------------+----------------------------------------------------+

Phân hệ 6 --- Quản lý học thuật và đào tạo