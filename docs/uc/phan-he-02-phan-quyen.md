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

Phân hệ 3 --- Quản lý công việc và quy trình