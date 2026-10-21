# Hướng dẫn tạo file Excel soạn câu hỏi (theo kỹ năng, có ví dụ)

Áp dụng cho tính năng **Soạn đề nhanh bằng Excel** (UC-40) — nút *Import* ở trang
"Ngân hàng câu hỏi" và bước "Soạn đề" trong "Soạn & giao đề". Tài liệu bám theo
code `QuestionImportService` / `ExcelQuestionRowParser` (không phải suy đoán).

---

## 1. Quy tắc chung (đọc trước)

### 1.1 File và sheet
- Chỉ nhận **`.xlsx`** (hoặc `.docx`). Không nhận `.xls`, `.csv`.
- **Dòng 1 = tiêu đề cột**, từ dòng 2 trở đi là dữ liệu. Mỗi dòng = 1 câu hỏi
  (riêng 3 loại *nhóm* ở mục 3–4 thì 1 dòng sinh ra nhiều câu).
- File có thể có **nhiều sheet**: hệ thống đọc lần lượt mọi sheet. Sheet nào thiếu
  cột "Nội dung" hoặc "Loại câu hỏi" (VD sheet "Hướng dẫn") sẽ **tự bị bỏ qua**.
  File chỉ có đúng 1 sheet mà thiếu 2 cột này thì báo lỗi cả file.
- **Cách dễ nhất**: bấm *Tải file mẫu* trong panel Import — file mẫu đã tách sẵn sheet
  theo nhóm loại câu hỏi và lọc theo kỹ năng của Bài đang soạn.

### 1.2 Các cột (đọc theo TÊN tiêu đề, không theo vị trí)
Thứ tự cột tuỳ ý; tiêu đề chấp nhận cả tiếng Việt lẫn tiếng Anh, không phân biệt
hoa/thường, có hoặc không dấu.

| Tiêu đề (Việt) | Tiêu đề (Anh) | Dùng cho |
|---|---|---|
| Loại câu hỏi | Question Type | Mã loại (mục 2–6), xem 1.3 |
| Độ khó | Difficulty | `EASY` / `MEDIUM` / `HARD` (trống = `MEDIUM`) |
| Nội dung | Content / Question | Đề bài (**bắt buộc**, trừ `DOC_DIEN_TU`) |
| Đáp án A, B, C, D | Answer A… / Option A… | Lựa chọn trắc nghiệm |
| Đáp án đúng | Correct Answer | Chữ cái, từ cần điền, hoặc danh sách `a\|b\|c` |
| URL Audio | Audio URL | Link file nghe đã upload sẵn |
| URL Hình ảnh | Image URL | Link ảnh đã upload sẵn |
| Đoạn văn tham chiếu / Transcript / Từ khóa phát âm | Reference Passage / Transcript / Keywords | Cột "đa nghĩa", tuỳ loại (xem từng mục) |
| Điểm | Points | Số, trống = `1`. Dùng `.` hoặc `,` đều được |
| Giải thích | Explanation | Hiện sau khi chấm |
| Tags | Tags | Phân cách bằng dấu phẩy, VD `geo,easy` |
| Hướng dẫn | Instruction | Dòng chỉ dẫn hiện phía trên đề (chỉ loại Điền từ - Hộp từ vựng/Đoạn văn) |

> Cột "Hướng dẫn chi tiết" trong file mẫu chỉ để đọc, hệ thống bỏ qua khi import.

### 1.3 Cột "Loại câu hỏi" và "Loại mặc định"
- Giá trị phải là **mã** (VD `TRAC_NGHIEM`), không điền tên tiếng Việt/tiếng Anh dài.
  Hệ thống tự bỏ dấu, đổi khoảng trắng/gạch ngang thành `_`, nên `trắc nghiệm` cũng
  hiểu thành `TRAC_NGHIEM`.
- Khi import, có thể chọn **"Loại câu hỏi mặc định"** ở panel → khi đó cột "Loại câu
  hỏi" **để trống được** (dòng nào tự ghi loại vẫn được ưu tiên).
  Thực tế 1 Ex chỉ có 1 loại câu hỏi nên nên dùng cách này.

### 1.4 Ký hiệu đặc biệt (rất hay nhầm)
| Ký hiệu | Nghĩa | Ví dụ |
|---|---|---|
| `\|` (gạch đứng) | Ngăn cách các **phần tử có thứ tự** (nhiều chỗ trống, nhiều khối, nhiều câu, nhiều ảnh) | `goes\|plays` |
| `/` (gạch chéo) | Nhiều **đáp án cùng được chấp nhận** cho 1 chỗ trống | `went/have gone` |
| `___` (3 gạch dưới) | Vị trí chỗ trống trong đề | `She ___ to school.` |
| `,` (phẩy) | Ngăn cách danh sách Tags hoặc từ trong hộp từ vựng | `under, next to, behind` |

### 1.5 Lỗi từng dòng
Dòng lỗi **không chặn** dòng khác: các dòng đúng vẫn được tạo, dòng sai được liệt kê
trong báo cáo kèm lý do (nếu file nhiều sheet: *"Trắc nghiệm · dòng 5"*). Câu trùng
nội dung với câu đã có trong ngân hàng cũng bị từ chối. File hỏng hẳn → báo lỗi cả file.

### 1.6 Link audio/ảnh
Cột URL Audio/URL Hình ảnh chứa **đường link đã upload sẵn** (qua Ngân hàng câu
hỏi/API media upload) — không dán file vào Excel.

---

## 2. Kỹ năng TỪ VỰNG – NGỮ PHÁP (`VOCAB_GRAMMAR`)

Nhóm này có nhiều loại nhất. Mã loại được phép: `TRAC_NGHIEM`, `TRAC_NGHIEM_VOICE`,
`DIEN_TU`, `DIEN_TU_NHOM`, `DIEN_TU_HOP_TU_VUNG`, `DIEN_TU_HOP_TU_VUNG_ANH`,
`DIEN_TU_DOAN_VAN`, `SAP_XEP_CAU`, `SAP_XEP_CHU_CAI`.

### 2.1 `TRAC_NGHIEM` — Trắc nghiệm (chấm tự động)
- **Bắt buộc:** Nội dung, Đáp án A, Đáp án B, Đáp án đúng.
- **Tuỳ chọn:** Đáp án C/D, URL Hình ảnh (ảnh đề bài), Đoạn văn tham chiếu.
- Chỉ cần A và B (câu 2 lựa chọn); C/D điền thêm nếu có, **không được bỏ trống xen
  giữa** (có D mà thiếu C là lỗi).
- Đáp án đúng = **đúng 1 chữ cái** nằm trong số lựa chọn đã điền.

| Loại câu hỏi | Độ khó | Nội dung | Đáp án A | Đáp án B | Đáp án C | Đáp án D | Đáp án đúng | Điểm | Giải thích |
|---|---|---|---|---|---|---|---|---|---|
| TRAC_NGHIEM | EASY | What is the capital of France? | London | Paris | Berlin | Madrid | B | 1 | Paris là thủ đô nước Pháp. |

### 2.2 `TRAC_NGHIEM_VOICE` — Trắc nghiệm Voice
Như `TRAC_NGHIEM` nhưng **bắt buộc thêm URL Audio**. Cột "Đoạn văn tham chiếu/Transcript"
dùng cho lời thoại của audio (không bắt buộc).

| Loại câu hỏi | Nội dung | Đáp án A | Đáp án B | Đáp án C | Đáp án D | Đáp án đúng | URL Audio | Transcript |
|---|---|---|---|---|---|---|---|---|
| TRAC_NGHIEM_VOICE | Listen and choose the word you hear. | ship | sheep | chip | cheap | B | https://…/mau.mp3 | sheep |

### 2.3 `DIEN_TU` — Điền từ (1 chỗ trống, chấm tự động)
- **Bắt buộc:** Nội dung, Đáp án đúng. **Tuỳ chọn:** URL Hình ảnh.
- Nhiều đáp án chấp nhận → phân tách bằng `/`.

| Loại câu hỏi | Nội dung | Đáp án đúng | Giải thích |
|---|---|---|---|
| DIEN_TU | She ___ (go) to school every day. | goes | Hiện tại đơn, ngôi thứ 3 số ít. |
| DIEN_TU | I ___ (live) here since 2010. | have lived/'ve lived | Chấp nhận cả 2 cách viết. |

### 2.4 `DIEN_TU_NHOM` — Nhiều câu điền từ (1 dòng → N câu riêng)
Dùng cho "Ex. 1: Choose the correct word…" gồm nhiều câu. **1 dòng tạo ra N câu hỏi
độc lập**, mỗi câu có điểm/nhãn "Câu N." riêng (không chấm all-or-nothing).

- **Nội dung:** các câu cách nhau bằng `|`.
- **Đáp án đúng:** các đáp án cách nhau bằng `|`, **cùng số lượng và đúng thứ tự** với câu.
- Một câu nhiều đáp án: dùng `/` ngay trong phần tử đó (`smart/clever|favourite|coach`).
- **Tuỳ chọn:** Transcript = hộp từ vựng tham khảo (hiện chung 1 lần, ngăn cách bằng
  phẩy); URL Hình ảnh = ảnh riêng từng câu cách nhau bằng `|` (câu không ảnh để trống
  vị trí đó, VD `a.png||c.png`); URL Audio = audio dùng chung cho cả nhóm (nghe điền).
- Không được có câu rỗng, không được 2 câu trùng nhau trong cùng nhóm.

| Loại câu hỏi | Nội dung | Đáp án đúng | Transcript |
|---|---|---|---|
| DIEN_TU_NHOM | Tom is very ___.\|English is my ___ subject.\|Our football ___ helps us win the game. | smart\|favourite\|coach | activity, advanced, beginner, smart, coach, favourite |

→ Sinh ra 3 câu: "Tom is very ___." (đáp án smart), v.v.

### 2.5 `DIEN_TU_HOP_TU_VUNG` — Điền từ, Hộp từ vựng (1 câu nhiều chỗ trống, chấm gộp)
- **Nội dung** có nhiều `___`; **Đáp án đúng** = danh sách `|` theo **đúng thứ tự** chỗ trống.
- Chấm **all-or-nothing** cho cả câu. Hộp từ vựng của học sinh = chính các đáp án.

| Loại câu hỏi | Nội dung | Đáp án đúng |
|---|---|---|
| DIEN_TU_HOP_TU_VUNG | She ___ to school every day. He ___ football on Sundays. | goes\|plays |

### 2.6 `DIEN_TU_HOP_TU_VUNG_ANH` — Hộp từ vựng có ảnh
Như 2.5 nhưng thêm **URL Hình ảnh** (ảnh minh hoạ) và cột **Transcript** = hộp từ vựng
hiển thị cho học sinh (có thể thêm từ nhiễu; để trống thì hộp từ = đáp án đúng).

| Loại câu hỏi | Nội dung | Đáp án đúng | URL Hình ảnh | Transcript |
|---|---|---|---|---|
| DIEN_TU_HOP_TU_VUNG_ANH | 1. The cat is ___ the bed. 2. The ball is ___ the box. | under\|next to | https://…/phong.png | under, next to, behind, in front of, on |

### 2.7 `DIEN_TU_DOAN_VAN` — Điền từ, đoạn văn liền mạch (gõ tay)
Giống 2.6 về cách chấm (không phân biệt hoa/thường, theo thứ tự) nhưng học sinh **gõ
vào ô nhập chữ** thay vì chọn từ danh sách. Hộp từ chỉ để **tham khảo**.

- **Đáp án đúng** cho phép **phần tử rỗng** (VD điền mạo từ a/an/the/Ø: `a||the` →
  chỗ trống thứ 2 để trống là đúng).
- **Hướng dẫn** (tuỳ chọn): dòng chỉ dẫn in nghiêng phía trên đề.

| Loại câu hỏi | Hướng dẫn | Nội dung | Đáp án đúng | Transcript |
|---|---|---|---|---|
| DIEN_TU_DOAN_VAN | Ex. 1: Fill in each blank (1-2) with the most appropriate word or phrase. | In recent years, tourism has become a (1) ___ industry. Many travellers now book a (2) ___ that includes both transport and accommodation. | growing\|package deal | growing, package deal, accommodation, destination, resort |

### 2.8 `SAP_XEP_CAU` — Sắp xếp câu
- **Đáp án đúng** = các khối từ/cụm theo **đúng thứ tự câu hoàn chỉnh**, cách nhau `|`.
- Tối thiểu **2 khối**.
- **Nội dung** có 2 cách dùng:
  - Câu hướng dẫn thường (không có dấu `/`) — hệ thống **tự xáo trộn ngẫu nhiên** các khối mỗi lần học
    sinh làm bài (như trước giờ).
  - Liệt kê sẵn CHÍNH các khối theo **thứ tự xáo trộn cố định**, cách nhau dấu `/` (VD "capital / is /
    Paris / ..."). Khi dùng cách này, **tập khối liệt kê ở Nội dung phải khớp CHÍNH XÁC** (không thiếu/
    thừa/lệch từ, chỉ khác THỨ TỰ) với tập khối ở Đáp án đúng — sai thì BE báo lỗi ngay lúc import.
    Học sinh sẽ thấy khối hiện ĐÚNG theo thứ tự đã liệt kê ở Nội dung (không xáo trộn thêm nữa).

| Loại câu hỏi | Nội dung | Đáp án đúng |
|---|---|---|
| SAP_XEP_CAU | Sắp xếp thành câu hoàn chỉnh. | This\|is\|a\|pen |
| SAP_XEP_CAU | Put the words in order. | I\|usually\|get up\|at six |
| SAP_XEP_CAU | capital / is / Paris / France / of / the / famous / for / its / architecture / . | Paris\|is\|the\|capital\|of\|France\|famous\|for\|its\|architecture\|. |

### 2.9 `SAP_XEP_CHU_CAI` — Sắp xếp chữ cái
Mỗi chữ cái 1 phần tử, `|` ngăn cách, đúng thứ tự tạo từ đúng; tối thiểu 2 chữ cái.
Tuỳ chọn **URL Hình ảnh** (ảnh gợi ý nghĩa của từ).

| Loại câu hỏi | Nội dung | Đáp án đúng | URL Hình ảnh |
|---|---|---|---|
| SAP_XEP_CHU_CAI | Sắp xếp chữ cái thành từ đúng (nghĩa: nụ cười). | s\|m\|i\|l\|e | https://…/smile.png |

---

## 3. Kỹ năng ĐỌC (`READING`)

Mã loại: `DOC_HIEU_LUOI`, `DOC_DIEN_TU`. Cả hai đều **1 dòng → nhiều câu** cùng chia sẻ 1
đoạn văn (cột "Đoạn văn tham chiếu"). Ngoài ra Reading còn dùng được `TRAC_NGHIEM`
(mục 2.1) để làm nhiều câu **độc lập** quanh 1 đoạn văn (xem 3.3).

### 3.1 `DOC_HIEU_LUOI` — Đọc hiểu dạng lưới (dùng chung bộ đáp án)
- **Đoạn văn tham chiếu**: bắt buộc.
- **Nội dung**: các câu hỏi cách nhau `|` (tối thiểu 2 câu, không câu rỗng/trùng).
- **Đáp án A–D**: **1 bộ dùng chung cho mọi câu** (KHÔNG dùng `|`); tối thiểu A và B.
- **Đáp án đúng**: chữ cái cho từng câu, cách nhau `|`, **đúng thứ tự** và đủ số lượng.

| Loại câu hỏi | Nội dung | Đáp án A | Đáp án B | Đáp án C | Đáp án đúng | Đoạn văn tham chiếu |
|---|---|---|---|---|---|---|
| DOC_HIEU_LUOI | Who loves a subject because of visiting museums?\|Who says friends think their hobby is strange?\|Who talks about both a school subject and a sport? | Tom | Max | Anna | B\|A\|C | Tom: I go to a big school in London…  (xuống dòng) Max: I live in New York… (xuống dòng) Anna: I am a new student in Paris… |

(Trong ô Excel, xuống dòng bằng **Alt+Enter**.)

### 3.2 `DOC_DIEN_TU` — Đọc điền từ Cloze (mỗi chỗ trống bộ đáp án riêng)
- **Đoạn văn tham chiếu**: bắt buộc, đánh số chỗ trống `(1)___`, `(2)___`…
- **Số chỗ trống = số phần tử `|` ở cột Đáp án A** (tối thiểu 2). Đáp án B (bắt buộc,
  vì cần ≥ 2 lựa chọn/chỗ), C, D nếu có phải **cùng số phần tử**.
- **Đáp án đúng**: chữ cái cho từng chỗ trống, `|` ngăn cách, đủ số lượng.
- **Nội dung** (tuỳ chọn): nhãn từng chỗ trống cách nhau `|`; để trống → tự đặt
  "Chỗ trống 1/2/3".

| Loại câu hỏi | Nội dung | Đáp án A | Đáp án B | Đáp án C | Đáp án đúng | Đoạn văn tham chiếu |
|---|---|---|---|---|---|---|
| DOC_DIEN_TU | *(để trống)* | environment\|timetable\|hours | equipment\|subject\|lessons | job\|homework\|subjects | B\|A\|C | The school has an excellent (1)___ with many computers. Every Monday, students check their new (2)___. Most students prefer creative (3)___ like art and music. |

→ Chỗ trống 1: A.environment / **B.equipment** / C.job → đáp án B…

### 3.3 Nhiều câu `TRAC_NGHIEM` độc lập cùng 1 đoạn văn
Không cần loại riêng: ghi **nhiều dòng liên tiếp** `TRAC_NGHIEM`, **cùng nội dung ô
"Đoạn văn tham chiếu"** (khớp nguyên văn). Hệ thống tự gộp thành 1 nhóm → đoạn văn chỉ
hiện **1 lần** thay vì lặp trước mỗi câu. Mỗi câu có 4 đáp án riêng.
Lưu ý: chỉ gộp khi từ **2 dòng liền nhau** trở lên; chen 1 dòng khác đoạn văn vào giữa
sẽ cắt nhóm.

---

## 4. Kỹ năng NGHE (`LISTENING`)

Mã loại: `TRAC_NGHIEM_VOICE` (2.2), `NGHE_DIEN_TU`, `NGHE_CHON_HINH`, `NGHE_NOP_AUDIO`,
`NGHE_PHIEU_THONG_TIN`, `NGHE_NHIEU_DAP_AN`. Tất cả **bắt buộc URL Audio**. (Giáo viên nước ngoài chỉ soạn
được nhóm Nghe.)

### 4.1 `NGHE_DIEN_TU` — Nghe điền từ (tự chấm)
**Bắt buộc:** Nội dung, URL Audio, Đáp án đúng (nhiều đáp án chấp nhận cách nhau `/`).

| Loại câu hỏi | Nội dung | Đáp án đúng | URL Audio |
|---|---|---|---|
| NGHE_DIEN_TU | Listen and fill in the blank: She usually ___ to work. | drives/rides | https://…/nghe-dien-tu.mp3 |

Muốn nhiều chỗ trống chung 1 audio, dùng `DIEN_TU_NHOM` có điền URL Audio (2.4).

### 4.2 `NGHE_CHON_HINH` — Nghe chọn hình
- **URL Hình ảnh** = danh sách ảnh đáp án, **mỗi ảnh 1 lựa chọn** theo thứ tự A/B/C(/D),
  cách nhau `|` (**2–4 ảnh**, không ảnh nào rỗng).
- **Đáp án đúng**: 1 chữ cái trong số ảnh đã có.
- Đáp án A–D (tuỳ chọn) = chú thích chữ dưới ảnh; để trống thì dùng chữ cái.

| Loại câu hỏi | Nội dung | URL Audio | URL Hình ảnh | Đáp án đúng |
|---|---|---|---|---|
| NGHE_CHON_HINH | What time is it? | https://…/gio.mp3 | https://…/dong-ho-a.png\|https://…/dong-ho-b.png\|https://…/dong-ho-c.png | B |

### 4.3 `NGHE_NOP_AUDIO` — Nghe & nộp audio (chấm tay)
Chỉ cần Nội dung + URL Audio. Học sinh ghi âm câu trả lời, giáo viên chấm ở
*Hàng chờ chấm bài*.

| Loại câu hỏi | Nội dung | URL Audio |
|---|---|---|
| NGHE_NOP_AUDIO | Listen to the audio and record your answer. | https://…/nghe.mp3 |

### 4.4 `NGHE_PHIEU_THONG_TIN` — Nghe điền phiếu thông tin (IELTS form completion)
- **Nội dung**: nguyên phiếu, mỗi chỗ trống ghi `___` (có thể kèm số `(14) ___`). Dòng
  đầu đứng riêng, cách 1 dòng trống → thành tiêu đề đậm; `**chữ**` → in đậm tiêu đề mục.
- **Đáp án đúng**: 1 phần tử cho mỗi chỗ trống, `|` ngăn cách; mỗi ô nhiều đáp án
  chấp nhận cách nhau `/`. **Số `___` trong Nội dung phải bằng số phần tử đáp án.**
- Số/giờ được chuẩn hoá (`7:30`, `7.30`, `7h30`, `five` = `5` coi như nhau).
- **Transcript** = dòng hướng dẫn hiện phía trên phiếu.
- Chấm theo **tỉ lệ ô đúng**.

| Loại câu hỏi | Nội dung | Đáp án đúng | URL Audio | Transcript |
|---|---|---|---|---|
| NGHE_PHIEU_THONG_TIN | The Sea Lady ⏎⏎ **Main Facts:** ⏎ Built in: (14) ___ ⏎ **Dinner:** ⏎ Time: (15) ___ p.m. to 9.30 p.m. | 1999/nineteen ninety-nine\|7:30/7.30/7h30 | https://…/phieu.mp3 | Write one or two words or a number or a date or a time. |

### 4.5 `NGHE_NHIEU_DAP_AN` — Nghe trắc nghiệm nhiều đáp án (IELTS "Choose TWO letters")
- **Đáp án đúng**: các chữ cái cách nhau `|` (VD `B|D`), **từ 2 đáp án đúng trở lên và ít hơn tổng số lựa chọn**.
- Đáp án A–D: 2–4 lựa chọn (đề có 5 lựa chọn A–E thì soạn bằng form tay: *Trắc nghiệm → Nghe trắc nghiệm nhiều đáp án*, tối đa 8 lựa chọn).
- **Chấm từng phần**: mỗi đáp án đúng đã chọn được 1/N điểm, mỗi đáp án sai đã chọn bị trừ 1/N (sàn 0); câu chỉ tính "đúng" khi chọn đúng hết.

| Loại câu hỏi | Nội dung | Đáp án A–D | Đáp án đúng | URL Audio |
|---|---|---|---|---|
| NGHE_NHIEU_DAP_AN | Which TWO reasons does Irina give for visiting the exhibition? | to meet a friend / to improve her knowledge / to buy something / to entertain her child | B\|D | https://…/nhieu-dap-an.mp3 |

### 4.6 Nhiều câu nghe chung 1 audio
Ghi nhiều dòng **liên tiếp** `TRAC_NGHIEM_VOICE` / `NGHE_DIEN_TU` / `NGHE_NOP_AUDIO` /
`NGHE_CHON_HINH` / `NGHE_NHIEU_DAP_AN` có **cùng giá trị URL Audio** → tự gộp thành nhóm, audio hiện 1 lần.

---

## 5. Kỹ năng VIẾT (`WRITING`)

### `TU_LUAN` — Tự luận (chấm tay)
- **Bắt buộc:** Nội dung. **Tuỳ chọn:** URL Hình ảnh (VD biểu đồ cho IELTS Writing
  Task 1), Điểm, Giải thích (dùng ghi tiêu chí chấm).

| Loại câu hỏi | Độ khó | Nội dung | URL Hình ảnh | Điểm | Giải thích |
|---|---|---|---|---|---|
| TU_LUAN | HARD | Write a 150-word essay about your favorite hobby. | https://…/chart.png | 2 | Chấm theo thang điểm nội dung/ngữ pháp/từ vựng. |

---

## 6. Kỹ năng NÓI (`SPEAKING`)

### `SPEAKING` — Nói (chấm theo từ khóa phát âm)
- **Bắt buộc:** Nội dung, **Từ khóa phát âm** (cột "Transcript / Từ khóa phát âm / Đoạn
  văn tham chiếu") — các từ trọng điểm, cách nhau phẩy.
- Loại này không nằm trong danh sách khoá theo kỹ năng của Bài, chỉ dùng được khi
  import không bị lọc theo kỹ năng (VD import thẳng vào Ngân hàng câu hỏi).

| Loại câu hỏi | Nội dung | Từ khóa phát âm |
|---|---|---|
| SPEAKING | Read the following sentence aloud. | enthusiasm, literature, variety |

---

## 7. Bảng tra nhanh — loại nào thuộc kỹ năng nào

| Kỹ năng | Mã loại | Chấm |
|---|---|---|
| Từ vựng – Ngữ pháp | `TRAC_NGHIEM`, `DIEN_TU`, `DIEN_TU_NHOM`, `DIEN_TU_HOP_TU_VUNG`, `DIEN_TU_HOP_TU_VUNG_ANH`, `DIEN_TU_DOAN_VAN`, `SAP_XEP_CAU`, `SAP_XEP_CHU_CAI` (và `TRAC_NGHIEM_VOICE`) | Tự động |
| Đọc | `DOC_HIEU_LUOI`, `DOC_DIEN_TU` (và `TRAC_NGHIEM` nhóm theo đoạn văn) | Tự động |
| Nghe | `TRAC_NGHIEM_VOICE`, `NGHE_DIEN_TU`, `NGHE_CHON_HINH`, `NGHE_PHIEU_THONG_TIN`, `NGHE_NHIEU_DAP_AN` · `NGHE_NOP_AUDIO` | Tự động · `NGHE_NOP_AUDIO` chấm tay |
| Viết | `TU_LUAN` | Chấm tay |
| Nói | `SPEAKING` | Từ khóa phát âm |

## 8. Bảng cột bắt buộc theo từng loại

| Loại | Nội dung | Đ.án A–D | Đáp án đúng | URL Audio | URL Hình | Đoạn văn/Transcript |
|---|---|---|---|---|---|---|
| TRAC_NGHIEM | ✔ | A,B ✔ | 1 chữ cái ✔ | – | tuỳ chọn | tuỳ chọn |
| TRAC_NGHIEM_VOICE | ✔ | A,B ✔ | 1 chữ cái ✔ | ✔ | tuỳ chọn | tuỳ chọn |
| DIEN_TU | ✔ | – | từ cần điền ✔ | – | tuỳ chọn | – |
| DIEN_TU_NHOM | ✔ (cách `\|`) | – | `\|` ✔ | tuỳ chọn | `\|` tuỳ chọn | hộp từ / đoạn văn |
| DIEN_TU_HOP_TU_VUNG | ✔ | – | `\|` ✔ | – | – | – |
| DIEN_TU_HOP_TU_VUNG_ANH | ✔ | – | `\|` ✔ | – | tuỳ chọn | hộp từ |
| DIEN_TU_DOAN_VAN | ✔ | – | `\|` ✔ (được rỗng) | – | tuỳ chọn | hộp từ |
| SAP_XEP_CAU | ✔ | – | `\|` ≥ 2 khối ✔ | – | – | – |
| SAP_XEP_CHU_CAI | ✔ | – | `\|` ≥ 2 chữ ✔ | – | tuỳ chọn | – |
| DOC_HIEU_LUOI | ✔ (cách `\|`) | A,B dùng chung ✔ | chữ cái `\|` ✔ | – | – | đoạn văn ✔ |
| DOC_DIEN_TU | tuỳ chọn | A,B (`\|`) ✔ | chữ cái `\|` ✔ | – | – | đoạn văn ✔ |
| NGHE_DIEN_TU | ✔ | – | ✔ | ✔ | – | – |
| NGHE_CHON_HINH | ✔ | tuỳ chọn (chú thích) | 1 chữ cái ✔ | ✔ | `\|` 2–4 ảnh ✔ | tuỳ chọn |
| NGHE_NHIEU_DAP_AN | ✔ | A,B ✔ (tối đa D) | `\|` ≥ 2 chữ cái ✔ | ✔ | tuỳ chọn | tuỳ chọn |
| NGHE_NOP_AUDIO | ✔ | – | – | ✔ | – | – |
| NGHE_PHIEU_THONG_TIN | ✔ (có `___`) | – | `\|` ✔ khớp số `___` | ✔ | – | dòng hướng dẫn |
| TU_LUAN | ✔ | – | – | – | tuỳ chọn | – |
| SPEAKING | ✔ | – | – | – | – | từ khóa ✔ |

## 9. Lỗi thường gặp

| Thông báo/hiện tượng | Nguyên nhân | Cách sửa |
|---|---|---|
| "Thiếu cột bắt buộc: Nội dung…" | Dòng 1 sai tiêu đề / sheet trống | Dùng đúng tên cột ở mục 1.2 |
| "Loại câu hỏi không hợp lệ" | Điền tên thay vì mã, hoặc gõ sai mã | Dùng đúng mã; hoặc chọn "Loại mặc định" |
| "Đáp án C đang để trống nhưng Đáp án D có…" | Bỏ trống xen giữa | Điền liên tục từ A |
| "Đáp án đúng phải là 1 trong A/B…" | Ghi `Paris` thay vì `B`, hoặc chữ cái không có lựa chọn | Chỉ ghi chữ cái |
| "Số đáp án không khớp số câu/chỗ trống" | `\|` ở Nội dung và Đáp án đúng lệch số lượng | Đếm lại; vị trí rỗng vẫn phải đủ dấu `\|` |
| "…đã tồn tại trong ngân hàng (trùng nội dung)" | Câu đã có | Sửa nội dung hoặc bỏ dòng |
| Số `7.30` bị Excel đổi thành ngày/giờ | Excel tự định dạng ô | Đặt cột **Định dạng Văn bản (Text)** trước khi nhập |
| Dấu `/` bị hiểu là chia | Ô bắt đầu bằng `=` | Không bắt đầu ô bằng `=`; đặt ô là Text |
