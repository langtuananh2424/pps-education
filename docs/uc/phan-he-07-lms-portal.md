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

UC-23a: Xem bài giảng (Học sinh)

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-23a                                             |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xem bài giảng (Học sinh)                           |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Học sinh                                           |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Học sinh xem kho bài giảng (lessons +              |
| tắt**           | lesson_materials) của (các) lớp mình đang ghi danh |
|                 | — đã có tên trong sơ đồ actor                      |
|                 | (UseCase-HocSinh.mmd) và được UC-42 dẫn chiếu từ   |
|                 | trước nhưng chưa từng có Main Flow/Postcondition   |
|                 | riêng; bổ sung đầy đủ nhân dịp sửa lại đúng logic  |
|                 | hiển thị theo SDD (bổ sung ngoài SDD gốc, đã xác   |
|                 | nhận với người dùng).                              |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Học sinh mở kho bài giảng của 1 lớp mình đang học. |
| hoạt**          |                                                    |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Học sinh đã đăng nhập, có class_enrollment     |
| tiên quyết      |     ACTIVE tại lớp đang xem.                       |
| (               |                                                    |
| Precondition)** | -   Bài giảng đã được Giáo viên publish (UC-23).   |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Học sinh chọn 1 lớp đang ghi danh (UC-42), mở  |
| chính (Main     |     tab "Kho bài giảng".                           |
| Flow)**         |                                                    |
|                 | 2.  Hệ thống trả về mọi lessons có                 |
|                 |     status=PUBLISHED và (gắn riêng đúng lớp này    |
|                 |     HOẶC gắn chung theo khung chương trình của lớp |
|                 |     này) — đúng logic OR đã thiết kế trong SDD     |
|                 |     ("HS trong lớp X xem được lessons WHERE        |
|                 |     class_id=X OR curriculum_id=curriculum của lớp |
|                 |     X").                                           |
|                 |                                                    |
|                 | 3.  Học sinh chọn 1 bài giảng, xem danh sách       |
|                 |     lesson_materials đính kèm (video/PDF/audio...) |
|                 |     để phát/tải về.                                |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Học sinh chưa/không còn ghi danh lớp     |
| thế / ngoại lệ  | đang gọi***                                        |
| (Alternate      |                                                    |
| Flow)**         | 1.  Nếu không có class_enrollment ACTIVE khớp lớp  |
|                 |     đang truy vấn, hệ thống từ chối truy cập (404, |
|                 |     không lộ thông tin bài giảng của lớp không     |
|                 |     thuộc về mình).                                |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Học sinh chỉ thấy bài giảng PUBLISHED thuộc    |
| (P              |     đúng phạm vi lớp/khung chương trình mình đang  |
| ostcondition)** |     học — không thấy bài DRAFT hay bài của         |
|                 |     lớp/khung khác.                                |
+-----------------+----------------------------------------------------+

---

UC-60: Kho tài liệu tham khảo

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-60                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Kho tài liệu tham khảo                             |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-13                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Giáo viên, Trưởng phòng đào tạo/Quản lý điểm       |
|                 | trường, Học sinh                                   |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Kho tài liệu tham khảo độc lập với bài giảng       |
| tắt**           | (UC-23/23a) — không gắn 1 bài giảng cụ thể nào,    |
|                 | chỉ gắn theo khung chương trình (curriculum). Giáo |
|                 | viên/Trưởng phòng đào tạo/Quản lý điểm trường      |
|                 | upload tài liệu (PDF/video/audio/slide/ảnh...),    |
|                 | Học sinh xem theo curriculum của (các) lớp đang    |
|                 | ghi danh (bổ sung ngoài SDD gốc, đã xác nhận với   |
|                 | người dùng — khái niệm hoàn toàn mới, không có     |
|                 | trong SDD gốc).                                    |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Giáo viên/Trưởng phòng đào tạo muốn chia sẻ tài    |
| hoạt**          | liệu tham khảo chung cho học sinh theo 1 khung     |
|                 | chương trình, không gắn với 1 bài giảng cụ thể     |
|                 | nào; hoặc Học sinh mở kho tài liệu để tự học thêm. |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người upload có quyền lms.document.manage.     |
| tiên quyết      |                                                    |
| (               | -   Tệp đã upload lên CDN từ trước (NFR-TECH-07),  |
| Precondition)** |     Service chỉ nhận URL.                          |
|                 |                                                    |
|                 | -   Học sinh xem: đã có class_enrollment ACTIVE    |
|                 |     tại 1 lớp thuộc curriculum đó.                 |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Giáo viên/Trưởng phòng đào tạo chọn 1          |
| chính (Main     |     curriculum, nhập metadata tài liệu (tiêu đề,   |
| Flow)**         |     mô tả, loại tệp, URL CDN), lưu ở trạng thái    |
|                 |     DRAFT.                                         |
|                 |                                                    |
|                 | 2.  Người upload publish tài liệu                  |
|                 |     (status=PUBLISHED) khi sẵn sàng hiển thị cho   |
|                 |     học sinh.                                      |
|                 |                                                    |
|                 | 3.  Học sinh mở kho tài liệu, hệ thống trả về mọi  |
|                 |     tài liệu PUBLISHED thuộc curriculum của (các)  |
|                 |     lớp học sinh đang ghi danh ACTIVE.             |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Học sinh chỉ thấy tài liệu PUBLISHED thuộc     |
| (P              |     đúng curriculum của lớp mình đang học — không  |
| ostcondition)** |     thấy tài liệu DRAFT hay của curriculum khác.   |
|                 |                                                    |
|                 | -   Người có quyền lms.document.manage xem được    |
|                 |     mọi trạng thái để quản lý.                     |
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
|                 | 2b. Hệ thống thực hiện UC-42 để xác định "lớp đang  |
|                 |     xem" của con vừa chọn (tự động bỏ qua nếu con   |
|                 |     chỉ có 1 lớp; nếu con đã từng chuyển lớp, Phụ   |
|                 |     huynh chọn được lớp/giai đoạn muốn xem, kể cả   |
|                 |     lớp đã kết thúc).                               |
|                 |                                                    |
|                 | 3.  Phụ huynh xem lịch học của con, bảng điểm đã   |
|                 | công bố (UC-20 --- V39: công bố thay duyệt) và     |
|                 | Overall/Level theo kỳ đánh giá (UC-53, bổ sung     |
|                 | ngoài SDD gốc, đã xác nhận với người dùng), nhận   |
|                 | xét giáo viên đã duyệt (UC-22).                    |
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
| **Luồng thay    | ***A1 --- Dữ liệu chưa công bố/chưa được duyệt***  |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Điểm đang ở trạng thái DRAFT (chưa công bố ---  |
| Flow)**         |     UC-20) không hiển thị cho Phụ huynh; hệ thống  |
|                 |     chỉ hiển thị điểm đã PUBLISHED.                |
|                 |                                                    |
|                 | 2.  Nhận xét đang ở trạng thái Chờ duyệt không     |
|                 |     hiển thị cho Phụ huynh; hệ thống chỉ hiển thị  |
|                 |     nhận xét đã APPROVED (UC-22).                  |
|                 |                                                    |
|                 | 3.  Cùng quy tắc PUBLISHED áp dụng cho             |
|                 |     Overall/Level theo kỳ đánh giá (UC-53) — chỉ   |
|                 |     hiển thị khi đã công bố (thủ công hoặc tự      |
|                 |     động, UC-20/A3).                               |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Phụ huynh xem được đầy đủ, chính xác thông tin |
| (P              |     học tập của con trong phạm vi quyền hạn (kiểm  |
| ostcondition)** |     soát ở tầng Service --- NFR-SEC-03), không làm |
|                 |     thay đổi dữ liệu.                              |
|                 |                                                    |
|                 | -   Bao gồm cả dữ liệu của lớp cũ nếu con đã từng   |
|                 |     chuyển lớp (UC-42) --- không chỉ lớp đang học   |
|                 |     hiện tại.                                       |
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
|                 |     sinh, lưu lại kèm mẫu chuẩn (script_text) để   |
|                 |     Giáo viên đối chiếu khi chấm — KHÔNG tự động   |
|                 |     so khớp phát âm bằng dịch vụ nhận diện giọng   |
|                 |     nói bên thứ 3 (bổ sung ngoài SDD gốc, đã xác   |
|                 |     nhận với người dùng).                          |
|                 |                                                    |
|                 | 5.  Hệ thống chấm/đánh giá kết quả: tự động hoàn   |
|                 |     toàn với Nghe (chỉ đánh dấu hoàn thành, không  |
|                 |     tính điểm) và Chép chính tả (so khớp với mẫu   |
|                 |     chuẩn, chấm ngay); riêng Nói LUÔN chuyển thẳng |
|                 |     vào hàng chờ Giáo viên chấm thủ công (tương tự |
|                 |     UC-41), không có bước tự động sơ bộ (bổ sung   |
|                 |     ngoài SDD gốc, đã xác nhận với người dùng).    |
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
|                 | nhận xét học sinh (đã duyệt), kế hoạch giảng dạy;  |
|                 | có thể xuất file.                                  |
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
|                 |     học/vắng mặt), kết quả học tập (điểm đã công   |
|                 |     bố — UC-20, V39: công bố thay duyệt), nhận xét |
|                 |     học sinh đã duyệt (APPROVED — UC-21/22, bổ     |
|                 |     sung ngoài SRS gốc, xác nhận 2026-07-16), kế   |
|                 |     hoạch giảng dạy theo tuần/năm học do Giáo viên |
|                 |     điền (UC-28).                                  |
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

---

UC-42: Chọn lớp đang xem (Portal Học sinh/Phụ huynh)

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-42                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Chọn lớp đang xem (Portal Học sinh/Phụ huynh)      |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-12                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Học sinh, Phụ huynh                                |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Học sinh có thể học qua nhiều lớp theo thời gian   |
| tắt**           | (chuyển lớp để phù hợp trình độ --- UC-13). Use    |
|                 | case này xác định "lớp đang xem" mỗi khi Học sinh  |
|                 | đăng nhập hoặc Phụ huynh chọn xem 1 con, để toàn    |
|                 | bộ dữ liệu Portal (bài giảng, kiểm tra, điểm,      |
|                 | chuyên cần...) hiển thị đúng theo lớp đã chọn ---   |
|                 | kể cả lớp đã kết thúc do chuyển lớp, dữ liệu không  |
|                 | bị mất hay ẩn vĩnh viễn.                            |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Học sinh vừa đăng nhập thành công (UC-01); hoặc     |
| hoạt**          | Phụ huynh vừa chọn xong 1 con muốn xem (UC-25 bước |
|                 | 2).                                                 |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Đã đăng nhập thành công (UC-01).               |
| tiên quyết      |                                                    |
| (               | -   Đối với Phụ huynh: đã chọn con muốn xem (UC-25 |
| Precondition)** |     bước 2, nếu có nhiều con).                     |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Hệ thống truy vấn toàn bộ bản ghi              |
| chính (Main     |     class_enrollments của học sinh (mọi trạng     |
| Flow)**         |     thái: ACTIVE/WITHDRAWN/TRANSFERRED/COMPLETED), |
|                 |     sắp xếp theo enrolled_date giảm dần.           |
|                 |                                                    |
|                 | 2.  Nếu học sinh chỉ có đúng 1 lớp (1 bản ghi       |
|                 |     enrollment duy nhất), hệ thống tự động chọn    |
|                 |     lớp đó làm "lớp đang xem", bỏ qua bước 3-4.     |
|                 |                                                    |
|                 | 3.  Nếu có từ 2 lớp trở lên, hệ thống hiển thị      |
|                 |     danh sách lớp để chọn --- mỗi mục gồm tên lớp,  |
|                 |     khoảng thời gian học (enrolled_date đến         |
|                 |     withdrawn_date, hoặc "Đang học" nếu status =    |
|                 |     ACTIVE), trạng thái. Mặc định pre-select lớp   |
|                 |     có status = ACTIVE (nếu có).                    |
|                 |                                                    |
|                 | 4.  Học sinh/Phụ huynh chọn 1 lớp trong danh sách.  |
|                 |                                                    |
|                 | 5.  Hệ thống lưu lựa chọn làm ngữ cảnh "lớp đang    |
|                 |     xem" cho phiên hiện tại; các use case Portal    |
|                 |     liên quan (UC-23a, UC-24, UC-26, UC-27, UC-25a, |
|                 |     UC-25b) lọc dữ liệu hiển thị theo đúng lớp này. |
|                 |                                                    |
|                 | 6.  Học sinh/Phụ huynh có thể đổi "lớp đang xem"    |
|                 |     bất kỳ lúc nào trong phiên (quay lại bước 3),   |
|                 |     không cần đăng nhập lại.                        |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Học sinh chưa từng được xếp lớp***       |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu không có bản ghi class_enrollments nào (hồ  |
| Flow)**         |     sơ mới tạo, chưa qua UC-18), hệ thống hiển thị  |
|                 |     thông báo "Chưa được xếp lớp"; không có dữ liệu |
|                 |     Portal nào được hiển thị. Luồng chính dừng ở    |
|                 |     bước 1.                                         |
|                 |                                                    |
|                 | ***A2 --- Không còn lớp nào đang ACTIVE***          |
|                 |                                                    |
|                 | 1.  Nếu toàn bộ lớp của học sinh đã kết thúc (đã    |
|                 |     tốt nghiệp/nghỉ học/chuyển hết), hệ thống vẫn   |
|                 |     hiển thị đầy đủ danh sách lớp cũ ở bước 3 (không |
|                 |     ẩn); mặc định pre-select lớp có enrolled_date   |
|                 |     gần nhất thay vì lớp ACTIVE.                    |
|                 |                                                    |
|                 | ***A3 --- Phụ huynh đổi sang xem con khác***        |
|                 |                                                    |
|                 | 1.  Nếu Phụ huynh quay lại chọn 1 con khác (UC-25   |
|                 |     bước 2), hệ thống thực hiện lại UC này từ đầu   |
|                 |     cho con vừa chọn --- ngữ cảnh "lớp đang xem"    |
|                 |     trước đó không áp dụng cho con mới.             |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   "Lớp đang xem" được xác định và lưu trong ngữ  |
| (P              |     cảnh phiên; toàn bộ dữ liệu Portal hiển thị sau |
| ostcondition)** |     đó (bài giảng, kiểm tra, điểm, chuyên cần, nhận |
|                 |     xét...) được lọc đúng theo lớp này.             |
|                 |                                                    |
|                 | -   Dữ liệu của các lớp khác (kể cả lớp đã kết thúc |
|                 |     do chuyển lớp) không bị xóa hay ẩn vĩnh viễn ---|
|                 |     có thể chọn lại để xem bất kỳ lúc nào            |
|                 |     (class_enrollments + class_enrollments_history  |
|                 |     là nguồn dữ liệu, xem docs/sdd-groups/           |
|                 |     06-hoc-thuat.md).                               |
+-----------------+----------------------------------------------------+

Phân hệ 8 --- Quản lý tài chính và học phí