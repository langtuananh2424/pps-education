# phan-he-06-hoc-thuat

UC-16: Quản lý khung chương trình

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-16                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Quản lý khung chương trình                         |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Trưởng phòng đào tạo                               |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Trưởng phòng đào tạo thiết lập khung chương trình  |
| tắt**           | đào tạo chuẩn áp dụng chung toàn hệ thống.         |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Cần khởi tạo mới hoặc cập nhật khung chương trình  |
| hoạt**          | chuẩn.                                             |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người dùng có role HEAD_ACADEMIC.              |
| tiên quyết      |                                                    |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Trưởng phòng đào tạo mở màn hình Khung chương  |
| chính (Main     |     trình, chọn Thêm mới hoặc chọn khung chương    |
| Flow)**         |     trình hiện có để chỉnh sửa.                    |
|                 |                                                    |
|                 | 2.  Nhập/cập nhật cấu trúc khung chương trình: các |
|                 |     cấp độ, học phần, nội dung, công thức tính     |
|                 |     điểm trung bình học phần.                      |
|                 |                                                    |
|                 | 3.  Hệ thống lưu khung chương trình chuẩn, đánh    |
|                 |     dấu là bản gốc (không thuộc điểm trường cụ thể |
|                 |     nào).                                          |
|                 |                                                    |
|                 | 4.  Khung chương trình chuẩn này được dùng làm cơ  |
|                 |     sở để Quản lý điểm trường tạo bản sao tùy biến |
|                 |     (UC-16b).                                      |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Khung chương trình đang được sử dụng bởi |
| thế / ngoại lệ  | lớp học hiện có***                                 |
| (Alternate      |                                                    |
| Flow)**         | 1.  Nếu chỉnh sửa ảnh hưởng tới cấu trúc điểm đang |
|                 |     áp dụng cho lớp đang chạy, hệ thống cảnh báo   |
|                 |     phạm vi ảnh hưởng trước khi cho phép lưu.      |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Khung chương trình chuẩn được cập nhật, sẵn    |
| (P              |     sàng làm cơ sở xếp lớp (UC-18) và tính điểm    |
| ostcondition)** |     trung bình (UC-19).                            |
+-----------------+----------------------------------------------------+

---

UC-16b: Đề xuất khung chương trình tùy biến

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-16b                                             |
+-----------------+----------------------------------------------------+
| **Tên Use       | Đề xuất khung chương trình tùy biến                |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản lý điểm trường                                |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Quản lý điểm trường tạo bản sao khung chương trình |
| tắt**           | gốc và đề xuất điều chỉnh riêng cho điểm trường    |
|                 | mình phụ trách.                                    |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Quản lý điểm trường cần tùy biến chương trình cho  |
| hoạt**          | đặc thù điểm trường (ví dụ: trường liên kết có yêu |
|                 | cầu riêng).                                        |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Khung chương trình chuẩn (gốc) đã tồn tại      |
| tiên quyết      |     (UC-16).                                       |
| (               |                                                    |
| Precondition)** | -   Quản lý điểm trường được gán phụ trách điểm    |
|                 |     trường liên quan.                              |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản lý điểm trường chọn khung chương trình    |
| chính (Main     |     gốc, chọn chức năng Tạo bản sao tùy biến.      |
| Flow)**         |                                                    |
|                 | 2.  Hệ thống tạo bản sao gắn với điểm trường của   |
|                 |     Quản lý điểm trường, giữ liên kết tới khung    |
|                 |     chương trình gốc.                              |
|                 |                                                    |
|                 | 3.  Quản lý điểm trường chỉnh sửa nội dung trên    |
|                 |     bản sao (không ảnh hưởng bản gốc).             |
|                 |                                                    |
|                 | 4.  Quản lý điểm trường gửi đề xuất (submit) bản   |
|                 |     tùy biến để Trưởng phòng đào tạo phê duyệt.    |
|                 |                                                    |
|                 | 5.  Hệ thống chuyển trạng thái bản tùy biến sang   |
|                 |     Chờ duyệt và thông báo Trưởng phòng đào tạo.   |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Lưu nháp chưa gửi duyệt***               |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Quản lý điểm trường có thể lưu bản tùy biến ở  |
| Flow)**         |     trạng thái nháp, chỉnh sửa nhiều lần trước khi |
|                 |     submit chính thức.                             |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bản tùy biến khung chương trình được tạo ở     |
| (P              |     trạng thái Chờ duyệt (hoặc Nháp), chưa có hiệu |
| ostcondition)** |     lực áp dụng cho tới khi được phê duyệt         |
|                 |     (UC-17).                                       |
+-----------------+----------------------------------------------------+

---

UC-17: Phê duyệt khung chương trình tùy biến

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-17                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Phê duyệt khung chương trình tùy biến              |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Trưởng phòng đào tạo                               |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Quản lý điểm trường (người đề   |
|                 | xuất))                                             |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Trưởng phòng đào tạo xem xét và phê duyệt cuối     |
| tắt**           | cùng các bản tùy biến chương trình do Quản lý điểm |
|                 | trường đề xuất.                                    |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Có bản khung chương trình tùy biến đang ở trạng    |
| hoạt**          | thái Chờ duyệt.                                    |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Bản tùy biến đã được submit (UC-16b).          |
| tiên quyết      |                                                    |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Trưởng phòng đào tạo mở danh sách các đề xuất  |
| chính (Main     |     khung chương trình tùy biến đang Chờ duyệt.    |
| Flow)**         |                                                    |
|                 | 2.  Trưởng phòng đào tạo xem chi tiết, so sánh với |
|                 |     bản gốc.                                       |
|                 |                                                    |
|                 | 3.  Trưởng phòng đào tạo ra quyết định: Phê duyệt  |
|                 |     hoặc Từ chối kèm ghi chú.                      |
|                 |                                                    |
|                 | 4.  Nếu Phê duyệt: hệ thống chuyển trạng thái bản  |
|                 |     tùy biến sang Có hiệu lực, áp dụng cho điểm    |
|                 |     trường tương ứng, thông báo Quản lý điểm       |
|                 |     trường.                                        |
|                 |                                                    |
|                 | 5.  Nếu Từ chối: hệ thống chuyển trạng thái về Bị  |
|                 |     từ chối kèm lý do, thông báo Quản lý điểm      |
|                 |     trường để chỉnh sửa và đề xuất lại.            |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Đề xuất lại sau khi bị từ chối***        |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Quản lý điểm trường chỉnh sửa bản tùy biến bị  |
| Flow)**         |     từ chối và submit lại, quay về trạng thái Chờ  |
|                 |     duyệt (lặp lại UC-16b bước 4-5).               |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bản tùy biến chỉ có hiệu lực áp dụng khi đã    |
| (P              |     được Trưởng phòng đào tạo phê duyệt.           |
| ostcondition)** |                                                    |
|                 | -   Điểm trường sử dụng đúng khung chương trình    |
|                 |     (gốc hoặc tùy biến đã duyệt) khi xếp lớp.      |
+-----------------+----------------------------------------------------+

---

UC-18: Xếp lớp & gán khóa học

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-18                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xếp lớp & gán khóa học                             |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-02                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Trưởng phòng đào tạo (quyết định điều phối)        |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Nhân viên (Giáo vụ --- nhập     |
|                 | liệu hành chính))                                  |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Trưởng phòng đào tạo quyết định sắp xếp lớp học và |
| tắt**           | điều phối giáo viên; Nhân viên giáo vụ thực hiện   |
|                 | khởi tạo record lớp học thực tế theo quyết định    |
|                 | đó.                                                |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Đến kỳ mở lớp mới hoặc cần điều chỉnh phân công    |
| hoạt**          | giáo viên/lớp học.                                 |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Khung chương trình/khóa học tương ứng đã tồn   |
| tiên quyết      |     tại và có hiệu lực (UC-16/UC-17).              |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Trưởng phòng đào tạo lên lịch, quyết định danh |
| chính (Main     |     sách lớp cần mở, điều phối giáo viên phụ trách |
| Flow)**         |     từng lớp.                                      |
|                 |                                                    |
|                 | 2.  Trưởng phòng đào tạo chuyển thông tin quyết    |
|                 |     định (lớp/giáo viên) xuống Quản lý điểm trường |
|                 |     tại từng điểm để triển khai.                   |
|                 |                                                    |
|                 | 3.  Nhân viên giáo vụ khởi tạo record lớp học thực |
|                 |     tế trên hệ thống: khai báo Loại hình lớp (Lớp  |
|                 |     liên kết trường/Lớp mở tại trung tâm), giới    |
|                 |     hạn sĩ số tối đa, gán khóa học tương ứng.      |
|                 |                                                    |
|                 | 4.  Nếu là Lớp liên kết: hệ thống yêu cầu bắt buộc |
|                 |     gán thêm Điểm trường (loại Trường liên kết)    |
|                 |     phụ trách.                                     |
|                 |                                                    |
|                 | 5.  Hệ thống kiểm tra ràng buộc phòng học tại Phân |
|                 |     hệ Cơ sở vật chất để cảnh báo trùng phòng      |
|                 |     (FR-FAC-03), trừ các phòng đánh dấu linh hoạt. |
|                 |                                                    |
|                 | 6.  Nhân viên giáo vụ hoàn tất khởi tạo, hệ thống  |
|                 |     lưu record lớp học, đưa vào theo dõi giám sát  |
|                 |     bởi Quản lý điểm trường.                       |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Trùng phòng học***                       |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu phòng học được chọn đã có lịch trùng và    |
| Flow)**         |     không thuộc diện phòng linh hoạt, hệ thống     |
|                 |     cảnh báo và yêu cầu chọn phòng/khung giờ khác. |
|                 |                                                    |
|                 | ***A2 --- Thiếu Điểm trường cho Lớp liên kết***    |
|                 |                                                    |
|                 | 1.  Nếu chọn Loại hình Lớp liên kết trường nhưng   |
|                 |     chưa gán Điểm trường phụ trách, hệ thống chặn  |
|                 |     lưu và yêu cầu bổ sung.                        |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Lớp học được khởi tạo với đầy đủ thông tin:    |
| (P              |     khóa học, giáo viên, sĩ số, phòng học (nếu áp  |
| ostcondition)** |     dụng).                                         |
|                 |                                                    |
|                 | -   Quản lý điểm trường tại điểm liên quan có thể  |
|                 |     bắt đầu giám sát triển khai và thực thi lớp.   |
+-----------------+----------------------------------------------------+

---

UC-48: Xếp lịch buổi học

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-48                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xếp lịch buổi học                                  |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-05                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Nhân viên (Giáo vụ), Trưởng phòng đào tạo          |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Xếp lịch từng buổi học cụ thể (ngày, khung giờ,    |
| tắt**           | phòng, giáo viên phụ trách) cho 1 lớp đã khởi tạo  |
|                 | (UC-18); có thể hủy hoặc dời lịch 1 buổi đã xếp    |
|                 | khi cần. Là điều kiện tiên quyết bắt buộc cho      |
|                 | UC-15 (Điểm danh học sinh) và dùng chung ràng buộc |
|                 | trùng phòng với UC-37.                             |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Lớp học đã khởi tạo (UC-18) cần lịch học cụ thể    |
| hoạt**          | theo tuần/kỳ; hoặc giáo viên nghỉ đột xuất/phòng   |
|                 | có sự cố cần hủy hoặc dời 1 buổi đã lên lịch.      |
+-----------------+----------------------------------------------------+
| **Điều kiện     | - Người thao tác có quyền academic.class.manage.   |
| tiên quyết      |                                                    |
| (               | - Lớp học đích đã tồn tại (UC-18).                 |
| Precondition)** |                                                    |
|                 | - Nếu có chỉ định phòng: phòng đã tồn tại (UC-37). |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1. Người dùng chọn lớp học, nhập ngày, khung giờ   |
| chính (Main     | bắt đầu/kết thúc, loại buổi                        |
| Flow)**         | (REGULAR/MAKEUP/EXAM/SPECIAL), giáo viên phụ trách |
|                 | buổi (có thể khác giáo viên chính của lớp — VD dạy |
|                 | thay), và phòng học (tùy chọn).                    |
|                 |                                                    |
|                 | 2. Nếu có chỉ định phòng và phòng không đánh dấu   |
|                 | linh hoạt: hệ thống kiểm tra trùng khung giờ với   |
|                 | các buổi khác cùng ngày tại phòng đó (FR-FAC-03).  |
|                 |                                                    |
|                 | 3. Hệ thống lưu buổi học với trạng thái SCHEDULED, |
|                 | tự sinh các tiết học (session_periods) theo số     |
|                 | lượng mặc định cấu hình sẵn trong system_settings, |
|                 | chia đều khung giờ của buổi.                       |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Trùng phòng***                           |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1. Hệ thống từ chối tạo, báo rõ phòng đã có buổi   |
| Flow)**         | khác trùng khung giờ trong ngày.                   |
|                 |                                                    |
|                 | ***A2 --- Hủy buổi đã xếp***                       |
|                 |                                                    |
|                 | 1. Người dùng chọn hủy 1 buổi đang ở trạng thái    |
|                 | SCHEDULED, có thể nhập lý do. Hệ thống chuyển      |
|                 | trạng thái buổi sang CANCELLED; phòng (nếu có)     |
|                 | được giải phóng khỏi ràng buộc trùng lịch cho các  |
|                 | buổi khác. Chỉ áp dụng cho buổi đang SCHEDULED —   |
|                 | buổi đang                                          |
|                 | IN_PROGRESS/COMPLETED/CANCELLED/RESCHEDULED bị từ  |
|                 | chối.                                              |
|                 |                                                    |
|                 | ***A3 --- Dời lịch buổi đã xếp***                  |
|                 |                                                    |
|                 | 1. Người dùng chọn dời 1 buổi đang SCHEDULED sang  |
|                 | ngày/khung giờ mới, có thể đổi phòng/giáo viên phụ |
|                 | trách. Hệ thống kiểm tra trùng phòng cho khung giờ |
|                 | mới (như bước 2), tạo 1 buổi học mới ở trạng thái  |
|                 | SCHEDULED với thông tin mới; đồng thời chuyển buổi |
|                 | cũ sang trạng thái RESCHEDULED và liên kết sang    |
|                 | buổi mới vừa tạo. Chỉ áp dụng cho buổi đang        |
|                 | SCHEDULED.                                         |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | - class_sessions/session_periods phản ánh đúng     |
| (P              | lịch học hiện hành của lớp, là dữ liệu tham chiếu  |
| ostcondition)** | cho UC-15 (điểm danh) và ràng buộc trùng phòng của |
|                 | UC-37.                                             |
|                 |                                                    |
|                 | - class_sessions_history/session_periods_history   |
|                 | lưu đầy đủ lịch sử tạo/hủy/dời lịch.               |
+-----------------+----------------------------------------------------+

---

UC-19: Nhập điểm

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-19                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Nhập điểm                                          |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-03                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Giáo viên                                          |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Giáo viên nhập điểm thành phần cho học sinh; hệ    |
| tắt**           | thống tự động tính điểm trung bình học phần theo   |
|                 | công thức cấu hình sẵn.                            |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Đến kỳ nhập điểm cho lớp học phụ trách.            |
| hoạt**          |                                                    |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Giáo viên được phân công giảng dạy lớp cần     |
| tiên quyết      |     nhập điểm (UC-18).                             |
| (               |                                                    |
| Precondition)** | -   Công thức tính điểm trung bình đã được cấu     |
|                 |     hình trong khung chương trình (UC-16/17).      |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Giáo viên mở Sổ điểm của lớp phụ trách, chọn   |
| chính (Main     |     cột điểm thành phần cần nhập.                  |
| Flow)**         |                                                    |
|                 | 2.  Giáo viên nhập điểm cho từng học sinh; hệ      |
|                 |     thống kiểm tra tính hợp lệ (0 ≤ score ≤        |
|                 |     max_score) ngay khi nhập, chặn lưu nếu không   |
|                 |     hợp lệ.                                        |
|                 |                                                    |
|                 | 3.  Giáo viên có thể nhập rải rác nhiều lần, lưu ở |
|                 |     trạng thái nháp (DRAFT) cho tới khi hoàn tất   |
|                 |     toàn bộ lớp.                                   |
|                 |                                                    |
|                 | 4.  Khi đã nhập xong toàn bộ lớp, Giáo viên chọn   |
|                 |     Submit --- theo 1 trong 2 cách: Submit từng    |
|                 |     bản ghi hoặc Submit theo lô (batch_id) cho     |
|                 |     nhiều học sinh cùng lúc.                       |
|                 |                                                    |
|                 | 5.  Hệ thống tự động tính điểm trung bình học phần |
|                 |     theo công thức cấu hình sẵn của Trưởng phòng   |
|                 |     đào tạo.                                       |
|                 |                                                    |
|                 | 6.  Hệ thống chuyển các bản ghi điểm đã submit     |
|                 |     sang trạng thái Chờ duyệt (approval_flows,     |
|                 |     entity_type = GRADE_ENTRY) và thông báo Quản   |
|                 |     lý điểm trường.                                |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Điểm nhập không hợp lệ***                |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu score ngoài khoảng \[0, max_score\], hệ    |
| Flow)**         |     thống chặn lưu ngay tại phía Giáo viên, không  |
|                 |     để lọt xuống database.                         |
|                 |                                                    |
|                 | ***A2 --- Điểm bị Quản lý điểm trường từ chối***   |
|                 |                                                    |
|                 | 1.  Khi UC-20 trả kết quả REJECTED, bản ghi điểm   |
|                 |     quay lại trạng thái cho Giáo viên sửa và       |
|                 |     submit lại (vòng lặp).                         |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bản ghi điểm được lưu ở trạng thái Chờ duyệt,  |
| (P              |     sẵn sàng cho UC-20.                            |
| ostcondition)** |                                                    |
|                 | -   Điểm trung bình học phần được tính toán tự     |
|                 |     động, hiển thị tạm thời cho Giáo viên (chưa    |
|                 |     công khai cho Phụ huynh cho tới khi được       |
|                 |     duyệt).                                        |
+-----------------+----------------------------------------------------+

---

UC-20: Duyệt điểm

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-20                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Duyệt điểm                                         |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-03                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản lý điểm trường                                |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Giáo viên (người nhập))         |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Quản lý điểm trường xem xét, duyệt hoặc từ chối    |
| tắt**           | các bản ghi điểm do Giáo viên submit, có thể duyệt |
|                 | từng bản ghi hoặc theo lô.                         |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Có bản ghi điểm ở trạng thái Chờ duyệt thuộc điểm  |
| hoạt**          | trường phụ trách.                                  |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Điểm đã được Giáo viên submit (UC-19).         |
| tiên quyết      |                                                    |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản lý điểm trường mở danh sách điểm Chờ      |
| chính (Main     |     duyệt của điểm trường mình phụ trách.          |
| Flow)**         |                                                    |
|                 | 2.  Quản lý điểm trường chọn cách duyệt: Duyệt     |
|                 |     từng bản ghi (xem chi tiết 1 học sinh cụ thể)  |
|                 |     hoặc Duyệt theo batch_id (theo lô Giáo viên đã |
|                 |     submit) --- không bắt buộc phải theo đúng cách |
|                 |     Giáo viên đã submit.                           |
|                 |                                                    |
|                 | 3.  Quản lý điểm trường ra quyết định APPROVED     |
|                 |     hoặc REJECTED, có thể kèm ghi chú.             |
|                 |                                                    |
|                 | 4.  Nếu APPROVED: hệ thống công khai điểm cho Phụ  |
|                 |     huynh xem qua Portal (UC-25), kết thúc luồng.  |
|                 |                                                    |
|                 | 5.  Nếu REJECTED: hệ thống trả bản ghi về cho Giáo |
|                 |     viên sửa và submit lại (quay lại UC-19).       |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Duyệt tách lẻ 1 học sinh trong lô đã     |
| thế / ngoại lệ  | submit theo batch***                               |
| (Alternate      |                                                    |
| Flow)**         | 1.  Quản lý điểm trường có thể mở riêng 1 bản ghi  |
|                 |     trong 1 batch để xem kỹ và duyệt/từ chối độc   |
|                 |     lập với phần còn lại của lô.                   |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Trạng thái điểm được cập nhật chính xác        |
| (P              |     (APPROVED/REJECTED).                           |
| ostcondition)** |                                                    |
|                 | -   Điểm APPROVED được công khai cho Phụ huynh;    |
|                 |     điểm REJECTED quay về Giáo viên để chỉnh sửa.  |
+-----------------+----------------------------------------------------+

---

UC-21: Viết nhận xét học sinh

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-21                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Viết nhận xét học sinh                             |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-04                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Giáo viên                                          |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Giáo viên viết nhận xét cho học sinh theo 3 biểu   |
| tắt**           | mẫu: Hàng ngày (thái độ), Giữa kỳ, Cuối kỳ (tổng   |
|                 | kết năng lực).                                     |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Giáo viên cần ghi nhận xét định kỳ hoặc theo ngày  |
| hoạt**          | cho học sinh.                                      |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Giáo viên được phân công giảng dạy lớp có học  |
| tiên quyết      |     sinh cần nhận xét.                             |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Giáo viên mở màn hình Nhận xét, chọn biểu mẫu: |
| chính (Main     |     Hàng ngày/Giữa kỳ/Cuối kỳ.                     |
| Flow)**         |                                                    |
|                 | 2.  Giáo viên viết nội dung nhận xét cho từng học  |
|                 |     sinh; có thể viết rải rác nhiều lần trước khi  |
|                 |     submit (lưu nháp).                             |
|                 |                                                    |
|                 | 3.  Nếu nội dung mang tính cảnh báo đặc biệt, Giáo |
|                 |     viên đánh dấu is_warning = TRUE (cờ hiển thị   |
|                 |     nổi bật cho Phụ huynh, độc lập với mức độ      |
|                 |     nghiêm trọng --- severity).                    |
|                 |                                                    |
|                 | 4.  Khi hoàn tất, Giáo viên submit --- theo 1      |
|                 |     trong 2 cách: từng nhận xét riêng lẻ hoặc theo |
|                 |     lô (batch) cho nhiều học sinh.                 |
|                 |                                                    |
|                 | 5.  Hệ thống chuyển các nhận xét đã submit sang    |
|                 |     trạng thái Chờ duyệt và thông báo Quản lý điểm |
|                 |     trường (tiếp nối UC-22).                       |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Nhận xét bị từ chối duyệt***             |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Khi UC-22 trả kết quả REJECTED, nhận xét quay  |
| Flow)**         |     lại cho Giáo viên sửa và submit lại.           |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Nhận xét được lưu ở trạng thái Chờ duyệt, sẵn  |
| (P              |     sàng cho quy trình duyệt (UC-22), chưa hiển    |
| ostcondition)** |     thị cho Phụ huynh.                             |
+-----------------+----------------------------------------------------+

---

UC-22: Duyệt nhận xét

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-22                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Duyệt nhận xét                                     |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6, 7                                       |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-09                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản lý điểm trường                                |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Giáo viên (người viết))         |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Quản lý điểm trường duyệt nhận xét do Giáo viên    |
| tắt**           | nhập trước khi hiển thị cho Phụ huynh, tránh trùng |
|                 | lặp hoặc sai sót; hỗ trợ duyệt từng cái hoặc theo  |
|                 | lô.                                                |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Có nhận xét ở trạng thái Chờ duyệt thuộc điểm      |
| hoạt**          | trường phụ trách.                                  |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Nhận xét đã được Giáo viên submit (UC-21).     |
| tiên quyết      |                                                    |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản lý điểm trường mở danh sách nhận xét Chờ  |
| chính (Main     |     duyệt.                                         |
| Flow)**         |                                                    |
|                 | 2.  Quản lý điểm trường chọn hình thức duyệt:      |
|                 |     Duyệt từng nhận xét (xem/duyệt/từ chối riêng   |
|                 |     lẻ từng dòng) hoặc Duyệt theo lô (chọn nhiều   |
|                 |     nhận xét cùng lúc theo lớp/theo ngày) ---      |
|                 |     không bị ràng buộc theo cách Giáo viên đã      |
|                 |     submit.                                        |
|                 |                                                    |
|                 | 3.  Quản lý điểm trường ra quyết định APPROVED     |
|                 |     hoặc REJECTED cho từng nhận xét/lô.            |
|                 |                                                    |
|                 | 4.  Nếu APPROVED: hệ thống hiển thị nhận xét lên   |
|                 |     Portal cho Phụ huynh (và cả Đại diện trường    |
|                 |     liên kết nếu nhận xét có cảnh báo ---          |
|                 |     is_warning = TRUE).                            |
|                 |                                                    |
|                 | 5.  Nếu REJECTED: hệ thống trả nhận xét về cho     |
|                 |     Giáo viên sửa (quay lại UC-21).                |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Duyệt lô nhanh cho khối lượng lớn***     |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Quản lý điểm trường chọn nhiều nhận xét theo   |
| Flow)**         |     lớp/ngày, duyệt hàng loạt trong 1 thao tác để  |
|                 |     tăng tốc độ xử lý.                             |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Nhận xét APPROVED được công khai đúng đối      |
| (P              |     tượng xem (Phụ huynh; thêm Đại diện trường     |
| ostcondition)** |     liên kết nếu có cảnh báo).                     |
|                 |                                                    |
|                 | -   Nhận xét REJECTED quay về Giáo viên, chưa hiển |
|                 |     thị cho bất kỳ ai bên ngoài.                   |
+-----------------+----------------------------------------------------+

Phân hệ 7 --- Cổng thông tin và E-Learning (Portal & LMS)