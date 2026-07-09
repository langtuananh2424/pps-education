# phan-he-01-dang-nhap

UC-01: Đăng nhập hệ thống

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-01                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Đăng nhập hệ thống                                 |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 1                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-AUT-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Tất cả 11 tác nhân (người dùng đã có tài khoản)    |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Hệ thống xác thực (JWT +        |
|                 | Refresh Token), Google OAuth)                      |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Người dùng xác thực danh tính để truy cập hệ thống |
| tắt**           | bằng tài khoản/mật khẩu nội bộ hoặc đăng nhập      |
|                 | nhanh qua Google.                                  |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Người dùng truy cập trang đăng nhập và nhập thông  |
| hoạt**          | tin xác thực.                                      |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người dùng đã có tài khoản trong bảng users    |
| tiên quyết      |     với status = \'ACTIVE\'.                       |
| (               |                                                    |
| Precondition)** | -   Tài khoản không đang trong trạng thái bị khóa  |
|                 |     (locked_until là NULL hoặc đã qua thời điểm    |
|                 |     khóa).                                         |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người dùng mở trang đăng nhập, chọn phương     |
| chính (Main     |     thức: (a) Tài khoản/Mật khẩu hoặc (b) Đăng     |
| Flow)**         |     nhập nhanh qua Google.                         |
|                 |                                                    |
|                 | 2.  Trường hợp (a): người dùng nhập username/email |
|                 |     và mật khẩu, bấm Đăng nhập.                    |
|                 |                                                    |
|                 | 3.  Hệ thống kiểm tra username/email tồn tại, so   |
|                 |     khớp password_hash (BCrypt) với mật khẩu nhập  |
|                 |     vào.                                           |
|                 |                                                    |
|                 | 4.  Trường hợp (b): người dùng chọn tài khoản      |
|                 |     Google, hệ thống nhận id_token từ Google, đối  |
|                 |     chiếu với google_id hoặc email đã liên kết     |
|                 |     trong bảng users.                              |
|                 |                                                    |
|                 | 5.  Xác thực thành công: hệ thống cấp Access Token |
|                 |     (JWT, stateless) và Refresh Token (lưu         |
|                 |     token_hash trong bảng refresh_tokens), cập     |
|                 |     nhật last_login_at, reset failed_login_count = |
|                 |     0.                                             |
|                 |                                                    |
|                 | 6.  Hệ thống ghi 1 dòng vào login_attempts với     |
|                 |     success = TRUE.                                |
|                 |                                                    |
|                 | 7.  Hệ thống điều hướng người dùng tới Dashboard   |
|                 |     tương ứng với vai trò (role) của tài khoản     |
|                 |     (NFR-UI-02).                                   |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Sai mật khẩu/tài khoản không tồn tại***  |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Tại bước 3, nếu username/email không tồn tại   |
| Flow)**         |     hoặc mật khẩu không khớp, hệ thống tăng        |
|                 |     failed_login_count lên 1, ghi login_attempts   |
|                 |     với success = FALSE và failure_reason tương    |
|                 |     ứng (WRONG_PASSWORD/USER_NOT_FOUND).           |
|                 |                                                    |
|                 | 2.  Hệ thống hiển thị thông báo lỗi chung chung    |
|                 |     (không tiết lộ tài khoản có tồn tại hay        |
|                 |     không), quay lại bước 2.                       |
|                 |                                                    |
|                 | ***A2 --- Vượt quá 5 lần sai (FR-AUT-02, cơ chế    |
|                 | chống Brute-Force)***                              |
|                 |                                                    |
|                 | 1.  Khi failed_login_count đạt 5, hệ thống đặt     |
|                 |     locked_until = now() + 15 phút, ghi nhận địa   |
|                 |     chỉ IP và gửi cảnh báo cho Quản trị viên.      |
|                 |                                                    |
|                 | 2.  Hệ thống hiển thị thông báo tài khoản đang tạm |
|                 |     khóa và thời gian có thể thử lại.              |
|                 |                                                    |
|                 | 3.  Use case kết thúc; các lần đăng nhập trong 15  |
|                 |     phút tới đều bị từ chối kèm failure_reason =   |
|                 |     USER_LOCKED cho tới khi locked_until trôi qua. |
|                 |                                                    |
|                 | ***A3 --- Tài khoản INACTIVE/SUSPENDED***          |
|                 |                                                    |
|                 | 1.  Tại bước 3/4, nếu status khác \'ACTIVE\', hệ   |
|                 |     thống từ chối đăng nhập, ghi failure_reason =  |
|                 |     USER_INACTIVE và yêu cầu người dùng liên hệ    |
|                 |     Quản trị viên.                                 |
|                 |                                                    |
|                 | ***A4 --- Google OAuth thất bại/chưa liên kết***   |
|                 |                                                    |
|                 | 1.  Nếu email Google chưa tồn tại trong bảng       |
|                 |     users, hệ thống thông báo tài khoản chưa được  |
|                 |     cấp, hướng dẫn liên hệ Quản trị viên để khởi   |
|                 |     tạo.                                           |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Thành công: người dùng có Access Token +       |
| (P              |     Refresh Token hợp lệ, phiên làm việc được      |
| ostcondition)** |     thiết lập, last_login_at cập nhật.             |
|                 |                                                    |
|                 | -   Thất bại: không có token nào được cấp;         |
|                 |     login_attempts ghi nhận lần thử thất bại để    |
|                 |     phục vụ tra soát và cơ chế khóa tài khoản.     |
+-----------------+----------------------------------------------------+

Phân hệ 2 --- Quản trị người dùng & Phân quyền