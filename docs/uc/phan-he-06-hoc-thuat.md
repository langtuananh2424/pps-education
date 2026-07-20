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
|                 |                                                    |
|                 | ***A2 --- Bổ sung thành phần điểm giữa kỳ, không   |
|                 | cần duyệt lại toàn bộ khung (bổ sung ngoài SDD gốc,|
|                 | đã xác nhận với người dùng)***                     |
|                 |                                                    |
|                 | 1.  Trưởng phòng đào tạo (hoặc người có quyền      |
|                 |     academic.grade.manage) có thể thêm 1 thành     |
|                 |     phần điểm mới (grade_component) vào 1 kỳ đánh  |
|                 |     giá đã tồn tại — kể cả khi khung đang Có hiệu  |
|                 |     lực và đã có lớp dùng — mà KHÔNG cần đi qua lại|
|                 |     UC-16b/UC-17. Áp dụng cho trường hợp phát sinh |
|                 |     kỹ năng thi mới (VD thêm Ngữ pháp giữa kỳ) mà  |
|                 |     không cần chờ quy trình tùy biến + phê duyệt   |
|                 |     đầy đủ.                                        |
|                 |                                                    |
|                 | 2.  Vì kỳ đánh giá thuộc về khung chương trình dùng |
|                 |     chung (curriculum_id), thành phần điểm mới sẽ  |
|                 |     áp dụng cho MỌI lớp đang dùng khung đó (không  |
|                 |     tách riêng theo từng lớp) — đúng bản chất khung|
|                 |     chuẩn dùng chung.                              |
|                 |                                                    |
|                 | *(V40 --- bổ sung ngoài SDD gốc, đã xác nhận với   |
|                 | người dùng: đã bỏ hẳn cột weight_in_period cấp     |
|                 | thành phần điểm — không còn bước validate tổng     |
|                 | trọng số ≤ 100 ở đây. Overall/Level công bố cho    |
|                 | Phụ huynh (UC-53) luôn do Giáo viên tự nhập/import  |
|                 | Excel, không tính lại theo trọng số thành phần.)*  |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Khung chương trình chuẩn được cập nhật, sẵn    |
| (P              |     sàng làm cơ sở xếp lớp (UC-18) và nhập điểm    |
| ostcondition)** |     (UC-19).                                       |
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
|                 |                                                    |
|                 | ***A3 --- Giáo viên chưa được gán vào Điểm trường  |
|                 | của lớp (bổ sung ngoài SDD gốc, đã xác nhận với    |
|                 | người dùng)***                                     |
|                 |                                                    |
|                 | 1.  Khi điều phối giáo viên phụ trách 1 lớp, nếu   |
|                 |     giáo viên đó chưa từng được gán vào Điểm       |
|                 |     trường của lớp, hệ thống TỰ ĐỘNG tạo liên kết  |
|                 |     giáo viên--điểm trường (site_teachers), không  |
|                 |     chặn thao tác.                                 |
|                 |                                                    |
|                 | 2.  Nếu giáo viên đã được gán vào điểm trường đó   |
|                 |     từ trước, bỏ qua (không tạo trùng).            |
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
|                 | SCHEDULED.                                          |
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

UC-56: Sinh lịch học hàng loạt theo mẫu lặp

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-56                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Sinh lịch học hàng loạt theo mẫu lặp               |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-05                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Nhân viên (Giáo vụ), Trưởng phòng đào tạo          |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Sinh nhanh nhiều buổi học cùng lúc theo mẫu lặp    |
| tắt**           | lại (khoảng ngày + các thứ trong tuần cố định +    |
|                 | khung giờ chung), thay vì phải tạo từng buổi một   |
|                 | qua UC-48. Dùng lại đúng logic tạo 1 buổi + kiểm   |
|                 | tra trùng phòng của UC-48 (bổ sung ngoài SDD gốc,  |
|                 | đã xác nhận với người dùng).                       |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Lớp học đã khởi tạo (UC-18) cần lịch học đều đặn   |
| hoạt**          | theo tuần trong 1 khoảng thời gian dài (VD học Thứ |
|                 | 2/4/6 suốt học kỳ), không muốn tạo tay từng buổi.  |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người thao tác có quyền academic.class.manage  |
| tiên quyết      |     (giống UC-48).                                 |
| (               |                                                    |
| Precondition)** | -   Lớp học đích đã tồn tại (UC-18).               |
|                 |                                                    |
|                 | -   Nếu có chỉ định phòng: phòng đã tồn tại        |
|                 |     (UC-37).                                       |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người dùng chọn lớp học, nhập khoảng ngày (từ  |
| chính (Main     |     ngày, đến ngày), danh sách thứ trong tuần lặp  |
| Flow)**         |     lại (VD Thứ 2/4/6), khung giờ bắt đầu/kết thúc |
|                 |     chung, loại buổi, giáo viên phụ trách, phòng   |
|                 |     học (tùy chọn).                                |
|                 |                                                    |
|                 | 2.  Với mỗi ngày trong khoảng khớp 1 trong các thứ |
|                 |     đã chọn, hệ thống thử tạo 1 buổi học — áp dụng |
|                 |     đúng bước 2-3 của UC-48 (kiểm tra trùng phòng  |
|                 |     nếu có chỉ định phòng không linh hoạt, lưu     |
|                 |     SCHEDULED, tự sinh session_periods).           |
|                 |                                                    |
|                 | 3.  Hệ thống trả về danh sách buổi đã tạo thành    |
|                 |     công và danh sách ngày bị bỏ qua kèm lý do     |
|                 |     (nếu có).                                      |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- 1 hoặc nhiều ngày trùng phòng***         |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Ngày nào phát hiện trùng phòng (như UC-48/A1)  |
| Flow)**         |     bị bỏ qua, ghi lại lý do; các ngày khác trong  |
|                 |     lô vẫn tiếp tục xử lý bình thường, không dừng  |
|                 |     cả lô (bổ sung ngoài SDD gốc, đã xác nhận với  |
|                 |     người dùng).                                   |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Mỗi ngày khớp mẫu lặp và không trùng phòng có  |
| (P              |     1 class_session mới (SCHEDULED) +              |
| ostcondition)** |     session_periods tương ứng, giống hệt kết quả   |
|                 |     UC-48 Main Flow.                               |
|                 |                                                    |
|                 | -   Response liệt kê rõ tổng số ngày khớp mẫu, số  |
|                 |     buổi tạo thành công, số ngày bị bỏ qua kèm lý  |
|                 |     do — người dùng biết ngay cần xử lý thủ công   |
|                 |     ngày nào.                                      |
+-----------------+----------------------------------------------------+

---

UC-57: Nhập lịch học qua Excel

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-57                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Nhập lịch học qua Excel                            |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-05                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Nhân viên (Giáo vụ), Trưởng phòng đào tạo          |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Nhập hàng loạt buổi học từ file Excel (.xlsx) đã   |
| tắt**           | chuẩn bị sẵn ngoài hệ thống, thay vì nhập tay từng |
|                 | buổi hoặc theo mẫu lặp đều (UC-56) — phù hợp lịch  |
|                 | không đều theo tuần. Dùng lại đúng logic tạo 1     |
|                 | buổi + kiểm tra trùng phòng của UC-48, theo        |
|                 | pattern nhập Excel dùng chung đã có ở              |
|                 | UC-35/50/51/53 (bổ sung ngoài SDD gốc, đã xác nhận |
|                 | với người dùng).                                   |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Người dùng đã có sẵn lịch học soạn ngoài hệ thống  |
| hoạt**          | (Excel), muốn import thẳng thay vì nhập tay.       |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người thao tác có quyền academic.class.manage  |
| tiên quyết      |     (giống UC-48).                                 |
| (               |                                                    |
| Precondition)** | -   Lớp học đích đã tồn tại (UC-18).               |
|                 |                                                    |
|                 | -   File .xlsx đúng định dạng cột quy định (xem    |
|                 |     Main Flow bước 1).                             |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người dùng chọn lớp học, tải lên file .xlsx    |
| chính (Main     |     theo định dạng cột quy định (dòng 1 = tiêu đề, |
| Flow)**         |     dữ liệu từ dòng 2): A=Ngày (dd/MM/yyyy), B=Giờ |
|                 |     bắt đầu (HH:mm), C=Giờ kết thúc (HH:mm),       |
|                 |     D=Loại buổi (để trống = REGULAR), E=Username   |
|                 |     giáo viên phụ trách (bắt buộc), F=Mã phòng     |
|                 |     (tùy chọn, tra theo đúng điểm trường của lớp). |
|                 |                                                    |
|                 | 2.  Hệ thống tạo bản ghi import_jobs               |
|                 |     (import_type=TEACHING_SCHEDULE), xử lý từng    |
|                 |     dòng: parse ngày/giờ, tìm giáo viên theo       |
|                 |     username, tìm phòng theo mã (nếu có), áp dụng  |
|                 |     đúng bước 2-3 của UC-48 (kiểm tra trùng phòng, |
|                 |     lưu SCHEDULED, tự sinh session_periods) cho    |
|                 |     mỗi dòng hợp lệ.                               |
|                 |                                                    |
|                 | 3.  Hệ thống trả về kết quả tổng hợp: tổng số      |
|                 |     dòng, số dòng thành công, số dòng lỗi, chi     |
|                 |     tiết lỗi từng dòng.                            |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A2 --- 1 dòng lỗi không chặn dòng khác***       |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Dòng thiếu giáo viên, sai định dạng ngày/giờ,  |
| Flow)**         |     không tìm thấy giáo viên/phòng theo mã, hoặc   |
|                 |     trùng phòng: dòng đó bị bỏ qua, ghi lý do vào  |
|                 |     error_summary; các dòng khác trong file vẫn    |
|                 |     tiếp tục xử lý bình thường, không dừng cả      |
|                 |     file.                                          |
|                 |                                                    |
|                 | ***A3 --- File sai định dạng***                    |
|                 |                                                    |
|                 | 1.  File không mở được dưới dạng .xlsx hợp lệ,     |
|                 |     hoặc thiếu dòng tiêu đề: import_jobs chuyển    |
|                 |     ngay trạng thái FAILED, không xử lý dòng nào.  |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Mỗi dòng hợp lệ có 1 class_session mới         |
| (P              |     (SCHEDULED) + session_periods tương ứng, giống |
| ostcondition)** |     hệt kết quả UC-48 Main Flow.                   |
|                 |                                                    |
|                 | -   import_jobs lưu đầy đủ tổng số dòng/số dòng    |
|                 |     thành công/lỗi/chi tiết lỗi từng dòng — trạng  |
|                 |     thái COMPLETED (không lỗi), PARTIAL_SUCCESS    |
|                 |     (có ít nhất 1 lỗi dòng), hoặc FAILED (A3).     |
+-----------------+----------------------------------------------------+

---

UC-58: Xem lịch dạy tổng hợp ("Lịch của tôi")

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-58                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xem lịch dạy tổng hợp ("Lịch của tôi")             |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-05                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Giáo viên                                          |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Giáo viên xem tổng hợp mọi buổi dạy của chính mình |
| tắt**           | qua TẤT CẢ các lớp đang phụ trách (không giới hạn  |
|                 | theo 1 lớp/1 điểm trường như UC-48 hiện có), lọc   |
|                 | theo khoảng ngày tùy chọn — self-service, không    |
|                 | cần quyền academic.class.manage (bổ sung ngoài SDD |
|                 | gốc, đã xác nhận với người dùng).                  |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Giáo viên cần xem lịch dạy sắp tới/đã qua của      |
| hoạt**          | chính mình để chủ động sắp xếp.                    |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Giáo viên đã đăng nhập (không cần quyền đặc    |
| tiên quyết      |     biệt nào khác).                                |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Giáo viên mở trang "Lịch của tôi", có thể chọn |
| chính (Main     |     khoảng ngày (từ ngày, đến ngày) để lọc, để     |
| Flow)**         |     trống = xem toàn bộ.                           |
|                 |                                                    |
|                 | 2.  Hệ thống trả về mọi buổi học (qua mọi lớp, mọi |
|                 |     điểm trường) mà Giáo viên này là               |
|                 |     primary_teacher của buổi, khớp khoảng ngày đã  |
|                 |     chọn, sắp xếp theo ngày/giờ tăng dần.          |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Giáo viên thấy đúng và đủ các buổi mình phụ    |
| (P              |     trách (không thấy buổi của giáo viên khác),    |
| ostcondition)** |     không phụ thuộc site_teachers như UC-48        |
|                 |     listSessions theo lớp.                         |
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
| **Mô tả tóm     | Giáo viên nhập điểm thành phần cho học sinh, ghi   |
| tắt**           | nhận ngay ở trạng thái nháp, không qua bước gửi    |
|                 | duyệt riêng (V39 — bổ sung ngoài SDD gốc, đã xác   |
|                 | nhận với người dùng, thay thế luồng Submit/Chờ     |
|                 | duyệt cũ). Điểm tổng kết/Overall hiển thị cho Phụ  |
|                 | huynh do Giáo viên tự nhập hoặc import Excel       |
|                 | (UC-53), hệ thống không tự tính từ điểm thành phần |
|                 | (V40 — bổ sung ngoài SDD gốc, đã xác nhận với      |
|                 | người dùng).                                       |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Đến kỳ nhập điểm cho lớp học phụ trách; hoặc cần   |
| hoạt**          | sửa lại điểm đã nhập, kể cả sau khi đã công bố     |
|                 | (UC-20), nếu còn trong hạn cho phép.               |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Giáo viên được phân công giảng dạy lớp cần     |
| tiên quyết      |     nhập điểm (UC-18); HOẶC Trưởng phòng đào tạo   |
| (               |     (quyền academic.grade.manage); HOẶC Quản lý    |
| Precondition)** |     điểm trường phụ trách đúng điểm trường của lớp |
|                 |     — được phép nhập thay giáo viên khi cần hỗ trợ |
|                 |     (bổ sung ngoài SDD gốc, đã xác nhận với người  |
|                 |     dùng).                                         |
|                 |                                                    |
|                 | -   Để sửa 1 bản ghi đã tồn tại (kể cả đã công bố  |
|                 |     — PUBLISHED): còn trong hạn X ngày kể từ lần   |
|                 |     đầu nhập điểm cho (lớp, kỳ đánh giá) đó        |
|                 |     (system_settings.academic.grade_edit_window_days, |
|                 |     mặc định 7 ngày — V39, bổ sung ngoài SDD gốc,  |
|                 |     đã xác nhận với người dùng), HOẶC actor có     |
|                 |     quyền academic.grade.edit.override (quyền      |
|                 |     ngoại lệ, gán được cho bất kỳ ai qua UC-04 —   |
|                 |     mặc định gán sẵn cho HEAD_ACADEMIC và          |
|                 |     SITE_MANAGER).                                 |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Giáo viên (hoặc người hỗ trợ hợp lệ ở trên) mở |
| chính (Main     |     Sổ điểm của lớp, chọn cột điểm thành phần cần  |
| Flow)**         |     nhập.                                          |
|                 |                                                    |
|                 | 2.  Giáo viên nhập điểm cho từng học sinh; hệ      |
|                 |     thống kiểm tra tính hợp lệ (0 ≤ score ≤        |
|                 |     max_score) ngay khi nhập, chặn lưu nếu không   |
|                 |     hợp lệ.                                        |
|                 |                                                    |
|                 | 3.  Hệ thống ghi nhận điểm ngay ở trạng thái nháp  |
|                 |     (DRAFT) — không có bước gửi duyệt riêng; Giáo  |
|                 |     viên có thể nhập/sửa rải rác nhiều lần.        |
|                 |                                                    |
|                 | 4.  Nếu đây là lần đầu tiên (lớp, kỳ đánh giá) này |
|                 |     có điểm được nhập, hệ thống ghi nhận mốc “lần  |
|                 |     đầu nhập” (grade_period_edit_windows) — làm    |
|                 |     gốc tính hạn X ngày toàn quyền chỉnh sửa.      |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Điểm nhập không hợp lệ***                |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu score ngoài khoảng [0, max_score], hệ      |
| Flow)**         |     thống chặn lưu ngay tại phía Giáo viên, không  |
|                 |     để lọt xuống database.                         |
|                 |                                                    |
|                 | ***A2 --- Hết hạn chỉnh sửa, không có quyền ngoại  |
|                 | lệ (bổ sung ngoài SDD gốc, đã xác nhận với người   |
|                 | dùng)***                                           |
|                 |                                                    |
|                 | 1.  Nếu actor sửa 1 bản ghi đã tồn tại mà đã quá   |
|                 |     hạn X ngày kể từ lần đầu nhập cho (lớp, kỳ     |
|                 |     đánh giá) đó, và actor không có quyền          |
|                 |     academic.grade.edit.override, hệ thống chặn    |
|                 |     lưu, báo rõ đã hết hạn kể từ ngày nào.         |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bản ghi điểm được lưu ngay — DRAFT nếu là lần  |
| (P              |     nhập đầu tiên, hoặc giữ nguyên trạng thái hiện |
| ostcondition)** |     có (kể cả PUBLISHED) nếu là sửa lại bản ghi đã |
|                 |     tồn tại.                                       |
|                 |                                                    |
|                 | -   Điểm tổng kết/Overall theo kỳ đánh giá         |
|                 |     (grade_period_results) là 1 thao tác nhập liệu |
|                 |     riêng của Giáo viên (UC-53) — hệ thống không   |
|                 |     tự tính lại từ điểm thành phần vừa nhập ở đây  |
|                 |     (V40 — bổ sung ngoài SDD gốc, đã xác nhận với  |
|                 |     người dùng).                                   |
|                 |                                                    |
|                 | -   Nếu bản ghi đã PUBLISHED và còn trong hạn X    |
|                 |     ngày (trường hợp phúc khảo), giá trị mới sửa   |
|                 |     lại hiển thị NGAY cho Phụ huynh, không cần     |
|                 |     công bố lại.                                   |
+-----------------+----------------------------------------------------+

---

UC-20: Công bố điểm

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-20                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Công bố điểm                                       |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-03                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản lý điểm trường (đúng site phụ trách); Trưởng  |
|                 | phòng đào tạo (mọi site — bổ sung ngoài SDD gốc,   |
|                 | đã xác nhận với người dùng)                        |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Giáo viên (người nhập))         |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Quản lý điểm trường/Trưởng phòng đào tạo quyết     |
| tắt**           | định thời điểm công bố điểm cho Phụ huynh/Học sinh |
|                 | xem qua Portal — không còn kiểm duyệt đúng/sai như |
|                 | trước, chỉ là mốc công khai dữ liệu (V39 — bổ sung |
|                 | ngoài SDD gốc, đã xác nhận với người dùng, thay    |
|                 | thế hoàn toàn khái niệm “Duyệt điểm” và luồng      |
|                 | Approved/Rejected cũ). Nếu không ai công bố thủ    |
|                 | công, hệ thống tự động công bố khi hết hạn X ngày  |
|                 | chỉnh sửa (UC-19) — 2 cơ chế chạy song song, không |
|                 | cái nào thay thế cái nào (bổ sung ngoài SDD gốc,   |
|                 | đã xác nhận với người dùng).                       |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Có bản ghi điểm ở trạng thái chưa công bố (DRAFT)  |
| hoạt**          | thuộc điểm trường phụ trách, đã sẵn sàng công khai |
|                 | cho Phụ huynh.                                     |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Actor có quyền academic.grade.publish (bổ sung |
| tiên quyết      |     ngoài SDD gốc, đã xác nhận với người dùng —    |
| (               |     mặc định gán cho role SITE_MANAGER và          |
| Precondition)** |     HEAD_ACADEMIC).                                |
|                 |                                                    |
|                 | -   Quản lý điểm trường còn phải được gán phụ      |
|                 |     trách đúng điểm trường của lớp (site_managers, |
|                 |     row-level); Trưởng phòng đào tạo (có thêm      |
|                 |     quyền academic.grade.manage) thì không bị giới |
|                 |     hạn theo site.                                 |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản lý điểm trường (hoặc Trưởng phòng đào     |
| chính (Main     |     tạo) mở danh sách điểm chưa công bố — của điểm |
| Flow)**         |     trường mình phụ trách, hoặc của MỌI điểm       |
|                 |     trường nếu là Trưởng phòng đào tạo.            |
|                 |                                                    |
|                 | 2.  Quản lý điểm trường chọn cách công bố: Công bố |
|                 |     từng bản ghi (xem chi tiết 1 học sinh cụ thể)  |
|                 |     hoặc Công bố theo lô (nhiều bản ghi cùng lúc). |
|                 |                                                    |
|                 | 3.  Quản lý điểm trường xác nhận công bố — không   |
|                 |     còn quyết định Đúng/Sai như trước, chỉ có 1    |
|                 |     hành động duy nhất: Công bố.                   |
|                 |                                                    |
|                 | 4.  Hệ thống chuyển các bản ghi đã chọn từ DRAFT   |
|                 |     sang PUBLISHED, ghi nhận người và thời điểm    |
|                 |     công bố, công khai điểm cho Phụ huynh xem ngay |
|                 |     qua Portal (UC-25).                            |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Công bố tách lẻ 1 bản ghi trong lô***    |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Quản lý điểm trường có thể mở riêng 1 bản ghi  |
| Flow)**         |     trong 1 lô để công bố độc lập với phần còn lại |
|                 |     — không bắt buộc công bố cả lô cùng lúc.       |
|                 |                                                    |
|                 | ***A2 --- Công bố lại bản ghi đã PUBLISHED (bổ     |
|                 | sung ngoài SDD gốc, đã xác nhận với người dùng)*** |
|                 |                                                    |
|                 | 1.  Nếu bản ghi được chọn đã ở trạng thái          |
|                 |     PUBLISHED từ trước, hệ thống báo lỗi (đã công  |
|                 |     bố trước đó), không cho công bố lại.           |
|                 |                                                    |
|                 | 2.  Việc này KHÔNG chặn Giáo viên sửa lại giá trị  |
|                 |     bản ghi (UC-19, còn trong hạn X ngày) — giá    |
|                 |     trị mới hiển thị ngay cho Phụ huynh mà không   |
|                 |     cần lặp lại bước công bố.                      |
|                 |                                                    |
|                 | ***A3 --- Tự động công bố khi hết hạn X ngày,      |
|                 | không ai công bố tay (bổ sung ngoài SDD gốc, đã    |
|                 | xác nhận với người dùng)***                        |
|                 |                                                    |
|                 | 1.  Mỗi đêm, hệ thống quét mọi (lớp, kỳ đánh giá)  |
|                 |     đã hết hạn X ngày kể từ lần đầu nhập điểm      |
|                 |     (UC-19, grade_period_edit_windows) — dùng đúng |
|                 |     1 giá trị X ngày cấu hình chung với hạn chỉnh  |
|                 |     sửa                                            |
|                 |     (system_settings.academic.grade_edit_window_days). |
|                 |                                                    |
|                 | 2.  Mọi grade_entries/grade_period_results còn     |
|                 |     DRAFT thuộc (lớp, kỳ đánh giá) đó được tự động |
|                 |     chuyển sang PUBLISHED — không cần Quản lý điểm |
|                 |     trường xác nhận. Bản ghi đã PUBLISHED thủ công |
|                 |     từ trước không bị ảnh hưởng.                   |
|                 |                                                    |
|                 | 3.  published_by để trống (không gán người công    |
|                 |     bố) để phân biệt với công bố thủ công —        |
|                 |     published_at vẫn ghi nhận đúng thời điểm.      |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Trạng thái điểm được cập nhật chính xác (DRAFT |
| (P              |     → PUBLISHED).                                  |
| ostcondition)** |                                                    |
|                 | -   Điểm PUBLISHED được công khai cho Phụ huynh    |
|                 |     ngay lập tức qua Portal (UC-25).               |
|                 |                                                    |
|                 | -   Nếu Giáo viên sửa lại bản ghi đã PUBLISHED     |
|                 |     trong hạn X ngày (UC-19), giá trị mới cũng     |
|                 |     hiển thị ngay cho Phụ huynh mà không cần công  |
|                 |     bố lại.                                        |
|                 |                                                    |
|                 | -   Nếu không ai công bố thủ công trong hạn X      |
|                 |     ngày, hệ thống tự động chuyển DRAFT →          |
|                 |     PUBLISHED cho toàn bộ bản ghi còn lại của      |
|                 |     (lớp, kỳ đánh giá) đó ngay sau khi hết hạn     |
|                 |     (A3) — Phụ huynh không phải chờ vô thời hạn    |
|                 |     nếu Quản lý điểm trường quên công bố.          |
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

---

UC-53: Nhập điểm thi qua Excel (bổ sung ngoài SDD gốc, đã xác nhận với
người dùng)

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-53                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Nhập điểm thi qua Excel                            |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-03                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Giáo viên                                          |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Giáo viên tải lên 1 file Excel đã hoàn thiện toàn  |
| tắt**           | bộ bảng điểm (từng kỹ năng + Overall + Level đã    |
|                 | quy đổi sẵn) cho 1 kỳ đánh giá; hệ thống quét dòng |
|                 | header, tự map từng cột vào đúng thành phần điểm/  |
|                 | Overall/Level đã cấu hình, rồi ghi nhận như nhập   |
|                 | tay (UC-19) — không tự tính lại công thức.         |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Giáo viên đã hoàn thiện bảng điểm ngoài hệ thống   |
| hoạt**          | (Excel) sau kỳ thi giữa kỳ/cuối kỳ, cần đẩy hàng    |
|                 | loạt lên thay vì nhập tay từng dòng.                |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Giáo viên được phân công giảng dạy lớp cần     |
| tiên quyết      |     nhập điểm (UC-18), như UC-19 — HOẶC Trưởng     |
| (               |     phòng đào tạo/Quản lý điểm trường phụ trách    |
| Precondition)** |     đúng điểm trường của lớp (cùng mở rộng quyền   |
|                 |     như UC-19).                                    |
|                 |                                                    |
|                 | -   Kỳ đánh giá và các thành phần điểm (kỹ năng)   |
|                 |     tương ứng đã được cấu hình sẵn cho khung        |
|                 |     chương trình của lớp (UC-16, kể cả qua A2).    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Giáo viên chọn lớp và kỳ đánh giá, tải lên 1   |
| chính (Main     |     file .xlsx đã hoàn thiện điểm: cột đầu là mã   |
| Flow)**         |     học viên, các cột sau là từng kỹ năng/thành    |
|                 |     phần điểm hoặc cột Overall/Level; dòng 1 là    |
|                 |     header, dữ liệu từ dòng 2.                     |
|                 |                                                    |
|                 | 2.  Hệ thống đọc header, so khớp tên từng cột (đã  |
|                 |     chuẩn hoá khoảng trắng/hoa-thường) với tên các |
|                 |     thành phần điểm đã cấu hình cho đúng kỳ đánh   |
|                 |     giá đó, hoặc với tên cột Overall/Level.        |
|                 |                                                    |
|                 | 3.  Nếu mọi cột đều khớp, hệ thống xử lý từng dòng: |
|                 |     ghi điểm từng kỹ năng vào grade_entries (áp    |
|                 |     dụng đúng validate 0 ≤ score ≤ max_score như   |
|                 |     UC-19 A1) và ghi Overall/Level vào              |
|                 |     grade_period_results — tất cả ở trạng thái     |
|                 |     nháp (DRAFT), giống như Giáo viên tự nhập tay. |
|                 |                                                    |
|                 | 4.  Hệ thống trả về kết quả tổng hợp: số dòng thành |
|                 |     công/lỗi (import_jobs).                        |
|                 |                                                    |
|                 | 5.  Giáo viên xem lại bảng điểm vừa nhập; điểm đã  |
|                 |     ở trạng thái nháp (DRAFT) ngay, sẵn sàng để     |
|                 |     Quản lý điểm trường/Trưởng phòng đào tạo công  |
|                 |     bố hàng loạt (UC-20) — không có luồng công bố  |
|                 |     riêng cho dữ liệu nhập từ Excel.                |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Cột không khớp cấu hình***               |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu có tên cột (ngoài cột mã học viên) không   |
| Flow)**         |     khớp bất kỳ thành phần điểm/Overall/Level nào  |
|                 |     đã cấu hình cho kỳ đánh giá đó, hệ thống DỪNG   |
|                 |     toàn bộ import (không ghi dòng nào), liệt kê rõ|
|                 |     danh sách tên cột không khớp, yêu cầu người có |
|                 |     quyền academic.grade.manage bổ sung thành phần |
|                 |     điểm đó (UC-16/A2) trước khi import lại.       |
|                 |                                                    |
|                 | ***A2 --- Lỗi ở từng dòng dữ liệu***               |
|                 |                                                    |
|                 | 1.  Nếu 1 dòng có mã học viên không tồn tại hoặc   |
|                 |     điểm ngoài khoảng hợp lệ, hệ thống ghi nhận lỗi|
|                 |     dòng đó vào error_summary, KHÔNG chặn các dòng |
|                 |     còn lại (theo đúng cơ chế UC-51 A2).           |
|                 |                                                    |
|                 | ***A3 --- File sai định dạng hoàn toàn***          |
|                 |                                                    |
|                 | 1.  Nếu không đọc được file (không mở được dạng    |
|                 |     .xlsx, thiếu dòng header), hệ thống đánh dấu    |
|                 |     import_job FAILED ngay, không xử lý dòng nào   |
|                 |     (theo đúng cơ chế UC-51 A1).                   |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Điểm nhập từ Excel ở trạng thái DRAFT, có thể  |
| (P              |     chỉnh sửa tiếp giống hệt nhập tay (UC-19, còn   |
| ostcondition)** |     trong hạn X ngày) và được công bố qua cùng      |
|                 |     luồng UC-20 — không có quy trình công bố riêng.|
|                 |                                                    |
|                 | -   import_jobs lưu lại kết quả (thành công/lỗi    |
|                 |     từng dòng) để Giáo viên đối chiếu.             |
+-----------------+----------------------------------------------------+

---

UC-54: Quản lý danh mục kỹ năng (bổ sung ngoài SDD gốc, đã xác nhận với
người dùng)

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-54                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Quản lý danh mục kỹ năng                           |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-06                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Trưởng phòng đào tạo                               |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Trưởng phòng đào tạo quản lý danh mục kỹ năng thi  |
| tắt**           | (skills) dùng chung cho toàn hệ thống — thêm kỹ    |
|                 | năng mới không cần lập trình viên can thiệp.       |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Phát sinh nhu cầu thêm/đổi tên/vô hiệu hoá 1 kỹ    |
| hoạt**          | năng thi (VD thêm "Từ vựng" ngoài 6 kỹ năng gốc).  |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người dùng có quyền academic.grade.manage.     |
| tiên quyết      |                                                    |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Trưởng phòng đào tạo mở màn hình Danh mục kỹ   |
| chính (Main     |     năng, xem danh sách kỹ năng hiện có (skills).  |
| Flow)**         |                                                    |
|                 | 2.  Thêm mới: nhập mã (code, duy nhất) và tên hiển |
|                 |     thị; hoặc Sửa: đổi tên hiển thị của 1 kỹ năng  |
|                 |     hiện có; hoặc Vô hiệu hoá: đánh dấu is_active = |
|                 |     FALSE cho 1 kỹ năng không còn dùng (không xoá  |
|                 |     cứng vì có thể đã được tham chiếu ở khung       |
|                 |     chương trình/thành phần điểm đã tồn tại).      |
|                 |                                                    |
|                 | 3.  Hệ thống lưu lại; kỹ năng mới có thể được chọn |
|                 |     ngay khi thêm thành phần điểm mới cho 1 kỳ đánh |
|                 |     giá (UC-16/A2) hoặc khi map cột Excel (UC-53). |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Trùng mã kỹ năng***                      |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu mã (code) đã tồn tại, hệ thống chặn lưu và |
| Flow)**         |     báo lỗi trùng mã.                              |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Danh mục kỹ năng được cập nhật, sẵn sàng dùng  |
| (P              |     cho UC-16/A2 và UC-53.                         |
| ostcondition)** |                                                    |
+-----------------+----------------------------------------------------+

Phân hệ 7 --- Cổng thông tin và E-Learning (Portal & LMS)
