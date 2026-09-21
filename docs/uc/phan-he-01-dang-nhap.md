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

**Bổ sung 2026-09-13 (đã xác nhận với người dùng) — chặn đăng nhập 2
thiết bị cùng lúc cho tài khoản Học sinh**

Phát hiện qua báo cáo thực tế: tài khoản Học sinh đăng nhập ở thiết bị 1
(VD đang làm bài), sau đó đăng nhập tiếp ở thiết bị 2 bằng cùng tài khoản
— hệ thống KHÔNG chặn, cả 2 thiết bị đều thao tác được cùng lúc trên cùng
1 đề (rủi ro "lách luật": 1 máy mở sẵn đề để tra cứu, máy còn lại làm
bài/nộp). Nguyên nhân: thiết kế `refresh_tokens` cho phép nhiều token
ACTIVE song song cho 1 user (chủ ý — để nhân viên/giáo viên dùng đồng thời
điện thoại + máy tính), không có rào theo vai trò.

Quy tắc mới, CHỈ áp dụng cho tài khoản có hồ sơ Student liên kết (không
đổi hành vi cho giáo viên/nhân viên/phụ huynh — các vai trò này vẫn đăng
nhập nhiều thiết bị bình thường):

-   Tại bước 5 (Main Flow) — trước khi cấp Access/Refresh Token mới, hệ
    thống kiểm tra: tài khoản Học sinh này còn refresh token nào ACTIVE
    (`revoked_at IS NULL` và `expires_at > now()`) không.
-   Nếu CÓ → từ chối đăng nhập (dù mật khẩu đúng), thông báo "Tài khoản
    này đang được đăng nhập trên thiết bị khác. Vui lòng đăng xuất ở
    thiết bị đó trước khi đăng nhập tiếp." Bản ghi `login_attempts` vẫn
    ghi `success = TRUE` (mật khẩu đúng, chỉ bị chặn bởi policy này —
    không phải lỗi xác thực A1/A2/A3 nào ở trên).
-   Nếu KHÔNG có (đã đăng xuất thiết bị 1, hoặc refresh token thiết bị 1
    đã tự hết hạn theo `refreshTokenTtlDays`) → cho đăng nhập bình
    thường, cấp token mới như Main Flow.
-   Học sinh chủ động đổi thiết bị: phải bấm "Đăng xuất" ở thiết bị cũ
    trước (thu hồi refresh token qua `POST /api/auth/logout`) rồi mới
    đăng nhập được ở thiết bị mới.

Implementation: `AuthService#requireNoActiveSessionForStudent`, exception
`ActiveSessionExistsException` (HTTP 409). Xem
`AuthServiceTest#login_boSung_rejectsSecondDeviceWhileStudentSessionActive`
/ `..._allowsSecondDeviceAfterLogoutFromFirstDevice` /
`..._allowsMultipleDevicesForNonStudentRoles`.

**Bổ sung 2026-09-19 (đã xác nhận với người dùng) — cho phép chủ động
"đăng xuất từ xa" thiết bị cũ ngay lúc đăng nhập, thay vì phải chờ hết
hạn**

Vấn đề của quy tắc 2026-09-13 ở trên: nếu thiết bị 1 bị mất/hỏng/xóa cache
mà học sinh KHÔNG kịp bấm "Đăng xuất" trước, refresh token vẫn còn ACTIVE
trong DB tới khi hết hạn (`refreshTokenTtlDays`) — học sinh bị khóa hoàn
toàn khỏi tài khoản của chính mình cho tới lúc đó, không có lối thoát nào
khác.

Thay đổi Main Flow bước 2 (request đăng nhập) và bước 5 (kiểm tra 1-thiết-
bị ở trên):

-   `LoginRequest`/`GoogleLoginRequest` có thêm field `confirm` (mặc định
    `false`) — cùng pattern `confirm` đã dùng ở UC-16
    (`UpdateCurriculumRequest`).
-   Nếu phát hiện có refresh token ACTIVE ở thiết bị khác (như luồng
    2026-09-13) VÀ `confirm == false` → vẫn từ chối như cũ (HTTP 409),
    nhưng FE nay hiểu 409 này là tín hiệu để hỏi lại người dùng: "Tài
    khoản đang đăng nhập ở một nơi khác. Bạn có muốn đăng xuất?" (thay vì
    chỉ hiện lỗi rồi dừng).
-   Nếu người dùng xác nhận "Có" → FE gọi lại đúng request đăng nhập đó
    với `confirm = true`. Backend khi đó THU HỒI (revoke) toàn bộ refresh
    token ACTIVE hiện có của tài khoản (`revoked_at = now()`, cùng cơ chế
    revoke đã dùng ở `logout`/nhánh phát hiện reuse token ở `refresh`),
    rồi tiếp tục Main Flow bước 5-7 cấp Access/Refresh Token mới bình
    thường.
-   Hệ thống hiện KHÔNG có kênh push real-time (WebSocket/SSE) tới thiết
    bị cũ, nên thiết bị đó KHÔNG bị đăng xuất ngay lập tức — nó chỉ thực
    sự mất quyền truy cập ở lần gọi API kế tiếp (access token hết hạn,
    hoặc `POST /api/auth/refresh` thất bại vì refresh token đã bị revoke).
    Chấp nhận được vì access token có TTL ngắn.
-   Quy tắc CHỈ áp dụng thiết bị mới đăng nhập (đọc request `confirm`) —
    không đổi gì ở luồng học sinh tự "Đăng xuất" chủ động qua
    `POST /api/auth/logout`.

Implementation: `AuthService#requireNoActiveSessionForStudent` (đã sửa để
nhận thêm `confirm`, revoke danh sách refresh token ACTIVE khi
`confirm == true`). Xem
`AuthServiceTest#login_boSung_forceLogoutRevokesOldSessionWhenConfirmed`.
FE: `LoginPage.tsx`/`GoogleSignInButton.tsx` (app `user`) — bắt HTTP 409 ở
endpoint đăng nhập (duy nhất `ActiveSessionExistsException` trả 409 ở đây)
để hiện banner xác nhận, gọi lại đúng API đăng nhập với `confirm: true`.

Phân hệ 2 --- Quản trị người dùng & Phân quyền