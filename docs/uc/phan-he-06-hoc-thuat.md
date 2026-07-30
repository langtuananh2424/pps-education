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
|                 | thay), phòng học (tùy chọn), và tùy chọn loại giáo |
|                 | viên (VIETNAMESE/FOREIGN — chỉ để hiển thị cho Học |
|                 | sinh/Phụ huynh biết buổi này GV Việt Nam hay nước  |
|                 | ngoài dạy, KHÔNG liên quan hồ sơ nhân sự — bổ sung |
|                 | ngoài SDD gốc, đã xác nhận với người dùng          |
|                 | 2026-07-29).                                       |
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
|                 |                                                    |
|                 | ***A4 --- Tạo buổi bù cho 1 buổi đã hủy (bổ sung   |
|                 | ngoài SDD gốc, đã xác nhận với người dùng          |
|                 | 2026-07-29)***                                      |
|                 |                                                    |
|                 | 1. Người dùng chọn loại buổi MAKEUP ở bước 1 Main  |
|                 | Flow, BẮT BUỘC chỉ định buổi đã hủy (đang           |
|                 | CANCELLED) mà buổi này bù cho. Hệ thống liên kết    |
|                 | 1-1 (1 buổi hủy chỉ được đúng 1 buổi bù — từ chối   |
|                 | nếu buổi hủy đã có buổi bù khác, hoặc buổi tham     |
|                 | chiếu không phải CANCELLED/khác lớp). Dời lịch 1    |
|                 | buổi bù (A3) giữ nguyên liên kết sang buổi mới.     |
|                 | GET /api/classes/{classId}/sessions/cancelled-      |
|                 | pending-makeup trả danh sách buổi hủy chưa có buổi  |
|                 | bù, phục vụ màn hình chọn buổi khi tạo buổi bù.     |
|                 | Chỉ áp dụng tạo 1 buổi lẻ (UC-48) — không áp dụng   |
|                 | sinh lịch hàng loạt (UC-56) hay nhập Excel (UC-57). |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | - class_sessions/session_periods phản ánh đúng     |
| (P              | lịch học hiện hành của lớp, là dữ liệu tham chiếu  |
| ostcondition)** | cho UC-15 (điểm danh) và ràng buộc trùng phòng của |
|                 | UC-37.                                             |
|                 |                                                    |
|                 | - class_sessions_history/session_periods_history   |
|                 | lưu đầy đủ lịch sử tạo/hủy/dời lịch.               |
|                 |                                                    |
|                 | - Mọi response buổi học (ClassSessionResponse) trả |
|                 | kèm sessionNumber — số thứ tự buổi trong lớp       |
|                 | (1-based, đếm theo session_date rồi id, TÍNH ĐỘNG   |
|                 | không lưu cột DB, đếm cả buổi CANCELLED — bổ sung  |
|                 | ngoài SDD gốc, đã xác nhận với người dùng          |
|                 | 2026-07-29), phục vụ FE hiển thị "Buổi N + ngày".  |
|                 |                                                    |
|                 | - Buổi MAKEUP (A4) trả kèm makeupForSessionId — id |
|                 | buổi CANCELLED nó bù cho (null nếu không phải      |
|                 | MAKEUP) — bổ sung ngoài SDD gốc, đã xác nhận với   |
|                 | người dùng 2026-07-29.                             |
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
|                 |     học (tùy chọn), và tùy chọn loại giáo viên      |
|                 |     (VIETNAMESE/FOREIGN — dùng chung cho cả lô;    |
|                 |     muốn 1 tuần có ngày GV Việt Nam, ngày GV nước  |
|                 |     ngoài thì gọi UC-56 riêng cho từng nhóm thứ,   |
|                 |     VD Thứ 2 gọi 1 lần với FOREIGN, Thứ 5 gọi 1    |
|                 |     lần khác với VIETNAMESE — bổ sung ngoài SDD    |
|                 |     gốc, đã xác nhận với người dùng 2026-07-29).   |
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

UC-59: Xem lịch học của tôi (Học sinh)

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-59                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xem lịch học của tôi (Học sinh)                    |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-05                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Học sinh                                           |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Học sinh tự xem lịch học theo tuần của (các) lớp   |
| tắt**           | mình đang ghi danh — giờ giấc từng buổi, đối xứng  |
|                 | với UC-58 ("Lịch của tôi" của Giáo viên), khác ở   |
|                 | chỗ lọc theo lớp học sinh ghi danh thay vì lớp     |
|                 | giáo viên phụ trách. Cũng gián tiếp phục vụ Phụ    |
|                 | huynh kiểm soát giờ giấc học của con (bổ sung      |
|                 | ngoài SDD gốc, đã xác nhận với người dùng).        |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Học sinh cần xem lịch học sắp tới/đã qua của chính |
| hoạt**          | mình để chủ động sắp xếp thời gian.                |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Học sinh đã đăng nhập (không cần quyền đặc     |
| tiên quyết      |     biệt nào khác).                                |
| (               |                                                    |
| Precondition)** | -   Đã có ít nhất 1 class_enrollment ACTIVE        |
|                 |     (UC-18).                                       |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Học sinh mở trang "Lịch học của tôi", có thể   |
| chính (Main     |     chọn khoảng ngày (từ ngày, đến ngày) để lọc,   |
| Flow)**         |     để trống = xem toàn bộ; có thể chọn lọc theo 1 |
|                 |     lớp cụ thể nếu đang học nhiều lớp (ngữ cảnh    |
|                 |     "lớp đang xem" — UC-42).                       |
|                 |                                                    |
|                 | 2.  Hệ thống trả về mọi buổi học (class_sessions)  |
|                 |     thuộc (các) lớp học sinh đang ghi danh ACTIVE, |
|                 |     khớp khoảng ngày/lớp đã chọn, sắp xếp theo     |
|                 |     ngày/giờ tăng dần.                             |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Học sinh thấy đúng và đủ các buổi học của      |
| (P              |     (các) lớp mình đang ghi danh (không thấy buổi  |
| ostcondition)** |     của lớp khác), không phụ thuộc site_teachers   |
|                 |     hay hồ sơ Parent (khác 2 endpoint sẵn có — GET |
|                 |     /classes/{id}/sessions và Portal Phụ huynh —   |
|                 |     vốn không dùng được cho actor Học sinh).       |
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
|                 | người dùng). Từ trạng thái điểm ở mức Nháp, Giáo   |
|                 | viên còn toàn quyền sửa/xoá không giới hạn thời    |
|                 | gian; sau khi công bố dự kiến (UC-20) chỉ sửa lại  |
|                 | được qua luồng phúc khảo (UC-62) — V43, bổ sung    |
|                 | ngoài SDD gốc, đã xác nhận với người dùng, thay    |
|                 | thế hẳn cơ chế "hạn X ngày toàn quyền sửa" của V39. |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Đến kỳ nhập điểm cho lớp học phụ trách; hoặc cần   |
| hoạt**          | sửa/xoá lại điểm còn Nháp; hoặc đã tiếp nhận 1 yêu |
|                 | cầu phúc khảo (UC-62) cho học sinh cụ thể.         |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Giáo viên được phân công giảng dạy lớp cần     |
| tiên quyết      |     nhập điểm (UC-18); HOẶC Trưởng phòng đào tạo   |
| (               |     (quyền academic.grade.manage); HOẶC Quản lý    |
| Precondition)** |     điểm trường phụ trách đúng điểm trường của lớp |
|                 |     — được phép nhập thay giáo viên khi cần hỗ trợ |
|                 |     (bổ sung ngoài SDD gốc, đã xác nhận với người  |
|                 |     dùng).                                         |
|                 |                                                    |
|                 | -   Để sửa/xoá 1 bản ghi đã tồn tại (V43 — bổ sung |
|                 |     ngoài SDD gốc, đã xác nhận với người dùng, sửa |
|                 |     đổi lần 2 sau V39): bản ghi phải đang ở trạng  |
|                 |     thái Nháp (DRAFT, không giới hạn thời gian),   |
|                 |     HOẶC đang Phúc khảo (APPEAL) và actor chính là |
|                 |     giáo viên đã tiếp nhận (UC-62) đúng yêu cầu    |
|                 |     phúc khảo của bản ghi đó — HOẶC actor có quyền |
|                 |     academic.grade.edit.override (ngoại lệ, gán    |
|                 |     được cho bất kỳ ai qua UC-04 — mặc định gán    |
|                 |     sẵn cho HEAD_ACADEMIC và SITE_MANAGER — bỏ qua |
|                 |     mọi ràng buộc trạng thái ở trên).              |
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
|                 |     viên có thể nhập/sửa/xoá rải rác nhiều lần,    |
|                 |     không giới hạn thời gian (V43).                |
|                 |                                                    |
|                 | 4.  Nếu đây là lần đầu tiên (lớp, kỳ đánh giá) này |
|                 |     có điểm được nhập, hệ thống ghi nhận mốc “lần  |
|                 |     đầu nhập” (grade_period_edit_windows) — làm    |
|                 |     gốc tính hạn X ngày tự động công bố dự kiến    |
|                 |     nếu không ai công bố tay (UC-20 A3; X ngày KHÔNG |
|                 |     còn là hạn chỉnh sửa như V39 — V43).           |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Điểm nhập không hợp lệ***                |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu score ngoài khoảng [0, max_score], hệ      |
| Flow)**         |     thống chặn lưu ngay tại phía Giáo viên, không  |
|                 |     để lọt xuống database.                         |
|                 |                                                    |
|                 | ***A2 --- Sửa/xoá bản ghi không ở trạng thái cho   |
|                 | phép (V43, bổ sung ngoài SDD gốc, đã xác nhận với  |
|                 | người dùng — thay hẳn A2 cũ "hết hạn X ngày")***   |
|                 |                                                    |
|                 | 1.  Nếu actor không có quyền                       |
|                 |     academic.grade.edit.override và bản ghi đang   |
|                 |     Công bố dự kiến (PROVISIONAL_PUBLISHED) hoặc   |
|                 |     Chính thức (OFFICIAL), hệ thống chặn sửa/xoá.  |
|                 |                                                    |
|                 | 2.  Nếu bản ghi đang Phúc khảo (APPEAL) nhưng actor |
|                 |     không phải giáo viên đã tiếp nhận đúng yêu cầu |
|                 |     phúc khảo đó (UC-62), hệ thống chặn sửa/xoá.   |
|                 |                                                    |
|                 | 3.  Sửa xong 1 bản ghi đang Phúc khảo (đã tiếp     |
|                 |     nhận): hệ thống tự động chuyển bản ghi về Công |
|                 |     bố dự kiến (PROVISIONAL_PUBLISHED) và đóng yêu |
|                 |     cầu phúc khảo (RESOLVED) — không cần thao tác  |
|                 |     "hoàn tất phúc khảo" riêng (UC-62).            |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bản ghi điểm được lưu ngay — DRAFT nếu là lần  |
| (P              |     nhập đầu tiên; giữ nguyên trạng thái Nháp nếu  |
| ostcondition)** |     sửa/xoá 1 bản ghi Nháp; hoặc quay về Công bố   |
|                 |     dự kiến (đóng yêu cầu phúc khảo) nếu sửa xong  |
|                 |     1 bản ghi đang Phúc khảo (V43).                |
|                 |                                                    |
|                 | -   Điểm tổng kết/Overall theo kỳ đánh giá         |
|                 |     (grade_period_results) là 1 thao tác nhập liệu |
|                 |     riêng của Giáo viên (UC-53) — hệ thống không   |
|                 |     tự tính lại từ điểm thành phần vừa nhập ở đây  |
|                 |     (V40 — bổ sung ngoài SDD gốc, đã xác nhận với  |
|                 |     người dùng).                                   |
|                 |                                                    |
|                 | -   Bản ghi đã Công bố dự kiến/Chính thức KHÔNG    |
|                 |     sửa/xoá trực tiếp được nữa — muốn sửa phải qua |
|                 |     luồng phúc khảo (UC-62), khác V39 (trước đây   |
|                 |     sửa trực tiếp được trong hạn X ngày).          |
+-----------------+----------------------------------------------------+

> **Bổ sung ngoài đặc tả gốc — đã xác nhận với người dùng (2026-07-22).**
> Cấu hình sổ điểm (kỳ đánh giá / thành phần điểm) — sửa & xoá + phân rã
> quyền (thực thi ở backend):
>
> - **Sửa:** đã có `PUT /api/grade-periods/{id}` và `PUT /api/grade-components/{id}`
>   (sửa tên/trọng số/thứ tự…; riêng `maxScore` của thành phần điểm chỉ đổi
>   được khi CHƯA có điểm nhập — `GradeComponentLockedException`).
> - **Xoá (mới):** `DELETE /api/grade-periods/{id}` và
>   `DELETE /api/grade-components/{id}`. Quy tắc an toàn: chỉ xoá thành phần
>   điểm khi **chưa có điểm nhập** nào; chỉ xoá kỳ đánh giá khi **rỗng** —
>   không còn thành phần điểm, không có điểm tổng kết, và chưa bắt đầu nhập
>   điểm ở lớp nào. Vi phạm → `GradeComponentNotDeletableException` /
>   `GradePeriodNotDeletableException` (HTTP 422).
> - **Phân rã quyền (migration V46):** `academic.grade.manage` (Cấu hình sổ
>   điểm) được tách thành `academic.grade.period.create|update|delete` và
>   `academic.grade.component.create|update|delete` để gán riêng lẻ. GIỮ
>   `academic.grade.manage` làm quyền tổng (mọi endpoint cấu hình chấp nhận
>   "quyền chi tiết HOẶC academic.grade.manage") — không phá quyền/role hiện
>   có (HEAD_ACADEMIC).

---

UC-20: Công bố điểm

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-20                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Công bố điểm dự kiến                               |
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
| tắt**           | định thời điểm công bố điểm DỰ KIẾN cho Phụ         |
|                 | huynh/Học sinh xem qua Portal — không còn kiểm     |
|                 | duyệt đúng/sai như trước, chỉ là mốc công khai dữ  |
|                 | liệu (V39 — bổ sung ngoài SDD gốc, đã xác nhận với |
|                 | người dùng, thay thế hoàn toàn khái niệm “Duyệt    |
|                 | điểm” và luồng Approved/Rejected cũ). Gọi là "dự    |
|                 | kiến" vì sau khi công bố còn mở cửa sổ Y ngày cho   |
|                 | Học sinh/Phụ huynh gửi phúc khảo (UC-62) trước khi  |
|                 | tự động chuyển Chính thức (V43 — bổ sung ngoài SDD |
|                 | gốc, đã xác nhận với người dùng, sửa đổi lần 2 sau |
|                 | V39). Nếu không ai công bố thủ công, hệ thống tự   |
|                 | động công bố dự kiến khi hết hạn X ngày kể từ lần  |
|                 | đầu nhập điểm (UC-19) — 2 cơ chế chạy song song,   |
|                 | không cái nào thay thế cái nào.                    |
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
|                 | 3.  Quản lý điểm trường xác nhận công bố dự kiến — |
|                 |     không còn quyết định Đúng/Sai như trước, chỉ   |
|                 |     có 1 hành động duy nhất: Công bố dự kiến.      |
|                 |                                                    |
|                 | 4.  Hệ thống chuyển các bản ghi đã chọn từ DRAFT   |
|                 |     sang PROVISIONAL_PUBLISHED, ghi nhận người và  |
|                 |     thời điểm công bố (mốc bắt đầu tính hạn Y ngày |
|                 |     phúc khảo — UC-62), công khai điểm cho Phụ     |
|                 |     huynh xem ngay qua Portal (UC-25), đồng thời   |
|                 |     gửi thông báo (notification) cho từng Phụ      |
|                 |     huynh liên kết với học sinh có điểm vừa công   |
|                 |     bố (bổ sung ngoài SDD gốc, đã xác nhận với     |
|                 |     người dùng).                                   |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Công bố tách lẻ 1 bản ghi trong lô***    |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Quản lý điểm trường có thể mở riêng 1 bản ghi  |
| Flow)**         |     trong 1 lô để công bố độc lập với phần còn lại |
|                 |     — không bắt buộc công bố cả lô cùng lúc.       |
|                 |                                                    |
|                 | ***A2 --- Công bố lại 1 bản ghi không còn DRAFT    |
|                 | (bổ sung ngoài SDD gốc, đã xác nhận với người      |
|                 | dùng)***                                           |
|                 |                                                    |
|                 | 1.  Nếu bản ghi được chọn không còn ở trạng thái   |
|                 |     DRAFT (đã PROVISIONAL_PUBLISHED, đang APPEAL,  |
|                 |     hoặc đã OFFICIAL), hệ thống báo lỗi (đã công   |
|                 |     bố dự kiến trước đó), không cho công bố lại    |
|                 |     (V43 — trước đây V39 chỉ chặn khi PUBLISHED).  |
|                 |                                                    |
|                 | ***A3 --- Tự động công bố dự kiến khi hết hạn X    |
|                 | ngày, không ai công bố tay (bổ sung ngoài SDD gốc, |
|                 | đã xác nhận với người dùng)***                     |
|                 |                                                    |
|                 | 1.  Mỗi đêm, hệ thống quét mọi (lớp, kỳ đánh giá)  |
|                 |     đã hết hạn X ngày kể từ lần đầu nhập điểm      |
|                 |     (UC-19, grade_period_edit_windows) — dùng đúng |
|                 |     1 giá trị X ngày cấu hình chung với độ trễ tự  |
|                 |     động công bố dự kiến                            |
|                 |     (system_settings.academic.grade_edit_window_days). |
|                 |                                                    |
|                 | 2.  Mọi grade_entries/grade_period_results còn     |
|                 |     DRAFT thuộc (lớp, kỳ đánh giá) đó được tự động |
|                 |     chuyển sang PROVISIONAL_PUBLISHED — không cần  |
|                 |     Quản lý điểm trường xác nhận. Bản ghi đã công  |
|                 |     bố dự kiến thủ công từ trước không bị ảnh      |
|                 |     hưởng.                                         |
|                 |                                                    |
|                 | 3.  published_by để trống (không gán người công    |
|                 |     bố) để phân biệt với công bố thủ công —        |
|                 |     published_at vẫn ghi nhận đúng thời điểm (mốc  |
|                 |     tính hạn Y ngày phúc khảo — UC-62); Phụ huynh  |
|                 |     vẫn nhận được thông báo như công bố thủ công,  |
|                 |     chỉ khác triggered_by để trống (hệ thống tự    |
|                 |     động, không có actor con người).               |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Trạng thái điểm được cập nhật chính xác (DRAFT |
| (P              |     → PROVISIONAL_PUBLISHED).                      |
| ostcondition)** |                                                    |
|                 | -   Điểm công bố dự kiến được công khai cho Phụ    |
|                 |     huynh ngay lập tức qua Portal (UC-25).         |
|                 |                                                    |
|                 | -   Bắt đầu tính hạn Y ngày phúc khảo kể từ         |
|                 |     publishedAt (UC-62) — Giáo viên KHÔNG còn tự   |
|                 |     sửa trực tiếp được bản ghi nữa (khác V39), chỉ |
|                 |     sửa được qua luồng phúc khảo nếu Học sinh/Phụ  |
|                 |     huynh gửi yêu cầu và Giáo viên tiếp nhận.       |
|                 |                                                    |
|                 | -   Nếu không ai công bố thủ công trong hạn X      |
|                 |     ngày, hệ thống tự động chuyển DRAFT →          |
|                 |     PROVISIONAL_PUBLISHED cho toàn bộ bản ghi còn  |
|                 |     lại của (lớp, kỳ đánh giá) đó ngay sau khi hết |
|                 |     hạn (A3) — Phụ huynh không phải chờ vô thời    |
|                 |     hạn nếu Quản lý điểm trường quên công bố.       |
|                 |                                                    |
|                 | -   Hết hạn Y ngày phúc khảo mà không có (hoặc đã  |
|                 |     xử lý xong) yêu cầu phúc khảo nào, hệ thống tự |
|                 |     động chuyển bản ghi sang Chính thức (OFFICIAL,  |
|                 |     UC-62 A3) — khoá vĩnh viễn, không sửa/xoá được  |
|                 |     nữa kể cả qua phúc khảo.                        |
|                 |                                                    |
|                 | -   Phụ huynh nhận được thông báo (notification)   |
|                 |     ngay khi điểm/Overall-Level được công bố dự    |
|                 |     kiến — cả công bố thủ công lẫn tự động (A3),   |
|                 |     không cần tự vào Portal kiểm tra (bổ sung ngoài |
|                 |     SDD gốc, đã xác nhận với người dùng).          |
+-----------------+----------------------------------------------------+

---

UC-62: Phúc khảo điểm (bổ sung ngoài SDD gốc, đã xác nhận với người dùng)

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-62                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Phúc khảo điểm                                     |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-03 (V43, bổ sung ngoài SDD gốc, đã xác nhận |
| năng gốc**      | với người dùng)                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Học sinh; Phụ huynh (gửi yêu cầu phúc khảo)         |
|                 |                                                    |
|                 | Giáo viên (tiếp nhận yêu cầu, sửa điểm)             |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Sau khi điểm được công bố dự kiến (UC-20), Học     |
| tắt**           | sinh hoặc Phụ huynh liên kết có thể gửi yêu cầu     |
|                 | phúc khảo trong hạn Y ngày. Bản ghi điểm chuyển     |
|                 | sang trạng thái Phúc khảo (APPEAL); Giáo viên phụ   |
|                 | trách lớp nhận thông báo, tiếp nhận yêu cầu rồi     |
|                 | mới được sửa điểm của đúng học sinh đó. Sửa xong,   |
|                 | bản ghi tự động quay lại Công bố dự kiến. Hết hạn Y |
|                 | ngày (dù còn yêu cầu phúc khảo dở dang hay không),  |
|                 | hệ thống tự động khoá bản ghi sang Chính thức       |
|                 | (OFFICIAL).                                        |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Học sinh/Phụ huynh không đồng ý với điểm đã công    |
| hoạt**          | bố dự kiến, còn trong hạn Y ngày kể từ lúc công bố. |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Bản ghi điểm (grade_entries hoặc               |
| tiên quyết      |     grade_period_results) đang ở trạng thái Công   |
| (               |     bố dự kiến (PROVISIONAL_PUBLISHED) — chưa hết  |
| Precondition)** |     hạn Y ngày (system_settings.academic.          |
|                 |     grade_appeal_window_days, mặc định 7 ngày) và  |
|                 |     chưa có yêu cầu phúc khảo nào khác đang mở.    |
|                 |                                                    |
|                 | -   Actor gửi yêu cầu phải là chính học sinh sở     |
|                 |     hữu bản ghi, hoặc Phụ huynh liên kết            |
|                 |     (parent_student) với học sinh đó.              |
|                 |                                                    |
|                 | -   Actor tiếp nhận/sửa điểm phải là Giáo viên được |
|                 |     phân công giảng dạy đúng lớp của bản ghi (bất   |
|                 |     kỳ giáo viên nào của lớp, không riêng người đã |
|                 |     nhập điểm ban đầu).                             |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Học sinh hoặc Phụ huynh mở bảng điểm đã công   |
| chính (Main     |     bố dự kiến, chọn 1 bản ghi và gửi yêu cầu phúc |
| Flow)**         |     khảo (kèm lý do, tuỳ chọn).                    |
|                 |                                                    |
|                 | 2.  Hệ thống tạo yêu cầu phúc khảo (PENDING), đổi   |
|                 |     trạng thái bản ghi điểm sang Phúc khảo (APPEAL) |
|                 |     ngay lập tức, gửi thông báo cho TẤT CẢ giáo     |
|                 |     viên phụ trách lớp.                             |
|                 |                                                    |
|                 | 3.  1 Giáo viên phụ trách lớp tiếp nhận yêu cầu —   |
|                 |     yêu cầu chuyển ACCEPTED, ghi nhận người và thời |
|                 |     điểm tiếp nhận.                                |
|                 |                                                    |
|                 | 4.  Giáo viên đã tiếp nhận sửa lại điểm của đúng    |
|                 |     học sinh đó (UC-19, enterGrade) — đây là        |
|                 |     trường hợp duy nhất Giáo viên sửa được bản ghi  |
|                 |     không còn ở trạng thái Nháp.                    |
|                 |                                                    |
|                 | 5.  Sửa xong, hệ thống tự động: đóng yêu cầu phúc   |
|                 |     khảo (RESOLVED) và chuyển bản ghi điểm về Công  |
|                 |     bố dự kiến (PROVISIONAL_PUBLISHED) — publishedAt |
|                 |     GIỮ NGUYÊN mốc gốc, hạn Y ngày KHÔNG bị gia hạn |
|                 |     lại. Không cần thao tác "hoàn tất phúc khảo"    |
|                 |     riêng.                                          |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Gửi phúc khảo khi không đủ điều kiện***  |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu bản ghi còn Nháp (chưa công bố), hệ thống  |
| Flow)**         |     báo không tìm thấy (chưa công khai).           |
|                 |                                                    |
|                 | 2.  Nếu bản ghi đang có 1 yêu cầu phúc khảo khác    |
|                 |     chưa xử lý xong (PENDING/ACCEPTED), hệ thống    |
|                 |     báo lỗi, không cho gửi thêm.                   |
|                 |                                                    |
|                 | 3.  Nếu bản ghi đã Chính thức (OFFICIAL — hết hạn Y |
|                 |     ngày), hệ thống báo đã hết hạn phúc khảo.       |
|                 |                                                    |
|                 | ***A2 --- Giáo viên khác cố tiếp nhận/sửa***       |
|                 |                                                    |
|                 | 1.  Nếu yêu cầu phúc khảo đã được 1 Giáo viên khác  |
|                 |     tiếp nhận (hoặc đã RESOLVED), hệ thống chặn     |
|                 |     tiếp nhận lại.                                 |
|                 |                                                    |
|                 | 2.  Nếu 1 Giáo viên khác (chưa tiếp nhận yêu cầu    |
|                 |     này) cố sửa điểm của bản ghi đang Phúc khảo, hệ |
|                 |     thống chặn sửa (UC-19 A2) — chỉ Giáo viên đã    |
|                 |     tiếp nhận đúng yêu cầu đó mới sửa được.         |
|                 |                                                    |
|                 | ***A3 --- Hết hạn Y ngày, kể cả đang phúc khảo dở   |
|                 | dang***                                            |
|                 |                                                    |
|                 | 1.  Mỗi đêm, hệ thống quét mọi bản ghi còn Công bố  |
|                 |     dự kiến hoặc đang Phúc khảo mà đã quá hạn Y     |
|                 |     ngày kể từ publishedAt.                        |
|                 |                                                    |
|                 | 2.  Chuyển các bản ghi đó sang Chính thức (OFFICIAL, |
|                 |     ghi nhận finalizedAt) — kể cả bản ghi đang Phúc |
|                 |     khảo dở dang (Giáo viên đã tiếp nhận nhưng chưa |
|                 |     sửa xong): khoá lại luôn, không chờ xử lý xong  |
|                 |     (đã xác nhận với người dùng — chỉ Trưởng phòng  |
|                 |     đào tạo/người có academic.grade.edit.override   |
|                 |     mới sửa tiếp được sau mốc này).                |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Yêu cầu phúc khảo được ghi nhận đầy đủ vòng đời |
| (P              |     PENDING → ACCEPTED → RESOLVED (hoặc dừng ở      |
| ostcondition)** |     PENDING/ACCEPTED nếu hết hạn Y ngày trước khi   |
|                 |     xử lý xong).                                    |
|                 |                                                    |
|                 | -   Trạng thái bản ghi điểm phản ánh đúng vòng đời: |
|                 |     PROVISIONAL_PUBLISHED → APPEAL → (sửa xong)     |
|                 |     PROVISIONAL_PUBLISHED, hoặc → OFFICIAL (hết hạn |
|                 |     Y ngày).                                        |
|                 |                                                    |
|                 | -   Giáo viên phụ trách lớp nhận được thông báo     |
|                 |     (notification, loại GRADE_APPEAL_REQUESTED)     |
|                 |     ngay khi có yêu cầu phúc khảo mới.              |
|                 |                                                    |
|                 | -   Bản ghi Chính thức (OFFICIAL) không sửa/xoá     |
|                 |     được nữa dưới bất kỳ hình thức nào (trừ actor   |
|                 |     có academic.grade.edit.override).               |
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

Mở rộng --- Nhận xét Hàng ngày kiểu mới (bổ sung ngoài SDD gốc, đã xác
nhận với người dùng 2026-07-24, kết luận họp — CHỈ áp dụng comment_type=
DAILY, Giữa/Cuối kỳ giữ nguyên 100% luồng ở trên)

-   Luồng thao tác: Giáo viên điểm danh buổi học (UC-15) → nhận xét từng
    học sinh của buổi đó. Học sinh Vắng/Có phép thì không cần điền các
    trường nhận xét.
-   **Sửa lại 2026-07-29 (đã xác nhận với người dùng) — "bài học hôm nay"
    chuyển từ Điểm danh sang Nhận xét:** `class_sessions.lesson_content`
    (TEXT, dùng chung cả lớp, không đổi) nay điền qua
    `PUT /api/class-sessions/{classSessionId}/comments/lesson-content`
    (rào giống ghi nhận xét — `requireCanWriteDailyComment`), KHÔNG còn
    endpoint ở Điểm danh (`PUT .../attendance/lesson-content` đã bỏ). Trên
    UI hiển thị 1 field chung theo lớp (không phải cột trong bảng), phục
    vụ hiển thị xuống GV/Phụ huynh (`ClassSessionResponse.lessonContent`)
    và Quản lý điểm trường lúc duyệt (`StudentCommentResponse.lessonContent`).
    Excel: cột "Bài học hôm nay" nằm NGAY TRƯỚC cột Điểm danh trong file
    mẫu (không bắt buộc điền) — điền sẵn từ giá trị đã nhập ở UI (nếu có);
    khi import, mọi dòng có điền phải khớp giá trị nhau (học sinh trong
    lớp học cùng 1 bài) — khác nhau thì CHẶN TOÀN BỘ file, không import
    dòng nào; để trống hết thì giữ nguyên giá trị hiện có (không xóa nhầm
    giá trị đã điền qua UI). Gửi duyệt (`submitComments`) 1 nhận xét DAILY
    mà buổi chưa có `lesson_content` thì bị từ chối (422,
    "chưa điền bài học hôm nay").
-   4 cột mới trên `student_comments` (chỉ có ý nghĩa khi comment_type=
    DAILY): `attitude` (VARCHAR(20), enum Kém/Yếu/Trung bình/Trung bình
    khá/Khá/Tốt — mở rộng từ 3 lên 6 mức 2026-07-27),
    `homework_previous_score` (VARCHAR(10), VD "80%" —
    chấm BTVN buổi TRƯỚC ngay trong dòng của buổi này, nay chỉ còn dùng
    khi kênh ngữ pháp OFFLINE — xem bổ sung V55 dưới), `homework_next`
    (TEXT, VD "Unit 4 Trang 18" — giao BTVN ngữ pháp OFFLINE cho buổi
    SAU, hạn nộp ngầm hiểu là ngày buổi học kế tiếp, không lưu cột
    deadline riêng), `note` (TEXT).
-   **Bổ sung V55 (2026-07-28, đã xác nhận với người dùng) — giao BTVN
    linh hoạt ONLINE/OFFLINE theo TỪNG học sinh:** thêm 2 cột FK
    `homework_next_exercise_assignment_id` (→ `exercise_assignments`,
    kênh ngữ pháp ONLINE — tái dùng thẳng UC-40's `assignExercise()`,
    không dựng kho bài tập riêng) và `homework_next_review_video_set_id`
    (→ `review_video_sets`, kênh Video Ôn tập — LUÔN online, không có
    khái niệm offline cho kênh này). Cả 2 đều lưu TRÊN TỪNG DÒNG học
    sinh (không theo cả lớp) — NULL = kênh đó đang OFFLINE hoặc không
    giao gì cho đúng học sinh này, cho phép có học sinh không cần giao
    bài. Dòng của buổi N lưu "sẽ giao gì cho buổi N+1"; % hoàn thành của
    buổi N+1 tự tính NGƯỢC lại từ FK trên dòng buổi N (không nhập tay,
    không lưu trùng lặp) — ngữ pháp qua `exercise_attempts.total_score/
    exercise.total_points`, video qua `review_video_progress.watched_
    seconds` (CONNECTION) hoặc `review_video_submissions.score/max_score`
    (REFLEX, UC-23b).
-   **Bổ sung V65 (2026-07-30, đã xác nhận với người dùng) — điểm giao
    bài "Ngữ pháp Online"/"Video Ôn tập" chuyển hẳn từ Soạn & Giao đề
    (UC-40)/Kho Video Ôn tập (UC-23) sang ĐÂY, đảo ngược thiết kế "theo
    từng học sinh, không theo cả lớp" của V55 ngay trên:** GV chọn 1
    `Exercise` (kênh ngữ pháp, field request đổi tên
    `homework_next_exercise_id` — trỏ THẲNG `exercises.id`, KHÔNG còn trỏ
    `exercise_assignments.id` như V55) hoặc 1 `ReviewVideoSet` (kênh
    Video, `homework_next_review_video_set_id`, ý nghĩa không đổi) làm
    "BTVN buổi sau" cho BẤT KỲ 1 học sinh nào trong 1 buổi DAILY → hệ
    thống TỰ ĐỘNG tạo bản giao (`ExerciseAssignment`/`ReviewVideoAssignment`
    mới — bảng `review_video_assignments` mới, mirror
    `exercise_assignments`) cho TOÀN BỘ học sinh ACTIVE của lớp
    (`target_student_ids = NULL`), hạn nộp = ngày/giờ buổi học KẾ TIẾP
    của lớp (tính từ `session_date` của buổi đang nhận xét, không phải
    "hôm nay" — GV nhập bù buổi cũ vẫn tính đúng). Cột lưu trên
    `student_comments` đổi tên tương ứng:
    `homework_next_review_video_set_id` →
    `homework_next_review_video_assignment_id` (đối xứng với
    `homework_next_exercise_assignment_id` sẵn có — cả 2 đều trỏ BẢN
    GIAO, không trỏ nguồn). Hệ quả trực tiếp: "Soạn & Giao đề" (UC-40) bỏ
    hẳn bước "Giao bài tập" (không còn `classId`/`dueAt`/`targetStudentIds`
    ở màn đó — xem UC-40 phân hệ 7); "Kho Video Ôn tập" (UC-23) publish
    "Video phản xạ" không còn tự động hiển thị cho học sinh (xem UC-23
    phân hệ 7) — cả 2 nơi Publish giờ CHỈ có nghĩa "đủ điều kiện dùng làm
    nguồn" (hiện trong dropdown ở đây), không còn tác dụng giao bài. 5
    quy tắc đã chốt cùng đợt:
    1.  **Xung đột cùng buổi**: mọi dòng DAILY cùng 1 `class_session`
        phải chọn CÙNG 1 đề/video cho mỗi kênh (độc lập nhau) — dòng đầu
        tiên khóa lựa chọn, dòng khác chọn khác bị chặn 409
        (`HomeworkNextConflictException`).
    2.  **Sửa lựa chọn khi còn DRAFT**: hủy bản giao cũ
        (`status=CANCELLED`), tạo/gắn bản giao mới NGAY — kể cả khi học
        sinh đã mở bài dở; KHÔNG cascade sang các dòng comment khác cùng
        buổi (dòng đó phát hiện lệch và bị chặn 409 khi chính GV lưu lại
        dòng đó).
    3.  **Comment bị REJECTED (UC-22)**: KHÔNG ảnh hưởng bài đã giao —
        giao bài và duyệt nhận xét vẫn hoàn toàn tách biệt như trước.
    4.  **Lớp chưa có buổi kế tiếp**: chặn hẳn, báo lỗi rõ
        (`NoUpcomingClassSessionException`) — không cho chọn đề/video làm
        BTVN buổi sau.
    5.  **Chỉ áp dụng DAILY**: MID_TERM/END_TERM (gắn `gradePeriod`,
        không có "buổi kế tiếp") điền 1 trong 2 field này bị chặn ngay
        (`InvalidCommentContextException`) — phải để trống.

    Kênh Video áp dụng cho CẢ `CONNECTION` lẫn `REFLEX` (không chỉ REFLEX
    dù tên gọi "Video phản xạ" gợi ý — xem UC-23). Đề/video Publish nhưng
    chưa từng được chọn: không cần màn theo dõi riêng, chỉ dùng cho
    dropdown ở đây.

-   Excel round-trip theo buổi học (**V65: dropdown cột Ngữ pháp đổi
    nguồn từ "bài đã giao sẵn cho lớp" sang mọi `Exercise` loại ASSIGNED
    đang PUBLISHED trong khung chương trình của lớp — chọn ở đây mới là
    hành động giao; dropdown cột Video không đổi**): `GET
    /api/class-sessions/{classSessionId}/comments/template` tải file mẫu
    điền sẵn học sinh ACTIVE của lớp (Ngày/Mã học viên/Họ và tên/Điểm
    danh hiện có/nhận xét đã nhập trước đó nếu có) — mở rộng 9→13 cột từ
    V55: thêm 2 cột đọc-only "% Ngữ pháp/Video buổi trước (tự động)" và 2
    cột nhập liệu "Giao BTVN ngữ pháp ONLINE/Video ôn tập (buổi sau)" —
    dropdown ĐỘNG theo lớp (chỉ hiện bài/video đã gán cho đúng lớp đang
    xuất, named-range nếu danh sách dài vượt ~255 ký tự) CỘNG THÊM chấp
    nhận dán trực tiếp `uuid` làm phương án thay dropdown (không giới
    hạn theo lớp khi dán uuid); mở rộng tiếp 13→14 cột ở V56: thêm 1 cột
    nhập tay "BTVN Nghe-nói buổi trước" (`homework_previous_speaking_
    score`, độc lập với cột "BTVN buổi trước" cũ vốn chỉ dùng cho kênh
    Ngữ pháp — không phải dropdown, không tự tính, khác với 2 cột %
    đọc-only nói trên); điền xong gọi `POST
    /api/class-sessions/{classSessionId}/comments/import` — cột Điểm danh
    trong file CHO PHÉP sửa luôn điểm danh khi import lại (tái dùng
    nguyên StudentAttendanceService.markAttendance — rào "chỉ trong ngày
    diễn ra buổi học" của UC-15 không đổi, KHÁC hạn X ngày của nhận xét
    bên dưới). Lỗi 1 dòng không chặn dòng khác (đúng pattern UC-35/50/51/53).
-   **Bổ sung 2026-07-29 (đã xác nhận với người dùng) — tự chọn buổi hôm
    nay khi vào tab Nhận xét:** `GET /api/classes/{classId}/sessions/today`
    trả buổi học của lớp có `session_date` = hôm nay (loại
    CANCELLED/RESCHEDULED — không còn là buổi "đang diễn ra hôm nay"). FE
    gọi khi Giáo viên vào tab Nhận xét học viên: có buổi thì tự hiển thị
    nhận xét của buổi đó, danh sách rỗng thì báo "hôm nay không có buổi
    học" và để Giáo viên tự chọn buổi khác từ `GET
    /api/classes/{classId}/sessions`.
-   Hạn nhập/sửa: mặc định 7 ngày kể từ NGÀY BUỔI HỌC diễn ra
    (`system_settings.academic.comment_edit_window_days`, cấu hình qua
    `GET`/`PUT /api/academic/settings/comment-edit-window-days`).
-   Quy trình duyệt (**SỬA LẠI 2026-07-29, thay quyết định ngay dưới đây
    — đã dùng thực tế, phát hiện thiếu bước xem lại trước khi gửi
    duyệt**): DAILY dùng lại NGUYÊN luồng Nháp (DRAFT) → Gửi (submit,
    `POST /api/classes/{classId}/comments/submit` — đã có sẵn, dùng
    chung với Giữa/Cuối kỳ) → Chờ duyệt (PENDING) → Duyệt (UC-22) ở Main
    Flow UC-21 gốc phía trên — không còn khác biệt gì so với Giữa/Cuối
    kỳ ở khâu này. `writeComment`/`updateComment`/Excel import chỉ
    tạo/sửa ở trạng thái DRAFT, không tự động chuyển trạng thái nào nữa.
    Actor có `academic.comment.approve` KHÔNG còn được ghi/sửa trực tiếp
    ra APPROVED bỏ qua chờ duyệt — muốn Gửi phải qua đúng
    `submitComments()`, vốn luôn yêu cầu actor là Giáo viên được phân
    công lớp (không đổi) — nghĩa là Quản lý điểm trường không kiêm giáo
    viên lớp đó tự viết 1 nhận xét DAILY thì không tự Gửi được, phải nhờ
    đúng Giáo viên lớp Gửi hộ (đánh đổi đã xác nhận với người dùng, giữ
    luồng đơn giản/đồng nhất với Giữa/Cuối kỳ thay vì mở lại rào riêng
    cho DAILY). Excel import cùng logic: dòng ứng với nhận xét đang
    DRAFT/REJECTED thì sửa được (về lại DRAFT); dòng ứng với nhận xét đã
    PENDING/APPROVED thì báo lỗi riêng dòng đó (không chặn dòng khác,
    đúng pattern UC-35/50/51/53) — không cho Excel âm thầm ghi đè, bỏ qua
    quy trình duyệt. Riêng việc actor có `academic.comment.approve` bỏ
    qua hạn X ngày khi GHI/SỬA (bullet phía trên) — KHÔNG đổi, đây là
    quyền quản trị độc lập với chuyện route trạng thái.

    ~~Quyết định 2026-07-24 (đã thay thế)~~: Giáo viên (chỉ có
    `academic.comment.write`) ghi xong tự động chuyển Chờ duyệt (PENDING)
    ngay — không còn bước Nháp (DRAFT) rồi submit riêng cho biểu mẫu Hàng
    ngày, kể cả sửa lại sau khi bị từ chối (UC-21 A1). Actor có
    `academic.comment.approve` (Quản lý điểm trường/Quản trị viên —
    permission đã có sẵn từ V44, không tạo permission mới) ghi trực tiếp
    thì bỏ qua bước chờ duyệt (APPROVED ngay, hiển thị Phụ huynh luôn) VÀ
    bỏ qua luôn hạn X ngày ở trên (cùng 1 permission gánh cả 2 "quyền
    quản trị").

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

Mở rộng --- File mẫu (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
2026-07-24)

-   Trước bước 1, Giáo viên có thể gọi `GET
    /api/classes/{classId}/grade-periods/{gradePeriodId}/grades/import-template`
    để tải file Excel mẫu điền sẵn: cột A = mã học viên (giữ nguyên vị trí
    để import lại đúng bước 1), cột B/C = họ tên/lớp (chỉ để đọc, hệ thống
    tự bỏ qua khi so khớp header ở bước 2 — không tính là "cột không khớp"
    của A1), các cột sau đúng tên từng thành phần điểm đã cấu hình cho kỳ
    đánh giá + Overall/Level — 1 dòng cho mỗi học sinh đang ghi danh
    (ACTIVE) của lớp, cột điểm để trống sẵn sàng nhập. Cùng điều kiện tiên
    quyết với bước tải lên (Giáo viên được phân công/Trưởng phòng đào
    tạo/Quản lý điểm trường phụ trách site).

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
