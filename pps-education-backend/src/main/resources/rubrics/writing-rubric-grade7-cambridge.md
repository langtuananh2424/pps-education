# WRITING RUBRIC — GRADE 7 · CAMBRIDGE B1 PRELIMINARY FOUNDATION · v3

## §0. Cấu hình

```text
Input: original task + original student writing
Target: Cambridge B1 Preliminary for Schools foundation (A2+ -> early B1)
Criteria: Content (C), Communicative Achievement (CA), Organisation (O), Language (L)
Checkpoint: 1 / 0.5 / 0
Word limit: use the task; if absent -> 60 words (school training default)
Temperature: 0
Model call: 1 call / script
```

Rubric trung hòa hai nhóm: `1` = performance B1 foundation khá chắc; `0.5` = functional A2+/developing B1; `0` = chưa kiểm soát được nền tảng.

## §1. Quy ước chấm

```text
N_total = tổng số từ học sinh viết.
N_copy = số từ thuộc chuỗi trùng ≥5 từ liên tiếp với đề/nguồn cho sẵn.
N_net = N_total - N_copy.
N_sent = tổng số câu.
N_complete = số câu/mệnh đề có chủ ngữ + động từ chia + ý hoàn chỉnh.
N_sp = lỗi chính tả/word formation.
N_pu = lỗi dấu câu/viết hoa.
N_lex = lỗi chọn từ/collocation làm sai hoặc mờ nghĩa.
N_verb_ok / N_verb = động từ chính dùng đúng thì/dạng / tổng động từ chính.
Developed idea = ý chính + ít nhất 1 reason / example / detail phù hợp.
Extended sentence = câu có nối/mệnh đề như because, but, so, when, if,
although, relative clause hoặc cấu trúc mở rộng tương đương.
```

- `1` = đạt chắc mục tiêu của khối; `0.5` = đang phát triển/đạt một phần; `0` = chưa thể hiện được.
- Chỉ chấm bằng chứng có trong bài. Không suy đoán ý học sinh “định viết”.
- Một lỗi chỉ bị tính ở checkpoint phù hợp nhất; tránh **double penalty**.
- Từ/câu chép nguyên từ đề hoặc nguồn cho sẵn không được dùng làm bằng chứng cho vocabulary/grammar range.
- Nếu checkpoint thực sự không áp dụng cho dạng bài → `0.5 (n/a)`; không tự cho `1`.
- Các chỉ số `N_*` và điểm từng checkpoint chỉ dùng để **tính toán nội bộ**; không xuất ra output.

## §2. Cổng dữ liệu

| Gate | Kích hoạt | Hậu quả |
|---|---|---|
| **G1 – Underlength** | `N_net` = 50–79% số từ yêu cầu | Task/Content tối đa **60%** |
|  | `N_net` <50% | Mọi criterion tối đa **40%** |
|  | `N_net` <25% | **0% – insufficient data** |
| **G2 – Off-topic** | Bài hoàn toàn không trả lời đề | Task/Content = **0%**; các criterion ngôn ngữ vẫn chấm nếu đủ dữ liệu |
| **G3 – Copied input** | Có chuỗi trùng ≥5 từ liên tiếp với đề/nguồn | Loại khỏi `N_net`; không tính làm bằng chứng LR/Language/GRA **Ngoại lệ khối 6–7:** từ trùng nằm trong **câu mở bài** (câu nhắc lại đề, tối đa 10 từ) **không bị trừ khỏi `N_net`** — vẫn tính là chép khi chấm paraphrase và không dùng làm bằng chứng từ vựng. |
| **G4 – Non-English** | Phần lớn bài không phải tiếng Anh | **0% – insufficient English evidence** |

Từ/cụm tiếng Việt lẻ tẻ được tính là **lexical gap** ở Language/LR, không trừ thêm lần thứ hai.

Nếu có bài mẫu/reference text được cung cấp và bài học sinh trùng đáng kể ngoài phần bắt buộc của đề, chỉ gắn cờ `CẦN GIÁO VIÊN XÁC MINH`; **không tự kết luận gian lận và không tự trừ điểm**.


## §3. Checkpoints

### CONTENT — C

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **C1** | **Task coverage** | Đủ tất cả ý bắt buộc | Thiếu 1 | Thiếu ≥2 |
| **C2** | **Relevance** | ≥90% câu liên quan | 75–89% | <75% |
| **C3** | **Development** | ≥2 developed ideas | 1 | 0 / mainly listing |
| **C4** | **Information clarity** | 0 điểm nội dung phải đoán | 1 | ≥2 |
| **C5** | **Reader informed** | Người đọc có đủ thông tin để đáp ứng task | Phần lớn đủ | Thiếu thông tin chính |

### COMMUNICATIVE ACHIEVEMENT — CA

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **CA1** | **Genre conventions** — đếm thành phần bắt buộc, mỗi phần phải có mặt **và** viết đúng hình thức (viết hoa, dấu câu). Email: lời chào + tên · câu mở đầu thư · câu/cụm chào cuối · ký tên. Article: tiêu đề · câu mở hướng tới người đọc · kết bài hướng tới người đọc · chia đoạn. Story: mở đầu đúng câu/đề cho sẵn · bối cảnh–nhân vật · diễn biến có thứ tự · kết thúc | Đủ 4 | Thiếu/sai 1–2 | Thiếu/sai ≥ 3 |
| **CA2** | **Tone / register** — trích dấu hiệu giọng hợp người đọc (thư bạn: câu cảm thán, câu hỏi tới người nhận, cụm thân mật; article: câu hỏi tu từ, xưng hô với người đọc). **Không tính** câu chào đầu/cuối (đã tính ở CA1) | ≥ 2 dấu hiệu **và** 0 chỗ lệch giọng | 0–1 dấu hiệu, hoặc 1–2 chỗ lệch giọng | ≥ 3 chỗ lệch giọng / sai register |
| **CA3** | **Purpose** — (i) câu đầu thân bài nêu / đáp đúng mục đích; (ii) 0 câu lạc mục đích | Đạt cả (i) và (ii) | Đạt 1 trong 2 | Không đạt cả hai |
| **CA4** | **Communicative functions** | Thực hiện đủ chức năng cần thiết (e.g. opinion/invite/explain) | Thiếu/ yếu 1 function | Thiếu ≥2 / function chính không đạt |
| **CA5** | **Reader engagement** — trích câu | ≥ 1 câu tương tác **không phải công thức**: hỏi lại người nhận, rủ / đề nghị làm cùng, phản hồi một chi tiết trong thư người gửi | Chỉ có câu công thức (*I am happy…, I hope you…, I can't wait…*) | Không có |

### ORGANISATION — O

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **O1** | **Logical progression** | Mạch rõ toàn bài | 1 chỗ nhảy ý | Ý rời rạc |
| **O2** | **Grouping / paragraphing** — đếm đoạn | Thân bài tách ≥ 2 đoạn (hoặc mỗi ý một dòng riêng) | Thân bài một khối, nhưng chào đầu **và** chào cuối tách dòng riêng | Toàn bài một khối |
| **O3** | **Linking** | ≥3 correct links, ≥2 types | 1–2 correct links | 0 / mostly wrong |
| **O4** | **Reference / sequencing** | ≥2 correct uses | 1 | 0 / unclear reference |
| **O5** | **Repetition control** — trích từng cụm / cấu trúc câu bị lặp không cần thiết | 0–1 | 2 | ≥ 3 |

### LANGUAGE — L

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **L1** | **Vocabulary sufficiency & appropriacy** | Đủ và phù hợp cho hầu hết ý | Đủ cho ý chính nhưng còn thiếu/sai vài chỗ | Thiếu từ làm nhiều ý khó hiểu |
| **L2** | **Vocabulary variety theo ngữ cảnh** — `K_topic` = số từ/cụm **hợp chủ đề theo ngữ cảnh đề bài**, dùng đúng, **không có sẵn trong đề**; mỗi mục tính 1 lần, phải trích nguyên văn | `K_topic` ≥ 4 | 2–3 | ≤ 1 |
| **L3** | **Spelling / word formation** | ≤3 lỗi / 50 từ | 4–6 | ≥7 / thường cản meaning |
| **L4** | **Basic grammar accuracy** | ≥70% complete/basic clauses acceptable | 45–69% | <45% |
| **L5** | **Sentence range** | Simple + ≥2 joined/extended sentences | Simple + 1 extended | Only short/repeated simple patterns / fragments |

## §4. Quy đổi

| Total /5 | 5 | 4.5 | 4 | 3.5 | 3 | 2.5 | 2 | 1.5 | 1 | 0.5 | 0 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **%** | 100 | 90 | 80 | 70 | 60 | 50 | 40 | 30 | 20 | 10 | 0 |

`Final % = average(C, CA, O, L)`, làm tròn xuống bội số 5. Áp gate cap sau khi chấm checkpoint.


### Internal anchor

| % | Interpretation |
|---:|---|
| **80–100** | Strong B1-foundation performance |
| **60–75** | Developing B1 / functional A2+ |
| **40–55** | Early foundation; needs structured support |
| **0–35** | Below current target / insufficient control |

## §5. Output bắt buộc

```markdown
### 1. Bài viết đã đánh dấu
<giữ nguyên bài gốc; chỉ chèn token màu, không viết phần sửa>

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | |
| Communicative Achievement | |
| Organisation | |
| Language | |
| **Tổng kết** | **__%** |

### 3. Nhận xét

**Nhận xét chung:** <≤2 câu, ≤50 từ, tiếng Việt: 1 điểm mạnh + 1 ưu tiên cần sửa>

**Key grammar: <tên các cấu trúc được giao>**
**<Đạt | Chưa đạt>** · dùng đúng <đúng>/<tổng> lần (<tỉ lệ>%)
<2 câu, khoảng 50 từ, tiếng Việt>
```

### Phần key grammar (filter 2)

Chỉ in khi lượt giao bài **có gắn key grammar** (mã tra trong `KeyGrammar_Grade7_Cambridge.md`). Không gắn thì bỏ hẳn phần này.

- Cách đếm, tiêu chí đạt, cách nhận diện từng cấu trúc: xem file syllabus.
- **Key grammar Chưa đạt → Language bị trần:** dùng đúng 0 lần → tối đa 40%; dùng đúng 1 lần → tối đa 50% (điểm thấp hơn thì giữ nguyên). Đạt thì không đổi điểm.
- **Chưa đạt → câu thứ hai bắt buộc yêu cầu học sinh viết lại bài**, dùng đúng ít nhất 2 lần cấu trúc được giao.
- **Không gợi ý cách sửa** trong nhận xét key grammar: không câu mẫu, không phiên bản viết lại, không nói câu nào nên đổi thành gì.
- Trong mục 1: dùng đúng key grammar → `{{kg|…}}` (xanh, thay cho `{{ok|…}}`); dùng sai → `{{gr2|…}}` (đỏ).

Các neo ở §6 không gắn key grammar nên chỉ có phần nhận xét chung.

### Đánh dấu trong bài — cú pháp `{{mã|đoạn văn bản}}`

| Mã | Màu | Dùng cho |
|---|---|---|
| `ok` | 🟢 xanh | chỗ dùng **đúng/tốt** đáng ghi nhận: từ vựng chính xác, câu mở rộng đúng, liên kết dùng chuẩn |
| `sp1` / `sp2` | 🟡 / 🔴 | lỗi **chính tả / word formation** |
| `gr1` / `gr2` | 🟡 / 🔴 | lỗi **ngữ pháp câu**: thì, chia động từ, cấu trúc, mệnh đề, câu thiếu thành phần |
| `wd1` / `wd2` | 🟡 / 🔴 | lỗi **chọn từ / collocation**, từ tiếng Việt, chuỗi chép từ đề |
| `pu1` / `pu2` | 🟡 / 🔴 | lỗi **dấu câu / viết hoa** |

Hậu tố mức độ: `1` = nhẹ, người đọc vẫn hiểu ngay → **vàng**; `2` = nặng, làm sai nghĩa hoặc người đọc phải đoán → **đỏ**.

Ví dụ: `She {{gr2|go}} to school and {{sp1|recieve}} a prize. {{ok|Although it was raining}}, she was happy.`

Chỉ đánh dấu **vị trí**: không viết từ/câu đúng, không thêm mũi tên, không ghi chú trong ngoặc.

**Cấm:** không xuất số liệu `N_*`; không xuất điểm từng checkpoint; không xuất điểm thô `/5` (chỉ xuất `%`); không viết lại toàn bài; không giải thích từng checkpoint; không thêm lời mở đầu/kết dài; không nêu tên học sinh.

## §6. Neo hiệu chuẩn

Ba bài dưới đây là **mốc chuẩn của khối 7 (hệ Cambridge B1 Foundation)**. Cách dùng: đọc bài học sinh → đối chiếu với **dòng Checkpoint** của các neo để quyết định từng checkpoint cho 1 / 0.5 / 0 → cộng → quy đổi §4 → áp trần. **Không có bước điều chỉnh sau khi cộng** — % của tiêu chí chính là phép cộng checkpoint (hệ thống bỏ qua mọi điều chỉnh như vậy). Neo nào chưa có dòng Checkpoint thì chỉ dùng để tham khảo cách đánh dấu và giọng nhận xét.

### Bài mỏng nội dung nhưng vẫn hiểu được — thang chuẩn theo khối

**"Mỏng"** = mọi ý bắt buộc đều có mặt, người đọc hiểu đúng, nhưng **không ý nào được phát triển** bằng lý do, ví dụ hay chi tiết cụ thể.

Với khối này: **Content = 50%** (tổng 2.5/5).

Đề cho 60 từ — đã đủ chỗ cho ít nhất một lý do. Bài chỉ liệt kê mà không giải thích thì `C3` = 0.

**Đây là TRẦN CỨNG, không phải gợi ý cân nhắc.** Điều kiện kích hoạt đọc thẳng từ mục 0: `Ý được phát triển = 0` **và** `Ý bắt buộc bị thiếu = không` → Content / Task Response **tối đa** mức trong bảng dưới, áp **sau** khi cộng checkpoint. Không cần "cảm thấy" bài mỏng — con số ở mục 0 quyết định. (Kiểm chứng vòng 7: khi luật này chỉ là ghi chú, một bài mỏng khối 8 B1 vẫn được Content 80%.)

Thang này khác nhau giữa các khối vì giới hạn số từ khác nhau — cùng một bài mỏng, khối 6 được 60% còn khối 9 chỉ 40%:

| Khối | Giới hạn từ | Content / Task Response khi bài mỏng |
|---|---|---|
| 6 (A2) | 25–35 | **60%** |
| 7 (IELTS & B1) | 60 | **50%** |
| 8 (IELTS & B1) | 100 | **40%** hoặc thấp hơn |
| 9 | 250 | **40%** hoặc thấp hơn |

**Đối chiếu với các cổng §2 — quan trọng:**

- **G1 xử lý *độ dài*, luật này xử lý *độ sâu*.** Hai thứ độc lập nhau: bài đủ dài mà mỏng vẫn bị luật này; bài ngắn mà sâu vẫn bị G1. Khi **cả hai cùng kích hoạt, lấy mức thấp hơn**, không cộng dồn.
- **Không dùng độ ngắn làm lý do hạ Content lần thứ hai.** Độ ngắn đã được tính đủ ở G1.
- Trần **"không có ý nào phát triển → tối đa 20%"** chỉ dành cho bài **rỗng thật sự**: không ý nào phát triển, **và** không một chi tiết cụ thể nào (tên, số, sự việc), **và** không nêu được lập trường khi đề yêu cầu. Bài mỏng nhưng vẫn có chi tiết cụ thể thì theo bảng trên, **không phải 20%**.

### Trần theo số lỗi cho Language — hệ thống tự áp

Các ngưỡng % ở §3 đo **"đạt mục tiêu khối"**, không đo **"không sai"**. Vì vậy sau khi cộng checkpoint, hệ thống
tự áp trần theo **mật độ lỗi** (ngữ pháp + chính tả + dùng từ + dấu câu, trên 100 từ), dựa đúng vào số lỗi đếm ở mục 0:

| Lỗi / 100 từ | Language tối đa |
|---:|---|
| 0 lỗi | **100%** |
| ≤ 2 | **90%** |
| ≤ 4 | **80%** |
| ≤ 7 | **70%** |
| ≤ 10 | **60%** |
| ≤ 14 | **50%** |
| > 14 | **40%** |

Kèm trần tuyệt đối: 1–2 lỗi → tối đa 90%; từ 3 lỗi → tối đa 80%. Lấy mức thấp hơn.
Số lỗi phải **bằng đúng** số token lỗi đánh dấu ở mục 1. Người chấm chỉ cần **đếm đúng**; bảng trần do máy áp.

### Phát triển ý phải khớp với số đã trích dẫn — bắt buộc

`C3` được chấm theo **đúng số ý đã trích dẫn được bằng chứng** (con số, tên riêng, hoặc việc đã xảy ra).
Không trích dẫn được ý nào → `C3` = 0, dù bài "trông" có chi tiết.

### Lỗi ngôn ngữ không được lan sang tiêu chí nội dung — bắt buộc

Đây là dạng **double penalty** hay gặp nhất và là lỗi chấm nghiêm trọng nhất: bài sai ngữ pháp nên đọc vất vả, người chấm bèn hạ luôn cả **Content**.

Lỗi ngữ pháp, chính tả và dấu câu **chỉ** được tính ở Language.

**Content** chỉ bị hạ khi:
- **thiếu ý** mà đề yêu cầu, hoặc
- ý **sai lệch** so với đề, hoặc
- người đọc **không xác định được** học sinh muốn nói gì.

Nếu người đọc vẫn hiểu đúng nội dung — dù câu thiếu động từ, sai thì, hay không có dấu chấm — thì **Content giữ nguyên**. Một câu chuyện kể đủ tình tiết và hiểu được vẫn đạt điểm nội dung cao, ngay cả khi hàng ngôn ngữ chỉ còn 20%.

Ngoại lệ duy nhất là các **trần cứng** đã ghi rõ bên dưới (tỉ lệ động từ sai, tỉ lệ lạc ý, bài rỗng nội dung). Ngoài các trần đó, không tự ý hạ thêm.

Mỗi neo có dòng **Checkpoint:** — cộng đúng từng checkpoint ra đúng % trong bảng của chính neo đó. Dùng các dòng này làm mẫu chấm checkpoint.

### Quy tắc lệch hàng — bắt buộc

Bốn tiêu chí **không phải bằng nhau**. Một bài có thể đủ ý và đúng giọng thư nhưng hỏng ngữ pháp hệ thống. Đừng kéo các hàng về gần nhau cho cân đối.

Trần cứng theo mức độ hỏng động từ, áp **trước** khi tính trung bình:

| Tỉ lệ động từ chính sai thì/dạng | Language | Content |
|---|---|---|
| > 50% | tối đa **20%** | tối đa **40%** |
| 34–50% | tối đa **40%** | tối đa **60%** |

Thêm: **quá nửa động từ sai → Tổng kết tối đa 35%** (hệ thống tự áp theo `N_verb_ok/N_verb` ở mục 0).

Lý do hạ cả Content: khi quá nửa động từ sai, người đọc phải tự dựng lại sự việc, nên thông tin chưa thực sự đến được người nhận dù đủ ý.

### Quy tắc chống bị ngôn ngữ đánh lừa — bắt buộc

Một cụm chỉ được tô `{{ok|...}}` khi nó **vừa đúng vừa tự nhiên**. Cụm "nghe có vẻ cao cấp" nhưng sai collocation, là từ ghép tự chế, sai register, hoặc dài mà không xác định được nghĩa thì là **lỗi `wd`, không phải điểm mạnh**.

Độ dài câu và độ hiếm của từ **không** phải bằng chứng cho Language — bằng chứng là **dùng đúng**.

**Đề dùng chung cho ba neo:**

> Read this email from your English friend Emma.
> "I am coming to your school next week for an exchange week! What should I bring? Which lesson do you think I will like best? What do students do at lunchtime?"
> Write your email to Emma and answer her three questions. Write about 60 words.

---

### NEO A — 40%

**Bài gốc (42 từ — 70% yêu cầu → G1 băng 50–79%: Content trần 60, không ảnh hưởng vì Content đã 50):**

> hi emma
> You bring a notebook and a pen. I think you like english lesson. At lunchtime student eat in the canteen. The food is cheap. My school is very big and have 900 student. I am very happy you come. bye

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{pu2|hi emma}}

You bring a notebook and a pen. I think you {{gr2|like}} {{pu1|english}} lesson. At lunchtime {{gr1|student}} eat in the canteen. The food is cheap. My school is very big and {{gr1|have}} 900 {{gr1|student}}. I am very happy you {{gr1|come}}. {{pu1|bye}}

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 50% |
| Communicative Achievement | 30% |
| Organisation | 50% |
| Language | 40% |
| **Tổng kết** | **40%** |

### 3. Nhận xét

Em đã trả lời được cả ba câu hỏi của Emma. Ưu tiên sắp tới: viết hoa đầu câu và tên riêng, và mở đầu thư bằng một câu chào hỏi thay vì vào thẳng câu trả lời.

**Checkpoint:**
`C1` = 1 (đủ ba câu hỏi) · `C2` = 0.5 (1/6 câu lạc: "My school is very big…" → 83%) · `C3` = 0 ("english lesson" là **trả lời**, không phải bằng chứng; không ý nào có số, tên riêng hay sự việc) · `C4` = 1 · `C5` = 0.5 → 3/5 = 60% → **trần bài mỏng khối 7: 50%**.
`CA1` = 0 (lời chào viết thường, **không** có câu mở đầu thư, **không** ký tên — thiếu/sai 3) · `CA2` = 0.5 (0 dấu hiệu giọng thân mật) · `CA3` = 0 (không có câu mở đầu nêu mục đích **và** có câu lạc mục đích) · `CA4` = 0.5 (lời khuyên viết dạng kể "You bring…") · `CA5` = 0.5 (chỉ câu công thức "I am very happy you come") → 1.5/5 = **30%**.
`O1` = 0.5 (nhảy sang quy mô trường) · `O2` = 0 (chào cuối "bye" dính vào thân bài) · `O3` = 0.5 (2 lần "and") · `O4` = 0.5 (1 tham chiếu: "The food") · `O5` = 1 → 2.5/5 = **50%**.
`L1` = 1 · `L2` = 1 (`K_topic` ≥ 4: notebook, pen, canteen, food, cheap) · `L3` = 1 · `L4` = 0.5 (~55% mệnh đề đúng) · `L5` = 0.5 → 4/5 = 80% → động từ sai 4/9 = 44% → **trần 40%** (cũng là trần mật độ: 8 lỗi / 42 từ).
Tổng kết (50 + 30 + 50 + 40) / 4 = 42.5 → **40%**.

---

### NEO A0 — 35% · đáy thang thực tế

Neo này định nghĩa **đáy của thang điểm cho bài có thật**, phân biệt "rất yếu" với "chưa đủ dữ liệu để chấm" (0% theo cổng G1/G4). Bài dưới đây là tiếng Anh thật, nhận ra được ý định, nhưng gần như không câu nào hoàn chỉnh. Bài rất yếu nhưng đủ ý thường rơi vào **30–40%**, không xuống 20% — vì Content và Communicative Achievement vẫn ghi nhận việc trả lời đủ câu hỏi.

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{pu1|hi}} emma. {{gr2|you come my school next week good}}. you bring book and pen and jacket. {{gr2|lesson english very good i like}}. {{pu1|lunchtime}} {{gr1|student}} eat in canteen and play. {{gr2|food cheap}}. {{gr2|my school big have many}} {{gr1|student}} and {{gr1|teacher}} nice. {{gr2|you happy when come}}. {{pu1|bye}} emma.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 40% |
| Communicative Achievement | 40% |
| Organisation | 40% |
| Language | 20% |
| **Tổng kết** | **35%** |

### 3. Nhận xét

Em đã trả lời được cả ba câu hỏi của Emma. Ưu tiên sắp tới: mỗi câu phải có động từ to be, ví dụ "The food **is** cheap".

**Checkpoint:**
46 từ = 77% yêu cầu → G1 băng 50–79% (Content trần 60 — không ảnh hưởng vì Content đã thấp hơn).
`C1` = 1 · `C2` = 0.5 (1 câu lạc về quy mô trường) · `C3` = 0 · `C4` = 0 (≥ 2 chỗ phải đoán: "you come my school next week good", "you happy when come") · `C5` = 0.5 → 2/5 = **40%** (động từ sai > 50% → trần Content 40 — trùng).
`CA1` = 0.5 (lời chào viết thường, không ký tên — sai/thiếu 2) · `CA2` = 0.5 · `CA3` = 0.5 (có câu mở, nhưng có câu lạc mục đích) · `CA4` = 0 (khuyên dạng kể; "i like" là ý kiến của mình, không phải về Emma) · `CA5` = 0.5 → 2/5 = **40%**.
`O1` = 0.5 · `O2` = 0 (cả bài một khối) · `O3` = 0.5 (3 lần "and", chỉ 1 loại) · `O4` = 0 (không tham chiếu nối ý) · `O5` = 1 → 2/5 = **40%**.
Language: động từ sai > 50% → **trần 20%**.
Tổng kết (40 + 40 + 40 + 20) / 4 = **35%**. **Đây là 35% chứ không phải 0%: bài vẫn là tiếng Anh và vẫn đủ ý.**

---

### NEO M — 75% · đủ ý nhưng mỏng, ngôn ngữ sạch

So với **NEO A0**: cả hai đều không phát triển ý, nhưng bài này **ngôn ngữ không lỗi**. Phép thử ở đây là **đừng để ngôn ngữ sạch kéo Content lên** — Content vẫn chỉ 50% (trần bài mỏng), dù Language được 100%.

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Hi Emma,

{{ok|I am happy that you are coming next week.}} You should bring a notebook and a pen. {{ok|I think you will like the art lesson best.}}

At lunchtime students eat in the canteen. Some students play football. The canteen is quite big.

See you soon,
Minh

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 50% |
| Communicative Achievement | 80% |
| Organisation | 80% |
| Language | 100% |
| **Tổng kết** | **75%** |

### 3. Nhận xét

Em trả lời đủ ba câu hỏi và viết đúng ngữ pháp. Ưu tiên sắp tới: mỗi câu trả lời thêm một chi tiết cụ thể, ví dụ tiết Mỹ thuật tuần này lớp em đang làm gì.

**Checkpoint:**
`C1` = 1 · `C2` = 1 · `C3` = 0 ("the art lesson" là **trả lời** câu hỏi, không phải bằng chứng) · `C4` = 1 · `C5` = 0.5 → 3.5/5 = 70% → **trần bài mỏng khối 7: 50%**.
`CA1` = 1 (Hi Emma · câu mở · See you soon · Minh) · `CA2` = 0.5 (0 dấu hiệu giọng thân mật) · `CA3` = 1 · `CA4` = 1 · `CA5` = 0.5 (chỉ câu công thức) → 4/5 = **80%**.
`O1` = 1 · `O2` = 1 (thân bài 2 đoạn) · `O3` = 0.5 (1 từ nối) · `O4` = 0.5 (1 tham chiếu: "The canteen") · `O5` = 1 → 4/5 = **80%**.
`L1`–`L5` = 1 (`K_topic`: notebook, pen, art, canteen, football) → 5/5, 0 lỗi → **100%**.
Tổng kết (50 + 80 + 80 + 100) / 4 = 77.5 → **75%**.

---

### NEO B — 60%

**Bài gốc (67 từ):**

> Hi Emma,
> Here are the answers to your questions. You bring a warm jacket because our classroom was only 16 degrees last monday. I think you will like the art lesson best, we paint many picture there. My sister is in grade 5. At lunchtime student eat in the canteen and then we go to the thing behind it. Bring a pen too.
> See you soon,
> Minh

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Hi Emma,

Here are the answers to your questions. {{ok|You bring a warm jacket because our classroom was only 16 degrees last}} {{pu1|monday}}. {{ok|I think you will like the art lesson best}}{{pu1|, we}} paint many {{gr1|picture}} there. My sister is in grade 5. At lunchtime {{gr1|student}} eat in the canteen and then we go to {{wd2|the thing}} behind it. Bring a pen too.

See you soon,
Minh

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 60% |
| Communicative Achievement | 50% |
| Organisation | 70% |
| Language | 60% |
| **Tổng kết** | **60%** |

### 3. Nhận xét

Em trả lời đủ ba câu hỏi và giải thích lời khuyên mang áo khoác bằng một chi tiết rất cụ thể. Ưu tiên sắp tới: bỏ câu không liên quan và viết rõ nơi các bạn đến sau giờ ăn trưa.

**Checkpoint:**
`C1` = 1 · `C2` = 0.5 (1/7 câu lạc: "My sister is in grade 5." → 86%) · `C3` = 0.5 (1 ý có bằng chứng loại (a): "only 16 degrees last monday"; "we paint many picture" là thói quen, không tính) · `C4` = 0.5 (1 chỗ phải đoán: "the thing behind it") · `C5` = 0.5 → 3/5 = **60%**.
`CA1` = 1 (đủ 4 phần) · `CA2` = 0.5 · `CA3` = 0.5 (có câu mở nêu mục đích, nhưng có 1 câu lạc mục đích) · `CA4` = 0.5 (lời khuyên dạng kể "You bring…") · `CA5` = 0 (không câu tương tác nào, kể cả câu công thức) → 2.5/5 = **50%**.
`O1` = 0.5 (quay lại chuyện mang đồ sau khi đã sang giờ ăn trưa) · `O2` = 0.5 (thân bài một khối) · `O3` = 0.5 (2 từ nối: because, and then) · `O4` = 1 (our classroom, there, it) · `O5` = 1 → 3.5/5 = **70%**.
`L1` = 0.5 (bí từ: "the thing") · `L2` = 1 · `L3` = 1 · `L4` = 1 · `L5` = 1 → 4.5/5 = 90% → 5 lỗi / 67 từ = 7.5 lỗi/100 từ → **trần 60%**.
Tổng kết (60 + 50 + 70 + 60) / 4 = **60%**.

---

### NEO C — 80%

**Bài gốc (73 từ):**

> Hi Emma,
> I am so exited that you are coming next week!
> You should bring a warm jacket because our classrooms are quite cold in the morning. I think you will enjoy the art lesson most. This month we are painting a big wall pictures.
> At lunchtime most student eat in the canteen. They plays football in the small yard. The yard is small but it is really fun!
> See you soon,
> Minh

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Hi Emma,

I am so {{sp1|exited}} that you are coming next week!

{{ok|You should bring a warm jacket because our classrooms are quite cold in the morning.}} I think you will enjoy the art lesson most. {{ok|This month we are painting}} a big wall {{gr1|pictures}}.

At lunchtime most {{gr1|student}} eat in the canteen. They {{gr1|plays}} football in the small yard. The yard is small but it is really fun!

See you soon,
Minh

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 90% |
| Communicative Achievement | 90% |
| Organisation | 80% |
| Language | 70% |
| **Tổng kết** | **80%** |

### 3. Nhận xét

Em trả lời đủ ba câu hỏi, giọng thư thân mật và có chi tiết thật về bức tranh tường cả lớp đang vẽ. Ưu tiên sắp tới: danh từ số nhiều và động từ đi với "they".

**Checkpoint:**
`C1` = 1 · `C2` = 1 · `C3` = 0.5 (1 ý có bằng chứng: "This month we are painting…" — loại (a)/(c); lý do áo khoác là thói quen, không tính) · `C4` = 1 · `C5` = 1 → 4.5/5 = **90%**.
`CA1` = 1 · `CA2` = 1 (2 câu cảm thán: "…next week!", "…really fun!") · `CA3` = 1 · `CA4` = 1 · `CA5` = 0.5 (chỉ câu công thức "I am so exited that you are coming") → 4.5/5 = **90%**.
`O1` = 1 · `O2` = 1 (thân bài 2 đoạn) · `O3` = 0.5 (2 từ nối: because, but) · `O4` = 1 (They, it) · `O5` = 0.5 (lặp 2 chỗ: "small", "yard") → 4/5 = **80%**.
`L1`–`L5` = 1 → 5/5 → 4 lỗi / 73 từ = 5.5 lỗi/100 từ → **trần 70%**.
Tổng kết (90 + 90 + 80 + 70) / 4 = 82.5 → **80%**.

**Vì sao không phải 100%:** chỉ một ý có chi tiết thật; chỉ hai từ nối; lặp từ; và bốn lỗi ngôn ngữ trong 73 từ.
