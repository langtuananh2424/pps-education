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

> **Đa giáo viên + kết thúc phụ trách theo kỳ (bổ sung ngoài SDD gốc, đã
> xác nhận với người dùng 2026-07-31):** `class_teachers` vốn đã hỗ trợ
> NHIỀU giáo viên/lớp cùng lúc (`teacherRole` PRIMARY/ASSISTANT/
> SUBSTITUTE, không ràng buộc unique 1-giáo-viên-1-lớp) và lưu lịch sử
> qua `assignedFrom`/`assignedTo` (`NULL` = đang phụ trách) — chỉ thiếu
> thao tác SET `assignedTo` khi giáo viên đổi/nghỉ phụ trách (giáo viên
> lớp thường đổi theo kỳ). Bổ sung `PUT /api/classes/{id}/teachers/
> {classTeacherId}/end` (`ClassService.endTeacherAssignment`, quyền
> `academic.class.manage`) — đặt `assignedTo`, KHÔNG xóa cứng bản ghi
> (giữ nguyên lịch sử phụ trách trước đó), ghi `class_teachers_history`
> (`Action.UPDATED`). Từ chối nếu phân công đã kết thúc từ trước (không
> kết thúc 2 lần). FE: nút "Kết thúc phụ trách" trên mỗi dòng giáo viên
> đang ACTIVE (`assignedTo == null`) ở `ClassDetailPanel.tsx`.
>
> **Giai đoạn/Học kỳ — `academic_terms` (bổ sung ngoài SDD gốc, đã xác
> nhận với người dùng 2026-07-31):** trước đây `classes.semester` là
> VARCHAR tự do, không có business logic nào đọc/ghi có ý nghĩa. Người
> dùng xác nhận **bỏ hẳn `classes.semester`** (không thay bằng FK trên
> `classes`) — thay vào đó "Giai đoạn/Học kỳ" (VD: Giữa kỳ 1, Cuối kỳ 1,
> Giữa kỳ 2, Cuối kỳ 2) là bảng riêng `academic_terms` (site/code/name/
> startDate/endDate), **giới hạn theo điểm trường** ("Học kỳ sẽ giới hạn
> theo điểm trường. Mỗi trường mỗi khác" — nguyên văn người dùng), **độc
> lập với lớp học**: 1 lớp tồn tại xuyên suốt nhiều kỳ, KHÔNG gán cứng 1
> kỳ. Lý do: qua từng kỳ sĩ số lớp thay đổi do sắp xếp lại học sinh theo
> trình độ (VD học sinh học 8A-1 ở Giữa kỳ 1, chuyển 8A-2 ở Cuối kỳ 1,
> quay lại 8A-1 ở Giữa kỳ 2 — `StudentService.recordTransfer`, UC-14, đã
> hỗ trợ đúng kịch bản này qua lịch sử `class_enrollments`, không cần sửa
> gì thêm). "Hồ sơ lớp/học sinh theo kỳ" (sĩ số, giáo viên phụ trách,
> điểm danh, nhận xét, bài tập trong 1 kỳ) vì vậy là dữ liệu **TÍNH RA**
> (derived) bằng cách lọc các bảng đã có sẵn ngày tháng
> (`class_enrollments`, `class_teachers.assignedFrom/assignedTo`,
> `class_sessions.sessionDate`, `student_comments.commentDate`...) theo
> khoảng `[startDate, endDate]` của kỳ — KHÔNG cần bảng snapshot/join
> riêng. CRUD kỳ học: `AcademicTermService`/`AcademicTermController`
> (`POST/GET/PUT /api/academic-terms`, quyền `academic.class.manage` cho
> create/update, GET mở cho mọi actor đã đăng nhập), FE: nút "Quản lý học
> kỳ" ở `ClassesPage.tsx` (chỉ bật khi đã chọn 1 điểm trường cụ thể ở
> dropdown Header, không phải "Tất cả điểm trường"). Migration: dữ liệu
> lớp cũ (`semester` cũ) để trống hoàn toàn, không tự động convert — giáo
> vụ gán tay lại nếu cần (đã xác nhận với người dùng). **Phạm vi CHƯA làm
> ở đây**: màn hình báo cáo/thống kê tổng hợp theo kỳ (VD "kỳ I sĩ số lớp
> X là bao nhiêu, giáo viên nào dạy") — người dùng xác định đây là 1
> **phân hệ Báo cáo & Thống kê riêng sẽ triển khai sau**; phạm vi hiện tại
> chỉ đảm bảo mô hình dữ liệu (ngày tháng trên các bảng liên quan + khái
> niệm kỳ theo site) đã đủ chặt chẽ để phân hệ đó dùng được ngay khi triển
> khai, không cần retrofit lại schema.

---

UC-18b: Chuyển lớp hàng loạt cuối năm học

> ⚠️ Bổ sung ngoài SDD gốc (mở rộng phạm vi FR-ACA-02, xác nhận với người
> dùng ngày 2026-08-07) — UC-18 gốc chỉ khởi tạo 1 lớp mới trống. UC-18b bổ
> sung thao tác "lên lớp" cuối năm học: từ 1 lớp đang có (VD 6A, năm học
> 2025-2026), tạo 1 lớp MỚI (VD 7A, năm học 2026-2027) và chuyển hàng loạt
> toàn bộ học sinh đang học (ClassEnrollment ACTIVE + Student.status ACTIVE)
> sang lớp mới, tái dùng nguyên vẹn cơ chế "đánh dấu ghi danh cũ TRANSFERRED
> + tạo ghi danh mới ACTIVE" đã có ở UC-13 A2 (`StudentService.recordTransfer`)
> — áp dụng lặp lại cho cả lớp trong 1 transaction, không đổi bất kỳ điều gì
> ở UC-18/UC-13.

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-18b                                             |
+-----------------+----------------------------------------------------+
| **Tên Use       | Chuyển lớp hàng loạt cuối năm học                  |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-02 (bổ sung)                                |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Nhân viên (Giáo vụ), Trưởng phòng đào tạo          |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Cuối năm học, chuyển toàn bộ học sinh đang học của |
| tắt**           | 1 lớp sang 1 lớp mới ứng với năm học/cấp học tiếp  |
|                 | theo (VD 6A 2025-2026 → 7A 2026-2027) trong 1 thao |
|                 | tác duy nhất, thay vì chuyển tay từng học sinh qua |
|                 | UC-13. Giáo viên KHÔNG được copy sang lớp mới —    |
|                 | giáo vụ tự gán lại sau qua UC-18.                  |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Kết thúc năm học, cần "lên lớp" cho học sinh của 1 |
| hoạt**          | lớp sang lớp/cấp học của năm học kế tiếp.          |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người thao tác có quyền academic.class.manage  |
| tiên quyết      |     (khớp đúng quyền UC-18).                       |
| (               |                                                    |
| Precondition)** | -   Lớp nguồn đã tồn tại (UC-18).                  |
|                 |                                                    |
|                 | -   Khung chương trình cho lớp mới (ứng với cấp    |
|                 |     học tiếp theo) đã tồn tại và ACTIVE (UC-16/    |
|                 |     UC-17).                                        |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người dùng chọn lớp nguồn cần chuyển, nhập mã  |
| chính (Main     |     lớp mới, tên lớp mới, khung chương trình mới   |
| Flow)**         |     (chọn thủ công, KHÔNG tự suy luận từ cấp học), |
|                 |     năm học mới, ngày bắt đầu/kết thúc, sĩ số tối  |
|                 |     đa/tối thiểu của lớp mới.                      |
|                 |                                                    |
|                 | 2.  Hệ thống khởi tạo lớp mới, kế thừa nguyên Điểm  |
|                 |     trường (site) và Loại hình lớp (LINKED/OPEN)   |
|                 |     từ lớp nguồn — không cho đổi trong thao tác    |
|                 |     này (đổi site/loại hình dùng UC-13/UC-18 riêng).|
|                 |                                                    |
|                 | 3.  Với từng học sinh đang ghi danh ACTIVE ở lớp   |
|                 |     nguồn: nếu Student.status = ACTIVE, hệ thống   |
|                 |     đánh dấu ghi danh cũ TRANSFERRED (ngày hiệu    |
|                 |     lực = ngày bắt đầu lớp mới, lý do "Chuyển lớp   |
|                 |     hàng loạt lên [mã lớp mới]") và tạo ghi danh    |
|                 |     mới ACTIVE ở lớp mới — đúng cơ chế UC-13 A2,    |
|                 |     áp dụng lặp lại cho cả lớp trong 1 giao dịch    |
|                 |     duy nhất.                                      |
|                 |                                                    |
|                 | 4.  Hệ thống trả kết quả: lớp mới, số học sinh đã   |
|                 |     chuyển thành công, danh sách học sinh bị bỏ    |
|                 |     lại kèm lý do.                                 |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Học sinh không ở trạng thái ACTIVE***    |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Học sinh có Student.status khác ACTIVE          |
| Flow)**         |     (SUSPENDED/EXPELLED/GRADUATED/WITHDRAWN/       |
|                 |     DEFERRAL) bị bỏ lại — KHÔNG tạo ghi danh mới,   |
|                 |     ghi danh cũ ở lớp nguồn giữ nguyên không đổi.   |
|                 |     Hệ thống liệt kê rõ học sinh bị bỏ lại kèm lý   |
|                 |     do trong kết quả trả về.                        |
|                 |                                                    |
|                 | ***A2 --- Mã lớp mới đã tồn tại***                 |
|                 |                                                    |
|                 | 1.  Hệ thống từ chối toàn bộ thao tác, không tạo    |
|                 |     lớp mới, không đổi bất kỳ ghi danh nào ở lớp    |
|                 |     nguồn (rollback toàn bộ giao dịch).             |
|                 |                                                    |
|                 | ***A3 --- Khung chương trình mới không hợp lệ***   |
|                 |                                                    |
|                 | 1.  Nếu khung chương trình chưa ACTIVE, hoặc là     |
|                 |     khung tùy biến của điểm trường khác (không     |
|                 |     khớp Điểm trường của lớp nguồn), hệ thống từ    |
|                 |     chối toàn bộ thao tác — không tạo lớp mới, giữ  |
|                 |     nguyên trạng thái lớp nguồn.                    |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Lớp mới được khởi tạo (status PLANNED), sẵn    |
| (P              |     sàng cho giáo vụ gán giáo viên phụ trách qua    |
| ostcondition)** |     UC-18.                                          |
|                 |                                                    |
|                 | -   Toàn bộ học sinh Student.status=ACTIVE của lớp  |
|                 |     nguồn có ghi danh ACTIVE mới ở lớp mới; ghi     |
|                 |     danh cũ chuyển TRANSFERRED — hồ sơ học sinh     |
|                 |     (điểm, nhận xét, lịch sử ghi danh) xuyên suốt   |
|                 |     qua các năm học, không mất dữ liệu.             |
|                 |                                                    |
|                 | -   Học sinh non-ACTIVE giữ nguyên ghi danh cũ ở    |
|                 |     lớp nguồn, không bị động tới.                   |
+-----------------+----------------------------------------------------+

---

UC-65: Ghi danh học sinh theo lô

> ⚠️ Bổ sung ngoài SDD gốc (mở rộng phạm vi FR-ACA-02, xác nhận với người
> dùng ngày 2026-07-31) — UC-18 gốc chỉ có ghi danh TỪNG học sinh (tích
> chọn tay qua danh sách toàn hệ thống, `ClassService.enroll`). UC-65 bổ
> sung kênh Excel cho thao tác ghi danh HÀNG LOẠT, tái dùng NGUYÊN VẸN
> validate của `enroll()` cho từng dòng — không đổi bất kỳ điều gì ở UC-18.

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-65                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Ghi danh học sinh theo lô                          |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-02 (bổ sung)                                |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Nhân viên (Giáo vụ), Trưởng phòng đào tạo          |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Ghi danh nhiều học sinh ĐÃ TỒN TẠI SẴN trong hệ    |
| tắt**           | thống vào 1 lớp cùng lúc qua file Excel, thay vì   |
|                 | tích chọn tay từng em — khác UC-35/UC-50: KHÔNG    |
|                 | tạo học sinh/tài khoản mới, chỉ tạo class_enroll-  |
|                 | ments cho học sinh có sẵn (tra theo mã học sinh).  |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Lớp cần ghi danh nhiều học sinh cùng lúc (VD nhập  |
| hoạt**          | danh sách từ hệ thống cũ, hoặc lớp mới mở đã có    |
|                 | sẵn danh sách học sinh xác định trước).            |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Người thao tác có quyền academic.class.manage  |
| tiên quyết      |     (khớp đúng quyền ghi danh từng học sinh, UC-18).|
| (               |                                                    |
| Precondition)** | -   Lớp đích đã tồn tại (UC-18).                   |
|                 |                                                    |
|                 | -   Học sinh cần ghi danh đã có hồ sơ sẵn trong hệ |
|                 |     thống (tra theo mã học sinh, UC-13).           |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Người dùng mở tab Học sinh trong Quản lý lớp   |
| chính (Main     |     học (UC-18), tải file Excel mẫu (2 cột: mã học |
| Flow)**         |     sinh*, ngày ghi danh).                         |
|                 |                                                    |
|                 | 2.  Người dùng điền mã học sinh cho từng dòng, tải |
|                 |     file lên.                                      |
|                 |                                                    |
|                 | 3.  Với từng dòng: tra mã học sinh — không tìm     |
|                 |     thấy thì đánh dấu dòng lỗi, bỏ qua. Ngày ghi   |
|                 |     danh để trống thì mặc định ngày hôm nay.       |
|                 |                                                    |
|                 | 4.  Hệ thống gọi đúng `ClassService.enroll()` cho  |
|                 |     dòng hợp lệ — tái dùng nguyên vẹn validate đã  |
|                 |     có (học sinh đã ghi danh ACTIVE trong lớp thì  |
|                 |     báo lỗi dòng đó, không tạo trùng).             |
|                 |                                                    |
|                 | 5.  Hệ thống cập nhật total_rows/success_rows/     |
|                 |     failed_rows/error_summary, trạng thái COMPLETED|
|                 |     hoặc PARTIAL_SUCCESS. Người dùng xem kết quả + |
|                 |     danh sách dòng lỗi (nếu có) ngay trong response.|
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- File sai định dạng***                    |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  File rỗng hoặc thiếu dòng tiêu đề — hệ thống   |
| Flow)**         |     từ chối xử lý toàn bộ, đánh dấu                |
|                 |     import_jobs.status=FAILED ngay, không tạo bản  |
|                 |     ghi ghi danh nào.                              |
|                 |                                                    |
|                 | ***A2 --- Một phần dòng lỗi***                     |
|                 |                                                    |
|                 | 1.  1 hoặc nhiều dòng lỗi (mã học sinh không tồn   |
|                 |     tại, học sinh đã ghi danh ACTIVE sẵn trong lớp |
|                 |     này) — hệ thống vẫn ghi danh dòng hợp lệ, bỏ   |
|                 |     qua dòng lỗi, đánh dấu status=PARTIAL_SUCCESS, |
|                 |     liệt kê chi tiết từng dòng lỗi.                |
|                 |                                                    |
|                 | ***A3 --- Lớp đích không tồn tại***                |
|                 |                                                    |
|                 | 1.  Đây là lỗi của chính request (classId sai),    |
|                 |     không phải lỗi 1 dòng trong file — hệ thống    |
|                 |     báo lỗi 404 ngay, không tạo import_jobs nào.   |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   class_enrollments được tạo cho từng dòng hợp   |
| (P              |     lệ, trạng thái ACTIVE — không tạo học sinh/tài |
| ostcondition)** |     khoản mới nào (khác UC-35/UC-50).              |
|                 |                                                    |
|                 | -   Người dùng biết chính xác dòng nào thành công/ |
|                 |     thất bại và lý do, không cần đoán.             |
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
| tắt**           | nhận ở trạng thái nháp (DRAFT), có thể sửa/xoá tự  |
|                 | do; khi sẵn sàng, gửi duyệt lên Quản lý điểm       |
|                 | trường (SUBMITTED). Điểm chỉ hiển thị cho Phụ huynh |
|                 | sau khi Quản lý điểm trường duyệt (OFFICIAL).      |
|                 | Điểm tổng kết/Overall được Giáo viên nhập hoặc    |
|                 | import riêng qua UC-53, hệ thống không tự tính từ  |
|                 | điểm thành phần. V44 — bổ sung ngoài SDD gốc,     |
|                 | thay thế luồng "công bố dự kiến + phúc khảo" cũ,   |
|                 | áp dụng quy trình duyệt/từ chối với 2 hướng xử lý  |
|                 | (giáo viên sửa lại hoặc quản lý sửa).              |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Đến kỳ nhập điểm cho lớp học phụ trách; hoặc cần   |
| hoạt**          | sửa/xoá lại điểm còn Nháp hoặc REJECTED; hoặc      |
|                 | cần gửi duyệt lên Quản lý điểm trường.             |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Giáo viên được phân công giảng dạy lớp cần     |
| tiên quyết      |     nhập điểm (UC-18); HOẶC Trưởng phòng đào tạo   |
| (               |     (quyền academic.grade.manage); HOẶC Quản lý    |
| Precondition)** |     điểm trường phụ trách đúng điểm trường của lớp |
|                 |     — được phép nhập thay giáo viên khi cần hỗ trợ |
|                 |     (bổ sung ngoài SDD gốc, đã xác nhận với người  |
|                 |     dùng).                                         |
|                 |                                                    |
|                 | -   Để sửa/xoá 1 bản ghi đã tồn tại: bản ghi phải  |
|                 |     đang ở trạng thái Nháp (DRAFT) hoặc Từ chối     |
|                 |     (REJECTED), không giới hạn thời gian —         |
|                 |     HOẶC actor có quyền academic.grade.edit.override|
|                 |     (ngoại lệ — mặc định gán sẵn cho HEAD_ACADEMIC |
|                 |     và SITE_MANAGER — bỏ qua ràng buộc trạng thái).  |
|                 |                                                    |
|                 | -   Để gửi duyệt: bản ghi đang DRAFT, không quá    |
|                 |     giới hạn (nếu có) của kỳ duyệt.                |
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
|                 | 3.  Hệ thống ghi nhận điểm ở trạng thái nháp       |
|                 |     (DRAFT); Giáo viên có thể nhập/sửa/xoá rải     |
|                 |     rác nhiều lần, không giới hạn thời gian.       |
|                 |                                                    |
|                 | 4.  Khi sẵn sàng, Giáo viên gửi duyệt — hệ thống   |
|                 |     chuyển điểm từ DRAFT sang SUBMITTED (chờ duyệt |
|                 |     của Quản lý điểm trường). Nếu đây là lần đầu   |
|                 |     tiên gửi lên, hệ thống ghi nhận mốc thời gian  |
|                 |     (để tính thời gian chờ duyệt nếu cần).         |
|                 |                                                    |
|                 | 5.  Quản lý điểm trường duyệt hoặc từ chối qua     |
|                 |     UC-20; nếu duyệt → OFFICIAL (hiển thị phụ      |
|                 |     huynh); nếu từ chối → REJECTED (Giáo viên      |
|                 |     hoặc Quản lý có thể sửa & gửi lại/duyệt).      |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Điểm nhập không hợp lệ***                |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu score ngoài khoảng [0, max_score], hệ      |
| Flow)**         |     thống chặn lưu ngay tại phía Giáo viên, không  |
|                 |     để lọt xuống database.                         |
|                 |                                                    |
|                 | ***A2 --- Sửa/xoá bản ghi không ở trạng thái cho   |
|                 | phép***                                            |
|                 |                                                    |
|                 | 1.  Nếu actor không có quyền                       |
|                 |     academic.grade.edit.override và bản ghi đang   |
|                 |     SUBMITTED hoặc OFFICIAL, hệ thống chặn sửa/xoá.|
|                 |                                                    |
|                 | 2.  Nếu bản ghi REJECTED, Giáo viên hoặc Quản lý    |
|                 |     (có quyền academic.grade.edit.override) có thể  |
|                 |     sửa không giới hạn.                             |
|                 |                                                    |
|                 | ***A3 --- Gửi duyệt thất bại***                    |
|                 |                                                    |
|                 | 1.  Nếu có lỗi validate ở server hoặc bản ghi     |
|                 |     không còn ở DRAFT, hệ thống báo lỗi không cho  |
|                 |     gửi duyệt.                                     |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Sau khi nhập/sửa: Bản ghi ở DRAFT, giáo viên    |
| (P              |     có thể tiếp tục nhập/sửa/xoá không giới hạn.   |
| ostcondition)** |                                                    |
|                 | -   Sau khi gửi duyệt: Bản ghi ở SUBMITTED, chờ    |
|                 |     Quản lý điểm trường duyệt qua UC-20.           |
|                 |                                                    |
|                 | -   Bản ghi SUBMITTED/OFFICIAL KHÔNG được Giáo     |
|                 |     viên sửa/xoá trực tiếp — chờ Quản lý duyệt     |
|                 |     (OFFICIAL) hoặc từ chối (REJECTED → có thể sửa |
|                 |     lại).                                          |
|                 |                                                    |
|                 | -   Điểm tổng kết/Overall (UC-53) được Giáo viên   |
|                 |     nhập riêng, không tự tính từ thành phần.       |
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

UC-20: Duyệt/Từ chối điểm

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-20                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Duyệt/Từ chối điểm                                 |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-03                                          |
| năng gốc**      |                                                    |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Quản lý điểm trường (đúng site phụ trách);        |
|                 | Trưởng phòng đào tạo (mọi site)                   |
|                 |                                                    |
|                 | (Liên quan/hỗ trợ: Giáo viên (có thể sửa lại nếu  |
|                 | từ chối))                                          |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Quản lý điểm trường/Trưởng phòng đào tạo xem xét  |
| tắt**           | và duyệt/từ chối điểm mà Giáo viên gửi (SUBMITTED).|
|                 | Nếu duyệt → OFFICIAL, hiển thị ngay cho Phụ huynh |
|                 | qua Portal. Nếu từ chối → REJECTED, Giáo viên     |
|                 | hoặc Quản lý có thể sửa & gửi lại hoặc duyệt. V44  |
|                 | — bổ sung ngoài SDD gốc, thay thế luồng “công bố   |
|                 | dự kiến + phúc khảo” cũ.                          |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Có bản ghi điểm chờ duyệt (SUBMITTED) thuộc điểm   |
| hoạt**          | trường phụ trách; hoặc cần tiếp tục duyệt sau khi  |
|                 | Giáo viên sửa lại bản ghi từ chối (REJECTED).      |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Actor có quyền academic.grade.approve (mặc     |
| tiên quyết      |     định gán cho SITE_MANAGER, HEAD_ACADEMIC).     |
| (               |                                                    |
| Precondition)** | -   Bản ghi đang SUBMITTED (chờ duyệt) hoặc bản    |
|                 |     ghi REJECTED được Giáo viên sửa → tự động     |
|                 |     quay lại SUBMITTED để duyệt tiếp.             |
|                 |                                                    |
|                 | -   Quản lý điểm trường phải được gán phụ trách    |
|                 |     đúng site của lớp; Trưởng phòng đào tạo không  |
|                 |     bị giới hạn.                                   |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Quản lý điểm trường (hoặc Trưởng phòng đào     |
| chính (Main     |     tạo) mở danh sách điểm chờ duyệt (SUBMITTED)   |
| Flow)**         |     — của điểm trường mình phụ trách, hoặc của MỌI  |
|                 |     điểm trường nếu là Trưởng phòng.               |
|                 |                                                    |
|                 | 2.  Quản lý chọn bản ghi từng cái hoặc theo lô để  |
|                 |     duyệt/từ chối.                                 |
|                 |                                                    |
|                 | 3.  Quản lý chọn hành động: **Duyệt** hoặc          |
|                 |     **Từ chối**.                                    |
|                 |                                                    |
|                 | 4a. Nếu **Duyệt**: Hệ thống chuyển từ SUBMITTED →  |
|                 |     OFFICIAL, công khai điểm cho Phụ huynh ngay    |
|                 |     qua Portal (UC-25), gửi thông báo              |
|                 |     (notification) cho từng Phụ huynh có học sinh  |
|                 |     vừa được duyệt.                                |
|                 |                                                    |
|                 | 4b. Nếu **Từ chối**: Hệ thống chuyển từ SUBMITTED  |
|                 |     → REJECTED, ghi nhận người từ chối & thời điểm,|
|                 |     không công khai cho Phụ huynh (A1).            |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Quản lý từ chối***                        |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Nếu chọn "Từ chối", bản ghi chuyển SUBMITTED → |
| Flow)**         |     REJECTED. Giáo viên nhận thông báo (notification|
|                 |     ) điểm bị từ chối (tuỳ chọn có ghi lý do       |
|                 |     không).                                        |
|                 |                                                    |
|                 | 2.  Giáo viên (hoặc Quản lý) có 2 hướng xử lý:     |
|                 |     - **Hướng 1:** Giáo viên sửa → gửi duyệt lại   |
|                 |       (SUBMITTED), quản lý duyệt lần 2.            |
|                 |     - **Hướng 2:** Quản lý sửa luôn → vẫn REJECTED, |
|                 |       rồi duyệt riêng (bước 4a).                   |
|                 |                                                    |
|                 | ***A2 --- Quản lý sửa bản ghi REJECTED rồi duyệt***|
|                 |                                                    |
|                 | 1.  Quản lý có quyền academic.grade.edit.override  |
|                 |     có thể sửa 1 bản ghi REJECTED mà không cần Giáo|
|                 |     viên gửi lại.                                  |
|                 |                                                    |
|                 | 2.  Sau khi sửa xong, bản ghi vẫn ở REJECTED. Quản |
|                 |     lý chọn "Duyệt" → chuyển REJECTED → OFFICIAL    |
|                 |     ngay (không cần giáo viên xác nhận).           |
|                 |                                                    |
|                 | ***A3 --- Bản ghi không còn ở SUBMITTED***         |
|                 |                                                    |
|                 | 1.  Nếu bản ghi không còn ở SUBMITTED (đã OFFICIAL |
|                 |     hoặc REJECTED được chỉnh sửa), hệ thống báo    |
|                 |     lỗi không cho duyệt lại.                       |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   **Nếu duyệt:** Bản ghi SUBMITTED → OFFICIAL,    |
| (P              |     công khai cho Phụ huynh qua Portal ngay, không  |
| ostcondition)** |     còn sửa được (trừ Quản lý có override).        |
|                 |                                                    |
|                 | -   **Nếu từ chối:** Bản ghi SUBMITTED → REJECTED,  |
|                 |     không công khai cho Phụ huynh. Giáo viên nhận   |
|                 |     thông báo, có thể sửa & gửi duyệt lại hoặc để  |
|                 |     Quản lý sửa.                                   |
|                 |                                                    |
|                 | -   Phụ huynh nhận được thông báo (notification)   |
|                 |     ngay khi bản ghi được duyệt (OFFICIAL).        |
|                 |                                                    |
|                 | -   Bản ghi OFFICIAL không được sửa/xoá trực tiếp  |
|                 |     (chỉ Quản lý có quyền override nếu cần chỉnh    |
|                 |     sửa).                                          |
+-----------------+----------------------------------------------------+

---

UC-62: Phúc khảo điểm — ĐÃ GỠ BỎ (V44, xem UC-19/UC-20)

> **V44 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): UC-62 (Phúc
> khảo điểm, V43) đã bị gỡ bỏ hoàn toàn khỏi hệ thống** — thay bằng luồng
> Duyệt/Từ chối ở UC-20. Lý do: nghiệp vụ không cần "công bố dự kiến rồi
> chờ phúc khảo" nữa — điểm chỉ hiển thị cho Phụ huynh/Học sinh SAU KHI
> Quản lý điểm trường duyệt (OFFICIAL); nếu điểm sai, Quản lý từ chối
> (REJECTED) và Giáo viên sửa lại trước khi công khai, thay vì để lộ điểm
> sai rồi mới xử lý phúc khảo. Đã xoá: `GradeAppealService`,
> `GradeAppealController`, entity/bảng `grade_appeal_requests`, permission
> phái sinh, 2 job đêm trong `GradeSchedulerService` (tự động công bố dự
> kiến + tự động khoá Chính thức sau hạn phúc khảo), cấu hình hạn Y ngày
> phúc khảo (`academic.grade_appeal_window_days`). Xem migration V77.

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

-   **Bổ sung V70 (2026-07-31, đã xác nhận với người dùng) — fix bug
    thông báo bị gửi lặp nhiều lần cho toàn bộ học sinh trong lớp khi
    "Gửi nhận xét" hàng loạt:** UI "Gửi nhận xét" gửi N request riêng biệt
    khi Giáo viên submit hàng loạt cho N học sinh cùng 1 buổi. Vì quy tắc
    1 "Xung đột cùng buổi" ở trên bắt buộc mọi dòng DAILY cùng buổi phải
    chọn CÙNG 1 đề/video mỗi kênh, cả N request đều gọi
    `deliverToClass()` với ĐÚNG CÙNG (`exerciseId`/`reviewVideoSetId`,
    `classId`, `dueAt`) — trước đây mỗi lần gọi tạo mới hẳn 1
    `ExerciseAssignment`/`ReviewVideoAssignment` rồi thông báo lại cho
    TOÀN BỘ học sinh ACTIVE của lớp, nên 1 học sinh nhận N thông báo giống
    hệt nhau (kể cả học sinh không liên quan gì tới N request đó). Fix:
    `deliverToClass()` (cả 2 kênh) tìm bản ghi Assignment ACTIVE đã tồn
    tại cho ĐÚNG (`classId`, `videoSetId`/`exerciseId`, `dueAt`) trước khi
    tạo mới — có thì trả về nguyên bản ghi cũ, KHÔNG tạo bản ghi mới,
    KHÔNG thông báo lại. Với kênh Video, bước tái dùng này chạy TRƯỚC quy
    tắc hủy-giao-cũ của V69 (giao lại ở buổi SAU, `dueAt` khác, vẫn hủy
    ACTIVE cũ + tạo mới + thông báo lại như V69 — chỉ N request trùng
    `dueAt` trong CÙNG 1 đợt gửi mới được tái dùng).

-   **Bổ sung V82 (2026-08-04, đã xác nhận với người dùng) — báo Giáo viên
    % hoàn thành cả lớp khi hết hạn BTVN:** áp dụng cho CẢ 2 kênh (Ngữ
    pháp `ExerciseAssignment` lẫn Video Ôn tập `ReviewVideoAssignment`).
    Job `HomeworkDeadlineSchedulerService` quét mỗi 5 phút các bản giao
    `ACTIVE` có `due_at` đã qua và `teacher_notified_at IS NULL`, gửi
    thông báo (Portal + Email, loại `HOMEWORK_DEADLINE_SUMMARY`) cho đúng
    Giáo viên đã giao (`assigned_by`), nội dung gồm: tỷ lệ học sinh **đã
    làm bài** trên tổng số học sinh được giao (VD "10 em 7 em làm thì
    70%") + danh sách % hoàn thành từng em (tái dùng
    `HomeworkProgressService.grammarProgressLabel`/`videoProgressLabel`).
    "Đã làm bài" = khác `Chưa làm bài` (Ngữ pháp: đã nộp ít nhất 1 lần, kể
    cả đang chờ chấm cũng tính) hoặc tiến độ > 0% (Video: label luôn dạng
    "NN%", 0% coi là chưa làm) — không yêu cầu hoàn thành 100%. Đánh dấu
    `teacher_notified_at` ngay sau khi gửi để không gửi trùng khi job
    chạy lại. Bài KHÔNG có hạn nộp (`due_at IS NULL`, "bài tự luyện")
    không bao giờ khớp điều kiện quét — không bao giờ kích hoạt.

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

**Bổ sung V107 (2026-08-08, đã xác nhận với người dùng) — quản trị viên
vượt rào phân công dạy khi viết/gửi nhận xét MID_TERM/END_TERM:**
`StudentCommentService#requireAssignedTeacher` (dùng cho tạo/sửa/gửi nhận
xét MID_TERM/END_TERM — nhận xét DAILY đã có đường vượt rào riêng qua
`academic.comment.approve`, xem bullet phía trên) trước đây CHỈ chấp nhận
Giáo viên được phân công dạy đúng lớp — quản trị viên (SYS_ADMIN) dù đã có
sẵn `academic.comment.write` ở Controller vẫn bị chặn ở Service. Thêm
quyền `academic.comment.manage` (gán HEAD_ACADEMIC + SYS_ADMIN, migration
`V107__admin_manage_permissions_for_class_scoped_lms_features.sql`) để
vượt rào này — cũng được kiểm tra thêm trong `requireCanWriteDailyComment`
để quản trị viên vượt rào cả nhận xét DAILY dù không có
`academic.comment.approve`.

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
|                 |                                                    |
|                 | ***A2 --- Sửa trực tiếp nội dung trước khi duyệt   |
|                 | (bổ sung ngoài SDD gốc, đã xác nhận với người      |
|                 | dùng 2026-08-02)***                                |
|                 |                                                    |
|                 | 1.  Nếu nội dung nhận xét (Chờ duyệt) có lỗi        |
|                 |     chính tả/câu chữ nhỏ, Quản lý điểm trường có   |
|                 |     thể tự sửa trực tiếp thay vì Từ chối bắt Giáo   |
|                 |     viên sửa lại — giảm tải qua lại không cần       |
|                 |     thiết.                                         |
|                 |                                                    |
|                 | 2.  Chỉ sửa được phần nội dung câu chữ (content) —  |
|                 |     KHÔNG sửa được Thái độ học tập/BTVN buổi        |
|                 |     trước-sau/Ghi chú (để tránh vô tình kích hoạt   |
|                 |     lại cơ chế tự động giao bài cho cả lớp của      |
|                 |     V65 nếu đổi BTVN buổi sau).                     |
|                 |                                                    |
|                 | 3.  Sau khi Lưu, nhận xét vẫn ở trạng thái Chờ      |
|                 |     duyệt (PENDING) — Quản lý điểm trường vẫn phải  |
|                 |     bấm Duyệt/Từ chối riêng ở bước 3 Main Flow,     |
|                 |     Sửa và Duyệt là 2 thao tác tách biệt.           |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Nhận xét APPROVED được công khai đúng đối      |
| (P              |     tượng xem (Phụ huynh; thêm Đại diện trường     |
| ostcondition)** |     liên kết nếu có cảnh báo).                     |
|                 |                                                    |
|                 | -   Nhận xét REJECTED quay về Giáo viên, chưa hiển |
|                 |     thị cho bất kỳ ai bên ngoài.                   |
+-----------------+----------------------------------------------------+

> **A2 — Sửa trực tiếp nội dung (bổ sung ngoài SDD gốc, đã xác nhận với
> người dùng 2026-08-02):** endpoint mới `PUT /api/comments/pending/{id}/content`
> (`StudentCommentService#updatePendingCommentContent`), quyền
> `academic.comment.approve` (dùng lại, không tạo quyền mới) + kiểm tra
> đúng Quản lý điểm trường của site chứa lớp
> (`requireSiteManagerForSite`). Chỉ chấp nhận khi nhận xét đang PENDING —
> ném `StudentCommentNotEditableException` nếu không. Dùng DTO RIÊNG
> `UpdateStudentCommentContentRequest` (chỉ có `content`/`structuredContent`)
> — KHÔNG dùng chung `UpdateStudentCommentRequest` (của Giáo viên) vì DTO
> đó có các trường BTVN buổi sau (`homeworkNextExerciseId`...) sẽ tự động
> tạo/đổi bản giao bài cho CẢ LỚP (V65) nếu vô tình truyền vào. Ghi lịch sử
> qua `student_comments_history` (`Action.UPDATED`) như các lần sửa khác —
> không có badge/thông báo riêng cho Giáo viên biết nội dung đã bị sửa
> (đã xác nhận với người dùng, không cần thiết ở giai đoạn này). FE: nút
> "Sửa" cạnh nút Duyệt/Từ chối ở `CommentApprovalByClass.tsx`, mở
> `<textarea>` inline tại dòng, có nút Lưu/Hủy riêng.

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

---

UC-66: Thống kê BTVN theo lớp

+-----------------+----------------------------------------------------+
| **Mã Use Case** | UC-66                                              |
+-----------------+----------------------------------------------------+
| **Tên Use       | Thống kê BTVN theo lớp                             |
| Case**          |                                                    |
+-----------------+----------------------------------------------------+
| **Phân hệ**     | Phân hệ 6                                          |
+-----------------+----------------------------------------------------+
| **Yêu cầu chức  | FR-ACA-07 (bổ sung ngoài SDD gốc, đã xác nhận với  |
| năng gốc**      | người dùng 2026-08-05)                             |
+-----------------+----------------------------------------------------+
| **Tác nhân**    | Giáo viên, Quản lý điểm trường                     |
+-----------------+----------------------------------------------------+
| **Mô tả tóm     | Giáo viên/Quản lý điểm trường xem thống kê tổng    |
| tắt**           | hợp kết quả BTVN đã giao cho 1 lớp: % hoàn thành,  |
|                 | tỷ lệ đạt, kết quả từng học sinh, và phân tích câu |
|                 | hỏi hay bị sai — xuất được file Excel.             |
+-----------------+----------------------------------------------------+
| **Sự kiện kích  | Giáo viên/Quản lý điểm trường cần đánh giá tiến độ |
| hoạt**          | và chất lượng làm BTVN của 1 lớp.                  |
+-----------------+----------------------------------------------------+
| **Điều kiện     | -   Giáo viên được phân công giảng dạy lớp (hoặc   |
| tiên quyết      |     Quản lý điểm trường phụ trách điểm trường của  |
| (               |     lớp).                                          |
| Precondition)** | -   Lớp đã có ít nhất 1 BTVN được giao              |
|                 |     (exercise_assignments, gồm cả ASSIGNED và       |
|                 |     SELF_PRACTICE — xem UC-27 V65).                |
+-----------------+----------------------------------------------------+
| **Luồng sự kiện | 1.  Actor chọn lớp cần xem — hệ thống hiển thị     |
| chính (Main     |     danh sách BTVN đã giao cho lớp, mỗi BTVN kèm   |
| Flow)**         |     % học sinh đã hoàn thành và tỷ lệ đạt (dựa     |
|                 |     trên exercises.pass_threshold_percent, V89).   |
|                 |                                                    |
|                 | 2.  Actor chọn 1 BTVN → xem kết quả từng học sinh: |
|                 |     trạng thái (chưa làm/đang làm/đã nộp/trễ hạn), |
|                 |     điểm, phần trăm, đạt/chưa đạt — đọc trực tiếp  |
|                 |     từ exercise_attempts.passed/total_score đã     |
|                 |     được tính sẵn (ExerciseAttemptService#         |
|                 |     applyPassOutcome), không tính lại.             |
|                 |                                                    |
|                 | 3.  Actor xem phân tích theo câu hỏi: câu nào bị   |
|                 |     sai nhiều nhất và những học sinh nào sai câu   |
|                 |     đó (câu tự luận/Nói chưa được Giáo viên chấm   |
|                 |     tay bị loại khỏi thống kê này).                |
|                 |                                                    |
|                 | 4.  Actor xuất file Excel kết quả từng học sinh    |
|                 |     của BTVN đang xem.                             |
+-----------------+----------------------------------------------------+
| **Luồng thay    | ***A1 --- Lớp chưa có BTVN nào***                  |
| thế / ngoại lệ  |                                                    |
| (Alternate      | 1.  Hệ thống hiển thị danh sách rỗng, không báo    |
| Flow)**         |     lỗi.                                           |
|                 |                                                    |
|                 | ***A2 --- Actor không có quyền xem lớp***          |
|                 |                                                    |
|                 | 1.  Nếu actor không được phân công giảng dạy lớp   |
|                 |     và không phụ trách điểm trường của lớp, hệ     |
|                 |     thống từ chối truy cập (403).                  |
+-----------------+----------------------------------------------------+
| **Hậu điều kiện | -   Không thay đổi dữ liệu — UC thuần đọc/báo cáo. |
| (P              |                                                    |
| ostcondition)** |                                                    |
+-----------------+----------------------------------------------------+

**Bổ sung V107 (2026-08-08, đã xác nhận với người dùng) — quản trị viên
vượt rào phân công dạy (mở rộng A2):** trước V107, A2 chỉ chấp nhận đúng 2
trường hợp (Giáo viên được phân công dạy lớp, Quản lý điểm trường phụ
trách điểm trường của lớp) — quản trị viên (SYS_ADMIN) dù đã có sẵn
`lms.exercise.report.view` ở Controller vẫn bị 403 ở Service. Thêm quyền
`lms.exercise-report.manage` (gán HEAD_ACADEMIC + SYS_ADMIN, migration
`V107__admin_manage_permissions_for_class_scoped_lms_features.sql`) làm
trường hợp thứ 3 vượt qua A2 — quản trị viên xem thống kê BTVN của lớp bất
kỳ.

Phân hệ 7 --- Cổng thông tin và E-Learning (Portal & LMS)
