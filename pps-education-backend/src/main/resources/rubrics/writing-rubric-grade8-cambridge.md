# WRITING RUBRIC — GRADE 8 · CAMBRIDGE B1 PRELIMINARY FOR SCHOOLS · v3

## §0. Cấu hình

```text
Input: original task + original student writing
Target: Cambridge B1 Preliminary for Schools / CEFR B1 pathway
Criteria: Content (C), Communicative Achievement (CA), Organisation (O), Language (L)
Checkpoint: 1 / 0.5 / 0
Word limit: use the task; if absent -> 100 words
Temperature: 0
Model call: 1 call / script
```

Rubric dùng chung cho học sinh khá và trung bình. `1` = B1 pathway khá chắc; `0.5` = functional A2+/developing B1; ngưỡng không đòi “advanced language”.

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
| **G3 – Copied input** | Có chuỗi trùng ≥5 từ liên tiếp với đề/nguồn | Loại khỏi `N_net`; không tính làm bằng chứng LR/Language/GRA |
| **G4 – Non-English** | Phần lớn bài không phải tiếng Anh | **0% – insufficient English evidence** |

Từ/cụm tiếng Việt lẻ tẻ được tính là **lexical gap** ở Language/LR, không trừ thêm lần thứ hai.

Nếu có bài mẫu/reference text được cung cấp và bài học sinh trùng đáng kể ngoài phần bắt buộc của đề, chỉ gắn cờ `CẦN GIÁO VIÊN XÁC MINH`; **không tự kết luận gian lận và không tự trừ điểm**.


## §3. Checkpoints

### CONTENT — C

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **C1** | **Task coverage** | Đủ tất cả ý bắt buộc | Thiếu 1 | Thiếu ≥2 |
| **C2** | **Relevance** | ≥90% câu liên quan | 80–89% | <80% |
| **C3** | **Development** | ≥2 developed ideas, có detail/reason cụ thể | 1 developed idea | 0 / mainly listing |
| **C4** | **Information clarity** | 0 điểm nội dung phải đoán | 1 | ≥2 |
| **C5** | **Reader informed** | Người đọc có đủ thông tin quan trọng | Phần lớn đủ | Thiếu thông tin chính |

### COMMUNICATIVE ACHIEVEMENT — CA

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **CA1** | **Genre conventions** — đếm thành phần bắt buộc, mỗi phần phải có mặt **và** đúng hình thức, đúng đối tượng. Email: lời chào + tên · câu mở đầu thư · câu/cụm chào cuối · ký tên. Article: tiêu đề · câu mở hướng tới người đọc · kết bài hướng tới người đọc · chia đoạn. Story: mở đầu đúng câu/đề cho sẵn · bối cảnh–nhân vật · diễn biến có thứ tự · kết thúc | Đủ 4 | Thiếu/sai 1–2 | Thiếu/sai ≥ 3 |
| **CA2** | **Tone / register** — trích dấu hiệu giọng hợp người đọc (thư bạn: câu cảm thán, câu hỏi tới người nhận, cụm thân mật; article: câu hỏi tu từ, xưng hô với người đọc). **Không tính** câu chào đầu/cuối (đã tính ở CA1) | ≥ 2 dấu hiệu **và** 0 chỗ lệch giọng | 0–1 dấu hiệu, hoặc 1–2 chỗ lệch giọng | ≥ 3 chỗ lệch giọng / sai register |
| **CA3** | **Purpose** — (i) câu đầu thân bài nêu / đáp đúng mục đích; (ii) 0 câu lạc mục đích | Đạt cả (i) và (ii) | Đạt 1 trong 2 | Không đạt cả hai |
| **CA4** | **Communicative functions** | Đủ các function task cần | Yếu/thiếu 1 | Thiếu ≥2 / function chính không đạt |
| **CA5** | **Reader engagement** — trích câu. **Không tính câu hỏi mà đề bắt buộc** (đã tính ở C1 / CA4 — tránh trừ chồng khi thiếu) | ≥ 1 câu tương tác **không phải công thức** ngoài câu bắt buộc: hỏi thêm, rủ / đề nghị làm cùng, phản hồi một chi tiết trong thư người gửi | Chỉ có câu công thức (*I am happy…, I hope you…, I can't wait…*) | Không có |

### ORGANISATION — O

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **O1** | **Logical progression** | Mạch rõ xuyên suốt | 1 chỗ nhảy ý | Ý rời rạc |
| **O2** | **Paragraphing / grouping** — đếm đoạn | Thân bài tách ≥ 2 đoạn theo ý | Thân bài một khối, nhưng chào đầu **và** chào cuối tách dòng riêng | Toàn bài một khối |
| **O3** | **Linking** | ≥4 correct links, ≥3 types | 2–3 links, ≥2 types | ≤1 / mostly wrong |
| **O4** | **Reference / sequencing** | ≥3 correct cohesive uses | 1–2 | 0 / often unclear |
| **O5** | **Repetition control** — trích từng cụm / cấu trúc câu bị lặp không cần thiết | 0–1 | 2 | ≥ 3 |

### LANGUAGE — L

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **L1** | **Vocabulary sufficiency & appropriacy** | Đủ, generally appropriate, meaning clear | Đủ cho ý chính nhưng có vài lexical gaps/errors | Limited; nhiều ý phải đơn giản hóa/bỏ |
| **L2** | **Vocabulary variety theo ngữ cảnh** — `K_topic` = số từ/cụm **hợp chủ đề theo ngữ cảnh đề bài**, dùng đúng, **không có sẵn trong đề**; mỗi mục tính 1 lần, phải trích nguyên văn | `K_topic` ≥ 6 | 3–5 | ≤ 2 |
| **L3** | **Spelling / word formation** | ≤2 lỗi / 50 từ | 3–5 | ≥6 / cản meaning |
| **L4** | **Basic grammar accuracy** | ≥75% complete/basic clauses acceptable | 50–74% | <50% |
| **L5** | **Sentence range** | Simple + ≥2 extended/complex sentences, ≥2 patterns | 1–2 extended sentences | Only short/repeated simple patterns |

## §4. Quy đổi

| Total /5 | 5 | 4.5 | 4 | 3.5 | 3 | 2.5 | 2 | 1.5 | 1 | 0.5 | 0 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **%** | 100 | 90 | 80 | 70 | 60 | 50 | 40 | 30 | 20 | 10 | 0 |

`Final % = average(C, CA, O, L)`, làm tròn xuống bội số 5. Áp gate cap sau khi chấm checkpoint.


### Internal anchor

| % | Interpretation |
|---:|---|
| **80–100** | Strong/secure B1-pathway performance |
| **60–75** | Meets/near Grade 8 Cambridge target |
| **40–55** | Developing B1; functional with support |
| **0–35** | Below current target / weak control |

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

Chỉ in khi lượt giao bài **có gắn key grammar** (mã tra trong `KeyGrammar_Grade8_Cambridge.md`). Không gắn thì bỏ hẳn phần này.

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

Ba bài dưới đây là **mốc chuẩn của khối 8 (hệ Cambridge B1)**. Cách dùng: đọc bài học sinh → đối chiếu với **dòng Checkpoint** của các neo để quyết định từng checkpoint cho 1 / 0.5 / 0 → cộng → quy đổi §4 → áp trần. **Không có bước điều chỉnh sau khi cộng** — % của tiêu chí chính là phép cộng checkpoint (hệ thống bỏ qua mọi điều chỉnh như vậy).

### Bài mỏng nội dung nhưng vẫn hiểu được — thang chuẩn theo khối

**"Mỏng"** = mọi ý bắt buộc đều có mặt, người đọc hiểu đúng, nhưng **không ý nào được phát triển** bằng lý do, ví dụ hay chi tiết cụ thể.

Với khối này: **Content = 40% hoặc thấp hơn** (tổng ≤2/5).

Đề cho 100 từ — thừa chỗ để phát triển ý. Ở khối này **phát triển ý là yêu cầu cốt lõi**, nên bài chỉ liệt kê thì `C3` = 0 và kéo cả `C1` xuống 0.5 vì nhiệm vụ chưa thực sự hoàn thành.

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

### Giá của một ý bắt buộc bị thiếu — bắt buộc

Đây là chỗ hay bị **double penalty**, trái với §1. Một ý bắt buộc bị bỏ sót chỉ được tính ở đúng hai chỗ:

| Số ý bắt buộc bị thiếu | C1 | CA4 | Ghi chú |
|---|---|---|---|
| 1 | 0.5 | 0.5 | **Không** trừ thêm ở C2, C3, C4, C5 hay CA khác cho cùng thiếu sót đó |
| ≥2 | 0 | 0 | Content trần **40%** |

Đặc biệt: **C5 (reader informed) chỉ xét thông tin mà người nhận cần**. Nếu ý bị thiếu là "đặt một câu hỏi cho bạn" thì người nhận vẫn nhận đủ thông tin họ hỏi — C5 vẫn = 1. Đừng dùng C5 để phạt lại lần nữa.

**Đề dùng chung cho ba neo:**

> Read this email from your English friend Olivia.
> "My family is visiting your town next month. Where is a good place for us to stay? What can we do there if it rains? Is it easy to travel around?"
> Write your email to Olivia. You should: suggest a place to stay and say why; suggest one thing to do on a rainy day; explain how to travel around the town; ask Olivia one question about the trip. Write about 100 words.

---

### NEO A — 40%

**Bài gốc (64 từ — 64% yêu cầu → G1 băng 50–79%: Content trần 60, không ảnh hưởng vì Content đã 50):**

> Hi Olivia
> Thank for your email. You can stay in Green Hotel. Green Hotel is good. If it rain you can go to the musium. It is a place for thing. My town have bus and taxy. You can go by bus. The bus are cheap. My town is very beautyful and many people come here every year. i hope you enjoy. See you.

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{pu1|Hi Olivia}}

{{gr1|Thank}} for your email. You can stay {{gr1|in Green Hotel}}. Green Hotel is good. If it {{gr2|rain}}{{pu1| you}} can go to the {{sp1|musium}}. {{wd2|It is a place for thing}}. My town {{gr2|have}} bus and {{sp1|taxy}}. You can go by bus. The bus {{gr1|are}} cheap. My town is very {{sp1|beautyful}} and many people come here every year. {{pu1|i}} hope you {{gr1|enjoy}}. See you.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 50% |
| Communicative Achievement | 40% |
| Organisation | 40% |
| Language | 40% |
| **Tổng kết** | **40%** |

### 3. Nhận xét

Em đã gợi ý được khách sạn, nơi đi chơi khi trời mưa và cách đi lại. Ưu tiên sắp tới: đặt một câu hỏi cho Olivia như đề yêu cầu, và giải thích vì sao em chọn khách sạn đó.

**Checkpoint:**
`C1` = 0.5 (thiếu ý 4: không hỏi lại Olivia) · `C2` = 1 · `C3` = 0 ("Green Hotel" là **trả lời**; không có chi tiết thêm) · `C4` = 0.5 (1 chỗ phải đoán: "It is a place for thing") · `C5` = 0.5 → 2.5/5 = **50%**.
`CA1` = 0.5 (lời chào thiếu dấu phẩy, không ký tên) · `CA2` = 0.5 · `CA3` = 0.5 (câu lạc mục đích: "My town is very beautyful…") · `CA4` = 0 (thiếu chức năng hỏi lại **và** lý do chọn khách sạn chỉ là "is good") · `CA5` = 0.5 (chỉ câu công thức "i hope you enjoy") → 2/5 = **40%**.
`O1` = 0.5 (nhảy sang giới thiệu thị trấn) · `O2` = 0 (một khối) · `O3` = 0.5 (If, and — 2 loại) · `O4` = 0.5 (It, here) · `O5` = 0.5 (lặp 2: "bus" ×3, "Green Hotel" ×2) → 2/5 = **40%**.
Language: động từ sai 5/11 = 45% → **trần 40%** (cũng là trần mật độ: 13 lỗi / 64 từ).
Tổng kết (50 + 40 + 40 + 40) / 4 = 42.5 → **40%**.

---

### NEO A0 — 30% · đáy thang thực tế

Neo này định nghĩa **đáy của thang điểm cho bài có thật**, phân biệt "rất yếu" với "chưa đủ dữ liệu để chấm" (0% theo cổng G1/G4). Bài là tiếng Anh thật, chạm được ba trong bốn ý, nhưng gần như không câu nào hoàn chỉnh.

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{pu1|hi}} olivia. {{gr2|your family come my town good}}. {{gr2|you stay green hotel}}. {{gr2|hotel near market}}. {{gr2|hotel cheap}}. {{gr2|rain you go museum}}. {{gr2|museum have many old thing}}. {{gr2|you like}}. {{gr2|travel easy}}. {{gr2|bus and taxi many}}. {{gr2|you take bus number five}}. {{gr2|my town small but nice}}. {{gr2|people friendly}}. {{gr2|you happy when come}}. {{pu1|bye}} olivia.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 40% |
| Communicative Achievement | 50% |
| Organisation | 20% |
| Language | 20% |
| **Tổng kết** | **30%** |

### 3. Nhận xét

Em trả lời được ba câu hỏi của Olivia và nhớ nêu số xe buýt cụ thể. Ưu tiên sắp tới: mỗi câu phải có động từ to be, ví dụ "The hotel **is** near the market".

**Checkpoint:**
52 từ = 52% yêu cầu → G1 băng 50–79% (Content trần 60).
`C1` = 0.5 (thiếu câu hỏi cho Olivia) · `C2` = 1 · `C3` = 0.5 (1 ý có bằng chứng loại (a): "bus number five") · `C4` = 0 (≥ 2 chỗ phải đoán: "your family come my town good", "you happy when come") · `C5` = 0.5 → 2.5/5 = 50% → động từ sai > 50% → **trần 40%**.
`CA1` = 0.5 (lời chào viết thường; "bye olivia" không có tên người viết) · `CA2` = 0.5 · `CA3` = 0.5 · `CA4` = 0.5 (thiếu chức năng hỏi lại) · `CA5` = 0.5 ("you happy when come") → 2.5/5 = **50%**.
`O1` = 0.5 · `O2` = 0 · `O3` = 0.5 (and, but) · `O4` = 0 (không tham chiếu — "hotel", "museum", "bus" lặp lại thay vì dùng đại từ) · `O5` = 0 → 1/5 = **20%**.
Language: động từ sai > 50% → **trần 20%**.
Tổng kết (40 + 50 + 20 + 20) / 4 = 32.5 → **30%**. **Đây là 30% chứ không phải 0%: bài vẫn là tiếng Anh và vẫn trả lời được ba câu hỏi.**

---

### NEO M — 75% · đủ ý nhưng mỏng, ngôn ngữ sạch

So với **NEO E (90%)**: bài đó cũng ngôn ngữ sạch nhưng **ý nào cũng có chi tiết thật** (mười phút từ chợ, xe buýt mười lăm phút một chuyến). Bài dưới đây đủ bốn ý nhưng **không ý nào có chi tiết**. Hai bài chỉ khác nhau ở đó, và Content chênh nhau 50 điểm.

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Hi Olivia,

I am glad that your family is coming next month.

You should stay at the Green Hotel. It is a good hotel. If it rains, you can visit the museum. It is interesting.

It is easy to travel around my town. There are buses and taxis.

How long will you stay?

See you soon,
Minh

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 40% |
| Communicative Achievement | 80% |
| Organisation | 80% |
| Language | 100% |
| **Tổng kết** | **75%** |

### 3. Nhận xét

Em trả lời đủ bốn ý và nhớ đặt câu hỏi cho Olivia. Ưu tiên sắp tới: mỗi gợi ý cần một chi tiết thật, ví dụ khách sạn cách chợ bao xa hay bảo tàng có gì.

**Checkpoint:**
57 từ = 57% yêu cầu → G1 băng 50–79% (Content trần 60).
`C1` = 1 · `C2` = 1 · `C3` = 0 ("It is a good hotel", "It is interesting", "There are buses and taxis" — **không một chi tiết nào Olivia dùng được**) · `C4` = 1 · `C5` = 0.5 → 3.5/5 → **trần bài mỏng khối 8: 40%**.
`CA1` = 1 · `CA2` = 0.5 (1 dấu hiệu: câu hỏi) · `CA3` = 1 · `CA4` = 1 · `CA5` = 0.5 (ngoài câu hỏi bắt buộc chỉ có câu công thức "I am glad…") → 4/5 = **80%**.
`O1` = 1 · `O2` = 1 · `O3` = 0.5 (If, and) · `O4` = 1 (It ×4) · `O5` = 0.5 (lặp cấu trúc "It is…" 2 lần) → 4/5 = **80%**.
`L1`–`L5` = 1 (that-clause, if-clause) → **100%**, 0 lỗi.
Tổng kết (40 + 80 + 80 + 100) / 4 = **75%**. **Đủ ý không đồng nghĩa với đủ thông tin.**

---

### NEO B — 60%

**Bài gốc (83 từ):**

> Hi Olivia,
> Thank you for your email about your trip. You should stay at the Green Hotel in the centre because the hotel is not expensive. If it rain, you can go to the museum. There are many old photo there. My sister work in a bank. Travel around my town is quite easy, you can take the bus or rent a bicycle. The bus is cheap and the bicycle is cheap too. I hope you have a good times.
> Best wishes,
> Minh

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Hi Olivia,

Thank you for your email about your trip. {{ok|You should stay at the Green Hotel in the centre because the hotel is not expensive.}} If it {{gr1|rain}}, you can go to the museum. There are many old {{gr1|photo}} there. My sister {{gr1|work}} in a bank. {{gr1|Travel}} around my town is quite easy{{pu1|, you}} can take the bus or rent a bicycle. The bus is cheap and the bicycle is cheap too. I hope you have a good {{gr1|times}}.

Best wishes,
Minh

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 60% |
| Communicative Achievement | 60% |
| Organisation | 60% |
| Language | 60% |
| **Tổng kết** | **60%** |

### 3. Nhận xét

Em gợi ý khách sạn có kèm lý do và trả lời đủ ba câu hỏi của Olivia. Ưu tiên sắp tới: nhớ đặt câu hỏi cho Olivia, và bỏ câu không liên quan đến chuyến đi.

**Checkpoint:**
`C1` = 0.5 (thiếu ý 4: không hỏi lại Olivia) · `C2` = 0.5 (1/9 câu lạc: "My sister work in a bank." → 89%) · `C3` = 0 (không có số, tên riêng hay sự việc ngoài câu trả lời trực tiếp) · `C4` = 1 · `C5` = 1 (Olivia nhận đủ câu trả lời cho ba câu hỏi của cô ấy) → 3/5 = **60%**.
`CA1` = 1 · `CA2` = 0.5 · `CA3` = 0.5 (1 câu lạc mục đích) · `CA4` = 0.5 (thiếu chức năng hỏi lại) · `CA5` = 0.5 (chỉ câu công thức "I hope you have…") → 3/5 = **60%**.
`O1` = 0.5 (đứt mạch ở câu lạc đề) · `O2` = 0.5 (thân bài một khối) · `O3` = 1 (because, If, or, and — 4 loại) · `O4` = 0.5 (1 tham chiếu: "there") · `O5` = 0.5 (lặp 2: "cheap", "the bus / the bicycle") → 3/5 = **60%**.
`L1`–`L5` = 1 → 5/5 → trần 6 lỗi / 83 từ = 7.2/100 → **60%**.
Tổng kết (60 + 60 + 60 + 60) / 4 = **60%**. Thiếu ý 4 chỉ bị tính ở `C1` và `CA4` — **không** trừ thêm ở `CA5` hay `C5`.

---

### NEO C — 80%

**Bài gốc (93 từ):**

> Hi Olivia,
> Thank you for your email. Here is some information for you trip.
> I would suggest the Green Hotel, which is near the market and cheap. If it rain, you should visit the town musuem on le loi Street, because it has a wonderful room of old photographs showing how my town look fifty years ago. Getting around is easy, the bus stops outside the hotel often, and you can also rent bicycles cheaply.
> How long are you planning to stay? I hope you will enjoy your trip.
> Best wishes,
> Minh

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Hi Olivia,

Thank you for your email. Here is some information for {{gr1|you}} trip.

{{ok|I would suggest the Green Hotel, which is near the market and cheap.}} If it {{gr1|rain}}, you should visit the town {{sp1|musuem}} on {{pu1|le loi}} Street, {{ok|because it has a wonderful room of old photographs showing how my town}} {{gr1|look}} {{ok|fifty years ago}}. Getting around is easy{{pu1|, the}} bus stops outside the hotel often, and you can also rent bicycles cheaply.

{{ok|How long are you planning to stay?}} I hope you will enjoy your trip.

Best wishes,
Minh

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 90% |
| Communicative Achievement | 80% |
| Organisation | 80% |
| Language | 70% |
| **Tổng kết** | **80%** |

### 3. Nhận xét

Em trả lời đủ bốn ý và có một chi tiết rất sống động về phòng ảnh cũ ở bảo tàng. Ưu tiên sắp tới: thêm chi tiết cụ thể cho khách sạn và xe buýt, và kiểm tra lại động từ sau "it" / "my town".

**Checkpoint:**
`C1` = 1 · `C2` = 1 · `C3` = 0.5 (1 ý có bằng chứng: bảo tàng trên phố Le Loi — tên riêng — với ảnh "fifty years ago"; khách sạn và xe buýt chỉ là trả lời chung) · `C4` = 1 · `C5` = 1 → 4.5/5 = **90%**.
`CA1` = 1 · `CA2` = 0.5 (1 dấu hiệu: câu hỏi) · `CA3` = 1 · `CA4` = 1 · `CA5` = 0.5 (ngoài câu hỏi bắt buộc chỉ có câu công thức "I hope you will enjoy…") → 4/5 = **80%**.
`O1` = 1 · `O2` = 0.5 (thân bài một khối) · `O3` = 1 (which, If, because, and, also) · `O4` = 1 (which, it, the hotel) · `O5` = 0.5 (lặp 2: "the hotel", "cheap / cheaply") → 4/5 = **80%**.
`L1`–`L5` = 1 → 5/5 → trần 6 lỗi / 93 từ = 6.5/100 → **70%**.
Tổng kết (90 + 80 + 80 + 70) / 4 = 80 → **80%**.

**Vì sao không phải 100%:** chỉ một ý có chi tiết thật; thân bài không tách đoạn; không có câu tương tác nào ngoài câu hỏi bắt buộc; sáu lỗi ngôn ngữ trong 93 từ.

---

### NEO D — 65% · hồ sơ lệch (ngữ pháp tốt nhưng sai hoàn toàn đối tượng nhận thư)

Bài viết cho bạn thân mà dùng giọng công văn. Neo này dạy: **câu đúng ngữ pháp không cứu được một lá thư gửi nhầm đối tượng** — Communicative Achievement rơi xuống 30% trong khi Organisation vẫn 100%.

**Đề:** PET Part 1 — email trả lời Olivia (4 ý, trong đó ý thứ tư là đặt một câu hỏi cho bạn).

**Bài gốc (92 từ):**

> Dear Sir or Madam,
> It is widely acknowledged that suitable accommodation constitutes a significant factor for international visitors. The Green Hotel may therefore be recommended, owing to its proximity to the municipal centre and its moderate tariff.
> In the event of precipitation, the town museum represents a viable alternative for the occupation of leisure time. Transportation within the urban area is facilitated by scheduled public buses and by bicycle rental services, both of which operate at regular intervals.
> I trust the aforementioned information will prove satisfactory for your requirements.
> Yours faithfully,
> Minh

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{wd2|Dear Sir or Madam,}}

{{wd2|It is widely acknowledged that}} suitable accommodation constitutes a significant factor for international visitors. {{ok|The Green Hotel may therefore be recommended, owing to its proximity to the municipal centre}} and its {{wd1|moderate tariff}}.

{{wd1|In the event of precipitation}}, the town museum represents a viable alternative for {{wd1|the occupation of leisure time}}. {{ok|Transportation within the urban area is facilitated by scheduled public buses and by bicycle rental services}}, both of which operate at regular intervals.

{{wd2|I trust the aforementioned information will prove satisfactory for your requirements.}}

{{wd2|Yours faithfully,}}
Minh

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 70% |
| Communicative Achievement | 30% |
| Organisation | 100% |
| Language | 60% |
| **Tổng kết** | **65%** |

### 3. Nhận xét

Em kiểm soát ngữ pháp rất tốt và câu nào cũng chặt chẽ. Ưu tiên sắp tới: viết cho bạn thân thì phải dùng giọng thân mật, và nhớ đặt một câu hỏi cho Olivia.

**Checkpoint:**
`C1` = 0.5 (thiếu ý 4) · `C2` = 1 · `C3` = 0 · `C4` = 1 · `C5` = 1 → 3.5/5 = **70%** (thiếu ý nên không phải bài "mỏng").
`CA1` = 0 (lời chào, câu kết và cụm chào cuối đều sai đối tượng — sai 3) · `CA2` = 0 (lệch giọng ở hầu hết các câu) · `CA3` = 1 · `CA4` = 0.5 (thiếu chức năng hỏi lại) · `CA5` = 0 → 1.5/5 = **30%**.
`O1`–`O5` = 1 (therefore, owing to, In the event of, both of which; its, its) → **100%**.
`L1` = 0.5 (từ ngữ không phù hợp người đọc) · `L2`–`L5` = 1 → 4.5/5 = 90% → trần 7 lỗi dùng từ (sai register) / 92 từ = 7.6/100 → **60%**.
Tổng kết (70 + 30 + 100 + 60) / 4 = **65%**.

---

### NEO E — 90% · email rất tốt nhưng thiếu đúng 1 ý bắt buộc

Neo này định giá chính xác cho một thiếu sót đơn lẻ, chặn kiểu trừ chồng nhiều lần.

**Đề:** PET Part 1 — email trả lời Olivia (4 ý; ý thứ tư là đặt một câu hỏi cho bạn).

**Bài gốc (92 từ) — trả lời đủ 3 ý, quên đặt câu hỏi:**

> Hi Olivia,
> I am really glad that your family is coming next month!
> I would suggest the Riverside Hotel, because it is only ten minutes from the old market and the rooms are quite cheap for a family.
> If it rains, you should visit the ceramic workshop on Hang Bac Street, where you can paint your own bowl and take it home.
> Getting around is easy. Bus number 7 passes the hotel every fifteen minutes, and you can also rent a motorbike for about 150,000 dong a day.
> See you soon,
> Minh

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Hi Olivia,

{{ok|I am really glad that your family is coming next month!}}

{{ok|I would suggest the Riverside Hotel, because it is only ten minutes from the old market and the rooms are quite cheap for a family.}}

{{ok|If it rains, you should visit the ceramic workshop on Hang Bac Street, where you can paint your own bowl and take it home.}}

Getting around is easy. {{ok|Bus number 7 passes the hotel every fifteen minutes}}, and you can also rent a motorbike for about 150,000 dong a day.

See you soon,
Minh

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 90% |
| Communicative Achievement | 70% |
| Organisation | 100% |
| Language | 100% |
| **Tổng kết** | **90%** |

### 3. Nhận xét

Ý nào em cũng có chi tiết thật và giọng thư rất tự nhiên, dễ mến. Ưu tiên sắp tới: đọc lại gạch đầu dòng của đề trước khi nộp, lần này em quên đặt câu hỏi cho Olivia.

**Checkpoint:**
`C1` = 0.5 (thiếu ý 4) · `C2` = 1 · `C3` = 1 (bằng chứng: "ten minutes from the old market", "Hang Bac Street", "Bus number 7 … every fifteen minutes") · `C4` = 1 · `C5` = 1 (Olivia đã nhận đủ câu trả lời cho cả ba câu hỏi của cô ấy — ý bị thiếu là câu hỏi ngược, không phải thông tin cô ấy cần) → 4.5/5 = **90%**.
`CA1` = 1 · `CA2` = 0.5 (1 dấu hiệu: câu cảm thán) · `CA3` = 1 · `CA4` = 0.5 (thiếu chức năng hỏi lại) · `CA5` = 0.5 (chỉ câu công thức "I am really glad…") → 3.5/5 = **70%**.
`O1`–`O5` = 1 (because, If, where, and, also; it, where, it) → **100%**.
`L1`–`L5` = 1 → **100%**, 0 lỗi.
Tổng kết (90 + 70 + 100 + 100) / 4 = **90%**. **Một thiếu sót đơn lẻ không được phép kéo sập bốn tiêu chí** — nó chỉ bị tính ở `C1` và `CA4`.
