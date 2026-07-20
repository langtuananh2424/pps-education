# phan-he-02-phan-quyen

UC-02: Quản lý danh mục quyền

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-02                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Quản lý danh mục quyền                             |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 2                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-PER-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản trị viên                                      |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Quản trị viên khởi tạo, chỉnh sửa danh mục các     |
| tắt**           | quyền hành động chi tiết (permissions) làm nền     |
|                 | tảng cho việc gán quyền theo vai trò và theo tài   |
|                 | khoản.                                             |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Quản trị viên truy cập màn hình Quản lý danh mục   |
| hoạt**          | quyền.                                             |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người dùng đã đăng nhập với vai trò Quản trị   |
| tiên quyết      |     viên (role SYS_ADMIN) và có quyền              |
| (               |     user.manage/permission tương ứng.              |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản trị viên mở màn hình danh mục quyền, hệ   |
| chính (Main     |     thống hiển thị danh sách permissions hiện có,  |
| Flow)**         |     nhóm theo module (AUTH, USER, TASK, HRM,       |
|                 |     STUDENT, ACADEMIC, LMS, FINANCE, CRM,          |
|                 |     FACILITY).                                     |
|                 |                                                    |
|                 | 2.  Quản trị viên chọn Thêm mới, nhập code (định   |
|                 |     dạng \<module\>.\<action\>, ví dụ              |
|                 |     class.create), name, module, description.      |
|                 |                                                    |
|                 | 3.  Hệ thống kiểm tra code chưa tồn tại (UNIQUE),  |
|                 |     lưu bản ghi mới vào bảng permissions.          |
|                 |                                                    |
|                 | 4.  Quản trị viên có thể chọn Sửa một permission   |
|                 |     hiện có (name, description --- không cho sửa   |
|                 |     code sau khi đã có role_permissions tham       |
|                 |     chiếu) hoặc Xóa permission chưa được gán cho   |
|                 |     role/user nào.                                 |
|                 |                                                    |
|                 | 5.  Hệ thống lưu thay đổi và hiển thị lại danh     |
|                 |     sách đã cập nhật.                              |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Trùng code***                            |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Tại bước 3, nếu code đã tồn tại, hệ thống báo  |
| Flow)**         |     lỗi trùng mã quyền và giữ nguyên form để Quản  |
|                 |     trị viên sửa lại.                              |
|                 |                                                    |
|                 | ***A2 --- Xóa permission đang được sử dụng***      |
|                 |                                                    |
|                 | 1.  Nếu permission đang được tham chiếu trong      |
|                 |     role_permissions hoặc                          |
|                 |     user_permission_overrides, hệ thống từ chối    |
|                 |     xóa và liệt kê các role/tài khoản đang dùng    |
|                 |     permission đó.                                 |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Danh mục permissions được cập nhật, sẵn sàng   |
| (P              |     để sử dụng ở bước cấu hình nhóm quyền (UC-03)  |
| ostcondition)** |     và tùy chỉnh riêng (UC-04).                    |
+-----------------+----------------------------------------------------+

---

UC-03: Cấu hình nhóm quyền mặc định

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-03                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Cấu hình nhóm quyền mặc định                       |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 2                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-PER-02                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản trị viên                                      |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Gom các permission thành nhóm gắn với từng vai trò |
| tắt**           | (role) mặc định --- áp dụng hàng loạt cho mọi tài  |
|                 | khoản thuộc vai trò đó.                            |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Quản trị viên chọn một vai trò (trong 11 vai trò   |
| hoạt**          | hệ thống) để cấu hình bộ quyền mặc định.           |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Danh mục permissions đã được khởi tạo (UC-02). |
| tiên quyết      |                                                    |
| (               | -   Vai trò cần cấu hình đã tồn tại trong bảng     |
| Precondition)** |     roles.                                         |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản trị viên chọn 1 role từ danh sách 11 role |
| chính (Main     |     hệ thống (is_system = TRUE) hoặc role tùy      |
| Flow)**         |     chỉnh (nếu có).                                |
|                 |                                                    |
|                 | 2.  Hệ thống hiển thị ma trận permission (theo     |
|                 |     module) với các permission hiện đang thuộc     |
|                 |     role đó (bảng role_permissions) được đánh dấu  |
|                 |     chọn.                                          |
|                 |                                                    |
|                 | 3.  Quản trị viên tick chọn/bỏ chọn các permission |
|                 |     cho role.                                      |
|                 |                                                    |
|                 | 4.  Quản trị viên xác nhận Lưu; hệ thống cập nhật  |
|                 |     lại bảng role_permissions (thêm bản ghi mới,   |
|                 |     xóa bản ghi bị bỏ chọn).                       |
|                 |                                                    |
|                 | 5.  Hệ thống áp dụng thay đổi ngay cho toàn bộ tài |
|                 |     khoản đang mang role đó ở lần truy vấn quyền   |
|                 |     tiếp theo (cache quyền được invalidate).       |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Cố sửa role hệ thống theo hướng xóa hết  |
| thế / ngoại lệ  | quyền cốt lõi***                                   |
| (Alternate      |                                                    |
| Flow)**         | 1.  Hệ thống cảnh báo nếu Quản trị viên bỏ chọn    |
|                 |     toàn bộ quyền của 1 role đang có tài khoản     |
|                 |     active, yêu cầu xác nhận lại trước khi lưu.    |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bảng role_permissions phản ánh đúng bộ quyền   |
| (P              |     mặc định mới của role.                         |
| ostcondition)** |                                                    |
|                 | -   Mọi tài khoản mang role này được áp dụng       |
|                 |     effective_permissions mới (trừ khi có override |
|                 |     riêng theo UC-04).                             |
+-----------------+----------------------------------------------------+

---

UC-04: Tùy chỉnh quyền riêng cho tài khoản

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-04                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Tùy chỉnh quyền riêng cho tài khoản                |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 2                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-PER-03                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản trị viên                                      |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Cấp thêm quyền hoặc tước bỏ quyền mặc định cho một |
| tắt**           | tài khoản cụ thể, có độ ưu tiên cao nhất trong     |
|                 | công thức effective_permissions.                   |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Quản trị viên cần cấp/thu hồi quyền ngoại lệ cho   |
| hoạt**          | một tài khoản cụ thể (ví dụ: Giáo viên được ủy     |
|                 | quyền xếp lịch thay Trưởng phòng đào tạo).         |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Tài khoản đích tồn tại và đang ACTIVE.         |
| tiên quyết      |                                                    |
| (               | -   Permission cần cấp/tước đã có trong danh mục   |
| Precondition)** |     (UC-02).                                       |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản trị viên tìm và chọn đích danh 1 tài      |
| chính (Main     |     khoản.                                         |
| Flow)**         |                                                    |
|                 | 2.  Hệ thống hiển thị effective_permissions hiện   |
|                 |     tại của tài khoản (hợp từ role + override đang |
|                 |     hiệu lực).                                     |
|                 |                                                    |
|                 | 3.  Quản trị viên chọn permission cần thao tác,    |
|                 |     chọn loại override: GRANT (bổ sung) hoặc       |
|                 |     REVOKE (tước bỏ), nhập reason, tùy chọn        |
|                 |     expires_at (thời hạn ủy quyền).                |
|                 |                                                    |
|                 | 4.  Hệ thống lưu bản ghi vào                       |
|                 |     user_permission_overrides (UNIQUE theo         |
|                 |     user_id + permission_id --- nếu đã tồn tại thì |
|                 |     cập nhật override_type/reason/expires_at).     |
|                 |                                                    |
|                 | 5.  Hệ thống ghi log vào permission_audit_log      |
|                 |     (action = PERM_OVERRIDE_ADDED, actor_user_id,  |
|                 |     target_user_id, target_permission_id,          |
|                 |     details).                                      |
|                 |                                                    |
|                 | 6.  effective_permissions của tài khoản được áp    |
|                 |     dụng ngay ở lần truy vấn tiếp theo.            |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Override hết hạn***                      |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Khi expires_at đã qua, hệ thống tự động loại   |
| Flow)**         |     override đó khỏi công thức                     |
|                 |     effective_permissions (không cần Quản trị viên |
|                 |     thao tác thủ công); bản ghi vẫn giữ lại phục   |
|                 |     vụ tra soát.                                   |
|                 |                                                    |
|                 | ***A2 --- Gỡ bỏ override đang hiệu lực***          |
|                 |                                                    |
|                 | 1.  Quản trị viên chọn Gỡ 1 override hiện có; hệ   |
|                 |     thống xóa/đánh dấu hết hiệu lực bản ghi        |
|                 |     user_permission_overrides và ghi log           |
|                 |     PERM_OVERRIDE_REMOVED.                         |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bảng user_permission_overrides phản ánh đúng   |
| (P              |     quyền ngoại lệ hiện hành của tài khoản.        |
| ostcondition)** |                                                    |
|                 | -   permission_audit_log có đầy đủ lịch sử ai      |
|                 |     cấp/thu hồi, cấp cho ai, khi nào.              |
+-----------------+----------------------------------------------------+

---

UC-05: Xem nhật ký thay đổi quyền

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-05                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xem nhật ký thay đổi quyền                         |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 2                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-PER-04                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản trị viên                                      |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Tra cứu lịch sử thay đổi quyền (gán/thu hồi role,  |
| tắt**           | thêm/xóa override) phục vụ kiểm tra, tra soát.     |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Quản trị viên cần tra soát ai đã thay đổi quyền    |
| hoạt**          | của ai, vào thời điểm nào.                         |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Có ít nhất 1 bản ghi trong                     |
| tiên quyết      |     permission_audit_log.                          |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản trị viên mở màn hình Nhật ký thay đổi     |
| chính (Main     |     quyền.                                         |
| Flow)**         |                                                    |
|                 | 2.  Quản trị viên nhập bộ lọc (tùy chọn): theo     |
|                 |     actor_user_id (người thực hiện),               |
|                 |     target_user_id (người bị ảnh hưởng), action    |
|                 |     (ROLE_GRANTED/ROLE_R                           |
|                 | EVOKED/PERM_OVERRIDE_ADDED/PERM_OVERRIDE_REMOVED), |
|                 |     khoảng thời gian.                              |
|                 |                                                    |
|                 | 3.  Hệ thống truy vấn permission_audit_log theo    |
|                 |     điều kiện lọc, trả về danh sách sắp xếp theo   |
|                 |     created_at giảm dần.                           |
|                 |                                                    |
|                 | 4.  Quản trị viên xem chi tiết 1 dòng log (details |
|                 |     dạng JSONB, ip_address) khi cần.               |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Không có kết quả phù hợp***              |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống hiển thị thông báo không tìm thấy bản |
| Flow)**         |     ghi khớp bộ lọc, gợi ý nới lỏng điều kiện.     |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Quản trị viên có đầy đủ thông tin tra soát mà  |
| (P              |     không làm thay đổi dữ liệu quyền hiện hành     |
| ostcondition)** |     (use case chỉ đọc).                            |
+-----------------+----------------------------------------------------+

---

UC-43: Khởi tạo tài khoản người dùng

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-43                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Khởi tạo tài khoản người dùng                      |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 2                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-USR-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản trị viên                                      |
|                 |                                                    |
|                 | (Liên quan: Quản lý nhân sự — qua luồng UC-08 tạo  |
|                 | hồ sơ nhân sự kèm tài khoản)                       |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Cấp phát tài khoản đăng nhập cho người dùng mới    |
| tắt**           | (nhân sự, đối tác...). Hệ thống không có tự đăng   |
|                 | ký — mọi tài khoản đều do Quản trị viên khởi tạo   |
|                 | (xem UC-01 A4).                                    |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Có người dùng mới cần truy cập hệ thống nhưng chưa |
| hoạt**          | được cấp tài khoản.                                |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người thao tác có quyền user.manage.           |
| tiên quyết      |                                                    |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản trị viên mở màn hình Quản trị tài khoản,  |
| chính (Main     |     chọn Thêm mới; nhập username, email, họ tên,   |
| Flow)**         |     SĐT (tùy chọn). Phòng ban/cờ miễn trừ chấm     |
|                 |     công (is_management) không thuộc bước này —    |
|                 |     thuộc hồ sơ nhân sự, gán qua UC-08.             |
|                 |                                                    |
|                 | 2.  Tùy chọn nhập mật khẩu ban đầu (tối thiểu 8 ký |
|                 |     tự). Bỏ trống nếu tài khoản chỉ đăng nhập bằng |
|                 |     Google — hệ thống lưu password_hash = NULL,    |
|                 |     đăng nhập Google khớp theo email (UC-01 A4).   |
|                 |                                                    |
|                 | 3.  Hệ thống kiểm tra username và email chưa tồn   |
|                 |     tại, lưu tài khoản với trạng thái ACTIVE (mật  |
|                 |     khẩu — nếu có — băm BCrypt, NFR-SEC-01).       |
|                 |                                                    |
|                 | 4.  Tài khoản đăng nhập được ngay (mật khẩu hoặc   |
|                 |     Google). Việc gán vai trò/quyền KHÔNG thuộc    |
|                 |     use case này — thực hiện sau qua UC-03/UC-04;  |
|                 |     tài khoản chưa gán role chỉ đăng nhập được,    |
|                 |     không thao tác được chức năng nào.             |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Username hoặc email đã tồn tại***        |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống từ chối tạo, báo rõ trường bị trùng   |
| Flow)**         |     để Quản trị viên sửa lại.                      |
|                 |                                                    |
|                 | ***A2 --- Mật khẩu ban đầu quá ngắn***             |
|                 |                                                    |
|                 | 1.  Mật khẩu được nhập nhưng dưới 8 ký tự: hệ      |
|                 |     thống từ chối với lỗi định dạng dữ liệu.       |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bảng users có bản ghi mới trạng thái ACTIVE,   |
| (P              |     chưa có role nào (danh sách quyền hiệu lực     |
| ostcondition)** |     rỗng cho tới khi được gán qua UC-46, tùy chỉnh |
|                 |     thêm qua UC-04 nếu cần).                       |
+-----------------+----------------------------------------------------+

---

UC-44: Xem/tra cứu danh sách tài khoản

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-44                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xem/tra cứu danh sách tài khoản                    |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 2                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-USR-03                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản trị viên                                      |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Xem danh sách toàn bộ tài khoản, tìm kiếm/lọc theo |
| tắt**           | tiêu chí, xem chi tiết 1 tài khoản — hạ tầng tra   |
|                 | cứu bắt buộc cho UC-45 (đổi mật khẩu tài khoản     |
|                 | khác), UC-04 (tùy chỉnh quyền riêng), UC-46        |
|                 | (gán/thu hồi vai trò), UC-47 (khóa/mở khóa tài     |
|                 | khoản).                                            |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Quản trị viên cần tìm 1 hoặc nhiều tài khoản để    |
| hoạt**          | xem thông tin hoặc làm căn cứ thao tác tiếp (đổi   |
|                 | mật khẩu, gán vai trò, tùy chỉnh quyền, khóa/mở    |
|                 | khóa).                                             |
+-----------------+----------------------------------------------------+
| **Điều kiện     | Người thao tác có quyền user.manage.               |
| tiên quyết      |                                                    |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1. Quản trị viên mở màn hình Quản trị tài khoản;   |
| chính (Main     | hệ thống hiển thị danh sách tài khoản (username,   |
| Flow)**         | họ tên, email, phòng ban, trạng thái, danh sách    |
|                 | role hiện tại) — không hiển thị password_hash.     |
|                 |                                                    |
|                 | 2. Quản trị viên nhập từ khóa tìm kiếm             |
|                 | (username/email/họ tên) và/hoặc chọn bộ lọc (phòng |
|                 | ban, trạng thái ACTIVE/INACTIVE/SUSPENDED); hệ     |
|                 | thống trả về danh sách phù hợp.                    |
|                 |                                                    |
|                 | 3. Quản trị viên chọn 1 tài khoản để xem chi tiết  |
|                 | đầy đủ (bao gồm last_login_at, failed_login_count, |
|                 | locked_until, danh sách role, danh sách permission |
|                 | override — nếu có).                                |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Không tìm thấy tài khoản phù hợp***      |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1. Hệ thống trả về danh sách rỗng, không báo lỗi.  |
| Flow)**         |                                                    |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | Quản trị viên có đủ thông tin (đặc biệt userId) để |
| (P              | tiếp tục các use case khác (UC-45, UC-04, UC-46,   |
| ostcondition)** | UC-47, UC-49) — use case này chỉ đọc, không thay   |
|                 | đổi dữ liệu.                                       |
+-----------------+----------------------------------------------------+

---

UC-45: Đổi mật khẩu

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-45                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Đổi mật khẩu                                       |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 2                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-USR-02                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Mọi tài khoản đã đăng nhập (tự đổi mật khẩu của    |
|                 | chính mình)                                        |
|                 |                                                    |
|                 | (Liên quan: Quản trị viên — đổi mật khẩu cho tài   |
|                 | khoản khác)                                        |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Đổi mật khẩu đăng nhập — tự phục vụ (yêu cầu xác   |
| tắt**           | thực mật khẩu hiện tại) hoặc do Quản trị viên thực |
|                 | hiện thay cho tài khoản khác (không cần biết mật   |
|                 | khẩu hiện tại của tài khoản đó).                   |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Người dùng muốn đổi mật khẩu của mình; hoặc Quản   |
| hoạt**          | trị viên cần đặt lại mật khẩu cho một tài khoản     |
|                 | khác (quên mật khẩu, nghi ngờ bị lộ...).           |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Đã đăng nhập (JWT hợp lệ). Riêng luồng đổi cho |
| tiên quyết      |     tài khoản khác: có quyền user.manage.          |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người dùng mở màn hình Đổi mật khẩu, nhập mật  |
| chính (Main     |     khẩu hiện tại và mật khẩu mới (tối thiểu 8 ký  |
| Flow)**         |     tự).                                           |
|                 |                                                    |
|                 | 2.  Hệ thống xác thực mật khẩu hiện tại khớp với    |
|                 |     password_hash đang lưu.                        |
|                 |                                                    |
|                 | 3.  Hệ thống băm mật khẩu mới (BCrypt, NFR-SEC-01), |
|                 |     cập nhật password_hash.                        |
|                 |                                                    |
|                 | 4.  Hệ thống thu hồi toàn bộ refresh token đang     |
|                 |     hoạt động của tài khoản — đăng xuất khỏi mọi   |
|                 |     thiết bị, bắt buộc đăng nhập lại bằng mật khẩu |
|                 |     mới.                                           |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Mật khẩu hiện tại không đúng***          |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống từ chối, không đổi mật khẩu.           |
| Flow)**         |                                                    |
|                 | ***A2 --- Mật khẩu mới quá ngắn***                 |
|                 |                                                    |
|                 | 1.  Mật khẩu mới dưới 8 ký tự: hệ thống từ chối    |
|                 |     với lỗi định dạng dữ liệu (giống UC-43 A2).    |
|                 |                                                    |
|                 | ***A3 --- Tài khoản chưa từng có mật khẩu (chỉ     |
|                 | đăng nhập Google, password_hash NULL)***           |
|                 |                                                    |
|                 | 1.  Bước 1-2 (nhập/xác thực mật khẩu hiện tại)     |
|                 |     không áp dụng — hệ thống cho đặt mật khẩu lần  |
|                 |     đầu trực tiếp, tiếp tục từ bước 3.             |
|                 |                                                    |
|                 | ***A4 --- Quản trị viên đổi mật khẩu cho tài khoản |
|                 | khác***                                            |
|                 |                                                    |
|                 | 1.  Quản trị viên (quyền user.manage) chọn đích    |
|                 |     danh 1 tài khoản, nhập mật khẩu mới — không    |
|                 |     cần biết/nhập mật khẩu hiện tại của tài khoản  |
|                 |     đó. Tiếp tục từ bước 3 (A2 vẫn áp dụng).       |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   password_hash của tài khoản được cập nhật.     |
| (P              | -   Toàn bộ refresh_tokens của tài khoản đó có     |
| ostcondition)** |     revoked_at được set (nếu đang NULL) — mọi      |
|                 |     phiên đăng nhập cũ không dùng lại được nữa.    |
+-----------------+----------------------------------------------------+

---

UC-46: Gán/Thu hồi vai trò cho tài khoản

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-46                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Gán/Thu hồi vai trò cho tài khoản                  |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 2                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-PER-05                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản trị viên                                      |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Liên kết 1 tài khoản với 1 vai trò (role) đang có  |
| tắt**           | sẵn trong hệ thống, hoặc gỡ liên kết đó — bước     |
|                 | trung gian bắt buộc để tài khoản mới tạo (UC-43)   |
|                 | có được effective_permissions từ role_permissions  |
|                 | (UC-03). Khác UC-04 (chỉ thêm/bớt 1 permission lẻ, |
|                 | không gán cả 1 role).                              |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Tài khoản mới tạo (UC-43) chưa có role nào, hoặc   |
| hoạt**          | cần mở rộng/thu hẹp phạm vi vai trò của 1 tài       |
|                 | khoản đang hoạt động (ví dụ: kiêm nhiệm thêm vai   |
|                 | trò Quản lý nhân sự).                              |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người thao tác có quyền user.role.manage.      |
| tiên quyết      | -   Vai trò cần gán đã tồn tại trong bảng roles    |
| (               |     (11 role hệ thống — UC-03).                    |
| Precondition)** | -   Gán role: tài khoản đích tồn tại và đang        |
|                 |     ACTIVE (giống UC-04).                          |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản trị viên tìm và chọn đích danh 1 tài       |
| chính (Main     |     khoản, hệ thống hiển thị danh sách role hiện   |
| Flow)**         |     tại của tài khoản đó (bảng user_roles).        |
|                 |                                                    |
|                 | 2.  Quản trị viên chọn 1 role cần gán thêm.        |
|                 |                                                    |
|                 | 3.  Hệ thống thêm 1 bản ghi vào user_roles          |
|                 |     (user_id, role_id, assigned_by, assigned_at) — |
|                 |     UNIQUE(user_id, role_id); nếu đã gán rồi thì   |
|                 |     không tạo trùng (idempotent, coi như thành     |
|                 |     công).                                         |
|                 |                                                    |
|                 | 4.  Hệ thống ghi log vào permission_audit_log       |
|                 |     (action = ROLE_GRANTED, actor_user_id,         |
|                 |     target_user_id, target_role_id).               |
|                 |                                                    |
|                 | 5.  effective_permissions của tài khoản được áp    |
|                 |     dụng ngay ở lần truy vấn tiếp theo (không cần  |
|                 |     đăng nhập lại — tính theo thời gian thực,      |
|                 |     giống UC-04).                                  |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Thu hồi 1 role đã gán***                 |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Quản trị viên chọn Thu hồi 1 role hiện có của  |
| Flow)**         |     tài khoản; hệ thống xóa bản ghi user_roles     |
|                 |     tương ứng và ghi log ROLE_REVOKED. Không yêu   |
|                 |     cầu tài khoản đang ACTIVE (giống UC-04 A2 —    |
|                 |     vẫn thu hồi được role của tài khoản đã bị vô   |
|                 |     hiệu hóa).                                     |
|                 |                                                    |
|                 | ***A2 --- Thu hồi 1 role chưa từng được gán***     |
|                 |                                                    |
|                 | 1.  Hệ thống báo không tìm thấy liên kết            |
|                 |     user_id/role_id tương ứng, không có gì để thu  |
|                 |     hồi.                                           |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bảng user_roles phản ánh đúng tập role hiện    |
| (P              |     hành của tài khoản.                            |
| ostcondition)** | -   permission_audit_log có đầy đủ lịch sử ai gán/ |
|                 |     thu hồi role nào, cho ai, khi nào.             |
+-----------------+----------------------------------------------------+

---

UC-47: Khóa/Mở khóa tài khoản

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-47                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Khóa/Mở khóa tài khoản                             |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 2                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-USR-04                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản trị viên                                      |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Quản trị viên chuyển trạng thái 1 tài khoản giữa   |
| tắt**           | ACTIVE và INACTIVE/SUSPENDED để kiểm soát quyền    |
|                 | đăng nhập — không xóa dữ liệu tài khoản (đúng      |
|                 | nguyên tắc không hard-delete của bảng users).      |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Nhân sự nghỉ việc/học sinh rời trung tâm cần ngừng |
| hoạt**          | truy cập dài hạn; hoặc phát hiện vi phạm/nghi vấn  |
|                 | bảo mật cần tạm khóa ngay; hoặc cần khôi phục 1    |
|                 | tài khoản đã khóa.                                 |
+-----------------+----------------------------------------------------+
| **Điều kiện     | - Người thao tác có quyền user.manage.             |
| tiên quyết      |                                                    |
| (               | - Tài khoản đích tồn tại trong bảng users (tra cứu |
| Precondition)** | qua UC-44).                                        |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1. Quản trị viên tìm và chọn đích danh 1 tài khoản |
| chính (Main     | (UC-44).                                           |
| Flow)**         |                                                    |
|                 | 2. Quản trị viên chọn chuyển trạng thái sang       |
|                 | INACTIVE (ngừng hoạt động dài hạn) hoặc SUSPENDED  |
|                 | (tạm khóa có chủ đích).                            |
|                 |                                                    |
|                 | 3. Hệ thống cập nhật users.status, ghi lại bản ghi |
|                 | vào users_history (JSONB diff-log — trạng thái     |
|                 | cũ/mới, actor, thời điểm — cùng cơ chế với         |
|                 | permission_audit_log).                             |
|                 |                                                    |
|                 | 4. Toàn bộ refresh token đang hoạt động của tài    |
|                 | khoản bị thu hồi ngay (giống hậu điều kiện UC-45)  |
|                 | — buộc đăng xuất khỏi mọi thiết bị; các lần đăng   |
|                 | nhập tiếp theo bị từ chối với failure_reason =     |
|                 | USER_INACTIVE (UC-01 A3).                          |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Khôi phục tài khoản (INACTIVE/SUSPENDED  |
| thế / ngoại lệ  | sang ACTIVE)***                                    |
| (Alternate      |                                                    |
| Flow)**         | 1. Quản trị viên chọn Kích hoạt lại; hệ thống set  |
|                 | status = ACTIVE, reset failed_login_count = 0 và   |
|                 | locked_until = NULL, ghi log users_history. Tài    |
|                 | khoản đăng nhập lại được ngay, giữ nguyên toàn bộ  |
|                 | role/permission override đã có trước đó.           |
|                 |                                                    |
|                 | ***A2 --- Tự khóa chính tài khoản đang đăng        |
|                 | nhập***                                            |
|                 |                                                    |
|                 | 1. Hệ thống từ chối, báo lỗi không thể tự khóa tài |
|                 | khoản của chính mình — tránh trường hợp không còn  |
|                 | Quản trị viên nào thao tác được.                   |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | - users.status phản ánh đúng trạng thái mới.       |
| (P              |                                                    |
| ostcondition)** | - users_history có đầy đủ lịch sử ai đổi trạng     |
|                 | thái tài khoản nào, khi nào, từ giá trị gì sang    |
|                 | giá trị gì.                                        |
+-----------------+----------------------------------------------------+

---

UC-49: Cập nhật thông tin tài khoản

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-49                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Cập nhật thông tin tài khoản                       |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 2                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-USR-05                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản trị viên                                      |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Sửa thông tin hồ sơ (họ tên, SĐT) của 1 tài khoản  |
| tắt**           | đã tồn tại --- không đổi username/email/mật khẩu/  |
|                 | trạng thái tài khoản. Phòng ban/cờ miễn trừ quản   |
|                 | lý (is_management) không thuộc UC này --- thuộc hồ |
|                 | sơ nhân sự, sửa qua UC-08.                         |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Thông tin hồ sơ của 1 tài khoản (VD SĐT) đã lỗi    |
| hoạt**          | thời hoặc cần điều chỉnh.                          |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người thao tác có quyền user.manage.           |
| tiên quyết      | -   Tài khoản đích tồn tại (tra cứu qua UC-44).    |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản trị viên tìm và chọn đích danh 1 tài       |
| chính (Main     |     khoản (UC-44), mở form sửa thông tin.          |
| Flow)**         |                                                    |
|                 | 2.  Quản trị viên sửa họ tên, SĐT; xác nhận Lưu.    |
|                 |                                                    |
|                 | 3.  Hệ thống cập nhật bản ghi users tương ứng       |
|                 |     (full_name, phone) và trả về thông tin đã cập  |
|                 |     nhật.                                          |
+-----------------+----------------------------------------------------+
| **Luồng thay    | Không có.                                          |
| thế / ngoại lệ  |                                                    |
| (Alternate      |                                                    |
| Flow)**         |                                                    |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bảng users phản ánh đúng thông tin hồ sơ mới   |
| (P              |     của tài khoản. Username, email, password_hash, |
| ostcondition)** |     status, danh sách role/permission override,    |
|                 |     phòng ban/is_management (thuộc employees)      |
|                 |     giữ nguyên không đổi.                          |
+-----------------+----------------------------------------------------+

---

UC-55: Cập nhật email tài khoản

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-55                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Cập nhật email tài khoản                           |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 2                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-USR-06 (bổ sung ngoài SDD gốc, đã xác nhận với  |
| năng gốc**      | người dùng)                                        |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản trị viên                                      |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Quản trị viên sửa email của 1 tài khoản đã tồn tại |
| tắt**           | — tách biệt hẳn khỏi UC-49 (Postcondition UC-49    |
|                 | quy định email giữ nguyên không đổi qua luồng đó). |
|                 | Phục vụ chủ yếu việc sửa email placeholder của tài |
|                 | khoản Phụ huynh/Học sinh tạo qua UC-34/UC-35/UC-50 |
|                 | (không có mật khẩu, chỉ dự kiến đăng nhập Google)  |
|                 | sang email Google thật, để đăng nhập Google (UC-01 |
|                 | bước 4) khớp được.                                 |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Tài khoản Phụ huynh/Học sinh tạo qua               |
| hoạt**          | UC-34/UC-35/UC-50 chỉ có email placeholder, không  |
|                 | đăng nhập Google được; hoặc email hồ sơ đã lỗi     |
|                 | thời cần sửa.                                      |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người thao tác có quyền user.manage.           |
| tiên quyết      |                                                    |
| (               | -   Tài khoản đích tồn tại (tra cứu qua UC-44).    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản trị viên tìm và chọn đích danh 1 tài      |
| chính (Main     |     khoản (UC-44), mở form sửa email.              |
| Flow)**         |                                                    |
|                 | 2.  Quản trị viên nhập email mới, xác nhận Lưu.    |
|                 |                                                    |
|                 | 3.  Hệ thống kiểm tra email mới không trùng với    |
|                 |     tài khoản khác (UNIQUE users.email).           |
|                 |                                                    |
|                 | 4.  Hệ thống cập nhật bản ghi users tương ứng      |
|                 |     (email) và trả về thông tin đã cập nhật.       |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Email trùng tài khoản khác***            |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu email mới đã được dùng bởi tài khoản khác, |
| Flow)**         |     hệ thống chặn lưu, báo lỗi trùng email.        |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   users.email được cập nhật;                     |
| (P              |     username/password_hash/status/role/permission  |
| ostcondition)** |     override giữ nguyên không đổi.                 |
|                 |                                                    |
|                 | -   Nếu tài khoản trước đó dùng email placeholder  |
|                 |     (UC-34/UC-35/UC-50), từ nay có thể đăng nhập   |
|                 |     Google bằng email thật này (UC-01 bước 4/A4),  |
|                 |     miễn google_id/email khớp lúc xác thực Google. |
+-----------------+----------------------------------------------------+

Phân hệ 3 --- Quản lý công việc và quy trình