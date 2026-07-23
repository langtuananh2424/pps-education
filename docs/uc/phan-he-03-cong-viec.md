# phan-he-03-cong-viec

UC-06: Giao việc

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-06                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Giao việc                                          |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 3                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-TSK-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Cấp quản lý, Trưởng phòng ban                      |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Quản lý vận hành (ngoại lệ:     |
|                 | giao việc toàn công ty))                           |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Cấp quản lý/trưởng phòng tạo và giao đầu việc cho  |
| tắt**           | nhân sự trực thuộc, đặt deadline, đính kèm tệp     |
|                 | tin.                                               |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Người giao việc cần phân công một nhiệm vụ cho     |
| hoạt**          | nhân sự cấp dưới.                                  |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người giao việc đã đăng nhập và có quyền       |
| tiên quyết      |     task.create.                                   |
| (               |                                                    |
| Precondition)** | -   Nhân sự nhận việc thuộc phòng ban trực thuộc   |
|                 |     người giao việc (trừ Quản lý vận hành có phạm  |
|                 |     vi toàn công ty).                              |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người giao việc mở màn hình Giao việc, chọn    |
| chính (Main     |     Tạo mới.                                       |
| Flow)**         |                                                    |
|                 | 2.  Nhập tiêu đề, mô tả công việc, chọn người nhận |
|                 |     (1 hoặc nhiều nhân sự trong phạm vi phòng ban  |
|                 |     phụ trách), đặt deadline, đính kèm tệp tin     |
|                 |     (nếu có).                                      |
|                 |                                                    |
|                 | 3.  Hệ thống kiểm tra phạm vi: người nhận việc     |
|                 |     phải thuộc phòng ban của người giao            |
|                 |     (department_id trùng), trừ trường hợp          |
|                 |     is_management và role = OPS_MANAGER được giao  |
|                 |     cho toàn bộ công ty kể cả cấp quản lý khác.    |
|                 |                                                    |
|                 | 4.  Người giao việc xác nhận Giao việc.            |
|                 |                                                    |
|                 | 5.  Hệ thống tạo bản ghi công việc với trạng thái  |
|                 |     ban đầu \'Cần làm\', gán người nhận, deadline. |
|                 |                                                    |
|                 | 6.  Hệ thống tự động gửi thông báo (Email +        |
|                 |     in-app) tới người nhận việc (FR-TSK-03).       |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Người nhận việc ngoài phạm vi phòng      |
| thế / ngoại lệ  | ban***                                             |
| (Alternate      |                                                    |
| Flow)**         | 1.  Nếu người giao không phải Quản lý vận hành và  |
|                 |     cố giao việc cho nhân sự ngoài phòng ban mình, |
|                 |     hệ thống từ chối và báo không đủ quyền hạn.    |
|                 |                                                    |
|                 | ***A2 --- Giao việc hàng loạt***                   |
|                 |                                                    |
|                 | 1.  Người giao việc chọn nhiều người nhận cùng lúc |
|                 |     cho cùng 1 nội dung công việc; hệ thống tạo    |
|                 |     nhiều bản ghi công việc tương ứng, mỗi người   |
|                 |     nhận theo dõi tiến độ độc lập.                 |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Công việc mới được tạo với trạng thái \'Cần    |
| (P              |     làm\', gán đúng người nhận và deadline.        |
| ostcondition)** |                                                    |
|                 | -   Người nhận việc nhận được thông báo tương ứng. |
+-----------------+----------------------------------------------------+

---

UC-07: Cập nhật tiến độ công việc

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-07                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Cập nhật tiến độ công việc                         |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 3                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-TSK-02                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Người nhận việc (mọi Giáo viên/Nhân viên/cấp Quản  |
|                 | lý được giao việc)                                 |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Cấp quản lý giao việc (theo dõi |
|                 | qua biểu đồ Kanban/Gantt))                         |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Người nhận việc cập nhật trạng thái công việc qua  |
| tắt**           | các bước; cấp quản lý theo dõi tiến độ tổng quan.  |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Người nhận việc bắt đầu xử lý hoặc cập nhật tiến   |
| hoạt**          | độ một công việc được giao.                        |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Công việc đã tồn tại và được giao cho người    |
| tiên quyết      |     dùng hiện tại (UC-06).                         |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người nhận việc mở không gian làm việc Kanban  |
| chính (Main     |     (hoặc Gantt), xem danh sách công việc được     |
| Flow)**         |     giao theo cột trạng thái.                      |
|                 |                                                    |
|                 | 2.  Người nhận việc kéo-thả hoặc chọn cập nhật     |
|                 |     trạng thái công việc theo luồng: Cần làm →     |
|                 |     Đang làm → Chờ duyệt → Hoàn thành.             |
|                 |                                                    |
|                 | 3.  Người nhận việc có thể để lại bình luận/phản   |
|                 |     hồi tiến độ đính kèm khi chuyển trạng thái.    |
|                 |                                                    |
|                 | 4.  Hệ thống lưu lại thời điểm chuyển trạng thái,  |
|                 |     gửi thông báo cho người giao việc khi có phản  |
|                 |     hồi mới hoặc khi công việc chuyển sang \'Chờ   |
|                 |     duyệt\'/\'Hoàn thành\'.                        |
|                 |                                                    |
|                 | 5.  Cấp quản lý xem biểu đồ Kanban/Gantt tổng quan |
|                 |     để theo dõi tiến độ toàn bộ công việc đã giao  |
|                 |     cho phòng ban/cá nhân.                         |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Công việc sắp trễ hạn***                 |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống tự động phát hiện công việc gần đến   |
| Flow)**         |     deadline nhưng chưa \'Hoàn thành\', gửi thông  |
|                 |     báo nhắc nhở tới người nhận việc (FR-TSK-03).  |
|                 |                                                    |
|                 | ***A2 --- Người giao việc từ chối kết quả ở trạng  |
|                 | thái \'Chờ duyệt\'***                              |
|                 |                                                    |
|                 | 1.  Người giao việc xem xét và chuyển trạng thái   |
|                 |     ngược về \'Đang làm\' kèm phản hồi lý do;      |
|                 |     người nhận việc nhận thông báo và tiếp tục xử  |
|                 |     lý.                                            |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Trạng thái công việc được cập nhật chính xác   |
| (P              |     theo thời gian thực.                           |
| ostcondition)** |                                                    |
|                 | -   Lịch sử cập nhật và bình luận được lưu phục vụ |
|                 |     theo dõi tiến độ.                              |
+-----------------+----------------------------------------------------+

> **Bổ sung ngoài đặc tả gốc — đã xác nhận với người dùng (2026-07-22).**
> SDD/UC gốc có trạng thái `DECLINED` (người nhận từ chối nhận việc) nhưng
> không mô tả hệ quả sau đó, khiến công việc có thể "kẹt" (không bao giờ
> đạt COMPLETED vì auto-status yêu cầu *tất cả* assignment = COMPLETED).
> Chốt bổ sung luồng A3 dưới đây; SDD mục "Logic auto-status" đã cập nhật
> tương ứng.
>
> ***A3 --- Người nhận từ chối nhận việc, người giao giao lại***
>
> 1.  Người nhận việc từ chối nhận việc (chuyển assignment sang `DECLINED`,
>     bắt buộc nêu lý do — lưu `decline_reason`). Assignment `DECLINED` là
>     trạng thái kết thúc, không chuyển tiếp được nữa.
>
> 2.  Phân công `DECLINED` KHÔNG tính vào điều kiện hoàn thành của công
>     việc: nếu còn người nhận khác, công việc vẫn tự `COMPLETED` khi những
>     người còn hiệu lực đều `COMPLETED`; nếu toàn bộ phân công đều bị từ
>     chối, công việc giữ nguyên trạng thái mở (không tự đóng).
>
> 3.  Người giao việc (chỉ `created_by`) có thể giao lại phần bị từ chối
>     cho một nhân sự khác trong phạm vi phòng ban (như UC-06 Main Flow
>     bước 3): hệ thống tạo một phân công MỚI trạng thái `PENDING`, giữ lại
>     bản ghi `DECLINED` làm lịch sử, và gửi thông báo cho người nhận mới
>     (FR-TSK-03). Không giao lại được khi công việc đã `COMPLETED`/
>     `CANCELLED`, hoặc khi phân công đích không ở trạng thái `DECLINED`,
>     hoặc người nhận mới đã có phân công trong công việc này.

> **Bổ sung ngoài đặc tả gốc — đã xác nhận với người dùng (2026-07-22).**
> Phạm vi giao việc theo vai trò + quyền chi tiết + luồng hủy (thực thi ở
> backend — migration V47):
>
> - **Phạm vi giao việc:**
>   - **Trưởng phòng** (`departments.head_user_id`) chỉ giao việc cho nhân
>     sự thuộc phòng do mình làm trưởng.
>   - **Company-wide** (giao cho BẤT KỲ AI, kể cả trưởng phòng) = có quyền
>     `task.manage` HOẶC role `EXECUTIVE` (Ban giám đốc). Đây là "ban quản
>     lý / ban giám đốc".
>   - Nhân sự không làm trưởng phòng nào và không company-wide → không giao
>     việc được (`AssigneeOutsideDepartmentException`).
> - **Quyền chi tiết:** `task.assign` (giao việc — thay `task.create`),
>   `task.receive` (nhận việc), `task.manage` (cao nhất: sửa/xóa/hủy bất kỳ,
>   giao toàn công ty, cấu hình dọn CANCELLED), `task.overview.company` (xem
>   tổng quan toàn công ty).
> - **Tổng quan công việc:** `GET /api/tasks/overview` — phân quyền 2 tầng
>   trong Service: (a) có `task.overview.company` → toàn bộ công việc công
>   ty; (b) là trưởng phòng (`departments.head_user_id`) → mọi việc thuộc
>   phòng mình làm trưởng (theo `tasks.department_id`, không lọc người tạo);
>   (c) không có cả hai → **403**, FE fallback `GET /api/tasks/my-assignments`
>   (nhân viên thường chỉ xem việc của mình).
> - **Hủy công việc (CANCELLED thay vì xóa):** người giao (hoặc `task.manage`)
>   KHÔNG xóa trực tiếp việc đã giao mà gọi `POST /api/tasks/{id}/cancel` →
>   task chuyển `CANCELLED` (giữ lịch sử, thông báo người nhận đang mở), rồi
>   tạo việc mới nếu cần. Không hủy task đã `COMPLETED`/`CANCELLED`; sau khi
>   hủy thì không cập nhật phân công được nữa.
> - **Dọn CANCELLED:** cron nightly xóa cứng task `CANCELLED` quá
>   `task.cancelled_retention_days` ngày (mặc định 7, chỉnh qua
>   `GET/PUT /api/task/settings/cancelled-retention-days` — quyền `task.manage`,
>   giống thiết lập cửa sổ điểm UC-19/20).

Phân hệ 4 --- Quản lý nhân sự