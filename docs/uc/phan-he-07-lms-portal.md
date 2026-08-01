# phan-he-07-lms-portal

UC-23: Quản lý Kho Video Ôn tập

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-23                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Quản lý Kho Video Ôn tập                           |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Giáo viên                                          |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: CDN/Object Storage, YouTube)    |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Tái cấu trúc 2026-07-27 từ "Kho bài giảng" — đã    |
| tắt**           | xác nhận với người dùng: bỏ hẳn PDF/Slide/Word,    |
|                 | chỉ còn video/audio ôn tập, tổ chức theo "bộ"      |
|                 | (Video từ kết nối/Video phản xạ), theo dõi % thời  |
|                 | lượng học sinh đã xem (UC-23a).                    |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Giáo viên cần bổ sung/cập nhật video ôn tập cho    |
| hoạt**          | lớp phụ trách.                                     |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Giáo viên được phân công giảng dạy lớp/khung   |
| tiên quyết      |     chương trình liên quan.                        |
| (               | -   Tài khoản có quyền lms.review-video.create/    |
| Precondition)** |     update/view (mặc định gán cho TEACHER — bổ     |
|                 |     sung ngoài SDD gốc, đã xác nhận với người dùng |
|                 |     2026-07-30, V63). Quyền chỉ mở được vào màn    |
|                 |     hình — thao tác trên 1 lớp cụ thể vẫn phải qua |
|                 |     điều kiện phân công giảng dạy ở trên.          |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Giáo viên mở Kho Video Ôn tập, tạo bộ mới —    |
| chính (Main     |     nhập tiêu đề, chọn loại (Video từ kết nối/     |
| Flow)**         |     Video phản xạ), gán vào 1 lớp cụ thể hoặc 1    |
|                 |     khung chương trình.                            |
|                 |                                                    |
|                 | 2.  Giáo viên thêm từng video vào bộ — chọn 1      |
|                 |     trong 3 nguồn: dán link YouTube, upload file   |
|                 |     video, hoặc upload file audio. Với file upload,|
|                 |     hệ thống đẩy lên Cloudflare R2 (không lưu trực |
|                 |     tiếp trên server ứng dụng --- NFR-TECH-07),    |
|                 |     trả về URL phân phối.                          |
|                 |                                                    |
|                 | 3.  Hệ thống lưu URL + thời lượng (giây) của video |
|                 |     — thời lượng do phía Giáo viên (FE) tự phát    |
|                 |     hiện và gửi kèm, hệ thống không tự dò.         |
|                 |                                                    |
|                 | 4.  Giáo viên sắp thứ tự video trong bộ, công bố   |
|                 |     (PUBLISHED) khi sẵn sàng cho học sinh xem.     |
|                 |                                                    |
|                 | 5.  Giáo viên có thể sửa metadata hoặc gỡ (chuyển  |
|                 |     ARCHIVED — soft-remove, không xóa cứng) bộ/    |
|                 |     video khi cần.                                 |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Upload thất bại/tệp quá lớn***           |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống báo lỗi upload, cho phép Giáo viên    |
| Flow)**         |     thử lại hoặc nén/giảm dung lượng tệp trước khi |
|                 |     tải lên lại.                                   |
|                 |                                                    |
|                 | ***A2 --- Không phát hiện được thời lượng video*** |
|                 |                                                    |
|                 | 1.  Nếu trình phát (YouTube IFrame Player) hoặc    |
|                 |     tệp lỗi không đọc được thời lượng, Giáo viên   |
|                 |     tự nhập tay thời lượng (giây).                 |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bộ video ôn tập được lưu trữ ổn định trên CDN/ |
| (P              |     YouTube, sẵn sàng dùng làm nguồn khi PUBLISHED |
| ostcondition)** |     --- V65: KHÔNG còn tự hiển thị cho Học sinh    |
|                 |     ngay khi PUBLISHED, xem bổ sung dưới đây.      |
+-----------------+----------------------------------------------------+

**Bổ sung V65 (2026-07-30, đã xác nhận với người dùng) --- Publish đổi ý
nghĩa, học sinh không còn tự thấy ngay:** trước V65, PUBLISHED = hiển thị
ngay cho học sinh thuộc lớp/khung chương trình tương ứng (UC-23a). Từ
V65, Publish chỉ còn nghĩa "đủ điều kiện dùng làm nguồn" (đề/video xuất
hiện trong dropdown "BTVN buổi sau" ở Nhận xét học viên, UC-21) --- áp
dụng cho CẢ 2 loại video (`CONNECTION` lẫn `REFLEX`, không chỉ REFLEX).
Học sinh chỉ thực sự xem/làm được khi 1 Giáo viên chọn bộ đó làm "BTVN
buổi sau" cho lớp qua UC-21 (tự động tạo `ReviewVideoAssignment` cho CẢ
LỚP đang ghi danh ACTIVE, hạn nộp = buổi kế tiếp). Xem đầy đủ cơ chế mới
(5 quy tắc: xung đột cùng buổi, sửa lựa chọn, REJECTED không ảnh hưởng,
lớp không có buổi kế tiếp, chỉ áp dụng DAILY) tại UC-21 (`docs/uc/
phan-he-06-hoc-thuat.md`).

---

UC-23a: Xem & Theo dõi Kho Video Ôn tập

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-23a                                             |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xem & Theo dõi Kho Video Ôn tập                    |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Học sinh (xem + báo tiến độ), Giáo viên (xem       |
|                 | thống kê)                                          |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Học sinh xem kho video ôn tập (review_video_sets + |
| tắt**           | review_videos) của (các) lớp mình đang ghi danh;   |
|                 | hệ thống theo dõi TỪNG LƯỢT xem (bổ sung V59,       |
|                 | 2026-07-28, đã xác nhận với người dùng — thay thế   |
|                 | cơ chế watermark suốt đời ban đầu), "đạt yêu cầu"   |
|                 | video CONNECTION cần cả 2 điều kiện, đều cấu hình   |
|                 | được khi Giáo viên tạo video: (1) % ngưỡng xem cho  |
|                 | 1 lượt (mặc định 80%), (2) số lượt đạt ngưỡng đó    |
|                 | tối thiểu (mặc định 1). Giáo viên xem thống kê học  |
|                 | sinh đã xem/chưa xem theo từng bộ + lớp.            |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Học sinh mở kho video ôn tập của 1 lớp mình đang   |
| hoạt**          | học; Giáo viên mở màn hình thống kê 1 bộ.          |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Học sinh đã đăng nhập, có class_enrollment     |
| tiên quyết      |     ACTIVE tại lớp đang xem.                       |
| (               |                                                    |
| Precondition)** | -   Bộ video đã được Giáo viên publish (UC-23) VÀ  |
|                 |     đã được giao cho lớp qua Nhận xét học viên     |
|                 |     (UC-21, V65 --- `ReviewVideoAssignment` ACTIVE |
|                 |     cho đúng lớp).                                 |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Học sinh chọn 1 lớp đang ghi danh (UC-42), mở  |
| chính (Main     |     tab "Kho Video Ôn tập".                        |
| Flow)**         |                                                    |
|                 | 2.  Hệ thống trả về mọi bộ có status=PUBLISHED,     |
|                 |     (gắn riêng đúng lớp này HOẶC gắn chung theo    |
|                 |     khung chương trình của lớp này --- đúng logic  |
|                 |     OR đã thiết kế trong SDD), VÀ có                |
|                 |     `ReviewVideoAssignment` ACTIVE cho lớp học sinh |
|                 |     đang ghi danh ACTIVE (V65, UC-21 --- publish    |
|                 |     đơn thuần không còn đủ, xem bổ sung V65 ở       |
|                 |     UC-23).                                        |
|                 |                                                    |
|                 | 3.  Học sinh chọn 1 bộ, xem danh sách video, bắt   |
|                 |     đầu phát 1 video — hệ thống mở 1 LƯỢT XEM mới  |
|                 |     (watch session).                               |
|                 |                                                    |
|                 | 4.  Trong lúc xem, hệ thống ghi nhận định kỳ số    |
|                 |     giây đã xem CHO ĐÚNG lượt đang mở — lấy mốc    |
|                 |     giây CAO NHẤT trong lượt đó, không tính lùi    |
|                 |     khi học sinh tua tới; lượt đạt % ngưỡng của    |
|                 |     video được đánh dấu "hợp lệ".                  |
|                 |                                                    |
|                 | 5.  "Đạt yêu cầu" (đã xem) = số lượt hợp lệ ≥ số   |
|                 |     lượt tối thiểu cấu hình cho video đó.          |
|                 |                                                    |
|                 | 6.  Giáo viên xem thống kê theo bộ + lớp — từng    |
|                 |     học sinh đã xem bao nhiêu % + bao nhiêu lượt   |
|                 |     hợp lệ mỗi video, đạt yêu cầu hay chưa (kể cả  |
|                 |     học sinh chưa từng mở video nào, hiển thị 0%). |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Học sinh chưa/không còn ghi danh lớp     |
| thế / ngoại lệ  | đang gọi***                                        |
| (Alternate      |                                                    |
| Flow)**         | 1.  Nếu không có class_enrollment ACTIVE khớp lớp  |
|                 |     đang truy vấn, hệ thống từ chối truy cập (404, |
|                 |     không lộ thông tin bộ video của lớp không      |
|                 |     thuộc về mình).                                |
|                 |                                                    |
|                 | ***A2 --- Học sinh mở lượt xem/báo tiến độ cho     |
|                 | video ngoài phạm vi lớp mình***                    |
|                 |                                                    |
|                 | 1.  Cùng cơ chế 404 như A1 — không lộ sự tồn tại   |
|                 |     của video ngoài phạm vi lớp học sinh đang học. |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Học sinh chỉ thấy bộ PUBLISHED thuộc đúng      |
| (P              |     phạm vi lớp/khung chương trình mình đang học    |
| ostcondition)** |     VÀ đã được giao qua UC-21 (V65) — không thấy    |
|                 |     bộ DRAFT, bộ của lớp/khung khác, hay bộ đã      |
|                 |     Publish nhưng chưa từng được GV chọn giao.      |
|                 |                                                    |
|                 | -   Mỗi lượt xem lưu riêng — trong 1 lượt, tiến độ |
|                 |     không giảm theo thời gian; lượt hợp lệ được    |
|                 |     cộng dồn vào số lượt đạt của video.            |
|                 |                                                    |
|                 | -   Giáo viên xem được thống kê chính xác (%+số    |
|                 |     lượt) kể cả học sinh chưa từng mở video nào    |
|                 |     (hiện 0%, không biến mất khỏi danh sách).      |
+-----------------+----------------------------------------------------+

> **Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31** — fix
> bug Portal không hiển thị hạn nộp (dueAt) cho BTVN Video: trước đây API
> duy nhất đọc được `availableFrom`/`dueAt` của `ReviewVideoAssignment` là
> `GET /api/classes/{classId}/review-video-assignments`, bị khoá
> `requireAssignedTeacher` — chỉ Giáo viên gọi được, Học sinh gọi sẽ bị
> chặn. Portal trước đó chỉ đọc được cấp Set (`GET /api/classes/{classId}/
> review-video-sets`, không có `dueAt`) nên không có gì để hiển thị. Thêm
> endpoint self-service `GET /api/students/me/review-video-assignments`
> (tùy chọn `?classId=`) — mirror `listMyAssignedExercises` (bài ngữ
> pháp) — trả `availableFrom`/`dueAt`/`videoType`/tiêu đề bộ theo đúng
> phạm vi (các) lớp học sinh đang ghi danh ACTIVE.

---

UC-23b: Nộp & Chấm điểm Audio cho Video Phản xạ

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-23b                                             |
+-----------------+----------------------------------------------------+
| **Tên Use       | Nộp & Chấm điểm Audio cho Video Phản xạ            |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-01                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Học sinh (nộp audio), Giáo viên (chấm điểm)        |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Bổ sung ngoài SDD gốc, đã xác nhận với người dùng  |
| tắt**           | (2026-07-27, cập nhật 2026-07-28 — V57, THAY THẾ   |
|                 | thiết kế "1 video = 1 audio duy nhất" ban đầu) —    |
|                 | video loại "Video phản xạ" (REFLEX) chia thành     |
|                 | NHIỀU câu hỏi, mỗi câu gắn 1 mốc thời gian trong    |
|                 | video (timestamp), thời lượng ghi âm tối đa và số  |
|                 | lần nộp lại tối đa RIÊNG theo từng câu hỏi. Học     |
|                 | sinh nộp audio cho TỪNG câu hỏi, nộp lại GIỮ LỊCH   |
|                 | SỬ (không ghi đè); Giáo viên chấm điểm + nhận xét   |
|                 | cho attempt mới nhất mỗi câu.                       |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Giáo viên soạn câu hỏi khi tạo video Phản xạ; Học   |
| hoạt**          | sinh bấm vào 1 câu hỏi, ghi âm câu trả lời; Giáo    |
|                 | viên mở danh sách bài audio đã nộp của 1 bộ + lớp   |
|                 | để chấm.                                            |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Học sinh đã đăng nhập, có class_enrollment     |
| tiên quyết      |     ACTIVE tại lớp đang xem, bộ video đã PUBLISHED |
| (               |     VÀ đã được giao cho lớp qua UC-21 (V65, xem bổ |
| Precondition)** |     sung ở UC-23), video thuộc bộ có                |
|                 |     video_type=REFLEX.                              |
|                 | -   Giáo viên được phân công giảng dạy lớp/khung   |
|                 |     chương trình sở hữu bộ video đó.               |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Giáo viên soạn video REFLEX, thêm từng câu hỏi |
| chính (Main     |     kèm mốc thời gian (timestampSeconds), thời     |
| Flow)**         |     lượng ghi âm tối đa (maxRecordingSeconds) và    |
|                 |     số lần cho phép nộp lại (maxAttempts, để trống  |
|                 |     = không giới hạn) — riêng cho câu hỏi đó.       |
|                 |                                                    |
|                 | 2.  Học sinh bấm 1 câu hỏi, ghi âm (hoặc chọn file  |
|                 |     có sẵn), upload qua API upload media chung, gửi |
|                 |     audioUrl — hệ thống tạo 1 ATTEMPT MỚI (tăng dần |
|                 |     attemptNumber), KHÔNG xoá các attempt trước.    |
|                 |                                                    |
|                 | 3.  Giáo viên mở danh sách bài audio đã nộp theo bộ|
|                 |     + lớp cụ thể — chỉ hiện ATTEMPT MỚI NHẤT mỗi   |
|                 |     (câu hỏi, học sinh).                            |
|                 |                                                    |
|                 | 4.  Giáo viên nghe audio, chấm điểm (score/maxScore|
|                 |     ) và ghi nhận xét (feedback) cho attempt đó.    |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Nộp audio cho video không phải REFLEX*** |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống từ chối (400) — chỉ video Phản xạ mới |
| Flow)**         |     nhận câu hỏi/audio trả lời, video Kết nối       |
|                 |     (CONNECTION) không áp dụng.                     |
|                 |                                                    |
|                 | ***A2 --- Học sinh ngoài phạm vi lớp/bộ chưa        |
|                 | PUBLISHED***                                       |
|                 |                                                    |
|                 | 1.  Cùng cơ chế 404 như UC-23a (A1/A2) — không lộ  |
|                 |     sự tồn tại của video/câu hỏi ngoài phạm vi lớp  |
|                 |     mình.                                           |
|                 |                                                    |
|                 | ***A3 --- Giáo viên không được phân công lớp/khung |
|                 | sở hữu bộ video***                                 |
|                 |                                                    |
|                 | 1.  Hệ thống từ chối (403) khi xem danh sách hoặc  |
|                 |     chấm điểm.                                     |
|                 |                                                    |
|                 | ***A4 --- Chấm điểm cho bài nộp không tồn tại***   |
|                 |                                                    |
|                 | 1.  Hệ thống báo lỗi (404).                        |
|                 |                                                    |
|                 | ***A5 --- Học sinh nộp lại khi đã hết lượt          |
|                 | (maxAttempts)***                                    |
|                 |                                                    |
|                 | 1.  Hệ thống từ chối, không tạo attempt mới (tái    |
|                 |     dùng nguyên cơ chế `RetakeNotAllowedException`  |
|                 |     của UC-24/27 bài tập ngữ pháp).                 |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Mỗi lần nộp tạo 1 attempt MỚI, GIỮ LỊCH SỬ mọi |
| (P              |     attempt trước TRONG CÙNG 1 lần giao (không ghi  |
| ostcondition)** |     đè, không xoá) — học sinh xem lại được toàn bộ  |
|                 |     lịch sử đã nộp của lần giao hiện tại (V69, xem  |
|                 |     bổ sung dưới đây).                              |
|                 |                                                    |
|                 | -   Giáo viên chấm được điểm (score/maxScore) +    |
|                 |     nhận xét cho TỪNG attempt (mặc định thao tác    |
|                 |     trên attempt mới nhất).                         |
+-----------------+----------------------------------------------------+

> **Lưu ý phân biệt:** UC-23b khác hoàn toàn với luồng "ghi âm phản xạ"
> ở FR-LMS-04 (chế độ Nói trong Luyện Nghe — Nói, UC-26, chấm qua
> `ListeningPracticeGradingService`) — 2 domain tách biệt, không dùng
> chung bảng/Service, chỉ trùng tên gọi thông thường "phản xạ".

> **Bổ sung V69 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
> 2026-07-31) — fix bug "Đã nộp bài" hiện sai khi giao lại:** trước đây
> `review_video_question_submissions` không liên kết với
> `ReviewVideoAssignment` nào — khi Giáo viên giao LẠI đúng 1 bộ REFLEX
> cho lớp đã từng làm ở buổi trước, hệ thống vẫn thấy "đã có câu trả lời
> cũ" nên hiện nhầm trạng thái "Đã nộp bài" cho lần giao mới. 3 thay đổi:
> 1. Bảng `review_video_question_submissions` thêm cột
>    `review_video_assignment_id` (NULL cho dữ liệu trước V69, không xác
>    định được lần giao nào — coi như không thuộc lần giao hiện tại nào).
>    `attemptNumber`/`maxAttempts`/lịch sử nộp giờ tính THEO ĐÚNG 1 lần
>    giao, không còn xuyên suốt mọi lần giao trong quá khứ.
> 2. `deliverToClass` (giao lại) tự động hủy (CANCELLED) mọi lần giao
>    ACTIVE cũ của ĐÚNG (bộ, lớp) đó trước khi tạo lần giao mới — tại mọi
>    thời điểm chỉ có TỐI ĐA 1 lần giao ACTIVE cho 1 (bộ, lớp), khớp đúng
>    quy tắc "giao lại = 1 lượt hoàn toàn mới, học sinh phải làm lại".
> 3. % tiến độ Video Phản xạ hiện ở Nhận xét học viên (UC-21,
>    `HomeworkProgressService.videoProgressLabel`) cũng tính lại theo
>    ĐÚNG lần giao đang báo cáo, cùng lý do.

> **Bổ sung V70 (2026-07-31, đã xác nhận với người dùng) — fix bug thông
> báo bị gửi lặp nhiều lần cho toàn bộ học sinh trong lớp:** "Gửi nhận
> xét" hàng loạt cho nhiều học sinh cùng buổi khiến `deliverToClass` bị
> gọi nhiều lần với ĐÚNG CÙNG (bộ, lớp, hạn nộp) — trước đây mỗi lần gọi
> tạo mới hẳn 1 `ReviewVideoAssignment` rồi thông báo lại cho TOÀN BỘ học
> sinh lớp. `deliverToClass` giờ tái dùng bản ghi ACTIVE đã khớp đúng cả 3
> field thay vì tạo mới/thông báo lại — chỉ khi `dueAt` KHÁC (giao lại ở
> buổi sau) mới áp dụng quy tắc hủy-cũ-tạo-mới của V69 ở trên. Chi tiết
> đầy đủ (áp dụng cho cả kênh Ngữ pháp Online) xem UC-21
> (`docs/uc/phan-he-06-hoc-thuat.md`).

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
| **Mô tả tóm     | Kho tài liệu tham khảo độc lập với Kho Video Ôn    |
| tắt**           | tập (UC-23/23a) — không gắn 1 bộ video cụ thể nào, |
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
| **Điều kiện     | -   Người upload có quyền lms.document.create      |
| tiên quyết      |     hoặc lms.document.update.                      |
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
|                 | -   Người có quyền lms.document.view xem được      |
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
|                 |     chuyên cần, tình trạng bài tập (chỉ xem tiến   |
|                 |     độ — Ngữ pháp online/offline + Video Kết nối/  |
|                 |     Phản xạ theo từng buổi có giao BTVN; endpoint   |
|                 |     riêng bổ sung 2026-07-29, trước đó chỉ có sẵn   |
|                 |     dữ liệu ẩn trong nhận xét chưa lộ ra), cảnh báo |
|                 |     (ý thức trên lớp + bài tập về nhà), tổng kết    |
|                 |     điểm theo từng giai đoạn.                       |
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

UC-61: Xem điểm của tôi (Học sinh)

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-61                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xem điểm của tôi (Học sinh)                        |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-03, FR-LMS-07                               |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Học sinh                                           |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Học sinh tự xem bảng điểm và Overall/Level đã công |
| tắt**           | bố của chính mình theo (các) lớp đang ghi danh —   |
|                 | đối xứng với phần xem điểm trong UC-25 (Portal Phụ |
|                 | huynh), khác ở chỗ tác nhân là chính Học sinh,     |
|                 | không cần qua Phụ huynh (bổ sung ngoài SDD gốc, đã |
|                 | xác nhận với người dùng).                          |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Học sinh mở mục "Điểm của tôi" trong Portal.       |
| hoạt**          |                                                    |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Học sinh đã đăng nhập, có class_enrollment     |
| tiên quyết      |     ACTIVE tại lớp đang xem.                       |
| (               |                                                    |
| Precondition)** | -   Điểm/Overall-Level đã được công bố (PUBLISHED) |
|                 |     — thủ công hoặc tự động sau N ngày (UC-20/A3). |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Học sinh chọn 1 lớp đang ghi danh (UC-42), mở  |
| chính (Main     |     bảng điểm — hệ thống trả về mọi grade_entries  |
| Flow)**         |     đã PUBLISHED của học sinh tại lớp đó (điểm     |
|                 |     chưa công bố không hiển thị).                  |
|                 |                                                    |
|                 | 2.  Học sinh chọn 1 kỳ đánh giá, xem Overall/Level |
|                 |     (UC-53) đã công bố của kỳ đó — nếu chưa công   |
|                 |     bố, hệ thống báo rõ chưa có điểm tổng kết,     |
|                 |     không trả dữ liệu nháp.                        |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Học sinh chỉ thấy điểm/Overall-Level đã        |
| (P              |     PUBLISHED của đúng (các) lớp mình đang ghi     |
| ostcondition)** |     danh — không thấy điểm DRAFT hay của lớp khác. |
+-----------------+----------------------------------------------------+

---

UC-64: Xem chuyên cần & nhận xét của tôi (Học sinh)

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-64                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Xem chuyên cần & nhận xét của tôi (Học sinh)       |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 7                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-LMS-03, FR-LMS-07                               |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Học sinh                                           |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Học sinh tự xem điểm danh (UC-15) và nhận xét giáo |
| tắt**           | viên đã duyệt (UC-22) của chính mình theo (các) lớp|
|                 | đang/đã ghi danh — đối xứng với phần xem điểm danh |
|                 | + nhận xét trong UC-25 (Portal Phụ huynh) và với   |
|                 | UC-61 (xem điểm của tôi), khác ở chỗ tác nhân là   |
|                 | chính Học sinh, không cần qua Phụ huynh (bổ sung   |
|                 | ngoài SDD gốc, đã xác nhận với người dùng          |
|                 | 2026-07-29).                                       |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Học sinh mở mục "Quá trình học tập" trong Portal.  |
| hoạt**          |                                                    |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Học sinh đã đăng nhập, có class_enrollment     |
| tiên quyết      |     ACTIVE tại lớp đang xem.                       |
| (Precondition)**|                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Học sinh chọn 1 lớp đang ghi danh (UC-42), xem |
| chính (Main     |     toàn bộ bản ghi điểm danh của mình tại lớp đó. |
| Flow)**         |                                                    |
|                 | 2.  Học sinh xem nhận xét giáo viên của mình tại   |
|                 |     lớp đó — chỉ nhận xét đã APPROVED (UC-22),     |
|                 |     bao gồm cảnh báo (is_warning) và chi tiết BTVN |
|                 |     (buổi trước/buổi sau, online lẫn offline).     |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Học sinh chỉ thấy điểm danh/nhận xét đã duyệt  |
| (Postcondition)**|    của đúng (các) lớp mình đang/đã ghi danh —      |
|                 |     không thấy dữ liệu của lớp khác hay nhận xét    |
|                 |     đang PENDING/REJECTED.                          |
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
| **Điều kiện     | -   Bài tập/đề ôn tập (SELF_PRACTICE) đã được Giáo |
| tiên quyết      |     viên biên soạn, gắn vào 1 "Đề" (Kho đề, xem    |
| (               |     UC-40) --- **V65 (bổ sung ngoài SDD gốc, đã    |
| Precondition)** |     xác nhận với người dùng 2026-07-30):** khác    |
|                 |     với thiết kế ban đầu (mở tự do ngay khi        |
|                 |     Publish), SELF_PRACTICE giờ CŨNG cần (a) Đề    |
|                 |     của bài đã được gán cho lớp học sinh đang học  |
|                 |     (Kho đề) VÀ (b) được Giáo viên chọn làm "BTVN  |
|                 |     buổi sau" ở Nhận xét học viên (UC-21) --- hệ    |
|                 |     thống tự động giao (tạo ExerciseAssignment) cho|
|                 |     TOÀN BỘ học sinh ACTIVE của lớp khi đó, hạn nộp|
|                 |     ngầm định = buổi học kế tiếp (dù bài tự luyện  |
|                 |     không có khái niệm "trễ hạn" thật sự). Publish |
|                 |     đơn thuần (không qua UC-21) KHÔNG còn đủ để    |
|                 |     học sinh thấy/làm được bài.                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Học sinh duyệt danh sách bài tập/đề ôn tập ĐÃ  |
| chính (Main     |     ĐƯỢC GIAO cho lớp mình (xem Precondition),     |
| Flow)**         |     chọn 1 đề theo chuyên đề/cấp độ phù hợp.       |
|                 |                                                    |
|                 | 2.  Học sinh làm bài (SELF_PRACTICE --- không có   |
|                 |     deadline chặn nộp muộn như ASSIGNED, nhưng vẫn |
|                 |     cần đã được giao như Precondition).            |
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
| **Mô tả tóm     | Giáo viên tổ chức Kho đề 2 cấp: "Đề" (VD IELTS     |
| tắt**           | Grade 6) chứa nhiều "Bài" (VD Unit 1, soạn từ ngân |
|                 | hàng câu hỏi có sẵn hoặc tạo mới), gán Đề cho lớp  |
|                 | cụ thể --- điều kiện hiển thị DUY NHẤT cho học sinh|
|                 | lớp đó (bổ sung Kho đề, 2026-07-30, xem dưới đây). |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Giáo viên cần tạo bài kiểm tra/bài tập giao cho    |
| hoạt**          | lớp phụ trách.                                     |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Giáo viên được phân công giảng dạy lớp/khóa    |
| tiên quyết      |     học cần giao đề.                               |
| (               |                                                    |
| Precondition)** |                                                    |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Giáo viên tạo/chọn 1 "Đề" (Exam) trong Kho đề  |
| chính (Main     |     --- mã, tên, gán 1 khung chương trình (CHỈ để |
| Flow)**         |     lọc/tìm kiếm trong Kho đề, không phải điều     |
|                 |     kiện hiển thị cho lớp).                        |
|                 |                                                    |
|                 | 2.  Giáo viên gán Đề cho 1 hoặc nhiều lớp --- đây   |
|                 |     là điều kiện hiển thị DUY NHẤT: học sinh của   |
|                 |     lớp đã gán mới xem/làm được các Bài thuộc Đề,  |
|                 |     kể cả lớp khác khung chương trình với Đề.       |
|                 |                                                    |
|                 | 3.  Giáo viên mở màn hình Soạn Bài (trong Đề), chọn|
|                 |     nguồn câu hỏi: từ ngân hàng có sẵn hoặc soạn    |
|                 |     câu hỏi mới --- cả 2 đều được lưu vào ngân hàng |
|                 |     câu hỏi (exercise_questions).                  |
|                 |                                                    |
|                 | 4.  Giáo viên chọn Loại Bài: SELF_PRACTICE (dùng   |
|                 |     cho UC-27) hoặc ASSIGNED (dùng cho UC-24) ---  |
|                 |     V65: cả 2 loại đều cùng 1 cơ chế giao (không   |
|                 |     còn phân biệt ở bước giao bài, xem Postcondition|
|                 |     và bổ sung Kho đề dưới đây).                    |
|                 |                                                    |
|                 | 5.  Giáo viên xác nhận Publish Bài --- V65: KHÔNG   |
|                 |     còn bước chọn lớp/deadline ở màn này; Publish   |
|                 |     chỉ đánh dấu Bài đủ điều kiện dùng làm nguồn.   |
|                 |                                                    |
|                 | 6.  Việc thật sự giao Bài (chọn lớp/đặt deadline/   |
|                 |     thông báo Học sinh) chuyển hẳn sang Nhận xét    |
|                 |     học viên (UC-21, V65) --- xem bổ sung dưới đây. |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Đề có câu tự luận/Nói***                 |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống đánh dấu Bài cần bước chấm thủ công bổ|
| Flow)**         |     sung sau khi Học sinh nộp bài (dẫn tới UC-41), |
|                 |     khác với Bài thuần trắc nghiệm chỉ cần chấm tự |
|                 |     động.                                          |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Bài kiểm tra được lưu vào ngân hàng câu hỏi,   |
| (P              |     gắn vào đúng 1 Đề; nếu Publish, đủ điều kiện   |
| ostcondition)** |     dùng làm nguồn --- CHƯA sẵn sàng cho Học sinh   |
|                 |     làm bài (UC-24/27) cho tới khi Đề của Bài đó    |
|                 |     đã gán cho lớp (bước 2) VÀ Bài được chọn giao   |
|                 |     qua UC-21 (bước 6) --- cả 2 điều kiện đều bắt   |
|                 |     buộc, thiếu 1 trong 2 vẫn chặn.                 |
+-----------------+----------------------------------------------------+

> **Bổ sung Kho đề (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
> 2026-07-30) --- tái cấu trúc UC-40 thành 2 cấp Đề/Bài:** trước đây
> `Exercise` (Bài) gán trực tiếp 1 khung chương trình; giờ mỗi Bài thuộc 1
> "Đề" (`Exam`, bảng `exams`, gán 1 khung chương trình CHỈ để lọc/tìm
> kiếm trong Kho đề) và 1 Đề gán được NHIỀU lớp (bảng `exam_class_
> assignments`, nhiều-nhiều) --- đây là điều kiện hiển thị DUY NHẤT cho
> học sinh, thay thế hoàn toàn "khớp khung chương trình của lớp" trước
> đây. 5 hệ quả:
> 1. **Áp dụng cho MỌI loại Bài** (SELF_PRACTICE/ASSIGNED/MOCK_TEST/
>    SKILL_PRACTICE), không riêng ASSIGNED như thiết kế V65 ban đầu ---
>    SELF_PRACTICE mất hẳn cơ chế "mở tự do sau khi Publish", giờ CŨNG
>    cần Đề đã gán lớp + được giao qua UC-21 như ASSIGNED (thay đổi hành
>    vi thật sự cho UC-27, xem Precondition UC-27).
> 2. Endpoint `GET /api/curriculums/{curriculumId}/exercises` (do PR
>    #128 tự bổ sung để GV duyệt đề theo khung chương trình) đã bị XÓA,
>    thay bằng duyệt qua `GET /api/exams` (lọc theo `curriculumId`) →
>    `GET /api/exams/{id}/exercises` (Bài trong 1 Đề).
> 3. `POST/DELETE /api/exams/{id}/classes/{classId}` (gán/gỡ lớp, quyền
>    `lms.exam.assign`) thay cho việc đặt scope trực tiếp trên từng Bài.
> 4. Dữ liệu Exercise cũ (Sprint 0/1, dữ liệu test) được migration tự
>    động bọc vào 1 Đề mặc định 1-1, không mất dữ liệu.
> 5. Dropdown "BTVN buổi sau" ở Nhận xét học viên (UC-21) đổi nhãn từ
>    `Tên bài (Mã bài)` sang **`Mã Đề - Tên bài`** để phân biệt khi 1 lớp
>    được gán nhiều Đề cùng lúc.

> **Bổ sung ngoài SDD gốc, đã xác nhận với người dùng (2026-07-21, cập
> nhật 2026-07-22):** `Question.audioUrl` (trắc nghiệm Voice) và
> `Question.imageUrl` (tự luận scan đề bài) ở bước 1 Main Flow trước đây
> yêu cầu Giáo viên tự dán URL đã upload sẵn lên CDN ngoài — thực tế
> backend/dự án chưa có hạ tầng lưu trữ file nào. Đã bổ sung API dùng
> chung `POST /api/media/upload` (multipart, chấp nhận `audio/*`/`image/*`,
> tối đa 50MB/10MB, kèm tham số bắt buộc `module=LMS_QUESTION` - xem
> `MediaModule`) trả về `{"url": "..."}`, upload lên **Cloudflare R2**
> (Object Storage tương thích S3 API, không tính phí egress) — thay cho
> quyết định lưu đĩa cục bộ + Docker volume ban đầu — trả thẳng URL công
> khai của R2 (r2.dev subdomain hoặc Custom Domain) với key dạng
> `lms/questions/{audio|images}/{uuid}.{ext}` (tham số `module` để phân
> biệt module gọi API dùng chung này, tránh trộn lẫn file nếu module khác
> ngoài LMS sau này cũng upload qua đây), không cần JWT để xem vì file
> nhúng trong thẻ `<audio>`/`<img>` không gửi kèm được header
> Authorization — xem `MediaController`/`MediaStorageService`/
> `R2StorageConfig`. Phạm vi ban đầu CHỈ áp dụng cho
> `Question.audioUrl`/`imageUrl`; các trường URL khác trong hệ thống
> (`Student.portraitUrl`, file đính kèm Task...) vẫn giữ nguyên quy ước
> "coi như đã upload sẵn" cho tới khi có yêu cầu riêng.
>
> **Mở rộng, đã xác nhận với người dùng (2026-07-22, theo yêu cầu FE):**
> thêm 2 module `CURRICULUM_DOCUMENT` (`curriculum_documents.file_url`,
> UC-60) và `LESSON_MATERIAL` (`lesson_materials.file_url`, xem UC-23 —
> đã đổi tên thành `REVIEW_VIDEO` từ 2026-07-27, xem ghi chú ngay dưới)
> vào `MediaModule` — 2 field này trước đây cũng là nhập tay URL, giờ
> upload thật qua cùng API `POST /api/media/upload`. `LMS_QUESTION` cũng
> được bật nhận PDF/ảnh cho câu tự luận cùng đợt này.
>
> **Tái cấu trúc 2026-07-27 (Kho Video Ôn tập, đã xác nhận với người
> dùng):** `LESSON_MATERIAL` đổi tên thành `REVIEW_VIDEO`
> (`review_videos.file_url`, xem UC-23 ở trên) — Kho Video Ôn tập đã bỏ
> hẳn PDF/Slide/Word, chỉ còn video/audio, nên cờ `acceptsDocuments()`
> (1 cờ duy nhất gộp cả video lẫn tài liệu văn phòng) được tách thành 2
> cờ độc lập `acceptsVideo()`/`acceptsOfficeDocuments()` trên
> `MediaModule` để có thể "cho video, cấm PDF" riêng cho `REVIEW_VIDEO`.
> `CURRICULUM_DOCUMENT`/`LMS_QUESTION` giữ nguyên hành vi kết hợp cũ (cả
> 2 cờ đều bật — nhận PDF/Word/Excel/PowerPoint ≤20MB và `video/*`
> ≤200MB ngoài audio/ảnh); `REVIEW_VIDEO` chỉ bật `acceptsVideo()` (nhận
> video/audio, từ chối PDF/Word/Excel/PowerPoint). Key R2 tương ứng:
> `lms/curriculum-documents/{category}/` và `lms/review-videos/{category}/`
> (`category` = `audio`/`images`/`video`/`documents` theo content-type).
>
> **Soạn đề nhanh (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
> 2026-07-30):**
>
> 1. **FE "Soạn & giao đề" giờ hiện thực đúng Main Flow bước 1** — trước
>    đây `CreateAndAssignExerciseModal.tsx` chỉ cho chọn câu hỏi có sẵn từ
>    ngân hàng. Bước "Soạn đề" giờ có 3 nguồn (tab), tất cả đều tự tạo câu
>    hỏi vào ngân hàng TRƯỚC rồi mới gắn vào đề (`exercise_questions`) —
>    khớp đúng Main Flow bước 1: *"cả 2 [nguồn có sẵn/soạn mới] đều được
>    lưu vào ngân hàng câu hỏi"*:
>    - **Chọn có sẵn** — hành vi cũ, không đổi.
>    - **Soạn câu hỏi mới** — nhúng thẳng `QuestionEditorForm` (form soạn
>      tay đã có sẵn ở trang Ngân hàng câu hỏi); mỗi câu tạo xong tự động
>      gọi API gắn vào đề đang soạn ngay lập tức.
>    - **Nhập Excel/Word** — xem mục 2.
> 2. **Import hàng loạt câu hỏi qua file mẫu** (`QuestionImportService`,
>    `QuestionImportController` — `POST /api/question-banks/{bankId}/
>    questions/import`) — dùng cả ở trang Ngân hàng câu hỏi (import độc
>    lập vào 1 bank) lẫn trong bước Soạn đề (import rồi tự gắn luôn vào đề
>    đang soạn). Hỗ trợ 2 định dạng, mẫu CỨNG (không AI/OCR nhận diện tự
>    do — đã đánh giá đổi lấy độ chính xác, tránh sai sót khó kiểm soát):
>    - `.xlsx` — cột cố định vị trí A→N (Loại câu hỏi/Độ khó/Nội dung/
>      Đáp án A-D/Đáp án đúng/URL Audio/URL Hình ảnh/Transcript-Từ khóa
>      phát âm/Điểm/Giải thích/Tags). Mẫu dựng client-side (không endpoint
>      backend), theo đúng tiền lệ `ImportExcelButton.tsx`/
>      `GradeExcelImportPanel.tsx` (tránh phụ thuộc thư viện đọc/ghi Excel
>      ngoài có lỗ hổng chưa vá).
>    - `.docx` — mỗi câu hỏi 1 block, các block cách nhau 1 dòng `---`,
>      dòng đầu block dạng `[LOAI_CAU_HOI]`, các dòng sau `Nhãn: giá trị`
>      (so khớp không phân biệt hoa/thường/dấu) hoặc `A.`-`D.` cho đáp án.
>      Mẫu sinh ở backend bằng Apache POI (`GET /api/question-imports/
>      template.docx`, `QuestionImportService.buildWordTemplate()`).
>    - Phạm vi loại câu hỏi: CHỈ 5 loại UI mà `QuestionEditorForm.tsx` hỗ
>      trợ (`TRAC_NGHIEM`/`TRAC_NGHIEM_VOICE`/`DIEN_TU`/`TU_LUAN`/
>      `SPEAKING`) — KHÔNG mở rộng sang `TRUE_FALSE`/`MULTIPLE_ANSWER` dù
>      `Question.QuestionType` có 6 giá trị, để câu hỏi tạo qua import
>      luôn sửa lại được bằng form tay sẵn có.
>    - Lỗi từng dòng/block không chặn phần còn lại của file (mirror UC-53
>      Nhập điểm qua Excel); file đọc hỏng hoàn toàn → job `FAILED` ngay.
>      Dùng chung bảng `import_jobs` (`ImportJob.ImportType.QUESTIONS`,
>      không cần migration — cột `import_type` là `VARCHAR` tự do).
>    - Quyền: dùng chung `lms.question-bank.create` (không có quyền mới).
>
> **Bổ sung V65 (2026-07-30, đã xác nhận với người dùng) --- bỏ hẳn bước
> "Giao bài tập" khỏi Soạn & Giao đề:** endpoint `POST
> /api/exercises/{id}/assign` (tạo `ExerciseAssignment` + publish + thông
> báo học sinh trong 1 lần gọi) đã bị XÓA. Publish (`POST
> /api/exercises/{id}/publish`, giữ nguyên) giờ là hành động DUY NHẤT ở
> màn này cho MỌI loại đề (SELF_PRACTICE lẫn ASSIGNED) --- chỉ đổi
> `status=PUBLISHED`, không tạo assignment, không thông báo ai. Với đề
> ASSIGNED: việc tạo `ExerciseAssignment` (giao cho lớp, đặt deadline,
> thông báo học sinh) chuyển hẳn sang Nhận xét học viên (UC-21) --- Giáo
> viên chọn đề này làm "BTVN buổi sau" cho 1 học sinh trong 1 buổi DAILY
> thì hệ thống tự động giao cho TOÀN BỘ học sinh ACTIVE của lớp, hạn nộp =
> buổi kế tiếp (xem đầy đủ cơ chế + 5 quy tắc tại UC-21,
> `docs/uc/phan-he-06-hoc-thuat.md`). `listAssignmentsForClass` (xem lại
> lịch sử đã giao) giữ nguyên, không đổi. Lý do đổi: PM xác nhận điểm
> phát sinh giao bài nên gắn liền với buổi học/nhận xét thay vì tách rời
> ở màn soạn đề, tránh Giáo viên quên giao hoặc giao nhầm lớp không liên
> quan tới buổi đang dạy.
>
> **Gỡ câu hỏi khỏi Bài + Lưu trữ (ẩn) câu hỏi trong Ngân hàng (bổ sung
> ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31):**
>
> 1. **Gỡ câu hỏi khỏi Bài** — `DELETE /api/exercises/{id}/questions/
>    {exerciseQuestionId}` (`ExerciseService.removeQuestion`). Chỉ cho
>    phép khi Bài còn `DRAFT` (chưa Publish) — tránh gỡ câu hỏi khỏi Bài
>    đã giao học sinh làm (có thể đã phát sinh `StudentAnswer` cho câu
>    đó). Xóa cứng dòng `exercise_questions`, không soft-delete/lịch sử
>    (mirror `class_teachers`/join thuần — không phải "bản giao" cần lưu
>    vết như `ExerciseAssignment`). FE: nút gỡ (X) trên từng câu hỏi trong
>    danh sách câu hỏi của Bài, chỉ hiện khi Bài còn DRAFT
>    (`ExerciseAssignPage.tsx`).
> 2. **Lưu trữ (ẩn) câu hỏi trong Ngân hàng** — dùng lại cơ chế
>    `Question.status` (`ACTIVE`/`ARCHIVED`) và trường `status` trong
>    `UpdateQuestionRequest`/`QuestionBankService.updateQuestion` **đã có
>    sẵn từ trước** (không cần đổi backend) — chỉ bổ sung nút "Lưu trữ" ở
>    FE (`QuestionBankPage.tsx`, PUT lại đủ các field hiện có kèm
>    `status=ARCHIVED`, vì endpoint là full-update không phải PATCH).
>    Danh sách Ngân hàng lọc ẩn câu `ARCHIVED` ngay khi lưu trữ thành
>    công. Câu đã `ARCHIVED` vẫn giữ nguyên trong các Bài đã gắn từ trước
>    (không xóa `exercise_questions` liên quan) — chỉ ẩn khỏi việc chọn
>    thêm câu mới. Chưa có chức năng khôi phục lại qua giao diện (chỉ có
>    thể sửa tay qua DB nếu cần) — sẽ bổ sung sau nếu phát sinh nhu cầu.

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