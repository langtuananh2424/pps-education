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
| chính (Main     |     Thêm mới hoặc tìm 1 học sinh hiện có. Khi Thêm |
| Flow)**         |     mới cho học sinh/phụ huynh CHƯA có tài khoản   |
|                 |     đăng nhập, hệ thống cho phép khởi tạo tài      |
|                 |     khoản kèm hồ sơ trong cùng 1 giao dịch (cơ chế |
|                 |     khởi tạo tài khoản theo UC-43/FR-USR-01, dưới  |
|                 |     thẩm quyền student.manage của luồng này — gán  |
|                 |     role STUDENT/PARENT tương ứng ngay lúc tạo,    |
|                 |     khác UC-43 gốc không gán role); nếu đã có tài  |
|                 |     khoản sẵn thì chỉ gắn hồ sơ vào tài khoản đó.  |
|                 |                                                    |
|                 | 2.  Nhập/cập nhật thông tin cá nhân, tải ảnh chân  |
|                 |     dung, thông tin liên hệ của Phụ huynh (liên    |
|                 |     kết tài khoản Phụ huynh nếu đã có, hoặc khởi   |
|                 |     tạo mới).                                      |
|                 |                                                    |
|                 | 3.  Người dùng nhập mã số học sinh (student_code,  |
|                 |     duy nhất trong hệ thống — không tự sinh); khi  |
|                 |     cập nhật hồ sơ đã có, mã này giữ nguyên, không |
|                 |     sửa được (chỉ nhập lúc tạo mới).               |
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
|                 |                                                    |
|                 | ***A2 --- Chuyển lớp (CLASS_CHANGE/BOTH)***        |
|                 |                                                    |
|                 | 1.  Hệ thống đồng bộ luôn class_enrollments (Phân  |
|                 |     hệ 6): ghi danh cũ ở lớp nguồn chuyển sang     |
|                 |     TRANSFERRED, tạo ghi danh mới ACTIVE ở lớp     |
|                 |     đích. Vì 1 học sinh có thể có nhiều ghi danh   |
|                 |     ACTIVE đồng thời ở nhiều lớp khác nhau, người  |
|                 |     dùng phải chỉ định rõ fromClassId -- hệ thống  |
|                 |     không tự suy luận lớp hiện tại.                |
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

UC-15b: Xem báo cáo chuyên cần theo điểm trường

> ⚠️ Bổ sung ngoài SRS/SDD gốc (mở rộng phạm vi FR-STU-03, xác nhận với
> người dùng ngày 2026-07-16) — UC-15 gốc chỉ có Giáo viên là tác nhân,
> không giao việc xem báo cáo chuyên cần cho Quản lý điểm trường. UC-15b
> bổ sung đúng 1 quyền xem (view-only), không đổi bất kỳ điều gì ở UC-15.

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-15b                                             |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xem báo cáo chuyên cần theo điểm trường            |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 5                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-STU-03 (bổ sung)                                |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản lý điểm trường                                |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Quản lý điểm trường xem báo cáo tổng hợp tỷ lệ đi  |
| tắt**           | học/vắng mặt của học sinh tại (các) điểm trường    |
|                 | mình phụ trách — chỉ xem, không có quyền chỉnh sửa |
|                 | điểm danh (vẫn thuộc Giáo viên, UC-15).            |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Quản lý điểm trường mở màn hình báo cáo chuyên cần |
| hoạt**          | của điểm trường mình phụ trách.                    |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Tài khoản được gán phụ trách điểm trường qua   |
| tiên quyết      |     site_managers role_type=SITE_MANAGER (UC-36).  |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản lý điểm trường mở màn hình báo cáo chuyên |
| chính (Main     |     cần.                                           |
| Flow)**         |                                                    |
|                 | 2.  Hệ thống hiển thị tỷ lệ đi học/vắng mặt/muộn   |
|                 |     theo từng học sinh, từng lớp, giới hạn trong   |
|                 |     (các) điểm trường Quản lý điểm trường đang     |
|                 |     phụ trách (không thấy điểm trường khác).       |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Điểm trường chưa có buổi điểm danh nào***|
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống hiển thị danh sách rỗng, không báo    |
| Flow)**         |     lỗi.                                          |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Quản lý điểm trường xem được đúng phạm vi dữ   |
| (P              |     liệu chuyên cần của điểm trường mình phụ       |
| ostcondition)** |     trách — không có quyền chỉnh sửa bản ghi điểm  |
|                 |     danh (Main Flow UC-15 giữ nguyên, chỉ Giáo     |
|                 |     viên được phân công mới sửa được).             |
+-----------------+----------------------------------------------------+

---

UC-50: Nhập phụ huynh theo lô

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-50                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Nhập phụ huynh theo lô                             |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 5                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-STU-04                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Nhân viên (Giáo vụ)                                |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Nhân viên giáo vụ nhập file Excel danh sách phụ    |
| tắt**           | huynh để tạo hồ sơ + liên kết với học sinh ĐÃ TỒN  |
|                 | TẠI SẴN trong hệ thống hàng loạt, thay vì nhập tay |
|                 | từng người (UC-13). Không tạo phụ huynh không có   |
|                 | liên kết học sinh nào.                             |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Nhân viên giáo vụ cần bổ sung hàng loạt phụ huynh  |
| hoạt**          | cho các học sinh đã ghi danh sẵn (VD chuyển dữ     |
|                 | liệu từ hệ thống cũ).                              |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người dùng có quyền student.manage.            |
| tiên quyết      | -   Học sinh cần liên kết đã có hồ sơ sẵn trong hệ |
| (               |     thống (tra theo mã học sinh).                  |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Nhân viên giáo vụ tải file Excel lên.          |
| chính (Main     |                                                    |
| Flow)**         | 2.  Hệ thống tạo bản ghi import_jobs               |
|                 |     (import_type=PARENTS), đọc và xác thực định   |
|                 |     dạng file.                                     |
|                 |                                                    |
|                 | 3.  Với từng dòng: tra mã học sinh --- không tìm   |
|                 |     thấy thì đánh dấu dòng lỗi, bỏ qua.            |
|                 |                                                    |
|                 | 4.  Tìm tài khoản phụ huynh theo số điện thoại ---  |
|                 |     nếu đã có hồ sơ phụ huynh cho số điện thoại đó |
|                 |     (VD 2 dòng cùng cha/mẹ khác con) thì DÙNG LẠI, |
|                 |     không tạo trùng; chưa có thì tạo tài khoản +   |
|                 |     hồ sơ phụ huynh mới (chỉ đăng nhập Google ---  |
|                 |     UC-01 A4, giống cơ chế UC-34).                 |
|                 |                                                    |
|                 | 5.  Tạo liên kết parent_student (quan hệ, người    |
|                 |     liên hệ chính, người chịu trách nhiệm tài      |
|                 |     chính) cho dòng hợp lệ --- tái dùng đúng quy   |
|                 |     tắc validate của UC-13 bước liên kết (không    |
|                 |     trùng liên kết, không quá 1 người liên hệ       |
|                 |     chính/1 người chịu trách nhiệm tài chính cho   |
|                 |     1 học sinh).                                   |
|                 |                                                    |
|                 | 6.  Hệ thống cập nhật total_rows/success_rows/     |
|                 |     failed_rows/error_summary, trạng thái COMPLETED|
|                 |     hoặc PARTIAL_SUCCESS. Nhân viên giáo vụ xem    |
|                 |     kết quả, tải danh sách dòng lỗi (nếu có).      |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- File sai định dạng***                    |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  File rỗng, thiếu dòng tiêu đề, hoặc không mở   |
| Flow)**         |     được như file Excel (.xlsx) hợp lệ --- hệ      |
|                 |     thống từ chối xử lý toàn bộ, đánh dấu          |
|                 |     import_jobs.status=FAILED ngay, không tạo bản  |
|                 |     ghi nào.                                       |
|                 |                                                    |
|                 | ***A2 --- Một phần dòng lỗi***                     |
|                 |                                                    |
|                 | 1.  1 hoặc nhiều dòng lỗi (thiếu trường bắt buộc,  |
|                 |     không tìm thấy mã học sinh, quan hệ không hợp  |
|                 |     lệ, liên kết đã tồn tại, xung đột người liên   |
|                 |     hệ chính/chịu trách nhiệm tài chính) --- hệ    |
|                 |     thống vẫn tạo liên kết cho dòng hợp lệ, bỏ qua |
|                 |     dòng lỗi, đánh dấu status=PARTIAL_SUCCESS, liệt|
|                 |     kê chi tiết từng dòng lỗi trong error_summary. |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Hồ sơ phụ huynh hợp lệ được tạo/tái sử dụng và |
| (P              |     liên kết đúng học sinh tương ứng; không có phụ |
| ostcondition)** |     huynh nào được tạo mà không liên kết học sinh  |
|                 |     nào.                                           |
+-----------------+----------------------------------------------------+

Phân hệ 6 --- Quản lý học thuật và đào tạo