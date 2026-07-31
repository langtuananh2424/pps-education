# phan-he-08-tai-chinh

UC-30: Xem hóa đơn & thanh toán học phí

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-30                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xem hóa đơn & thanh toán học phí                   |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 8                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-FIN-01, FR-FIN-02                               |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Phụ huynh                                          |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Hệ thống ngân hàng (webhook))   |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Hệ thống tự động xuất hóa đơn học phí định kỳ; Phụ |
| tắt**           | huynh xem hóa đơn và thanh toán qua mã QR ngân     |
|                 | hàng động, hệ thống tự động gạch nợ khi xác nhận   |
|                 | thành công.                                        |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Đến kỳ xuất hóa đơn định kỳ, hoặc Phụ huynh mở màn |
| hoạt**          | hình hóa đơn để thanh toán.                        |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Học sinh có hồ sơ và đang theo học khóa        |
| tiên quyết      |     học/lớp tính phí (UC-13, UC-18).               |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Hệ thống quét dữ liệu từ Phân hệ Khóa học/Học  |
| chính (Main     |     sinh, tự động xuất hóa đơn học phí định kỳ, áp |
| Flow)**         |     dụng chính xác mã miễn giảm/học bổng nếu học   |
|                 |     sinh có scholarship đang active.               |
|                 |                                                    |
|                 | 2.  Phụ huynh mở Portal, xem danh sách hóa đơn (đã |
|                 |     thanh toán/chưa thanh toán).                   |
|                 |                                                    |
|                 | 3.  Phụ huynh chọn 1 hóa đơn chưa thanh toán, hệ   |
|                 |     thống hiển thị mã QR ngân hàng động riêng cho  |
|                 |     hóa đơn đó.                                    |
|                 |                                                    |
|                 | 4.  Phụ huynh chọn phương thức thanh toán: quét QR |
|                 |     (chuyển khoản tự động) hoặc thủ công (tiền     |
|                 |     mặt/chuyển khoản thông thường, do Kế toán ghi  |
|                 |     nhận sau).                                     |
|                 |                                                    |
|                 | 5.  Với phương thức QR: hệ thống chờ webhook xác   |
|                 |     nhận từ ngân hàng.                             |
|                 |                                                    |
|                 | 6.  Khi webhook xác nhận thành công, hệ thống đối  |
|                 |     chiếu paid_amount với total_amount: nếu        |
|                 |     paid_amount ≥ total_amount → chuyển trạng thái |
|                 |     hóa đơn PAID; nếu nhỏ hơn → PARTIAL_PAID.      |
|                 |                                                    |
|                 | 7.  Hệ thống tự động gạch nợ và cập nhật trạng     |
|                 |     thái hóa đơn cho Phụ huynh xem ngay.           |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Webhook timeout/chưa xác nhận***         |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu chưa nhận được xác nhận webhook, hóa đơn ở |
| Flow)**         |     trạng thái chờ; một tiến trình cron kiểm tra   |
|                 |     định kỳ (chạy mỗi đêm, độc lập với các bước    |
|                 |     còn lại) sẽ đánh dấu hóa đơn quá hạn (OVERDUE) |
|                 |     nếu đã quá hạn và chưa PAID.                   |
|                 |                                                    |
|                 | ***A2 --- Thanh toán thủ công (tiền mặt/chuyển     |
|                 | khoản)***                                          |
|                 |                                                    |
|                 | 1.  Kế toán (UC-31 liên quan) ghi nhận thủ công    |
|                 |     khoản thu vào hóa đơn; hệ thống cập nhật trạng |
|                 |     thái tương tự bước 6-7 nhưng không qua         |
|                 |     webhook.                                       |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Trạng thái hóa đơn phản ánh đúng thực tế thanh |
| (P              |     toán (PAID/PARTIAL_PAID/OVERDUE).              |
| ostcondition)** |                                                    |
|                 | -   Công nợ học phí của Phụ huynh được cập nhật    |
|                 |     chính xác, làm dữ liệu cho báo cáo tài chính   |
|                 |     (UC-32).                                       |
+-----------------+----------------------------------------------------+

---

UC-31: Ghi nhận chi vận hành

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-31                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Ghi nhận chi vận hành                              |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 8                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-FIN-03                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Nhân viên (Kế toán)                                |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Kế toán ghi nhận các khoản chi lương, chi phí mặt  |
| tắt**           | bằng, chi phí bản quyền công nghệ và hạ tầng CDN.  |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Phát sinh khoản chi vận hành cần ghi nhận vào hệ   |
| hoạt**          | thống.                                             |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người dùng có role STAFF thuộc bộ phận Kế      |
| tiên quyết      |     toán, có quyền finance.expense.create (V51,    |
| (               |     tách từ finance.manage).                       |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Kế toán mở màn hình Quản lý chi vận hành, chọn |
| chính (Main     |     Thêm mới.                                      |
| Flow)**         |                                                    |
|                 | 2.  Kế toán chọn loại chi phí (lương/mặt bằng/bản  |
|                 |     quyền công nghệ/hạ tầng CDN/khác), nhập số     |
|                 |     tiền, ngày phát sinh, mô tả, điểm trường liên  |
|                 |     quan (nếu là chi phí phân bổ theo cơ sở).      |
|                 |                                                    |
|                 | 3.  Kế toán đính kèm chứng từ (nếu có).            |
|                 |                                                    |
|                 | 4.  Hệ thống lưu bản ghi chi vận hành.             |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Chi phí dùng chung nhiều điểm trường***  |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1. Kế toán có thể để trống điểm trường liên quan   |
| Flow)**         | hoặc phân bổ tỷ lệ, ghi rõ trong mô tả để phục vụ  |
|                 | báo cáo tổng hợp (UC-32).                          |
|                 |                                                    |
|                 | ***A2 --- Ban giám đốc duyệt/từ chối khoản chi (bổ |
|                 | sung, FR-FIN-03)***                                |
|                 |                                                    |
|                 | 1. Ban giám đốc (quyền finance.expense.approve)    |
|                 | xem danh sách khoản chi đang RECORDED, chọn Duyệt  |
|                 | hoặc Từ chối.                                      |
|                 |                                                    |
|                 | 2. Nếu Từ chối: bắt buộc nhập lý do. Hệ thống      |
|                 | chuyển trạng thái sang APPROVED hoặc REJECTED, ghi |
|                 | nhận người quyết định.                             |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | - Khoản chi được lưu chính xác, làm dữ liệu đầu    |
| (P              | vào cho báo cáo Thu/Chi/Công nợ (UC-32).           |
| ostcondition)** |                                                    |
|                 | - Trạng thái khoản chi                             |
|                 | (RECORDED/APPROVED/REJECTED) phản ánh đúng quyết   |
|                 | định của Ban giám đốc (nếu đã có).                 |
+-----------------+----------------------------------------------------+

---

UC-32: Xem báo cáo tài chính

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-32                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xem báo cáo tài chính                              |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 8                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-FIN-04                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản lý điểm trường (báo cáo cơ sở mình)           |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Ban giám đốc (báo cáo tổng hợp  |
|                 | toàn chuỗi))                                       |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Quản lý điểm trường xem báo cáo Thu/Chi/Công nợ    |
| tắt**           | của cơ sở mình; Ban giám đốc xem biểu đồ tài chính |
|                 | tổng hợp toàn chuỗi.                               |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Người dùng cần tra cứu tình hình tài chính theo    |
| hoạt**          | kỳ.                                                |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Đã có dữ liệu hóa đơn/thanh toán (UC-30) và    |
| tiên quyết      |     chi vận hành (UC-31) trong kỳ cần xem.         |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người dùng mở màn hình Báo cáo tài chính, chọn |
| chính (Main     |     kỳ báo cáo.                                    |
| Flow)**         |                                                    |
|                 | 2.  Trường hợp Quản lý điểm trường: hệ thống chỉ   |
|                 |     hiển thị dữ liệu Thu (học phí đã thu)/Chi/Công |
|                 |     nợ thuộc (các) điểm trường người dùng phụ      |
|                 |     trách (kiểm soát tầng Service --- NFR-SEC-03). |
|                 |                                                    |
|                 | 3.  Trường hợp Ban giám đốc: hệ thống hiển thị     |
|                 |     biểu đồ tài chính tổng hợp của toàn chuỗi, có  |
|                 |     thể xem chi tiết theo từng điểm trường.        |
|                 |                                                    |
|                 | 4.  Người dùng có thể xuất báo cáo (PDF/Excel) khi |
|                 |     cần.                                           |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Chưa có dữ liệu kỳ được chọn***          |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống thông báo chưa có dữ liệu, gợi ý chọn |
| Flow)**         |     kỳ khác.                                       |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Người dùng xem được đúng phạm vi báo cáo tài   |
| (P              |     chính theo vai trò; use case không làm thay    |
| ostcondition)** |     đổi dữ liệu gốc.                               |
+-----------------+----------------------------------------------------+

Phân hệ 9 --- Quản lý khách hàng và tuyển sinh