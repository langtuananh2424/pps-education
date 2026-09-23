# WRITING RUBRIC — GRADE 7 · IELTS FOUNDATION · v3

## §0. Cấu hình

```text
Input: original task + original student writing
Target: IELTS foundation ≈ CEFR A2+ / early B1
Criteria: TR/TA, CC, LR, GRA
Checkpoint: 1 / 0.5 / 0
Word limit: use the task; if absent -> 60 words (school training default)
Temperature: 0
Model call: 1 call / script
```

Rubric này là **training rubric**, không phải official band conversion. `1` = đạt tốt mục tiêu khối 7; `0.5` = đang phát triển và vẫn ghi nhận được năng lực nền.

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

### TASK RESPONSE / ACHIEVEMENT — TR/TA

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **T1** | **Task coverage** | Đủ tất cả phần chính | Thiếu/ yếu 1 phần | Thiếu ≥2 / hiểu sai chính |
| **T2** | **Purpose / position** | Rõ và phù hợp khi task yêu cầu | Có nhưng chưa ổn định | Không rõ / mâu thuẫn |
| **T3** | **Idea development** | ≥2 developed ideas | 1 | 0 / mainly listing |
| **T4** | **Relevance** | ≥90% câu liên quan | 75–89% | <75% |
| **T5** | **Task information control** | Không có mâu thuẫn/misinterpretation đáng kể | 1 chỗ | ≥2 / ảnh hưởng task |

### COHERENCE & COHESION — CC

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **CC1** | **Progression** | Mạch rõ từ đầu đến cuối | 1 chỗ đứt mạch | Ý rời rạc |
| **CC2** | **Paragraphing / grouping** — đếm đoạn. Email: thân bài tách ≥ 2 đoạn/dòng ý; chào đầu và chào cuối tách dòng riêng. Essay / story ≤ 70 từ: có câu mở (nêu ý kiến / bối cảnh) **và** câu kết, hoặc ≥ 2 đoạn | Email: thân bài ≥ 2 đoạn. Essay/story: có cả câu mở và câu kết | Email: thân bài một khối nhưng chào đầu **và** chào cuối tách riêng. Essay/story: thiếu câu mở **hoặc** câu kết | Toàn bài một khối / thiếu cả câu mở lẫn câu kết |
| **CC3** | **Linking** | ≥3 correct links, ≥2 types | 1–2 | 0 / mostly wrong |
| **CC4** | **Reference / pronouns** | ≥2 correct cohesive uses | 1 | 0 / reference often unclear |
| **CC5** | **Repetition control** — trích từng ý / cụm lặp lại không cần thiết | 0–1 | 2 | ≥ 3 |

### LEXICAL RESOURCE — LR

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **L1** | **Vocabulary sufficiency** | Đủ từ để diễn đạt mọi ý chính | Thiếu từ ở 1–2 chỗ nhưng ý vẫn hiểu | Thiếu từ làm ≥2 ý không rõ |
| **L2** | **Word choice** | `N_lex` 0–2 | 3–4 | ≥5 / repeated meaning loss |
| **L3** | **Variety theo ngữ cảnh** — `K_topic` = số từ/cụm **hợp chủ đề theo ngữ cảnh đề bài**, dùng đúng, **không có sẵn trong đề**; mỗi mục tính 1 lần, phải trích nguyên văn | `K_topic` ≥ 4 | 2–3 | ≤ 1 |
| **L4** | **Spelling / word formation** | ≤3 lỗi / 50 từ | 4–6 | ≥7 / cản meaning |
| **L5** | **Paraphrase** — `P_para` = số chỗ **nói lại ý của đề bằng lời khác** (trích cặp *đề → bài*); `N_copy` do máy đo | `P_para` ≥ 1 **và** `N_copy` = 0 | `P_para` ≥ 1 nhưng `N_copy` > 0; hoặc `P_para` = 0 và `N_copy` = 0 | `P_para` = 0 **và** `N_copy` > 0 |

### GRAMMATICAL RANGE & ACCURACY — GRA

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **R1** | **Sentence completeness** | ≥80% complete clauses | 50–79% | <50% |
| **R2** | **Basic grammar control** | ≥70% basic clauses acceptable | 45–69% | <45% |
| **R3** | **Tense / verb control** | ≥70% correct | 45–69% | <45% |
| **R4** | **Sentence range** | Simple + ≥2 joined/extended sentences | Simple + 1 extended | Only short/repeated simple patterns |
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
| **80–100** | Strong Grade 7 IELTS-foundation performance |
| **60–75** | Meets/near current foundation target |
| **40–55** | Developing; basic meaning generally possible |
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

Chỉ in khi lượt giao bài **có gắn key grammar** (mã tra trong `KeyGrammar_Grade7_IELTS.md`). Không gắn thì bỏ hẳn phần này.

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

Ba bài dưới đây là **mốc chuẩn của khối 7**. Cách dùng: đọc bài học sinh → đối chiếu với **dòng Checkpoint** của các neo để quyết định từng checkpoint cho 1 / 0.5 / 0 → cộng → quy đổi §4 → áp trần. **Không có bước điều chỉnh sau khi cộng** — % của tiêu chí chính là phép cộng checkpoint (hệ thống bỏ qua mọi điều chỉnh như vậy).

### Bài mỏng nội dung nhưng vẫn hiểu được — thang chuẩn theo khối

**"Mỏng"** = mọi ý bắt buộc đều có mặt, người đọc hiểu đúng, nhưng **không ý nào được phát triển** bằng lý do, ví dụ hay chi tiết cụ thể.

Với khối này: **Task Response / Achievement = 50%** (tổng 2.5/5).

Đề cho 60 từ — đã đủ chỗ cho ít nhất một lý do. Bài chỉ liệt kê mà không giải thích thì `T3` = 0.

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

Mỗi neo có dòng **Checkpoint:** — cộng đúng từng checkpoint ra đúng % trong bảng của chính neo đó. Dùng các dòng này làm mẫu chấm checkpoint.

### Quy tắc lệch hàng — bắt buộc

Bốn tiêu chí **không phải bằng nhau**. Một bài có thể đúng đề và mạch rõ nhưng hỏng ngữ pháp hệ thống. Đừng kéo các hàng về gần nhau cho cân đối.

Trần cứng theo mức độ hỏng động từ, áp **trước** khi tính trung bình:

| Tỉ lệ động từ chính sai thì/dạng | Grammatical Range & Accuracy | Task Response / Achievement |
|---|---|---|
| > 50% | tối đa **20%** | tối đa **40%** |
| 34–50% | tối đa **40%** | tối đa **60%** |

Thêm: **quá nửa động từ sai → Tổng kết tối đa 35%** (hệ thống tự áp theo `N_verb_ok/N_verb` ở mục 0).

Lý do hạ cả Task Response: khi quá nửa động từ sai thì, người đọc phải tự dựng lại mốc thời gian của sự việc, nên nhiệm vụ chưa thực sự hoàn thành dù đủ ý.

### Quy tắc chống bị ngôn ngữ đánh lừa — bắt buộc

Một cụm chỉ được tô `{{ok|...}}` khi nó **vừa đúng vừa tự nhiên**. Cụm "nghe có vẻ cao cấp" nhưng sai collocation, là từ ghép tự chế, sai register, hoặc dài mà không xác định được nghĩa thì là **lỗi `wd`, không phải điểm mạnh**.

Độ dài câu và độ hiếm của từ **không** phải bằng chứng cho Lexical Resource — bằng chứng là **dùng đúng**.

**Đề dùng chung cho ba neo đầu:**

> Your English friend Chris wants to know about your school. Write an email to Chris. Say: what your school is like, which subject you like best and why, what you usually do after school. Write about 60 words.

---

### NEO A — 40%

**Bài gốc (52 từ):**

> Hi Chris
> My school is big and have many student. I like english best. the teacher is nise. My brother is 10. after school i play footbal with my freind. Sometime i go home and see tv. I am happy in my scool. My school is very good school. I like it.

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Hi Chris

My school is big and {{gr2|have}} many {{gr1|student}}. I like {{pu1|english}} best. {{pu1|the}} teacher is {{sp1|nise}}. My brother is 10. {{pu1|after}} school {{pu1|i}} play {{sp1|footbal}} with my {{sp1|freind}}. {{sp1|Sometime}} {{pu1|i}} go home and {{wd1|see tv}}. I am happy in my {{sp1|scool}}. My school is {{gr1|very good school}}. I like it.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 50% |
| Coherence & Cohesion | 30% |
| Lexical Resource | 50% |
| Grammatical Range & Accuracy | 40% |
| **Tổng kết** | **40%** |

### 3. Nhận xét

Em đã chạm được cả ba ý mà đề yêu cầu. Ưu tiên sắp tới: chia động từ theo chủ ngữ và viết hoa đầu câu.

**Checkpoint:**
`T1` = 1 (đủ ba ý; "the teacher is nise" trả lời *why*) · `T2` = 1 · `T3` = 0 (không ý nào có số, tên riêng hay sự việc) · `T4` = 1 (1/10 câu lạc → 90%) · `T5` = 1 → 5/5 → **trần bài mỏng khối 7: 50%**.
`CC1` = 0.5 (đứt mạch ở "My brother is 10.") · `CC2` = 0 (không có chào cuối, thân bài một khối) · `CC3` = 0.5 (2 lần "and") · `CC4` = 0.5 (1 tham chiếu: "it") · `CC5` = 0 (lặp ≥ 3: "happy in my scool", "very good school", "I like it") → 1.5/5 = **30%**.
`L1` = 1 · `L2` = 1 (N_lex = 1) · `L3` = 1 (`K_topic`: big, home, tv, happy) · `L4` = 0.5 (5 lỗi chính tả / 52 từ) · `L5` = 0.5 (P_para = 0, N_copy = 0) → 4/5 = 80% → trần 6 lỗi chính tả + dùng từ / 52 từ = 11.5/100 → **50%**.
`R1` = 1 · `R2` = 0.5 (3/10 mệnh đề sai) · `R3` = 1 · `R4` = 0.5 · `R5` = 1 → 4/5 = 80% → trần 8 lỗi ngữ pháp + dấu câu / 52 từ = 15/100 → **40%**.
Tổng kết (50 + 30 + 50 + 40) / 4 = 42.5 → **40%**.

---

### NEO A0 — 30% · đáy thang thực tế

Neo này định nghĩa **đáy của thang điểm cho bài có thật**, phân biệt "rất yếu" với "chưa đủ dữ liệu để chấm" (0% theo cổng G1/G4). Bài là tiếng Anh thật, nhận ra được ý định, nhưng gần như không câu nào hoàn chỉnh và sai chính tả dày.

**Bài gốc (43 từ — 72% yêu cầu → G1 băng 50–79%):**

> hi chris. my scool big. have many studen and techer. i like english. english good. techer good too. after scool i go home. sometime i play with frend. my scool have libary. libary big. i happy in scool. scool very nice. bye chris.

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{pu1|hi}} chris. {{gr2|my}} {{sp1|scool}} {{gr2|big}}. {{gr2|have many}} {{sp1|studen}} and {{sp1|techer}}. {{pu1|i}} like {{pu1|english}}. {{gr2|english good}}. {{sp1|techer}} {{gr2|good too}}. {{pu1|after}} {{sp1|scool}} {{pu1|i}} go home. {{sp1|sometime}} {{pu1|i}} play with {{sp1|frend}}. my {{sp1|scool}} {{gr2|have}} {{sp1|libary}}. {{sp1|libary}} {{gr2|big}}. {{pu1|i}} {{gr2|happy}} in {{sp1|scool}}. {{sp1|scool}} {{gr2|very nice}}. bye chris.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 40% |
| Coherence & Cohesion | 20% |
| Lexical Resource | 40% |
| Grammatical Range & Accuracy | 20% |
| **Tổng kết** | **30%** |

### 3. Nhận xét

Em đã chạm được cả ba ý mà đề hỏi. Ưu tiên sắp tới: mỗi câu phải có động từ, ví dụ "My school **is** big" thay vì "my scool big".

**Checkpoint:**
`T1` = 0.5 ("english good" — phần *why* rất yếu) · `T2` = 1 · `T3` = 0 · `T4` = 1 · `T5` = 1 → 3.5/5 → trần bài mỏng 50 → động từ sai > 50% → **trần 40%**.
`CC1` = 0.5 · `CC2` = 0 (một khối) · `CC3` = 0.5 (1 "and") · `CC4` = 0 · `CC5` = 0 (lặp ≥ 3: good, big, scool) → 1/5 = **20%**.
`L1` = 0.5 · `L2` = 1 · `L3` = 1 (`K_topic`: home, play, english, big, nice) · `L4` = 0 (11 lỗi chính tả / 43 từ) · `L5` = 0.5 → 3/5 = 60% → trần 11 lỗi / 43 từ → **40%**.
Grammatical Range: động từ sai > 50% → **trần 20%**.
Tổng kết (40 + 20 + 40 + 20) / 4 = **30%** (trần Tổng kết 35% do động từ sai > 50% — không ảnh hưởng). **Đây là 30% chứ không phải 0%: bài vẫn là tiếng Anh và vẫn chạm đủ ý.**

---

### NEO M — 80% · đủ ý nhưng mỏng, ngôn ngữ sạch

So với **NEO A0** và **NEO A**: cả ba bài đều không phát triển ý. Khác biệt là bài này **ngôn ngữ không lỗi**, nên hai hàng ngôn ngữ cao trong khi Task Response vẫn chỉ 50%. Đây là phép thử: **đừng để ngôn ngữ sạch kéo Task Response lên.**

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Hi Chris,

{{ok|My school is quite big and it is near my house.}} I like English best. It is a good subject.

After school I usually play football with my friends. Sometimes I go home early. I hope you are well.

Write back soon,
Minh

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 50% |
| Coherence & Cohesion | 90% |
| Lexical Resource | 90% |
| Grammatical Range & Accuracy | 90% |
| **Tổng kết** | **80%** |

### 3. Nhận xét

Câu nào của em cũng đúng ngữ pháp và bố cục thư rất gọn. Ưu tiên sắp tới: đề hỏi vì sao em thích môn đó nhất — hãy thêm một lý do cụ thể cho mỗi ý.

**Checkpoint:**
45 từ = 75% yêu cầu → G1 băng 50–79% (Task Response trần 60).
`T1` = 0.5 ("It is a good subject" không trả lời *why*) · `T2` = 1 · `T3` = 0 · `T4` = 1 · `T5` = 1 → 3.5/5 → **trần bài mỏng 50%**.
`CC1` = 1 · `CC2` = 1 (thân bài 2 đoạn) · `CC3` = 0.5 (1 "and") · `CC4` = 1 (it, It) · `CC5` = 1 → 4.5/5 = **90%**.
`L1`–`L4` = 1 (`K_topic`: house, football, friends, home, early) · `L5` = 0.5 → 4.5/5 = **90%**, 0 lỗi.
`R1`–`R3` = 1 · `R4` = 0.5 (chỉ 1 câu ghép) · `R5` = 1 → 4.5/5 = **90%**, 0 lỗi.
Tổng kết (50 + 90 + 90 + 90) / 4 = 80 → **80%**. **Ba hàng cao không kéo được Task Response lên** — đó là điều neo này chặn.

---

### NEO B — 60%

**Bài gốc (55 từ):**

> Hi Chris
> My school is quite big and there are about 900 student. I like English best. My sister is in grade 3. After school I usually play football with my freind and sometime we goes to the libary to see books. I like my school, it is a nice school.
> Write soon,
> Minh

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{pu1|Hi Chris}}

My school is quite big and there are about 900 {{gr1|student}}. I like English best. My sister is in grade 3. After school I usually play football with my {{sp1|freind}} and {{sp1|sometime}} we {{gr1|goes}} to the {{sp1|libary}} to {{wd1|see books}}. I like my school{{pu1|, it}} is a nice school.

Write soon,
Minh

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 70% |
| Coherence & Cohesion | 60% |
| Lexical Resource | 60% |
| Grammatical Range & Accuracy | 60% |
| **Tổng kết** | **60%** |

### 3. Nhận xét

Em cho người đọc một chi tiết cụ thể về quy mô trường. Ưu tiên sắp tới: trả lời đủ câu hỏi *why* và bỏ câu không liên quan đến đề.

**Checkpoint:**
`T1` = 0.5 (thiếu phần *why*) · `T2` = 1 · `T3` = 0.5 (1 ý có bằng chứng loại (a): "about 900 student") · `T4` = 0.5 (1/6 câu lạc: "My sister is in grade 3." → 83%) · `T5` = 1 → 3.5/5 = **70%**.
`CC1` = 0.5 (đứt mạch ở câu lạc đề) · `CC2` = 0.5 (thân bài một khối, chào đầu và chào cuối tách dòng) · `CC3` = 0.5 (2 lần "and") · `CC4` = 1 (we, it) · `CC5` = 0.5 (lặp 2: "I like", "school") → 3/5 = **60%**.
`L1` = 1 · `L2` = 1 (N_lex = 1) · `L3` = 1 (`K_topic`: big, 900, football, books, nice) · `L4` = 1 (3 lỗi chính tả / 55 từ) · `L5` = 0.5 → 4.5/5 = 90% → trần 4 lỗi chính tả + dùng từ / 55 từ = 7.3/100 → **60%**.
`R1` = 1 · `R2` = 1 · `R3` = 1 · `R4` = 0.5 · `R5` = 1 → 4.5/5 = 90% → trần 4 lỗi ngữ pháp + dấu câu / 55 từ = 7.3/100 → **60%**.
Tổng kết (70 + 60 + 60 + 60) / 4 = 62.5 → **60%**.

---

### NEO C — 80%

**Bài gốc (80 từ):**

> Hi Chris,
> My school is quite big and there are many student and teachers. I like English best because the teacher explain everything clearly and we often play games in the lesson. After school I usually play football with my freinds, but on friday I go to the school libary to prepare for my tests, it is quiet there. My school is a big school and I like it very much.
> Please tell me about your school too.
> Write soon,
> Minh

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Hi Chris,

My school is quite big and there are many {{gr1|student}} and teachers. {{ok|I like English best because the teacher}} {{gr1|explain}} everything clearly and we often play games in the lesson. After school I usually play football with my {{sp1|freinds}}, {{ok|but on}} {{pu1|friday}} {{ok|I go to the school}} {{sp1|libary}} {{ok|to prepare for my tests}}{{pu1|, it}} is quiet there. My school is a big school and I like it very much.

Please tell me about your school too.

Write soon,
Minh

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 90% |
| Coherence & Cohesion | 80% |
| Lexical Resource | 80% |
| Grammatical Range & Accuracy | 70% |
| **Tổng kết** | **80%** |

### 3. Nhận xét

Em trả lời đủ ba ý, có lý do cho môn học yêu thích và một chi tiết cụ thể về ngày thứ Sáu. Ưu tiên sắp tới: chia động từ theo chủ ngữ và tách thân bài thành hai đoạn.

**Checkpoint:**
`T1` = 1 · `T2` = 1 · `T3` = 0.5 (1 ý có bằng chứng loại (a): "on friday I go to the school libary to prepare for my tests"; lý do thích English là thói quen chung, không tính) · `T4` = 1 · `T5` = 1 → 4.5/5 = **90%**.
`CC1` = 1 · `CC2` = 0.5 (thân bài một khối) · `CC3` = 1 (and, because, but — 3 loại) · `CC4` = 1 (the teacher, we, it, there) · `CC5` = 0.5 (lặp 2: "big school", "I like it") → 4/5 = **80%**.
`L1`–`L4` = 1 (`K_topic`: teachers, clearly, games, football, tests, quiet) · `L5` = 0.5 → 4.5/5 = 90% → trần 2 lỗi chính tả / 80 từ = 2.5/100 → **80%**.
`R1`–`R5` = 1 → 5/5 → trần 4 lỗi ngữ pháp + dấu câu / 80 từ = 5/100 → **70%**.
Tổng kết (90 + 80 + 80 + 70) / 4 = **80%**.

**Vì sao không phải 100%:** chỉ một ý có chi tiết thật; thân bài không tách đoạn; lặp ý ở câu cuối; bốn lỗi ngữ pháp và dấu câu trong 80 từ.

---

### NEO D — 35% · hồ sơ lệch (đúng đề nhưng hỏng thì động từ)

Neo này dạy cách chấm dạng bài phổ biến nhất ở học sinh Việt Nam: **kể đúng, mạch rõ, nhưng gần như mọi động từ đều sai thì**. Bốn hàng lệch nhau rõ rệt — và **Tổng kết bị trần 35%**.

**Đề:**

> Write a short story that begins with this sentence: "The school bus did not come that morning." Write about 60 words.

**Bài gốc (60 từ, trong đó 8 từ là câu mở đề cho sẵn — câu mở bài nên được miễn trừ khỏi G1 ở khối 7):**

> The school bus did not come that morning. I wait at the bus stop very long time. I am verry worry becaus i will late for the test.
> My freind Nam call his father and he take us to school by motorbike. When we arrive, the teacher is angry but she let us in. I am very happy and lucky.

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

The school bus did not come that morning. I {{gr2|wait}} at the bus stop {{gr1|very long time}}. I {{gr2|am}} {{sp1|verry}} {{wd1|worry}} {{sp1|becaus}} {{pu1|i}} {{gr2|will late}} for the test.

My {{sp1|freind}} Nam {{gr2|call}} his father and he {{gr2|take}} us to school by motorbike. {{gr2|When we arrive}}, the teacher {{gr2|is}} angry {{ok|but she let us in}}. I am very happy and lucky.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 40% |
| Coherence & Cohesion | 100% |
| Lexical Resource | 70% |
| Grammatical Range & Accuracy | 20% |
| **Tổng kết** | **35%** |

### 3. Nhận xét

Em kể đúng đề, diễn biến có mở đầu và kết thúc rõ ràng. Ưu tiên sắp tới: kể chuyện đã xảy ra thì mọi động từ phải ở quá khứ đơn — đây là lỗi lặp lại ở gần hết bài.

**Checkpoint:**
`T1`–`T5` = 1 (có "Nam" — tên riêng, và một sự việc kể lại được) → 100% → động từ sai > 50% → **trần 40%**.
`CC1`–`CC5` = 1 (hai đoạn, becaus / and / When / but, he / us / she / we) → **100%**.
`L1`–`L4` = 1 · `L5` = 0 (N_copy = 8, P_para = 0) → 4/5 = 80% → trần 4 lỗi chính tả + dùng từ / 60 từ = 6.7/100 → **70%**.
Grammatical Range: 8/9 động từ chính sai (> 50%) → **trần 20%**.
Trung bình (40 + 100 + 70 + 20) / 4 = 57.5 → **trần Tổng kết 35%** vì quá nửa động từ sai. **Bài đúng đề mà hỏng thì động từ hệ thống thì không thể đạt 40%** — bất kể mạch văn tốt đến đâu.

---

### NEO E — 80% · pha tiếng Việt khi bí từ

Kiểu bài rất phổ biến: học sinh viết đúng hướng, bố cục tốt, nhưng cứ bí từ là chèn thẳng tiếng Việt vào. Neo này định nghĩa **mức phạt đúng** cho hiện tượng đó: phạt **một lần, ở Lexical Resource**.

**Quy tắc (§2):** từ/cụm tiếng Việt lẻ tẻ là **lexical gap**, đánh dấu `wd` và kéo Lexical Resource xuống. **Không trừ lần thứ hai** ở Grammatical Range — câu chứa từ tiếng Việt vẫn được tính là câu đúng ngữ pháp nếu cấu trúc của nó đúng.

**Đề:**

> Your English friend Chris wants to know about your school. Write an email to Chris. Say: what your school is like, which subject you like best and why, what you usually do after school. Write about 60 words.

**Bài gốc (57 từ):**

> Hi Chris,
> My school is very big and it has a san truong very large. There are 900 students and many cay xanh around.
> I like Literature best because the teacher is very hien and she tell many interesting story. After school I usually play cau long with my friends near the gate.
> Write back soon,
> Minh

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Hi Chris,

My school is very big and it has a {{wd2|san truong}} very large. There are 900 students and many {{wd2|cay xanh}} around.

{{ok|I like Literature best because the teacher is very}} {{wd2|hien}} and she {{gr2|tell}} many interesting {{gr1|story}}. {{ok|After school I usually play}} {{wd2|cau long}} {{ok|with my friends near the gate}}.

Write back soon,
Minh

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 90% |
| Coherence & Cohesion | 100% |
| Lexical Resource | 60% |
| Grammatical Range & Accuracy | 80% |
| **Tổng kết** | **80%** |

### 3. Nhận xét

Em trả lời đủ ba ý, bố cục thư rõ ràng và biết dùng because để giải thích lựa chọn. Ưu tiên sắp tới: tra từ tiếng Anh thay vì viết tiếng Việt, và thêm s vào danh từ số nhiều.

**Checkpoint:**
`T1` = 1 · `T2` = 1 · `T3` = 0.5 (1 ý có bằng chứng loại (a): "There are 900 students") · `T4` = 1 · `T5` = 1 → 4.5/5 = **90%**.
`CC1`–`CC5` = 1 (thân bài 2 đoạn; and, because; it, she) → **100%** — **bài có tiếng Việt vẫn có thể mạch lạc rất tốt**.
`L1` = 0.5 (bí từ ở 4 chỗ) · `L2` = 0.5 (N_lex = 4 — cả bốn cụm tiếng Việt) · `L3` = 1 · `L4` = 1 · `L5` = 0.5 → 3.5/5 = 70% → trần 4 lỗi dùng từ / 57 từ = 7.02/100 (vượt ngưỡng 7) → **60%**.
`R1`–`R5` = 1 → 5/5 → trần 2 lỗi ngữ pháp / 57 từ = 3.5/100 → **80%**. **Không bị trừ thêm vì các từ tiếng Việt** — đúng nguyên tắc tránh double penalty.
Tổng kết (90 + 100 + 60 + 80) / 4 = 82.5 → **80%**.
