# WRITING RUBRIC — GRADE 8 · IELTS FOUNDATION · v3

## §0. Cấu hình

```text
Input: original task + original student writing
Target: IELTS foundation around 4.0 (A2+ / early B1)
Criteria: TR/TA, CC, LR, GRA
Checkpoint: 1 / 0.5 / 0
Word limit: use the task; if absent -> 100 words (school training default)
Temperature: 0
Model call: 1 call / script
```

Rubric trung hòa hai nhóm: học sinh khá phải thể hiện range/control để đạt `1`; học sinh cơ bản vẫn có thể đạt `0.5` bằng output đơn giản nhưng có nghĩa và tương đối kiểm soát.

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

### TASK RESPONSE / ACHIEVEMENT — TR/TA

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **T1** | **Task coverage** | Đủ các phần chính | Thiếu/ yếu 1 phần | Thiếu ≥2 / hiểu sai chính |
| **T2** | **Purpose / position** | Rõ và nhất quán khi task yêu cầu | Có nhưng chưa ổn định | Không rõ / mâu thuẫn |
| **T3** | **Idea development** | ≥2 developed ideas | 1 | 0 / mainly listing |
| **T4** | **Relevance** | ≥90% câu liên quan | 80–89% | <80% |
| **T5** | **Task information control** | 0 major contradiction/misinterpretation | 1 | ≥2 / ảnh hưởng task |

### COHERENCE & COHESION — CC

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **CC1** | **Progression** | Mạch rõ xuyên suốt | 1 chỗ đứt mạch | Ý rời rạc |
| **CC2** | **Paragraphing / grouping** — đếm đoạn | Mở bài · ≥ 1 đoạn thân · kết bài, mỗi phần một đoạn riêng | 2 đoạn (mở bài hoặc kết bài không tách riêng) | Toàn bài một khối |
| **CC3** | **Linking** | ≥4 correct links, ≥3 types | 2–3 links, ≥2 types | ≤1 / mostly wrong |
| **CC4** | **Reference / pronouns** | ≥3 correct cohesive uses | 1–2 | 0 / unclear reference |
| **CC5** | **Repetition control** — trích từng ý / cụm lặp lại không cần thiết | 0–1 | 2 | ≥ 3 |

### LEXICAL RESOURCE — LR

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **L1** | **Vocabulary sufficiency** | Resource đủ cho mọi ý chính | Đủ cho ý chính nhưng có 1–2 lexical gaps | Limited; nhiều ý phải bỏ/đơn giản hóa |
| **L2** | **Word choice** | `N_lex` 0–2 | 3–5 | ≥6 / repeated meaning loss |
| **L3** | **Variety theo ngữ cảnh** — `K_topic` = số từ/cụm **hợp chủ đề theo ngữ cảnh đề bài**, dùng đúng, **không có sẵn trong đề**; mỗi mục tính 1 lần, phải trích nguyên văn | `K_topic` ≥ 6 | 3–5 | ≤ 2 |
| **L4** | **Spelling / word formation** | ≤2 lỗi / 50 từ | 3–5 | ≥6 / cản meaning |
| **L5** | **Paraphrase** — `P_para` = số chỗ **nói lại ý của đề bằng lời khác** (trích cặp *đề → bài*); `N_copy` do máy đo | `P_para` ≥ 1 **và** `N_copy` = 0 | `P_para` ≥ 1 nhưng `N_copy` > 0; hoặc `P_para` = 0 và `N_copy` = 0 | `P_para` = 0 **và** `N_copy` > 0 |

### GRAMMATICAL RANGE & ACCURACY — GRA

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **R1** | **Sentence completeness** | ≥85% complete clauses | 60–84% | <60% |
| **R2** | **Basic grammar control** | ≥75% basic clauses acceptable | 50–74% | <50% |
| **R3** | **Tense / verb control** | ≥75% correct | 50–74% | <50% |
| **R4** | **Sentence range** | Simple + ≥2 extended/complex sentences, ≥2 patterns | 1 extended/complex sentence | Only short/repeated simple patterns |
| **R5** | **Error impact** | 0–1 idea unclear because of grammar | 2 | ≥3 / frequent meaning loss |

## §4. Quy đổi

| Total /5 | 5 | 4.5 | 4 | 3.5 | 3 | 2.5 | 2 | 1.5 | 1 | 0.5 | 0 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **%** | 100 | 90 | 80 | 70 | 60 | 50 | 40 | 30 | 20 | 10 | 0 |

`Final % = average(TR/TA, CC, LR, GRA)`, làm tròn xuống bội số 5. Áp gate cap sau khi chấm checkpoint.

**Không tự quy Final % thành official IELTS band.** Đây là training score nội bộ đã hiệu chuẩn cho khối.


### Internal anchor

| % | Interpretation |
|---:|---|
| **80–100** | Strong Grade 8 IELTS-foundation performance |
| **60–75** | Meets/near current Grade 8 target |
| **40–55** | Developing; simple language still communicates |
| **0–35** | Early foundation / insufficient control |

## §5. Output bắt buộc

```markdown
### 1. Bài viết đã đánh dấu
<giữ nguyên bài gốc; chỉ chèn token màu, không viết phần sửa>

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | |
| Coherence & Cohesion | |
| Lexical Resource | |
| Grammatical Range & Accuracy | |
| **Tổng kết** | **__%** |

### 3. Nhận xét

**Nhận xét chung:** <≤2 câu, ≤50 từ, tiếng Việt: 1 điểm mạnh + 1 ưu tiên cần sửa>

**Key grammar: <tên các cấu trúc được giao>**
**<Đạt | Chưa đạt>** · dùng đúng <đúng>/<tổng> lần (<tỉ lệ>%)
<2 câu, khoảng 50 từ, tiếng Việt>
```

### Phần key grammar (filter 2)

Chỉ in khi lượt giao bài **có gắn key grammar** (mã tra trong `KeyGrammar_Grade8_IELTS.md`). Không gắn thì bỏ hẳn phần này.

- Cách đếm, tiêu chí đạt, cách nhận diện từng cấu trúc: xem file syllabus.
- **Key grammar Chưa đạt → Grammatical Range & Accuracy bị trần:** dùng đúng 0 lần → tối đa 40%; dùng đúng 1 lần → tối đa 50% (điểm thấp hơn thì giữ nguyên). Đạt thì không đổi điểm.
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

Ba bài dưới đây là **mốc chuẩn của khối 8 (hệ IELTS Foundation)**. Cách dùng: đọc bài học sinh → đối chiếu với **dòng Checkpoint** của các neo để quyết định từng checkpoint cho 1 / 0.5 / 0 → cộng → quy đổi §4 → áp trần. **Không có bước điều chỉnh sau khi cộng** — % của tiêu chí chính là phép cộng checkpoint (hệ thống bỏ qua mọi điều chỉnh như vậy).

### Bài mỏng nội dung nhưng vẫn hiểu được — thang chuẩn theo khối

**"Mỏng"** = mọi ý bắt buộc đều có mặt, người đọc hiểu đúng, nhưng **không ý nào được phát triển** bằng lý do, ví dụ hay chi tiết cụ thể.

Với khối này: **Task Response / Achievement = 40% hoặc thấp hơn** (tổng ≤2/5).

Đề cho 100 từ — thừa chỗ để phát triển ý. Ở khối này **phát triển ý là yêu cầu cốt lõi**, nên bài chỉ liệt kê thì `T3` = 0 và kéo cả `T1` xuống 0.5 vì nhiệm vụ chưa thực sự hoàn thành.

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
- **Không dùng độ ngắn làm lý do hạ Task Response / Achievement lần thứ hai.** Độ ngắn đã được tính đủ ở G1.
- Trần **"không có ý nào phát triển → tối đa 20%"** chỉ dành cho bài **rỗng thật sự**: không ý nào phát triển, **và** không một chi tiết cụ thể nào (tên, số, sự việc), **và** không nêu được lập trường khi đề yêu cầu. Bài mỏng nhưng vẫn có chi tiết cụ thể thì theo bảng trên, **không phải 20%**.

### Trần theo số lỗi cho tiêu chí ngôn ngữ — hệ thống tự áp

Các ngưỡng % ở §3 đo **"đạt mục tiêu khối"**, không đo **"không sai"**. Vì vậy sau khi cộng checkpoint, hệ thống
tự áp trần theo **mật độ lỗi trên 100 từ**, dựa đúng vào số lỗi đếm ở mục 0:

- **Grammatical Range & Accuracy** — đếm lỗi **ngữ pháp + dấu câu**.
- **Lexical Resource** — đếm lỗi **chính tả + dùng từ** (kể cả từ tiếng Việt). Lỗi ngữ pháp và dấu câu **không** làm giảm Lexical Resource.

| Lỗi / 100 từ | Tối đa |
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

`T3` được chấm theo **đúng số ý đã trích dẫn được bằng chứng** (con số, tên riêng, hoặc việc đã xảy ra).
Không trích dẫn được ý nào → `T3` = 0, dù bài "trông" có chi tiết.

### Lỗi ngôn ngữ không được lan sang tiêu chí nội dung — bắt buộc

Đây là dạng **double penalty** hay gặp nhất và là lỗi chấm nghiêm trọng nhất: bài sai ngữ pháp nên đọc vất vả, người chấm bèn hạ luôn cả **Task Response / Achievement**.

Lỗi ngữ pháp, chính tả và dấu câu **chỉ** được tính ở Lexical Resource và Grammatical Range & Accuracy.

**Task Response / Achievement** chỉ bị hạ khi:
- **thiếu ý** mà đề yêu cầu, hoặc
- ý **sai lệch** so với đề, hoặc
- người đọc **không xác định được** học sinh muốn nói gì.

Nếu người đọc vẫn hiểu đúng nội dung — dù câu thiếu động từ, sai thì, hay không có dấu chấm — thì **Task Response / Achievement giữ nguyên**. Một câu chuyện kể đủ tình tiết và hiểu được vẫn đạt điểm nội dung cao, ngay cả khi hàng ngôn ngữ chỉ còn 20%.

Ngoại lệ duy nhất là các **trần cứng** đã ghi rõ bên dưới (tỉ lệ động từ sai, tỉ lệ lạc ý, bài rỗng nội dung). Ngoài các trần đó, không tự ý hạ thêm.

Cả ba neo đều đủ số từ nên không neo nào kích hoạt cổng G1.

### Quy tắc lệch hàng — bắt buộc

Bốn tiêu chí **không phải bằng nhau**. Một bài có thể đúng đề và mạch rõ nhưng hỏng ngữ pháp hệ thống. Đừng kéo các hàng về gần nhau cho cân đối.

Trần cứng theo mức độ hỏng động từ, áp **trước** khi tính trung bình:

| Tỉ lệ động từ chính sai thì/dạng | Grammatical Range & Accuracy | Task Response / Achievement |
|---|---|---|
| > 50% | tối đa **20%** | tối đa **40%** |
| 34–50% | tối đa **40%** | tối đa **60%** |

Thêm: **quá nửa động từ sai → Tổng kết tối đa 35%** (hệ thống tự áp theo `N_verb_ok/N_verb` ở mục 0).

Lý do hạ cả Task Response: khi quá nửa động từ sai thì, người đọc phải tự dựng lại mốc thời gian của lập luận, nên nhiệm vụ chưa thực sự hoàn thành dù đủ ý.

### Quy tắc chống bị ngôn ngữ đánh lừa — bắt buộc

Một cụm chỉ được tô `{{ok|...}}` khi nó **vừa đúng vừa tự nhiên**. Cụm "nghe có vẻ cao cấp" nhưng sai collocation, là từ ghép tự chế, sai register, hoặc dài mà không xác định được nghĩa thì là **lỗi `wd`, không phải điểm mạnh**.

Độ dài câu và độ hiếm của từ **không** phải bằng chứng cho Lexical Resource — bằng chứng là **dùng đúng**. Bài đọc trôi chảy vẫn phải bị hạ hai hàng ngôn ngữ nếu đếm được nhiều lỗi chọn từ thật.

**Đề dùng chung cho ba neo:**

> Some students think that learning a foreign language is more useful than learning history. Do you agree or disagree? Give reasons for your answer. Write about 100 words.

---

### NEO A — 40%

**Bài gốc (81 từ; 5 từ trùng đề → `N_net` 76 = 76% yêu cầu → G1 băng 50–79%: Task Response trần 60, không ảnh hưởng vì đã bị trần mỏng 40):**

> I think foreign language is more useful. Because we can talk with foreigner. History is boredom, i don't like it. My teacher tell history is important. i don't agree. My sister is 15. Foreign language help us take job. Many company want people who speaks english. History are about old thing. It happen long time ago. i think we should learn foreign language. That is my opinion. Foreign language is very useful for student. We should learn it hardly every day.

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

I think foreign language is more useful. {{gr2|Because we can talk with foreigner}}. History is {{sp1|boredom}}, {{pu1|i}} don't like it. My teacher {{wd1|tell}} history is important. {{pu1|i}} don't agree. My sister is 15. Foreign language {{gr2|help}} us {{wd1|take job}}. Many {{gr1|company}} want people who {{gr1|speaks}} {{pu1|english}}. History {{gr1|are}} about old {{gr1|thing}}. It {{gr2|happen}} {{gr1|long time ago}}. {{pu1|i}} think we should learn foreign language. That is my opinion. Foreign language is very useful for {{gr1|student}}. We should learn it {{wd1|hardly}} every day.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 40% |
| Coherence & Cohesion | 20% |
| Lexical Resource | 70% |
| Grammatical Range & Accuracy | 40% |
| **Tổng kết** | **40%** |

### 3. Nhận xét

Em nêu được quan điểm rõ ràng ngay từ câu đầu. Ưu tiên sắp tới: giải thích mỗi lý do bằng một ví dụ cụ thể, và dùng từ nối để nối các câu ngắn.

**Checkpoint:**
`T1` = 1 · `T2` = 1 · `T3` = 0 (không ý nào có số, tên riêng hay sự việc) · `T4` = 1 (1/15 câu lạc → 93%) · `T5` = 1 → 5/5 → **trần bài mỏng khối 8: 40%**.
`CC1` = 0.5 (đứt mạch ở "My sister is 15.") · `CC2` = 0 (một khối) · `CC3` = 0 ("Because" dùng sai thành câu cụt; không từ nối đúng) · `CC4` = 0.5 (it, it) · `CC5` = 0 (lặp ≥ 3: "foreign language is (very) useful", "should learn", "That is my opinion") → 1/5 = **20%**.
`L1` = 1 · `L2` = 0.5 (N_lex = 3: tell, take job, hardly) · `L3` = 1 (`K_topic`: foreigner, teacher, job, company, old, study) · `L4` = 1 · `L5` = 0 (P_para = 0, N_copy = 5) → 3.5/5 = **70%** (trần 4 lỗi chính tả + dùng từ / 81 từ = 70 — trùng).
`R1` = 1 · `R2` = 0 · `R3` = 0.5 (4/14 động từ sai → 71%) · `R4` = 1 · `R5` = 1 → 3.5/5 = 70% → trần 13 lỗi ngữ pháp + dấu câu / 81 từ = 16/100 → **40%**.
Tổng kết (40 + 20 + 70 + 40) / 4 = 42.5 → **40%**.

---

### NEO A0 — 30% · đáy thang thực tế

Neo này định nghĩa **đáy của thang điểm cho bài có thật**, phân biệt "rất yếu" với "chưa đủ dữ liệu để chấm" (0% theo cổng G1/G4). Bài là tiếng Anh thật, nêu được quan điểm, nhưng không ý nào được phát triển, câu nào cũng thiếu thành phần và sai chính tả dày.

**Bài gốc (62 từ — 62% yêu cầu → G1 băng 50–79%):**

> i think foreign languag good. englsh very important. many people learn englsh. histroy not important. histroy old. we no use. foreign languag help job. compeny want englsh. i learn englsh every day. my teecher good. she teach english. i like. histroy boring. i no like. school teach english more good. that my idea. foreign language very good for studant. everybody learn english.

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{pu1|i}} think foreign {{sp1|languag}} good. {{sp1|englsh}} {{gr2|very important}}. many people learn {{sp1|englsh}}. {{sp1|histroy}} {{gr2|not important}}. {{sp1|histroy}} {{gr2|old}}. {{gr2|we no use}}. foreign {{sp1|languag}} {{gr2|help job}}. {{sp1|compeny}} want {{sp1|englsh}}. {{pu1|i}} learn {{sp1|englsh}} every day. my {{sp1|teecher}} {{gr2|good}}. she teach english. {{gr2|i like}}. {{sp1|histroy}} {{gr2|boring}}. {{gr2|i no like}}. {{gr2|school teach english more good}}. {{gr2|that my idea}}. foreign language {{gr2|very good}} for {{sp1|studant}}. everybody learn english.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 40% |
| Coherence & Cohesion | 20% |
| Lexical Resource | 40% |
| Grammatical Range & Accuracy | 20% |
| **Tổng kết** | **30%** |

### 3. Nhận xét

Em nêu được quan điểm ngay câu đầu và giữ nhất quán đến cuối. Ưu tiên sắp tới: mỗi câu phải có động từ to be hoặc trợ động từ, ví dụ "History **is** not important".

**Checkpoint:**
`T1` = 1 · `T2` = 1 · `T3` = 0 · `T4` = 1 · `T5` = 1 → **trần bài mỏng 40%** (động từ sai > 50% → trần 40% — trùng).
`CC1` = 0.5 · `CC2` = 0 · `CC3` = 0 (không từ nối) · `CC4` = 0.5 (1: "she") · `CC5` = 0 (lặp ≥ 3: good, englsh, histroy) → 1/5 = **20%**.
`L1` = 0.5 · `L2` = 1 · `L3` = 1 (`K_topic`: people, job, school, boring, idea, every day) · `L4` = 0 (12 lỗi chính tả / 62 từ) · `L5` = 0.5 → 3/5 = 60% → trần 12 lỗi / 62 từ = 19/100 → **40%**.
Grammatical Range: động từ sai > 50% → **trần 20%**.
Tổng kết (40 + 20 + 40 + 20) / 4 = **30%** (trần Tổng kết 35% — không ảnh hưởng). **Đây là 30% chứ không phải 0%: bài vẫn là tiếng Anh và vẫn nêu được quan điểm.**

---

### NEO M — 75% · đủ ý nhưng mỏng, ngôn ngữ sạch

So với **NEO A0** và **NEO A**: bài này **không lỗi ngôn ngữ nào**, đủ độ dài, nêu được quan điểm. Cái nó thiếu là **bằng chứng**. Phép thử: đừng để ngôn ngữ sạch kéo Task Response lên — Task Response vẫn chỉ 40%.

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

I agree that learning a foreign language is more useful than learning history.

A foreign language is useful for work. {{ok|Many companies want employees who speak English.}} It is also useful for travelling. People can talk to foreigners more easily.

{{ok|History is interesting, but it is less useful in daily life.}} Students do not need it for most jobs.

In conclusion, I think schools should spend more time on foreign languages than on history.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 40% |
| Coherence & Cohesion | 80% |
| Lexical Resource | 90% |
| Grammatical Range & Accuracy | 100% |
| **Tổng kết** | **75%** |

### 3. Nhận xét

Quan điểm của em rõ và câu nào cũng đúng ngữ pháp. Ưu tiên sắp tới: mỗi lý do cần một ví dụ thật, ví dụ một người em quen đã dùng tiếng Anh để xin việc.

**Checkpoint:**
`T1` = 1 · `T2` = 1 · `T3` = 0 (**không một ví dụ, con số hay trường hợp cụ thể nào** — chỉ mệnh đề khái quát) · `T4` = 1 · `T5` = 1 → 5/5 → **trần bài mỏng khối 8: 40%**.
`CC1` = 1 · `CC2` = 1 (mở bài · 2 đoạn thân · kết bài) · `CC3` = 0.5 (also, but, In conclusion — 3 từ nối, chưa đủ 4) · `CC4` = 0.5 (It, it) · `CC5` = 1 → 4/5 = **80%**.
`L1`–`L4` = 1 · `L5` = 0.5 (N_copy > 0 ở câu mở bài; P_para = 1: "spend more time on foreign languages than on history") → 4.5/5 = **90%**, 0 lỗi.
`R1`–`R5` = 1 (who-clause, but-clause) → **100%**, 0 lỗi.
Tổng kết (40 + 80 + 90 + 100) / 4 = 77.5 → **75%**.

Đối chiếu để không nhầm: bài này **không phải rỗng** — nó nói về việc làm và du lịch, là nội dung thật. Nó **mỏng**: nêu ra rồi bỏ đó.

---

### NEO B — 60%

**Bài gốc (102 từ; 10 từ trùng đề):**

> In my opinion learning a foreign language is more useful than learning history, history is not useful at all. English help us to communicate with people from other country. For example, my cousin got a good job in a hotel last year because my cousin can speak english well. We can also watch film and read book from many country. My brother play football every weekend.
> However, the history is also important because history teach us about our nation. But i think foreign language is more useful for our future. therefore, students should spend more time on the language and less time on the history.

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{pu1|In my opinion learning}} a foreign language is more useful than learning history{{pu1|, history}} is not useful at all. English {{gr2|help}} us to communicate with people from other {{gr1|country}}. {{ok|For example, my cousin got a good job in a hotel last year because my cousin can speak}} {{pu1|english}} well. We can also watch {{gr1|film}} and read {{gr1|book}} from many {{gr1|country}}. My brother {{gr1|play}} football every weekend.

However, {{gr1|the history}} is also important because history {{gr2|teach}} us about our nation. But {{pu1|i}} think foreign language is more useful for our future. {{pu1|therefore}}, students should spend more time on {{gr1|the language}} and less time on {{gr1|the history}}.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 70% |
| Coherence & Cohesion | 60% |
| Lexical Resource | 80% |
| Grammatical Range & Accuracy | 40% |
| **Tổng kết** | **60%** |

### 3. Nhận xét

Em có một ví dụ cụ thể rất thuyết phục và dùng tốt các từ nối For example, However. Ưu tiên sắp tới: giữ quan điểm nhất quán về môn Lịch sử, và danh từ số nhiều sau "many", "other".

**Checkpoint:**
`T1` = 1 · `T2` = 1 · `T3` = 0.5 (1 ý có bằng chứng loại (c): "my cousin got a good job in a hotel last year") · `T4` = 0.5 (1/9 câu lạc: "My brother play football…" → 89%) · `T5` = 0.5 (mâu thuẫn: "history is not useful at all" ↔ "history is also important") → 3.5/5 = **70%**.
`CC1` = 0.5 (đứt mạch ở câu lạc đề) · `CC2` = 0.5 (2 đoạn, không có kết bài riêng) · `CC3` = 1 (For example, because, also, However, But, therefore) · `CC4` = 0.5 (2: our, our) · `CC5` = 0.5 (lặp 2: "my cousin", "more useful") → 3/5 = **60%**.
`L1`–`L4` = 1 · `L5` = 0 (N_copy = 10, P_para = 0) → 4/5 = **80%**, 0 lỗi chính tả / dùng từ.
`R1` = 1 · `R2` = 0.5 · `R3` = 0.5 (3/11 động từ sai → 73%) · `R4` = 1 · `R5` = 1 → 4/5 = 80% → trần 15 lỗi ngữ pháp + dấu câu / 102 từ = 14.7/100 → **40%**.
Tổng kết (70 + 60 + 80 + 40) / 4 = 62.5 → **60%**.

---

### NEO C — 80%

**Bài gốc (111 từ):**

> I strongly agree that learning a foreign language is more useful for students today than learning history. The main reason is that a foreign language creates real oportunities. My cousin, who speak English fluenty, was offered a job at an internatonal hotel last year, while his friends with better history mark are still looking for work. Moreover, most useful information on the internet are written in English, so students who cannot read it lose access to a huge amount of knowledges.
> Although history helps us understand our country, it does not open doors in the same practical way. For this reason, i believe schools should give more hours to foreign languages.

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

I strongly agree that learning a foreign language is more useful for students today than learning history. The main reason is that a foreign language creates real {{sp1|oportunities}}. {{ok|My cousin, who}} {{gr1|speak}} English {{sp1|fluenty}}, {{ok|was offered a job at an}} {{sp1|internatonal}} {{ok|hotel last year, while his friends with better history}} {{gr1|mark}} are still looking for work. {{ok|Moreover}}, most useful information on the internet {{gr1|are}} written in English, {{ok|so students who cannot read it lose access to a huge amount of}} {{gr1|knowledges}}.

{{ok|Although history helps us understand our country}}, it does not open doors in the same practical way. For this reason, {{pu1|i}} believe schools should give more hours to foreign languages.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 90% |
| Coherence & Cohesion | 90% |
| Lexical Resource | 80% |
| Grammatical Range & Accuracy | 70% |
| **Tổng kết** | **80%** |

### 3. Nhận xét

Em phát triển lý do chính bằng một ví dụ thật và dùng câu phức rất chính xác. Ưu tiên sắp tới: chia động từ theo chủ ngữ ("who speaks", "information is") và tách mở bài thành đoạn riêng.

**Checkpoint:**
`T1` = 1 · `T2` = 1 · `T3` = 0.5 (1 ý có bằng chứng loại (c): "was offered a job … last year"; ý về internet là khái quát) · `T4` = 1 · `T5` = 1 → 4.5/5 = **90%**.
`CC1` = 1 · `CC2` = 0.5 (2 đoạn — mở bài không tách riêng) · `CC3` = 1 (while, Moreover, so, Although, For this reason) · `CC4` = 1 (who, his, it, it) · `CC5` = 1 → 4.5/5 = **90%**.
`L1`–`L4` = 1 · `L5` = 0.5 (N_copy > 0 ở câu mở bài; P_para = 1: "does not open doors in the same practical way") → 4.5/5 = 90% → trần 3 lỗi chính tả / 111 từ → **80%**.
`R1`–`R5` = 1 (who-clause, while, although, so) → 5/5 → trần 5 lỗi ngữ pháp + dấu câu / 111 từ = 4.5/100 → **70%**.
Tổng kết (90 + 90 + 80 + 70) / 4 = 82.5 → **80%**.

**Vì sao không phải 100%:** chỉ một ý có bằng chứng thật; mở bài không tách đoạn; năm lỗi ngữ pháp và ba lỗi chính tả trong 111 từ.

---

### NEO D — 65% · chép đề để kéo dài bài

Kiểu lách rất phổ biến: học sinh chép nguyên một câu của đề vào mở bài và lặp lại ở kết bài cho đủ số từ. Neo này định nghĩa cách áp **cổng G3**.

**Quy tắc:** chuỗi trùng ≥5 từ liên tiếp với đề bị **loại khỏi `N_net`** và **không được dùng làm bằng chứng** cho Lexical Resource hay Grammatical Range. Sau khi trừ, nếu `N_net` rơi vào vùng G1 thì áp tiếp trần underlength. (Khối 8 **không** có miễn trừ câu mở bài như khối 6–7.)

**Đề:**

> Some students think that learning a foreign language is more useful than learning history. Do you agree or disagree? Give reasons for your answer. Write about 100 words.

**Bài gốc (87 từ, trong đó 28 từ chép từ đề → `N_net` ≈ 59 = 59% yêu cầu → G1 băng 50–79%):**

> Some students think that learning a foreign language is more useful than learning history. In my opinion I agree with this idea.
> English help us to talk with people from other country and find a good job. My cousin speak English very well and he work in a big company now. History is only about the past and we can read it in the book.
> Some students think that learning a foreign language is more useful than learning history. That is why I agree with this opinion.

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{wd2|Some students think that learning a foreign language is more useful than learning history.}} In my opinion I agree with this idea.

English {{gr2|help}} us to talk with people from other {{gr1|country}} and find a good job. My cousin {{gr2|speak}} English very well and he {{gr2|work}} in a big company now. History is only about the past and we can read it in {{wd1|the book}}.

{{wd2|Some students think that learning a foreign language is more useful than learning history.}} That is why I agree with this opinion.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 40% |
| Coherence & Cohesion | 80% |
| Lexical Resource | 70% |
| Grammatical Range & Accuracy | 70% |
| **Tổng kết** | **65%** |

### 3. Nhận xét

Em có nêu được quan điểm và đưa ví dụ về người anh họ. Ưu tiên sắp tới: không chép lại câu của đề, hãy diễn đạt bằng lời của mình và viết thêm ý cho đủ độ dài.

**Checkpoint:**
`N_net` 59% → G1 băng 50–79% (Task Response trần 60).
`T1` = 1 · `T2` = 1 · `T3` = 0 ("he work in a big company now" là tình trạng, không phải một sự việc có mốc thời gian) · `T4` = 1 · `T5` = 1 → **trần bài mỏng 40%**.
`CC1` = 1 · `CC2` = 1 (mở · thân · kết) · `CC3` = 0.5 (and ×3, That is why — 2 loại) · `CC4` = 1 (us, he, it, this) · `CC5` = 0.5 (lặp 2: câu chép, "I agree") → 4/5 = **80%**.
`L1` = 1 · `L2` = 0.5 (N_lex = 3: 2 câu chép + "the book") · `L3` = 1 · `L4` = 1 · `L5` = 0 (N_copy = 28, P_para = 0) → 3.5/5 = **70%**.
`R1`–`R2` = 1 · `R3` = 0.5 (3/9 động từ sai) · `R4`–`R5` = 1 → 4.5/5 = 90% → trần 4 lỗi ngữ pháp / 87 từ = 4.6/100 → **70%**.
Tổng kết (40 + 80 + 70 + 70) / 4 = **65%**. Phần chép bị trừ ở ba chỗ đúng chức năng: `N_net` (→ G1), `L5` (không diễn đạt lại), và `CC5` (lặp) — **không** trừ thêm ở checkpoint nào khác.
