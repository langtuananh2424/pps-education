# phan-he-07-lms-portal

UC-23: Quản lý bài giảng

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-23                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Quản lý bài giảng                                  |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Giáo viên                                          |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: CDN/Object Storage)             |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Giáo viên tải lên bài giảng video, tài liệu PDF;   |
| tắt**           | toàn bộ tệp tin được lưu trữ và phân phối qua CDN  |
|                 | đảm bảo tải/xem mượt mà.                           |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Giáo viên cần bổ sung/cập nhật học liệu cho lớp    |
| hoạt**          | phụ trách.                                         |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Giáo viên được phân công giảng dạy lớp/khóa    |
| tiên quyết      |     học liên quan.                                 |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Giáo viên mở Kho bài giảng, chọn Thêm mới, tải |
| chính (Main     |     lên tệp video hoặc tài liệu PDF.               |
| Flow)**         |                                                    |
|                 | 2.  Hệ thống upload tệp lên CDN/Object Storage     |
|                 |     (không lưu trực tiếp trên server ứng dụng ---  |
|                 |     NFR-TECH-07), trả về URL phân phối.            |
|                 |                                                    |
|                 | 3.  Giáo viên nhập metadata: tiêu đề, mô tả, gán   |
|                 |     vào khóa học/lớp học liên quan, thứ tự hiển    |
|                 |     thị.                                           |
|                 |                                                    |
|                 | 4.  Hệ thống lưu bản ghi bài giảng, liên kết URL   |
|                 |     CDN.                                           |
|                 |                                                    |
|                 | 5.  Giáo viên có thể chỉnh sửa metadata hoặc gỡ    |
|                 |     bài giảng khỏi kho khi cần.                    |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Upload thất bại/tệp quá lớn***           |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống báo lỗi upload, cho phép Giáo viên    |
| Flow)**         |     thử lại hoặc nén/giảm dung lượng tệp trước khi |
|                 |     tải lên lại.                                   |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bài giảng được lưu trữ ổn định trên CDN và     |
| (P              |     hiển thị cho Học sinh thuộc lớp/khóa học tương |
| ostcondition)** |     ứng (UC-25, UC-27).                            |
+-----------------+----------------------------------------------------+

---

UC-24: Làm bài kiểm tra trực tuyến

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-24                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Làm bài kiểm tra trực tuyến                        |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-02                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Học sinh                                           |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Giáo viên (chấm thủ công phần   |
|                 | tự luận --- UC-41))                                |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Học sinh làm bài tập trắc nghiệm/tự luận trực      |
| tắt**           | tuyến; hệ thống tự động chấm điểm phần trắc nghiệm |
|                 | và lưu kết quả vào Phân hệ Học thuật.              |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Học sinh mở đề kiểm tra được Giáo viên giao        |
| hoạt**          | (UC-40) hoặc đề tự luyện.                          |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Đề kiểm tra đã được Giáo viên soạn và giao cho |
| tiên quyết      |     lớp/học sinh (UC-40).                          |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Học sinh mở đề kiểm tra được giao, xem thời    |
| chính (Main     |     hạn nộp (nếu là bài giao có deadline).         |
| Flow)**         |                                                    |
|                 | 2.  Học sinh làm bài: trả lời câu hỏi trắc nghiệm, |
|                 |     tự luận, và/hoặc câu hỏi Nói (ghi âm) tùy cấu  |
|                 |     trúc đề.                                       |
|                 |                                                    |
|                 | 3.  Học sinh nộp bài trước hoặc đúng deadline.     |
|                 |                                                    |
|                 | 4.  Hệ thống tự động chấm điểm các câu trắc nghiệm |
|                 |     ngay khi nộp.                                  |
|                 |                                                    |
|                 | 5.  Nếu đề có câu tự luận/Nói, hệ thống chuyển các |
|                 |     câu này sang hàng chờ để Giáo viên chấm thủ    |
|                 |     công (UC-41); các câu trắc nghiệm hiển thị     |
|                 |     điểm ngay cho Học sinh, điểm tổng kết (total   |
|                 |     score) tạm để trống.                           |
|                 |                                                    |
|                 | 6.  Kết quả (một phần hoặc toàn phần) được lưu vào |
|                 |     Phân hệ Học thuật.                             |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Nộp bài quá hạn***                       |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu Học sinh nộp sau deadline, hệ thống kiểm   |
| Flow)**         |     tra cấu hình Cho phép nộp muộn: nếu không cho  |
|                 |     phép, từ chối nhận bài; nếu cho phép, nhận bài |
|                 |     kèm đánh dấu nộp muộn.                         |
|                 |                                                    |
|                 | ***A2 --- Muốn làm lại (retake)***                 |
|                 |                                                    |
|                 | 1.  Nếu đề cho phép làm lại và Học sinh còn lượt   |
|                 |     làm, hệ thống cho phép Học sinh làm lại từ     |
|                 |     đầu, ghi nhận là 1 lượt làm mới, lặp lại luồng |
|                 |     chính.                                         |
|                 |                                                    |
|                 | ***A3 --- Toàn bộ câu hỏi là trắc nghiệm***        |
|                 |                                                    |
|                 | 1.  Không có phần chờ Giáo viên chấm; Học sinh xem |
|                 |     được điểm tổng kết ngay sau khi nộp.           |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bài làm được lưu, phần trắc nghiệm có điểm     |
| (P              |     ngay; phần tự luận/Nói (nếu có) chờ chấm thủ   |
| ostcondition)** |     công.                                          |
|                 |                                                    |
|                 | -   Kết quả cuối cùng được đồng bộ vào sổ điểm khi |
|                 |     hoàn tất chấm.                                 |
+-----------------+----------------------------------------------------+

---

UC-25: Xem Portal Phụ huynh

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-25                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xem Portal Phụ huynh                               |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-03, FR-LMS-07                               |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Phụ huynh                                          |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Phụ huynh đăng nhập xem lịch học, bảng điểm, nhận  |
| tắt**           | xét giáo viên, thông báo khẩn, và hồ sơ tổng hợp   |
|                 | của con.                                           |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Phụ huynh đăng nhập Portal để theo dõi tình hình   |
| hoạt**          | học tập của con.                                   |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Tài khoản Phụ huynh đã được liên kết với hồ sơ |
| tiên quyết      |     (các) học sinh là con của mình (UC-13).        |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Phụ huynh đăng nhập (UC-01), hệ thống điều     |
| chính (Main     |     hướng tới Portal Phụ huynh.                    |
| Flow)**         |                                                    |
|                 | 2.  Nếu Phụ huynh có nhiều con, hệ thống hiển thị  |
|                 |     lựa chọn để tách riêng dữ liệu theo từng học   |
|                 |     sinh.                                          |
|                 |                                                    |
|                 | 3.  Phụ huynh xem lịch học của con, bảng điểm đã   |
|                 |     duyệt (UC-20), nhận xét giáo viên đã duyệt     |
|                 |     (UC-22).                                       |
|                 |                                                    |
|                 | 4.  Phụ huynh xem hồ sơ tổng hợp: kết quả học tập, |
|                 |     chuyên cần, tình trạng bài tập, cảnh báo (ý    |
|                 |     thức trên lớp + bài tập về nhà), tổng kết điểm |
|                 |     theo từng giai đoạn.                           |
|                 |                                                    |
|                 | 5.  Phụ huynh nhận các thông báo khẩn từ nhà       |
|                 |     trường (thông báo vắng mặt --- UC-15, thông    |
|                 |     báo chung).                                    |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Dữ liệu chưa được duyệt***               |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Điểm/nhận xét đang ở trạng thái Chờ duyệt      |
| Flow)**         |     không hiển thị cho Phụ huynh; hệ thống chỉ     |
|                 |     hiển thị dữ liệu đã APPROVED.                  |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Phụ huynh xem được đầy đủ, chính xác thông tin |
| (P              |     học tập của con trong phạm vi quyền hạn (kiểm  |
| ostcondition)** |     soát ở tầng Service --- NFR-SEC-03), không làm |
|                 |     thay đổi dữ liệu.                              |
+-----------------+----------------------------------------------------+

---

UC-26: Luyện Nghe -- Nói

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-26                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Luyện Nghe -- Nói                                  |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-04                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Học sinh                                           |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Học sinh luyện tập theo 3 chế độ: Nghe (highlight  |
| tắt**           | văn bản theo thời gian thực), Chép chính tả, Nói   |
|                 | (ghi âm phản xạ, so khớp phát âm).                 |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Học sinh chọn bài luyện Nghe -- Nói từ kho học     |
| hoạt**          | liệu/bài tập.                                      |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Bài luyện Nghe -- Nói đã tồn tại trong hệ      |
| tiên quyết      |     thống, gắn với khóa học/lớp học sinh đang theo |
| (               |     học.                                           |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Học sinh chọn bài luyện, chọn chế độ: Nghe /   |
| chính (Main     |     Chép chính tả / Nói.                           |
| Flow)**         |                                                    |
|                 | 2.  Chế độ Nghe: hệ thống phát audio, highlight    |
|                 |     văn bản theo thời gian thực; Học sinh có thể   |
|                 |     điều chỉnh tốc độ phát (0.6x--1.15x), chọn     |
|                 |     giọng đọc, và tạm dừng khi đang nghe.          |
|                 |                                                    |
|                 | 3.  Chế độ Chép chính tả: hệ thống phát audio, Học |
|                 |     sinh điền từ khóa hoặc điền toàn bộ nội dung   |
|                 |     nghe được.                                     |
|                 |                                                    |
|                 | 4.  Chế độ Nói: hệ thống ghi âm phản xạ của Học    |
|                 |     sinh, so khớp phát âm với mẫu chuẩn.           |
|                 |                                                    |
|                 | 5.  Hệ thống chấm/đánh giá kết quả (tự động với    |
|                 |     Nghe/Chép chính tả; sơ bộ tự động và có thể bổ |
|                 |     sung chấm thủ công với Nói), hiển thị kết quả  |
|                 |     cho Học sinh.                                  |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Tạm dừng giữa chừng***                   |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Học sinh tạm dừng khi đang nghe, hệ thống lưu  |
| Flow)**         |     vị trí, cho phép tiếp tục sau.                 |
|                 |                                                    |
|                 | ***A2 --- Ghi âm thất bại (chế độ Nói)***          |
|                 |                                                    |
|                 | 1.  Hệ thống thông báo lỗi ghi âm (thiết bị/quyền  |
|                 |     truy cập micro), cho phép Học sinh thử ghi âm  |
|                 |     lại.                                           |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Kết quả luyện tập của Học sinh được lưu lại;   |
| (P              |     có thể dùng làm dữ liệu cho Hệ thống           |
| ostcondition)** |     Gamification (FR-LMS-05, Phase 2).             |
+-----------------+----------------------------------------------------+

---

UC-27: Làm bài tập/đề ôn tập

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-27                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Làm bài tập/đề ôn tập                              |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-06                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Học sinh                                           |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Học sinh làm bài giảng, bài tập chuyên đề, đề ôn   |
| tắt**           | tập tự biên soạn theo format chuẩn hóa từ ngân     |
|                 | hàng bài tập.                                      |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Học sinh chọn 1 bài tập/đề ôn tập từ ngân hàng để  |
| hoạt**          | tự luyện.                                          |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Ngân hàng bài tập/đề ôn tập đã có nội dung (do |
| tiên quyết      |     Giáo viên biên soạn --- liên quan UC-40).      |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Học sinh duyệt ngân hàng bài tập & đề ôn tập,  |
| chính (Main     |     chọn 1 đề theo chuyên đề/cấp độ phù hợp.       |
| Flow)**         |                                                    |
|                 | 2.  Học sinh làm bài (SELF_PRACTICE --- mở tự do,  |
|                 |     không deadline).                               |
|                 |                                                    |
|                 | 3.  Học sinh nộp bài khi hoàn tất.                 |
|                 |                                                    |
|                 | 4.  Hệ thống tự động chấm phần trắc nghiệm; nếu có |
|                 |     phần tự luận/Nói, đưa vào hàng chờ Giáo viên   |
|                 |     chấm (tương tự UC-24, UC-41), tùy cấu hình đề  |
|                 |     tự luyện.                                      |
|                 |                                                    |
|                 | 5.  Học sinh xem lại kết quả và có thể làm lại đề  |
|                 |     (nếu được phép) để luyện tập thêm.             |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Đề chỉ có đáp án tham khảo, không cần    |
| thế / ngoại lệ  | Giáo viên chấm***                                  |
| (Alternate      |                                                    |
| Flow)**         | 1.  Nếu đề ôn tập được cấu hình tự chấm hoàn toàn  |
|                 |     (kèm đáp án tham khảo), Học sinh xem kết quả   |
|                 |     ngay không cần chờ Giáo viên.                  |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Kết quả luyện tập được lưu vào lịch sử làm bài |
| (P              |     của Học sinh, không ảnh hưởng tới sổ điểm      |
| ostcondition)** |     chính thức (khác với bài kiểm tra ASSIGNED ở   |
|                 |     UC-24).                                        |
+-----------------+----------------------------------------------------+

---

UC-28: Điền kế hoạch giảng dạy

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-28                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Điền kế hoạch giảng dạy                            |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-08                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Giáo viên                                          |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Giáo viên điền kế hoạch giảng dạy theo tuần hoặc   |
| tắt**           | theo năm học cho từng lớp phụ trách; hệ thống tổng |
|                 | hợp hiển thị cho Portal trường liên kết.           |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Đến kỳ lập kế hoạch giảng dạy (đầu tuần/đầu năm    |
| hoạt**          | học) cho lớp Giáo viên phụ trách.                  |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Giáo viên được phân công giảng dạy lớp liên    |
| tiên quyết      |     kết trường cần lập kế hoạch.                   |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Giáo viên mở màn hình Kế hoạch giảng dạy, chọn |
| chính (Main     |     lớp và kỳ lập kế hoạch (theo tuần hoặc theo    |
| Flow)**         |     năm học).                                      |
|                 |                                                    |
|                 | 2.  Giáo viên nhập nội dung kế hoạch: chủ đề, mục  |
|                 |     tiêu, nội dung giảng dạy dự kiến.              |
|                 |                                                    |
|                 | 3.  Giáo viên lưu/gửi kế hoạch.                    |
|                 |                                                    |
|                 | 4.  Hệ thống tổng hợp và hiển thị trực tiếp trong  |
|                 |     tài khoản Portal của trường liên kết tương ứng |
|                 |     (UC-29).                                       |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Cập nhật kế hoạch đã gửi***              |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Giáo viên chỉnh sửa kế hoạch đã lập trước đó;  |
| Flow)**         |     hệ thống cập nhật phiên bản mới nhất hiển thị  |
|                 |     trên Portal trường liên kết.                   |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Kế hoạch giảng dạy được lưu và đồng bộ hiển    |
| (P              |     thị cho Đại diện trường liên kết theo dõi.     |
| ostcondition)** |                                                    |
+-----------------+----------------------------------------------------+

---

UC-29: Xem báo cáo Portal trường liên kết

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-29                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xem báo cáo Portal trường liên kết                 |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-08                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Đại diện trường liên kết                           |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Đại diện trường liên kết xem báo cáo tổng hợp của  |
| tắt**           | học sinh trường mình: chuyên cần, kết quả học tập, |
|                 | kế hoạch giảng dạy; có thể xuất file.              |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Đại diện trường liên kết đăng nhập Portal để theo  |
| hoạt**          | dõi tình hình học sinh trường mình.                |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Tài khoản Đại diện trường liên kết đã được gán |
| tiên quyết      |     vào 1 điểm trường loại Trường liên kết.        |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Đại diện trường liên kết đăng nhập (UC-01), hệ |
| chính (Main     |     thống điều hướng tới Portal Báo cáo trường     |
| Flow)**         |     liên kết.                                      |
|                 |                                                    |
|                 | 2.  Hệ thống hiển thị báo cáo tổng hợp của học     |
|                 |     sinh trường mình: chuyên cần (tỷ lệ đi         |
|                 |     học/vắng mặt), kết quả học tập (điểm đã        |
|                 |     duyệt), kế hoạch giảng dạy theo tuần/năm học   |
|                 |     do Giáo viên điền (UC-28).                     |
|                 |                                                    |
|                 | 3.  Đại diện trường liên kết có thể xuất báo cáo   |
|                 |     dưới định dạng PDF/Excel để tải về hoặc gửi    |
|                 |     qua kênh khác.                                 |
|                 |                                                    |
|                 | 4.  Đại diện trường liên kết có thể gửi ý          |
|                 |     kiến/phản hồi tới Quản lý điểm trường phụ      |
|                 |     trách từ màn hình này (liên kết sang UC-38).   |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Chưa có dữ liệu kỳ hiện tại***           |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống thông báo chưa có dữ liệu báo cáo cho |
| Flow)**         |     kỳ được chọn, gợi ý chọn kỳ khác đã có dữ      |
|                 |     liệu.                                          |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Đại diện trường liên kết xem/xuất được đúng    |
| (P              |     phạm vi dữ liệu học sinh trường mình (không có |
| ostcondition)** |     quyền chỉnh sửa hồ sơ học sinh hoặc dữ liệu    |
|                 |     học thuật --- theo định nghĩa tác nhân trong   |
|                 |     SRS).                                          |
+-----------------+----------------------------------------------------+

---

UC-40: Soạn & giao đề kiểm tra

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-40                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Soạn & giao đề kiểm tra                            |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-10                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Giáo viên                                          |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Giáo viên soạn đề kiểm tra (từ ngân hàng câu hỏi   |
| tắt**           | có sẵn hoặc tạo mới), giao đề cho lớp cụ thể kèm   |
|                 | thời hạn nộp nếu cần.                              |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Giáo viên cần tạo bài kiểm tra/bài tập giao cho    |
| hoạt**          | lớp phụ trách.                                     |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Giáo viên được phân công giảng dạy lớp/khóa    |
| tiên quyết      |     học cần giao đề.                               |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Giáo viên mở màn hình Soạn đề, chọn nguồn câu  |
| chính (Main     |     hỏi: từ ngân hàng có sẵn hoặc soạn câu hỏi mới |
| Flow)**         |     --- cả 2 đều được lưu vào ngân hàng câu hỏi    |
|                 |     (exercise_questions).                          |
|                 |                                                    |
|                 | 2.  Giáo viên chọn Loại đề: SELF_PRACTICE (mở tự   |
|                 |     do, dùng cho UC-27) hoặc ASSIGNED (giao có     |
|                 |     deadline, dùng cho UC-24).                     |
|                 |                                                    |
|                 | 3.  Nếu chọn ASSIGNED: Giáo viên chọn lớp cụ thể   |
|                 |     để giao đề, đặt deadline; hệ thống tạo bản ghi |
|                 |     exercise_assignments liên kết đề với           |
|                 |     lớp/deadline.                                  |
|                 |                                                    |
|                 | 4.  Giáo viên xác nhận lưu/giao đề; hệ thống thông |
|                 |     báo cho Học sinh trong lớp (nếu là ASSIGNED).  |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Đề có câu tự luận/Nói***                 |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống đánh dấu đề cần bước chấm thủ công bổ |
| Flow)**         |     sung sau khi Học sinh nộp bài (dẫn tới UC-41), |
|                 |     khác với đề thuần trắc nghiệm chỉ cần chấm tự  |
|                 |     động.                                          |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Đề kiểm tra được lưu vào ngân hàng câu hỏi;    |
| (P              |     nếu là ASSIGNED, được giao đúng lớp với        |
| ostcondition)** |     deadline xác định, sẵn sàng cho Học sinh làm   |
|                 |     bài (UC-24).                                   |
+-----------------+----------------------------------------------------+

---

UC-41: Chấm bài thủ công

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-41                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Chấm bài thủ công                                  |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-11                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Giáo viên                                          |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Học sinh (người nộp bài))       |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Giáo viên chấm điểm thủ công các câu hỏi tự luận   |
| tắt**           | và câu hỏi Nói (ghi âm), ghi nhận xét cho từng câu |
|                 | trả lời.                                           |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Có bài làm của Học sinh chứa câu tự luận/Nói đang  |
| hoạt**          | chờ Giáo viên chấm (từ UC-24 hoặc UC-27).          |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Học sinh đã nộp bài có chứa câu hỏi tự luận    |
| tiên quyết      |     và/hoặc câu hỏi Nói.                           |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Giáo viên mở danh sách bài chờ chấm, chọn 1    |
| chính (Main     |     bài làm cụ thể.                                |
| Flow)**         |                                                    |
|                 | 2.  Giáo viên xem/nghe từng câu trả lời tự         |
|                 |     luận/ghi âm, chấm điểm và ghi nhận xét cho     |
|                 |     từng câu.                                      |
|                 |                                                    |
|                 | 3.  Hệ thống hiển thị điểm cho Học sinh theo từng  |
|                 |     kỹ năng ngay khi kỹ năng đó đã có đáp án được  |
|                 |     chấm xong (tự động hoặc thủ công) --- không    |
|                 |     chờ chấm hết toàn bộ đề.                       |
|                 |                                                    |
|                 | 4.  Giáo viên tiếp tục chấm các câu còn lại của    |
|                 |     bài (vòng lặp cho tới khi hết câu cần chấm     |
|                 |     trong bài).                                    |
|                 |                                                    |
|                 | 5.  Khi toàn bộ các kỹ năng trong đề đã có điểm,   |
|                 |     hệ thống tự động tính và hiển thị điểm tổng    |
|                 |     kết (total score) của cả đề cho Học sinh.      |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Còn nhiều bài chờ chấm (nhiều học        |
| thế / ngoại lệ  | sinh)***                                           |
| (Alternate      |                                                    |
| Flow)**         | 1.  Giáo viên lặp lại luồng chính cho từng bài làm |
|                 |     khác trong danh sách chờ chấm cho tới khi xử   |
|                 |     lý hết.                                        |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Toàn bộ câu tự luận/Nói được chấm điểm và có   |
| (P              |     nhận xét.                                      |
| ostcondition)** |                                                    |
|                 | -   Điểm tổng kết của đề được hiển thị đầy đủ cho  |
|                 |     Học sinh khi tất cả các kỹ năng đã có điểm;    |
|                 |     kết quả được đồng bộ vào sổ điểm liên quan.    |
+-----------------+----------------------------------------------------+

Phân hệ 8 --- Quản lý tài chính và học phí