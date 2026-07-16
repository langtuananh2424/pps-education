# phan-he-10-co-so-vat-chat

UC-36: Quản lý điểm trường

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-36                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Quản lý điểm trường                                |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 10                                         |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-FAC-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản lý vận hành                                   |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Quản trị viên)                  |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Khởi tạo danh mục điểm trường, phân loại theo Loại |
| tắt**           | hình (Cơ sở tự vận hành/Trường liên kết), gán Quản |
|                 | lý điểm trường phụ trách.                          |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Cần mở điểm trường mới hoặc cập nhật thông tin     |
| hoạt**          | điểm trường hiện có.                               |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người dùng có quyền facility.manage.           |
| tiên quyết      |                                                    |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người dùng mở màn hình Quản lý điểm trường,    |
| chính (Main     |     chọn Thêm mới hoặc chọn điểm trường hiện có.   |
| Flow)**         |                                                    |
|                 | 2.  Nhập thông tin cơ bản: tên, địa chỉ, Loại hình |
|                 |     (Cơ sở tự vận hành/Trường liên kết).           |
|                 |                                                    |
|                 | 3.  Nếu Loại hình là Trường liên kết: bổ sung      |
|                 |     thông tin người liên hệ đầu mối; trạng thái    |
|                 |     hợp đồng hợp tác được Quản lý vận hành khởi    |
|                 |     tạo/cập nhật (liên kết UC-36b).                |
|                 |                                                    |
|                 | 4.  Gán 1 Quản lý điểm trường phụ trách chính cho  |
|                 |     điểm trường (1 Quản lý điểm trường có thể phụ  |
|                 |     trách nhiều điểm trường cùng lúc).             |
|                 |                                                    |
|                 | 5.  Hệ thống lưu thông tin điểm trường.            |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Đổi Quản lý điểm trường phụ trách***     |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Người dùng chọn Quản lý điểm trường mới thay   |
| Flow)**         |     thế; hệ thống cập nhật phạm vi truy cập dữ     |
|                 |     liệu row-level tương ứng ngay khi lưu.         |
|                 |                                                    |
|                 | ***A2 --- Gán giáo viên vào điểm trường (bổ sung   |
|                 | ngoài SDD gốc, đã xác nhận với người dùng)***      |
|                 |                                                    |
|                 | 1.  Người dùng (quyền facility.manage) gán 1 giáo  |
|                 |     viên vào điểm trường (mọi Loại hình, cả Cơ sở  |
|                 |     tự vận hành lẫn Trường liên kết) — 1 giáo viên |
|                 |     có thể được gán nhiều điểm trường cùng lúc     |
|                 |     (site_teachers, migration V35).                |
|                 |                                                    |
|                 | 2.  Giáo viên không có quyền academic.class.manage |
|                 |     (VD role Giáo viên thuần) chỉ xem/thao tác     |
|                 |     được lớp học, buổi học, điểm danh thuộc (các)  |
|                 |     điểm trường mình được gán — xem UC-18 A3.      |
|                 |                                                    |
|                 | 3.  Người dùng có thể gỡ 1 phân công (đóng          |
|                 |     assigned_to, giữ lịch sử).                     |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Danh mục điểm trường phản ánh đúng thực tế,    |
| (P              |     sẵn sàng làm cơ sở cho xếp lớp (UC-18), quản   |
| ostcondition)** |     lý phòng học (UC-37).                          |
|                 |                                                    |
|                 | -   Giáo viên được gán chỉ thao tác được dữ liệu   |
|                 |     lớp học/điểm danh thuộc (các) điểm trường mình |
|                 |     được gán (A2).                                 |
+-----------------+----------------------------------------------------+

---

UC-36b: Quản lý hợp đồng liên kết trường

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-36b                                             |
+-----------------+----------------------------------------------------+
| **Tên Use       | Quản lý hợp đồng liên kết trường                   |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 10                                         |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-FAC-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản lý vận hành                                   |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Quản lý vận hành khởi tạo và cập nhật trạng thái   |
| tắt**           | hợp đồng hợp tác với các trường liên kết.          |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Ký mới, gia hạn, hoặc chấm dứt hợp đồng hợp tác    |
| hoạt**          | với 1 trường liên kết.                             |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Điểm trường liên quan đã được khởi tạo với     |
| tiên quyết      |     Loại hình Trường liên kết (UC-36).             |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản lý vận hành mở hồ sơ điểm trường loại     |
| chính (Main     |     Trường liên kết, chọn mục Hợp đồng hợp tác.    |
| Flow)**         |                                                    |
|                 | 2.  Quản lý vận hành nhập/cập nhật thông tin hợp   |
|                 |     đồng: thời hạn, điều khoản, trạng thái (đang   |
|                 |     hiệu lực/sắp hết hạn/đã chấm dứt).             |
|                 |                                                    |
|                 | 3.  Hệ thống lưu thông tin hợp đồng, gắn với điểm  |
|                 |     trường tương ứng.                              |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Hợp đồng sắp hết hạn***                  |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1. Hệ thống cảnh báo Quản lý vận hành khi hợp đồng |
| Flow)**         | gần đến hạn để xử lý gia hạn hoặc chấm dứt kịp     |
|                 | thời.                                              |
|                 |                                                    |
|                 | ***A2 --- Chấm dứt hợp đồng***                     |
|                 |                                                    |
|                 | 1. Quản lý vận hành đánh dấu hợp đồng chấm dứt; hệ |
|                 | thống cảnh báo các lớp/hoạt động đang gắn với điểm |
|                 | trường đó để xử lý chuyển tiếp.                    |
|                 |                                                    |
|                 | ***A3 --- Xóa hợp đồng nhập nhầm (bổ sung,         |
|                 | FR-FAC-01)***                                      |
|                 |                                                    |
|                 | 1. Quản lý vận hành xóa 1 hợp đồng đang DRAFT      |
|                 | (nhập sai điểm trường/thông tin, chưa từng ký/kích |
|                 | hoạt); hệ thống xóa mềm (deleted_at), loại khỏi    |
|                 | mọi danh sách/tra cứu. Chỉ áp dụng cho hợp đồng    |
|                 | đang DRAFT — hợp đồng đã ACTIVE/EXPIRED/TERMINATED |
|                 | (đã từng có hiệu lực pháp lý) không thể xóa, dùng  |
|                 | A2 (chấm dứt) thay thế để giữ đúng chứng cứ pháp   |
|                 | lý.                                                |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | - Trạng thái hợp đồng hợp tác được cập nhật chính  |
| (P              | xác, làm cơ sở cho Ban giám đốc phê duyệt các      |
| ostcondition)** | quyết định chiến lược liên quan (hợp đồng liên kết |
|                 | trường mới).                                       |
|                 |                                                    |
|                 | - Hợp đồng đã bị xóa mềm (A3) không còn xuất hiện  |
|                 | trong danh sách/tra cứu, nhưng bản ghi vẫn tồn tại |
|                 | (không hard-delete).                               |
+-----------------+----------------------------------------------------+

---

UC-37: Quản lý phòng học & thiết bị

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-37                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Quản lý phòng học & thiết bị                       |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 10                                         |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-FAC-02, FR-FAC-04                               |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Nhân viên (Giáo vụ/Hành chính)                     |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Quản lý điểm trường)            |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Khởi tạo danh sách phòng học tại từng điểm trường  |
| tắt**           | kèm sức chứa, trạng thái sử dụng; điều phối trạng  |
|                 | thái thiết bị dạy học.                             |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Cần khai báo phòng học/thiết bị mới hoặc cập nhật  |
| hoạt**          | trạng thái sử dụng.                                |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Điểm trường liên quan đã được khởi tạo         |
| tiên quyết      |     (UC-36).                                       |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người dùng mở màn hình Quản lý phòng học, chọn |
| chính (Main     |     điểm trường, thêm mới phòng học: loại phòng    |
| Flow)**         |     (Phòng lý thuyết/phòng máy tính/phòng lab),    |
|                 |     sức chứa tối đa.                               |
|                 |                                                    |
|                 | 2.  Với điểm trường loại Trường liên kết: người    |
|                 |     dùng có thể đánh dấu phòng là \'linh hoạt\'    |
|                 |     (có thể thay đổi theo tuần) --- các phòng này  |
|                 |     được loại trừ khỏi ràng buộc cảnh báo trùng    |
|                 |     phòng (FR-FAC-03).                             |
|                 |                                                    |
|                 | 3.  Người dùng khai báo danh mục thiết bị dạy học  |
|                 |     (máy chiếu, loa, micro, máy tính) gắn với từng |
|                 |     phòng, quản lý trạng thái sử dụng.             |
|                 |                                                    |
|                 | 4.  Hệ thống lưu thông tin phòng học/thiết bị, sẵn |
|                 |     sàng phục vụ ràng buộc xếp lịch (UC-48).       |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Thiết bị hỏng/bảo trì***                 |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Người dùng cập nhật trạng thái thiết bị sang   |
| Flow)**         |     Bảo trì/Hỏng; hệ thống loại thiết bị đó khỏi   |
|                 |     danh sách khả dụng cho tới khi được cập nhật   |
|                 |     lại.                                           |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Danh mục phòng học/thiết bị được cập nhật      |
| (P              |     chính xác, dùng làm dữ liệu tham chiếu khi xếp |
| ostcondition)** |     lịch học (UC-48) để tránh trùng phòng.         |
+-----------------+----------------------------------------------------+

---

UC-38: Gửi phản hồi tới Quản lý điểm trường

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-38                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Gửi phản hồi tới Quản lý điểm trường               |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 10                                         |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-FAC-05                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Đại diện trường liên kết                           |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Đại diện trường liên kết gửi ý kiến/phản hồi (về   |
| tắt**           | giáo viên, lớp học, vận hành, ý kiến khác) tới     |
|                 | Quản lý điểm trường phụ trách, kèm mức độ ưu tiên. |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Đại diện trường liên kết có ý kiến/phản hồi cần    |
| hoạt**          | gửi tới trung tâm.                                 |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Tài khoản Đại diện trường liên kết đã được gán |
| tiên quyết      |     vào điểm trường liên kết tương ứng.            |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Đại diện trường liên kết mở màn hình Gửi phản  |
| chính (Main     |     hồi, chọn loại nội dung (giáo viên/lớp học/vận |
| Flow)**         |     hành/ý kiến khác).                             |
|                 |                                                    |
|                 | 2.  Nhập nội dung phản hồi, chọn mức độ ưu tiên.   |
|                 |                                                    |
|                 | 3.  Xác nhận Gửi; hệ thống tạo bản ghi phản hồi    |
|                 |     với trạng thái Mới, gán tới Quản lý điểm       |
|                 |     trường phụ trách điểm trường đó.               |
|                 |                                                    |
|                 | 4.  Hệ thống gửi thông báo cho Quản lý điểm        |
|                 |     trường.                                        |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Theo dõi phản hồi đã gửi***              |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Đại diện trường liên kết xem lại lịch sử phản  |
| Flow)**         |     hồi đã gửi và trạng thái xử lý hiện tại        |
|                 |     (Mới/Đang xử lý/Đã giải quyết/Đóng).           |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Phản hồi được ghi nhận đầy đủ, đúng người phụ  |
| (P              |     trách xử lý, sẵn sàng cho UC-39.               |
| ostcondition)** |                                                    |
+-----------------+----------------------------------------------------+

---

UC-39: Xử lý phản hồi từ trường liên kết

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-39                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xử lý phản hồi từ trường liên kết                  |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 10                                         |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-FAC-05                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản lý điểm trường                                |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Đại diện trường liên kết)       |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Quản lý điểm trường tiếp nhận, cập nhật trạng thái |
| tắt**           | xử lý và ghi nội dung giải quyết cho các phản hồi  |
|                 | từ trường liên kết.                                |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Có phản hồi mới từ Đại diện trường liên kết        |
| hoạt**          | (UC-38) thuộc điểm trường phụ trách.               |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Phản hồi đã được gửi (UC-38) và đang ở trạng   |
| tiên quyết      |     thái Mới hoặc Đang xử lý.                      |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản lý điểm trường mở danh sách phản hồi từ   |
| chính (Main     |     trường liên kết, ưu tiên theo mức độ ưu tiên   |
| Flow)**         |     đã được gán.                                   |
|                 |                                                    |
|                 | 2.  Quản lý điểm trường xem chi tiết phản hồi,     |
|                 |     chuyển trạng thái sang Đang xử lý.             |
|                 |                                                    |
|                 | 3.  Quản lý điểm trường xử lý vấn đề, ghi nội dung |
|                 |     phản hồi giải quyết vào hệ thống.              |
|                 |                                                    |
|                 | 4.  Quản lý điểm trường chuyển trạng thái sang Đã  |
|                 |     giải quyết; hệ thống thông báo cho Đại diện    |
|                 |     trường liên kết.                               |
|                 |                                                    |
|                 | 5.  Sau khi xác nhận, Quản lý điểm trường (hoặc hệ |
|                 |     thống) chuyển trạng thái sang Đóng.            |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Cần trao đổi thêm trước khi giải         |
| thế / ngoại lệ  | quyết***                                           |
| (Alternate      |                                                    |
| Flow)**         | 1.  Quản lý điểm trường và Đại diện trường liên    |
|                 |     kết trao đổi qua lại nhiều lượt; toàn bộ lịch  |
|                 |     sử trao đổi được lưu lại để phục vụ tra soát   |
|                 |     trước khi chuyển Đã giải quyết.                |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Phản hồi được xử lý đúng quy trình trạng thái  |
| (P              |     (Mới → Đang xử lý → Đã giải quyết → Đóng),     |
| ostcondition)** |     lịch sử trao đổi được lưu đầy đủ.              |
+-----------------+----------------------------------------------------+