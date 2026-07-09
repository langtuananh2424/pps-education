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

Phân hệ 4 --- Quản lý nhân sự