# phan-he-09-crm

UC-33: Quản lý lead & tư vấn tuyển sinh

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-33                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Quản lý lead & tư vấn tuyển sinh                   |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 9                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-CRM-01, FR-CRM-02                               |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Nhân viên (Tư vấn tuyển sinh/CSKH)                 |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Quản lý điểm trường)            |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Thu thập dữ liệu khách hàng tiềm năng đa kênh,     |
| tắt**           | phân phối cho nhân viên tư vấn, lưu vết lịch sử tư |
|                 | vấn.                                               |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Có lead mới phát sinh từ Website/Fanpage/chiến     |
| hoạt**          | dịch Marketing, hoặc Nhân viên cần cập nhật lịch   |
|                 | sử tư vấn cho lead hiện có.                        |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Kênh thu thập dữ liệu                          |
| tiên quyết      |     (Website/Fanpage/Marketing) đã được cấu hình   |
| (               |     tích hợp.                                      |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Hệ thống tự động thu thập thông tin Phụ        |
| chính (Main     |     huynh/Học sinh tiềm năng từ các kênh, tạo bản  |
| Flow)**         |     ghi lead mới với trạng thái NEW; hệ thống kiểm |
|                 |     tra trùng số điện thoại với lead active khác   |
|                 |     (ràng buộc UNIQUE(phone) ở tầng DB) trước khi  |
|                 |     tạo.                                           |
|                 |                                                    |
|                 | 2.  Hệ thống/Quản lý điểm trường phân phối lead    |
|                 |     cho Nhân viên tư vấn phụ trách.                |
|                 |                                                    |
|                 | 3.  Nhân viên tư vấn liên hệ khách hàng, ghi lại   |
|                 |     lịch sử cuộc gọi, nội dung trao đổi, nhu cầu   |
|                 |     học tập.                                       |
|                 |                                                    |
|                 | 4.  Nếu chưa liên hệ được, Nhân viên đặt lịch nhắc |
|                 |     gọi lại; trạng thái lead giữ nguyên cho tới    |
|                 |     khi liên hệ thành công (vòng lặp).             |
|                 |                                                    |
|                 | 5.  Khi liên hệ thành công, Nhân viên chuyển trạng |
|                 |     thái NEW/CONTACTED → QUALIFIED (nếu khách phù  |
|                 |     hợp) hoặc → LOST (nếu không phù hợp).          |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Số điện thoại trùng lead active khác***  |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống từ chối tạo lead mới, thông báo trùng |
| Flow)**         |     lặp và gợi ý cập nhật lead hiện có thay vì tạo |
|                 |     mới.                                           |
|                 |                                                    |
|                 | ***A2 --- Khách không phù hợp ngay từ đầu***       |
|                 |                                                    |
|                 | 1.  Nhân viên đánh dấu lead LOST kèm lý do để phân |
|                 |     tích sau, không cần qua bước QUALIFIED.        |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Lead được cập nhật đúng trạng thái và có đầy   |
| (P              |     đủ lịch sử tư vấn.                             |
| ostcondition)** |                                                    |
|                 | -   Lead ở trạng thái QUALIFIED sẵn sàng cho bước  |
|                 |     chốt đăng ký (UC-34).                          |
+-----------------+----------------------------------------------------+

---

UC-34: Chuyển đổi lead thành học sinh

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-34                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Chuyển đổi lead thành học sinh                     |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 9                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-CRM-03                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Nhân viên (Tư vấn tuyển sinh)                      |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Khi khách hàng đồng ý nhập học và đóng phí lần     |
| tắt**           | đầu, hệ thống chuyển thẳng toàn bộ thông tin sang  |
|                 | Phân hệ Quản lý Học sinh, tạo hồ sơ tự động.       |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Lead ở trạng thái QUALIFIED, khách hàng xác nhận   |
| hoạt**          | đăng ký và đóng phí lần đầu.                       |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Lead đang ở trạng thái QUALIFIED (UC-33).      |
| tiên quyết      |                                                    |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Nhân viên tư vấn xác nhận khách hàng chốt đăng |
| chính (Main     |     ký (khách đã đồng ý nhập học và đóng phí lần   |
| Flow)**         |     đầu).                                          |
|                 |                                                    |
|                 | 2.  Nhân viên nhập mã học sinh (student_code, duy  |
|                 |     nhất trong hệ thống — không tự sinh, đồng bộ   |
|                 |     UC-13/UC-35) và bấm nút chuyển đổi lead thành  |
|                 |     học sinh.                                      |
|                 |                                                    |
|                 | 3.  Hệ thống thực hiện transaction: chuyển trạng   |
|                 |     thái lead sang WON, tự động tạo hồ sơ học sinh |
|                 |     mới (Phân hệ Quản lý Học sinh) từ toàn bộ      |
|                 |     thông tin đã có ở lead cộng mã học sinh vừa    |
|                 |     nhập, không cần nhập liệu thủ công lại các     |
|                 |     trường khác.                                   |
|                 |                                                    |
|                 | 4.  Hệ thống tạo tài khoản Phụ huynh (nếu chưa có) |
|                 |     liên kết với hồ sơ học sinh mới.               |
|                 |                                                    |
|                 | 5.  Hệ thống ghi nhận khoản đóng phí lần đầu vào   |
|                 |     Phân hệ Tài chính.                             |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Khách hàng không chốt đăng ký***         |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu khách từ chối, Nhân viên đánh dấu lead     |
| Flow)**         |     LOST và ghi lý do để phân tích sau, không thực |
|                 |     hiện chuyển đổi.                               |
|                 |                                                    |
|                 | ***A2 --- Lỗi trong quá trình tạo hồ sơ tự động*** |
|                 |                                                    |
|                 | 1.  Nếu transaction thất bại giữa chừng, hệ thống  |
|                 |     rollback toàn bộ (không để lead ở trạng thái   |
|                 |     WON nhưng thiếu hồ sơ học sinh tương ứng), báo |
|                 |     lỗi cho Nhân viên thử lại.                     |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Lead chuyển trạng thái WON.                    |
| (P              |                                                    |
| ostcondition)** | -   Hồ sơ học sinh và tài khoản Phụ huynh được tạo |
|                 |     tự động, đầy đủ, nhất quán dữ liệu --- không   |
|                 |     phát sinh nhập liệu trùng lặp.                 |
+-----------------+----------------------------------------------------+

---

UC-35: Nhập học theo lô cho lớp liên kết

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-35                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Nhập học theo lô cho lớp liên kết                  |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 9                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-CRM-04                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Nhân viên (Giáo vụ)                                |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Nhân viên giáo vụ import file Excel danh sách học  |
| tắt**           | sinh từ trường liên kết để tạo hồ sơ học sinh hàng |
|                 | loạt, có kiểm tra trùng lặp.                       |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Trường liên kết cung cấp danh sách học sinh mới    |
| hoạt**          | theo lớp/khối cần nhập học hàng loạt.              |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Nhân viên giáo vụ có file Excel danh sách học  |
| tiên quyết      |     sinh đúng định dạng mẫu.                       |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Nhân viên giáo vụ mở màn hình Nhập học theo    |
| chính (Main     |     lô, tải lên file Excel.                        |
| Flow)**         |                                                    |
|                 | 2.  Hệ thống tạo 1 bản ghi import_jobs             |
|                 |     (import_type = STUDENTS), đọc và xác thực định |
|                 |     dạng file.                                     |
|                 |                                                    |
|                 | 3.  Hệ thống kiểm tra trùng lặp theo mã học        |
|                 |     sinh/họ tên + ngày sinh cho từng dòng trước    |
|                 |     khi tạo mới.                                   |
|                 |                                                    |
|                 | 4.  Hệ thống tạo hồ sơ học sinh hàng loạt cho các  |
|                 |     dòng hợp lệ, gán vào lớp/điểm trường liên kết  |
|                 |     tương ứng; đánh dấu các dòng lỗi/trùng lặp.    |
|                 |                                                    |
|                 | 5.  Hệ thống cập nhật import_jobs với total_rows,  |
|                 |     success_rows, failed_rows, error_summary;      |
|                 |     trạng thái COMPLETED hoặc PARTIAL_SUCCESS.     |
|                 |                                                    |
|                 | 6.  Nhân viên giáo vụ xem kết quả import, tải về   |
|                 |     danh sách lỗi (nếu có) để xử lý riêng.         |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- File sai định dạng***                    |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống từ chối xử lý toàn bộ file, đánh dấu  |
| Flow)**         |     import_jobs.status = FAILED, thông báo lỗi     |
|                 |     định dạng cụ thể.                              |
|                 |                                                    |
|                 | ***A2 --- Một phần dòng bị trùng lặp/lỗi dữ        |
|                 | liệu***                                            |
|                 |                                                    |
|                 | 1.  Hệ thống vẫn tạo hồ sơ cho các dòng hợp lệ, bỏ |
|                 |     qua các dòng lỗi/trùng, trạng thái             |
|                 |     PARTIAL_SUCCESS, liệt kê chi tiết dòng lỗi     |
|                 |     trong error_summary.                           |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Hồ sơ học sinh hợp lệ được tạo hàng loạt, đúng |
| (P              |     lớp/điểm trường liên kết.                      |
| ostcondition)** |                                                    |
|                 | -   Kết quả import (thành công/thất bại) được lưu  |
|                 |     lại đầy đủ phục vụ tra soát.                   |
+-----------------+----------------------------------------------------+

Phân hệ 10 --- Quản lý điểm trường & Cơ sở vật chất