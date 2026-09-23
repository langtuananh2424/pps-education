# WRITING RUBRIC — GRADE 9 · IELTS FOUNDATION / PRE-IELTS · v3

## §0. Cấu hình

```text
Input: original task + original student writing
Target: IELTS 4.0–5.0 pathway (A2+ -> B1)
Criteria: TR/TA, CC, LR, GRA
Checkpoint: 1 / 0.5 / 0
Word limit: use the task; if absent -> Task 1/letter 150 words; essay 250 words
Temperature: 0
Model call: 1 call / script
```

Rubric dùng chung cho nhóm khá và cơ bản–trung bình. `1` = strong Grade 9 foundation; `0.5` = functional nhưng hạn chế; không yêu cầu “wide range”, idiom hoặc nhiều loại complex grammar để đạt điểm tốt.

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
| **T1** | **Task coverage** | Đủ tất cả phần chính | Thiếu/ yếu 1 phần | Thiếu ≥2 / hiểu sai chính |
| **T2** | **Purpose / position** | Rõ, trực tiếp và nhất quán khi task yêu cầu | Có nhưng còn lệch/không rõ 1 chỗ | Không rõ / mâu thuẫn |
| **T3** | **Idea development** | Các main ideas đều có support; ít nhất 2 developed ideas | Có 1 developed idea; ý khác còn ngắn | Mainly listing / little support |
| **T4** | **Relevance** | ≥90% câu liên quan | 80–89% | <80% |
| **T5** | **Task information control** | 0 major contradiction/misinterpretation | 1 | ≥2 / ảnh hưởng task |

### COHERENCE & COHESION — CC

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **CC1** | **Overall progression** | Mạch rõ xuyên suốt | 1–2 chỗ đứt mạch | Không có progression rõ |
| **CC2** | **Paragraphing** — đếm đoạn | Mở bài · ≥ 2 đoạn thân · kết bài, mỗi phần một đoạn riêng | 2–3 đoạn (thiếu mở bài, kết bài, hoặc chỉ 1 đoạn thân) | Toàn bài một khối |
| **CC3** | **Linking** | ≥5 correct links, ≥3 types; không mechanical | 3–4 correct links, ≥2 types | ≤2 / overuse/misuse rõ |
| **CC4** | **Reference / substitution** | ≥3 correct cohesive uses | 1–2 | 0 / often unclear |
| **CC5** | **Repetition control** — trích từng ý / cụm lặp lại không cần thiết | 0–1 | 2 | ≥ 3 |

### LEXICAL RESOURCE — LR

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **L1** | **Vocabulary sufficiency** | Generally adequate for all main ideas | Minimally adequate; some simplification/gaps | Inadequate for several main ideas |
| **L2** | **Word choice / collocation** | `N_lex` 0–2 | 3–5 | ≥6 / frequent meaning difficulty |
| **L3** | **Variety theo ngữ cảnh** — `K_topic` = số từ/cụm **hợp chủ đề theo ngữ cảnh đề bài**, dùng đúng, **không có sẵn trong đề**; mỗi mục tính 1 lần, phải trích nguyên văn | `K_topic` ≥ 10 | 5–9 | ≤ 4 |
| **L4** | **Spelling / word formation** | ≤2 lỗi / 50 từ | 3–5 | ≥6 / frequently impedes meaning |
| **L5** | **Paraphrase** — `P_para` = số chỗ **nói lại ý của đề bằng lời khác** (trích cặp *đề → bài*); `N_copy` do máy đo | `P_para` ≥ 2 **và** `N_copy` = 0 | `P_para` = 1; hoặc `P_para` ≥ 2 nhưng `N_copy` > 0; hoặc `P_para` = 0 và `N_copy` = 0 | `P_para` = 0 **và** `N_copy` > 0 |

### GRAMMATICAL RANGE & ACCURACY — GRA

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **R1** | **Sentence completeness** | ≥90% complete clauses | 65–89% | <65% |
| **R2** | **Basic grammar control** | ≥80% basic clauses acceptable | 55–79% | <55% |
| **R3** | **Tense / verb control** | ≥80% correct | 55–79% | <55% |
| **R4** | **Sentence range** | Mix simple + ≥3 extended/complex sentences, ≥2 patterns | 1–2 extended/complex sentences | Limited/repetitive simple patterns |
| **R5** | **Error impact** | 0–1 idea unclear because of grammar | 2 | ≥3 / frequent difficulty for reader |

## §4. Quy đổi

| Total /5 | 5 | 4.5 | 4 | 3.5 | 3 | 2.5 | 2 | 1.5 | 1 | 0.5 | 0 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **%** | 100 | 90 | 80 | 70 | 60 | 50 | 40 | 30 | 20 | 10 | 0 |

`Final % = average(TR/TA, CC, LR, GRA)`, làm tròn xuống bội số 5. Áp gate cap sau khi chấm checkpoint.

**Không tự quy Final % thành official IELTS band.** Đây là training score nội bộ đã hiệu chuẩn cho khối.


### Internal anchor

| % | Interpretation |
|---:|---|
| **80–100** | Strong/above current Grade 9 foundation target |
| **60–75** | Meets/near Grade 9 IELTS pathway target |
| **40–55** | Developing; functional but limited |
| **20–35** | Early foundation / below current target |
| **0–15** | Insufficient functional performance/data |

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
<≤2 câu, ≤50 từ, tiếng Việt: 1 điểm mạnh + 1 ưu tiên cần sửa>
```

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

Ba bài dưới đây là **mốc chuẩn của khối 9**. Cách dùng: đọc bài học sinh → đối chiếu với **dòng Checkpoint** của các neo để quyết định từng checkpoint cho 1 / 0.5 / 0 → cộng → quy đổi §4 → áp trần. **Không có bước điều chỉnh sau khi cộng** — % của tiêu chí chính là phép cộng checkpoint (hệ thống bỏ qua mọi điều chỉnh như vậy).

### Bài mỏng nội dung nhưng vẫn hiểu được — thang chuẩn theo khối

**"Mỏng"** = mọi ý bắt buộc đều có mặt, người đọc hiểu đúng, nhưng **không ý nào được phát triển** bằng lý do, ví dụ hay chi tiết cụ thể.

Với khối này: **Task Response / Achievement = 40% hoặc thấp hơn** (tổng ≤2/5).

Đề cho 250 từ — thừa chỗ để phát triển ý. Ở khối này **phát triển ý là yêu cầu cốt lõi**, nên bài chỉ liệt kê thì `T3` = 0 và kéo cả `T1` xuống 0.5 vì nhiệm vụ chưa thực sự hoàn thành.

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

Lý do hạ cả Task Response: khi quá nửa động từ sai thì, người đọc phải tự dựng lại mốc thời gian của lập luận, nên nhiệm vụ chưa thực sự hoàn thành dù đủ ý.

### Trần cứng cho bài rỗng nội dung — bắt buộc

Rubric đếm 20 checkpoint, nên một bài **không có ý nào được phát triển** chỉ mất đúng 1 checkpoint (T3) và vẫn có thể lên 60%. Đó là lỗ hổng: bài toàn khẩu hiệu chung chung, không một ví dụ, con số hay chi tiết cụ thể nào, vẫn "đạt mục tiêu khối". Chặn bằng trần cứng, áp **trước** khi tính trung bình:

| Tình trạng | Task Response |
|---|---|
| T3 = 0, nhưng bài **vẫn có chi tiết cụ thể** (tên, số, sự việc có thật) | tối đa **40%** — xem thang "bài mỏng" ở trên |
| T3 = 0 **và** không một chi tiết cụ thể nào **và** T2 = 0 (không nêu được lập trường) | tối đa **20%** |
| T2 = 0 nhưng các ý vẫn được phát triển | tối đa **40%** |

Phân biệt hai dòng đầu là điểm mấu chốt: **"mỏng" khác "rỗng"**. Bài mỏng nêu được việc thật nhưng không giải thích — 40%. Bài rỗng chỉ có khẩu hiệu chung chung, không một sự việc nào có thể kiểm chứng — 20%.

### Giá của một đoạn lạc ý — bắt buộc

Đây là chỗ hay bị **double penalty**, trái với §1. Một đoạn nằm ngoài phạm vi đề chỉ được tính ở đúng hai chỗ:

- **T4 (relevance)** — theo tỉ lệ câu liên quan.
- **CC1 (progression)** — vì đoạn đó làm đứt mạch.

**Không** trừ thêm ở T1, T3, T5, CC2, CC3, CC4 hay CC5 cho cùng đoạn đó. Riêng Lexical Resource và Grammatical Range **hoàn toàn không bị ảnh hưởng** bởi việc lạc ý — đoạn lạc đề vẫn là bằng chứng ngôn ngữ hợp lệ.

Trần cứng kèm theo:

| Tỉ lệ nội dung nằm ngoài phạm vi đề | Task Response |
|---|---|
| ≥25% số từ | tối đa **40%** |
| 10–24% số từ | tối đa **60%** |

Đồng thời nhắc lại CC3: từ nối **dùng máy móc hoặc rập khuôn** thì CC3 = 0 dù đếm đủ số lượng và số loại. Chuỗi `On the one hand… On the other hand… In addition… In conclusion…` mà mỗi đoạn không có nội dung riêng chính là dùng máy móc.

**Đề dùng chung cho ba neo:**

> Some people think that students should study only the subjects they like. Do you agree or disagree? Give reasons for your answer and include any relevant examples from your own knowledge or experience.

---

### NEO A0 — 35% · đáy thang thực tế

Neo này định nghĩa **đáy của thang điểm**, phân biệt "rất yếu" với "chưa đủ dữ liệu để chấm" (0% theo cổng G1/G4). Bài dưới đây (161 từ = 64% yêu cầu → G1 băng 50–79%) là tiếng Anh thật, bám đúng đề, nhưng không ý nào được phát triển và câu nào cũng thiếu thành phần. Bài rất yếu nhưng đúng đề thường rơi vào **30–35%**, không xuống 20%.

**Đề:** Some people think that students should study only the subjects they like. Do you agree or disagree?

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{pu1|i}} agree student study subject they like. {{gr2|because like subject good}}. {{gr2|student happy when study subject they like}}. {{gr2|student not happy when study subject not like}}.

{{gr2|first, subject we like easy}}. {{gr2|we study fast}}. {{gr2|we remember long}}. {{gr2|my friend like math}}. {{gr2|he study math every day}}. {{gr2|he good}}. {{gr2|he happy}}. {{gr2|i like english}}. {{gr2|i study english}}. {{gr2|i not like history}}. {{gr2|history difficult}}. {{gr2|history boring}}. {{gr2|i not remember history}}. {{gr2|my mark history low}}.

{{gr2|second, student have many subject now}}. {{gr2|too many}}. {{gr2|student tired}}. {{gr2|student no time rest}}. {{gr2|student study morning and evening}}. {{gr2|student no play}}. {{gr2|this not good for health}}. {{gr2|if student study only subject like, student more happy}}.

{{gr2|some people say all subject important}}. {{gr2|but i not agree}}. {{gr2|many people no use math}}. {{gr2|my uncle singer}}. {{gr2|he no use math}}. {{gr2|he happy}}.

{{wd2|so i think student study only subject they like. that my opinion. student study subject they like is good. school must let student choose. this very important for student.}}

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 40% |
| Coherence & Cohesion | 70% |
| Lexical Resource | 80% |
| Grammatical Range & Accuracy | 20% |
| **Tổng kết** | **35%** |

### 3. Nhận xét

Em giữ được quan điểm nhất quán từ đầu đến cuối và có chia đoạn. Ưu tiên sắp tới: mỗi câu phải có động từ to be, ví dụ "History **is** difficult" thay vì "history difficult".

**Checkpoint:**
`T1`–`T2` = 1 · `T3` = 0 · `T4`–`T5` = 1 → **trần bài mỏng 40%** (động từ sai > 50% → trần 40% — trùng).
`CC1` = 0.5 · `CC2` = 1 (5 đoạn) · `CC3` = 1 (first, second, but, if, so) · `CC4` = 1 (he, this) · `CC5` = 0 (lặp liên tục) → 3.5/5 = **70%**.
`L1` = 0.5 · `L2` = 1 · `L3` = 1 (`K_topic`: math, english, history, difficult, boring, remember, mark, tired, rest, health, singer, uncle) · `L4` = 1 · `L5` = 0.5 → 4/5 = **80%**.
Grammatical Range: động từ sai > 50% → **trần 20%**.
Trung bình (40 + 70 + 80 + 20) / 4 = 52.5 → **trần Tổng kết 35%** vì quá nửa động từ sai. **Đây là 35% chứ không phải 0%: bài đúng đề và là tiếng Anh.**

---

### NEO A — 40%

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

I agree that students should study only the subjects they like. {{gr1|Student}} study {{gr1|subject}} they like{{pu1|, they}} will study {{gr1|more good}}. They feel happy. They {{gr2|not feel}} tired. {{gr1|Student}} {{gr1|study}} {{gr1|subject}} they don't like will feel {{wd1|boring}}. They cannot remember anything. {{gr1|Student}} should study {{gr1|subject}} they like. I think this is {{gr1|very important thing}} for {{gr1|student}} today. Many {{gr1|student}} in my school have too many {{gr1|subject}} and they are very tired. They study all day. They {{gr2|have not time}} {{gr1|for play}}. This is not good for their {{sp1|healthy}}. {{gr1|Student}} study only {{gr1|subject}} they like{{pu1|, they}} will have more time. They {{gr2|will happy}}.

Some people think all {{gr1|subject}} {{gr1|are}} important for {{gr1|student}}. They say {{gr1|student}} {{gr1|need}} to know many {{gr1|thing}}. But {{pu1|i}} think this idea is not right. {{gr1|Student}} can learn other {{gr1|thing}} later. The most important thing is {{gr1|student}} {{gr2|must happy}}. {{gr1|Student}} {{gr2|happy}}{{pu1|, they}} study {{gr1|more good}} and they get {{gr1|good result}}. {{gr1|Student}} {{gr1|like}} {{gr1|subject}} will study hard. {{gr1|Student}} {{gr1|don't like}} {{gr1|subject}} will not study hard. I think {{gr1|student}} should study only {{gr1|subject}} they like. School should let {{gr1|student}} study {{gr1|subject}} they like. This is the best way for {{gr1|student}}.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 40% |
| Coherence & Cohesion | 40% |
| Lexical Resource | 60% |
| Grammatical Range & Accuracy | 30% |
| **Tổng kết** | **40%** |

### 3. Nhận xét

Em giữ quan điểm nhất quán từ đầu đến cuối. Ưu tiên sắp tới: mỗi lý do cần một ví dụ thật (một bạn, một môn học, một điểm số cụ thể), và thêm s vào danh từ số nhiều.

**Checkpoint:**
`T1` = 1 · `T2` = 1 · `T3` = 0 (không một con số, tên riêng hay sự việc nào) · `T4` = 1 · `T5` = 1 → 5/5 → **trần bài mỏng khối 9: 40%**.
`CC1` = 0 (bài đi vòng tròn — cùng một ý "học môn mình thích thì vui, học tốt" lặp lại, không tiến triển) · `CC2` = 0.5 (2 đoạn, không có mở bài / kết bài riêng) · `CC3` = 0.5 (and, But, and — 3 từ nối, 2 loại) · `CC4` = 1 (they, This, this) · `CC5` = 0 (lặp ≥ 3: "should study (only) subject they like" ×4, "more good" ×2, "study hard / not study hard") → 2/5 = **40%**.
`L1` = 0.5 (bí từ: "more good") · `L2` = 1 (N_lex = 1) · `L3` = 0.5 (`K_topic` = 6: happy, tired, school, day, time, hard) · `L4` = 1 · `L5` = 0 (N_copy = 8 — câu mở chép đề; P_para = 0) → 3/5 = **60%**.
190 từ, 8 từ chép đề → `N_net` 182 = 73% → G1 băng 50–79% (Task Response trần 60 — không ảnh hưởng vì đã bị trần mỏng 40).
`R1` = 0.5 (4 câu nối bằng dấu phẩy thiếu từ nối) · `R2` = 0 · `R3` = 0.5 · `R4` = 0 (không câu phức nào đúng) · `R5` = 0.5 → 1.5/5 = **30%** (trần mật độ lỗi cũng là 40 — lấy mức thấp hơn).
Tổng kết (40 + 40 + 60 + 30) / 4 = 42.5 → **40%**.

---

### NEO M — 80% · đủ ý nhưng mỏng, ngôn ngữ sạch

Neo này khép bộ ba ở khối 9, cần đọc cùng lúc cả ba:

| Neo | Đặc điểm | Task Response |
|---|---|---:|
| **NEO E (55%)** | chỉ khẩu hiệu chung chung, không sự việc nào có thật | **20%** |
| **NEO M (dưới đây)** | nêu sự việc thật nhưng không ý nào được triển khai | **40%** |
| **NEO B2 (95%)** | có chi tiết thật (mười túi một ngày, xe rác hai lần một tuần) | **100%** |

Ba bài đều đúng đề. Khác biệt lớn nhất là **mức độ triển khai** — và nó quyết định Task Response.

**Đề:** Plastic waste is a serious problem in many towns and cities. What are the main causes of this problem, and what solutions can you suggest?

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Plastic waste has become a serious problem in many Vietnamese cities, and I believe there are two main causes.

The first cause is cheap packaging. Sellers give customers plastic bags because the bags cost very little. Customers accept them without thinking. This happens in markets and in supermarkets every day.

The second cause is the collection system. Many towns do not collect rubbish often enough. As a result, people throw their rubbish into rivers or burn it in their gardens. This makes the air and the water dirty.

There are some solutions. The government could charge money for plastic bags. Shops could sell cloth bags instead. Schools could also teach students about recycling, and families could separate their rubbish at home.

In conclusion, cheap packaging and weak collection cause most of the problem, and both the government and families need to act.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 40% |
| Coherence & Cohesion | 100% |
| Lexical Resource | 90% |
| Grammatical Range & Accuracy | 100% |
| **Tổng kết** | **80%** |

### 3. Nhận xét

Bài của em có bố cục chặt chẽ và gần như không lỗi ngữ pháp. Ưu tiên sắp tới: mỗi nguyên nhân cần một con số hoặc một chuyện có thật em chứng kiến.

**Checkpoint:**
142 từ = 57% yêu cầu → G1 băng 50–79% (Task Response trần 60).
`T1` = 1 · `T2` = 1 · `T3` = 0 (**không một con số, tên riêng hay trường hợp cụ thể nào** — "Sellers give customers plastic bags", "many towns do not collect rubbish often enough" đều là mệnh đề khái quát) · `T4` = 1 · `T5` = 1 → **trần bài mỏng khối 9: 40%**.
`CC1`–`CC5` = 1 (5 đoạn; because, As a result, also, and, In conclusion; them, This, it) → **100%**.
`L1`–`L4` = 1 (`K_topic` ≥ 10) · `L5` = 0.5 (N_copy > 0: "a serious problem in many"; P_para = 1) → 4.5/5 = **90%**, 0 lỗi.
`R1`–`R5` = 1 → **100%**, 0 lỗi.
Tổng kết (40 + 100 + 90 + 100) / 4 = 82.5 → **80%**. **Ba hàng cao không kéo được Task Response lên.**

So với NEO B2: bài đó viết "mỗi lần mua hàng được gói hai ba túi vì một túi giá chưa tới năm mươi đồng" — **đó là chi tiết**. Bài này chỉ viết "the bags cost very little" — **đó là mỏng**.

---

### NEO B2 — 95% · đủ ý, có chi tiết thật, từ vựng đơn giản nhưng đúng

Neo này cho thấy **chuẩn của khối 9 là IELTS 4.0–5.0**: một bài đủ ý, mỗi nguyên nhân có chi tiết thật từ đời sống, từ vựng đơn giản nhưng **dùng đúng và hợp chủ đề**, câu phức vừa đủ — là bài **đạt chuẩn khối**, nên lên 90–100%.

**Nguyên tắc:** độ "cao cấp" của từ **không** phải tiêu chí. `L3` đếm số từ / cụm **hợp chủ đề và dùng đúng** (`K_topic`), không đếm độ hiếm. Lặp từ khoá của đề (`rubbish`, `plastic bags`) không bị trừ ở Lexical Resource; lặp **ý** mới bị trừ ở `CC5`.

**Đề:**

> Plastic waste is a serious problem in many towns and cities.
> What are the main causes of this problem, and what solutions can you suggest?

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Nowadays, plastic waste is a big problem in many cities around the world. In my opinion, there are two main causes and the government should solve it quickly.

The first cause is that plastic is very cheap. Factories produce millions of plastic bags and bottles every day because they do not cost much money. When people buy food at the market, the seller always {{gr1|give}} them a plastic bag. {{ok|Many families use ten bags in one day}} and then they throw them away. This {{gr1|make}} a lot of rubbish.

The second cause is that many cities do not have a good system to collect rubbish. {{ok|In my town, the rubbish truck only}} {{gr1|come}} {{ok|two times in one week, so people throw their rubbish in the river or burn it}}. The river near my house is very dirty now and there are many plastic bottles on the water.

I think the government should do something. They can put a tax on plastic bags so people will use cloth bags. They can also build more recycling factories and teach students about the environment in school.

In conclusion, plastic waste {{gr1|come}} from cheap production and bad rubbish collection. If we do not act now, our cities will become dirtier and dirtier.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 100% |
| Coherence & Cohesion | 100% |
| Lexical Resource | 100% |
| Grammatical Range & Accuracy | 80% |
| **Tổng kết** | **95%** |

### 3. Nhận xét

Em nêu đủ hai nguyên nhân và có ví dụ thật từ nơi mình sống. Ưu tiên sắp tới: thêm s vào động từ khi chủ ngữ số ít, và thay bớt từ lặp bằng từ đồng nghĩa.

**Checkpoint:**
`T1` = 1 · `T2` = 1 · `T3` = 1 (bằng chứng loại (a): "ten bags in one day", "two times in one week"; loại (b)/(c): "The river near my house") · `T4` = 1 · `T5` = 1 → **100%**.
`CC1`–`CC5` = 1 (5 đoạn; because, When, so, also, If; them, This, it, They — hai đoạn nguyên nhân mang nội dung khác nhau nên **không** máy móc) → **100%**.
`L1`–`L5` = 1 (`K_topic` ≥ 10; N_copy = 0; P_para ≥ 2) → **100%**, 0 lỗi chính tả / dùng từ.
`R1`–`R5` = 1 (because, when, so, if) → 5/5 → trần 4 lỗi ngữ pháp / 208 từ (từ 3 lỗi → tối đa 80) → **80%**.
Tổng kết (100 + 100 + 100 + 80) / 4 = **95%**.

---

### NEO E — 55% · trôi chảy, đúng ngữ pháp, nhưng rỗng nội dung

Neo này là **phép thử ngược của NEO C+**: bài dưới đây không có một lỗi ngữ pháp nào, từ nối đầy đủ, bố cục năm đoạn chuẩn — nhưng không nói được điều gì cụ thể. Không ví dụ, không con số, không lập trường. Nếu bài này đạt 60% thì rubric đang thưởng cho hình thức thay vì nội dung.

**Đề:**

> Some people think that students should study only the subjects they like. Do you agree or disagree?

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

In today's modern society, the question of whether students should study only the subjects they like is {{wd1|a topic that many people are interested in}}. {{wd2|In my opinion, this issue deserves careful consideration.}}

{{wd1|On the one hand, there are many advantages that we cannot deny.}} When students study the subjects they like, they feel more comfortable and they can develop themselves better. {{wd2|This brings many benefits not only to the students but also to society as a whole.}} {{wd1|Therefore, we should pay attention to this matter.}}

{{wd1|On the other hand, there are also some disadvantages.}} {{wd2|Some students may face difficulties and challenges if they choose in the wrong way.}} {{wd2|However, these problems can be solved if everyone works together and tries their best.}} Schools, teachers and families all have an important part to play in this process.

In addition, the role of guidance should not be forgotten. Good advice can support students in many ways and make their choices easier. At the same time, too much pressure can also create problems if it is not handled carefully.

In conclusion, although there are both advantages and disadvantages, {{wd2|I believe that with the effort of all people, we can achieve a better result in the future. This is a very important issue and everyone should be aware of it.}}

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 20% |
| Coherence & Cohesion | 50% |
| Lexical Resource | 60% |
| Grammatical Range & Accuracy | 100% |
| **Tổng kết** | **55%** |

### 3. Nhận xét

Em kiểm soát ngữ pháp rất tốt, cả bài gần như không lỗi. Ưu tiên sắp tới: mỗi đoạn phải có một ví dụ hoặc chi tiết thật, và phải nêu rõ em đồng ý hay không đồng ý.

**Checkpoint:**
`T2` = 0 (đề hỏi đồng ý hay không, bài chỉ nói "deserves careful consideration") · `T3` = 0 (không một ví dụ hay chi tiết) → **trần bài rỗng: 20%** (không ý nào phát triển **và** không chi tiết cụ thể **và** không lập trường).
`CC1` = 0.5 · `CC2` = 1 · `CC3` = 0 (từ nối đủ loại nhưng **máy móc**: đoạn "On the one hand…" và "On the other hand…" không mang nội dung khác nhau) · `CC4` = 1 · `CC5` = 0 (lặp ý rỗng ≥ 3) → 2.5/5 = **50%**.
`L1` = 1 · `L2` = 0 (N_lex = 9 — toàn cụm rỗng) · `L3` = 1 · `L4` = 1 · `L5` = 0 (N_copy > 0, P_para = 0) → 3/5 = **60%**.
`R1`–`R5` = 1 → **100%**, 0 lỗi.
Tổng kết (20 + 50 + 60 + 100) / 4 = 57.5 → **55%**. **Bài không sai không đồng nghĩa với bài tốt.**

---

### NEO F — 80% · ngôn ngữ hoàn hảo nhưng một đoạn lạc ý

Neo này dạy cách cô lập thiệt hại của việc lạc ý: kéo Task Response xuống mạnh, chạm nhẹ Coherence, và **không đụng gì đến hai tiêu chí ngôn ngữ**.

**Đề:**

> Many young people today do not get enough sleep.
> What are the causes of this problem, and what can be done to solve it?

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{ok|Sleep deprivation among teenagers has become increasingly common}}, and I believe the causes lie mainly in technology and school pressure.

The first cause is the use of phones late at night. {{ok|Many students scroll through social media until midnight because the videos are designed to keep them watching.}} My younger brother, for example, often falls asleep after one in the morning and cannot wake up for his first lesson.

Another important point is that our school canteen does not sell healthy food. Most of the dishes are fried and contain a lot of oil, and the vegetables are usually cold. Students also complain that the queue is too long during the short break, so many of them skip lunch and buy snacks from the shop outside the gate instead. The school should hire a new cook and open a second counter.

{{ok|To solve the sleep problem, schools could start lessons half an hour later, which several countries have already tried successfully.}} Parents should also agree on a fixed time to collect phones at night, {{ok|so that the temptation simply disappears}}.

In conclusion, phones and early timetables are the main reasons why teenagers sleep too little, and small changes at home and at school could improve the situation.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 40% |
| Coherence & Cohesion | 90% |
| Lexical Resource | 100% |
| Grammatical Range & Accuracy | 100% |
| **Tổng kết** | **80%** |

### 3. Nhận xét

Tiếng Anh của em rất tự nhiên, không một lỗi nào và có nhiều ví dụ cụ thể. Ưu tiên sắp tới: đoạn ba nói về căng tin, không liên quan đến nguyên nhân thiếu ngủ — cần bỏ hoặc thay bằng một nguyên nhân thật.

**Checkpoint:**
Đoạn lạc ý (căng tin) ≈ 34% số từ → **trần lạc ý: Task Response 40%**.
`CC1` = 0.5 (đứt mạch ở đoạn căng tin) · `CC2`–`CC5` = 1 → 4.5/5 = **90%**.
`L1`–`L5` = 1 → **100%** — **lạc ý không phải lỗi ngôn ngữ**, đoạn lạc đề vẫn là bằng chứng hợp lệ cho vốn từ. Không đánh `wd` lên đoạn lạc ý.
`R1`–`R5` = 1 → **100%**, 0 lỗi.
Tổng kết (40 + 90 + 100 + 100) / 4 = 82.5 → **80%**. Đừng để một lỗi nội dung kéo sập cả bốn hàng.


---

### NEO C+ — 100% · vượt chuẩn khối 9

Neo này định nghĩa **100%**: mọi checkpoint đều đạt — lập trường sắc, hai ví dụ **đối chứng** có thật, đoạn nào cũng có chức năng riêng, câu phức đa dạng mà không lỗi. 100% ở khối 9 nghĩa là **vượt chuẩn khối**, không phải "bài hoàn hảo tuyệt đối".

**Đề:**

> Some people think that students should study only the subjects they like. Do you agree or disagree?

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{ok|While some argue that teenagers should be allowed to concentrate solely on the subjects they enjoy, I firmly disagree with this view}}, mainly because fifteen-year-olds cannot yet predict what their adult lives will demand.

Supporters of free choice claim that {{ok|interest drives effort}}. There is some truth in this: {{ok|a student who enjoys biology will read beyond the syllabus and remember far more than one who is merely counting the minutes}}. My own cousin spent two years reading about marine life on her own and eventually won a provincial science award, something no amount of compulsory study could have produced.

{{ok|However, this argument overlooks how narrow a teenager's judgement can be.}} At fifteen, most students choose subjects based on which teacher they like, not on what their careers will require. A classmate of mine dropped mathematics as soon as it became optional and now struggles to read the financial reports his part-time job involves. {{ok|Skills such as basic statistics or a second language rarely feel urgent at school, yet they quietly decide how many doors stay open later.}}

A more sensible approach is a balanced one. Schools could keep a small compulsory core, for example mathematics, the national language and one foreign language, {{ok|while allowing genuine freedom over the remaining subjects}}.

In conclusion, {{ok|although interest is undoubtedly a powerful motivator}}, complete freedom of choice at fifteen would harm more students than it helps.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 100% |
| Coherence & Cohesion | 100% |
| Lexical Resource | 100% |
| Grammatical Range & Accuracy | 100% |
| **Tổng kết** | **100%** |

### 3. Nhận xét

Em lập luận hai chiều sắc bén với hai ví dụ đối chứng rất thuyết phục và ngữ pháp gần như không lỗi. Ưu tiên sắp tới: thử dùng một vài cụm từ học thuật chính xác hơn để nâng vốn từ.

**Checkpoint:**
`T1`–`T5` = 1 (lập trường rõ; bằng chứng loại (c): người chị họ đạt giải khoa học cấp tỉnh, người bạn cùng lớp bỏ môn toán) → **100%**.
`CC1`–`CC5` = 1 → **100%**.
`L1`–`L5` = 1 (N_copy = 0; P_para ≥ 2: "concentrate solely on the subjects they enjoy", "complete freedom of choice") → **100%**, 0 lỗi.
`R1`–`R5` = 1 → **100%**, 0 lỗi.
Tổng kết **100%**.

---

### NEO D — 80% · hồ sơ lệch (ngôn ngữ tham vọng nhưng dùng sai)

Neo này chặn lỗi chấm nguy hiểm nhất ở khối 9: **bài đọc trôi chảy, từ vựng nghe kêu, nên được chấm cao hơn thực tế**.

**Quy tắc chống bị ngôn ngữ đánh lừa — bắt buộc:**

Một cụm từ chỉ được tô `{{ok|...}}` khi nó **vừa đúng vừa tự nhiên**. Cụm "nghe có vẻ cao cấp" nhưng rơi vào một trong các trường hợp sau là **lỗi `wd`, không phải điểm mạnh**:

- sai collocation (`part of driving` thay vì *part in driving*; `go out of hand` thay vì *out of hand*)
- từ ghép tự chế không tồn tại (`budget-efficient`, `evermore worrying`)
- thành ngữ sai register cho bài luận học thuật (`the elephant in the room`)
- cụm dài nhưng người đọc không xác định được nghĩa (`in the same task it is pushing forward`)

Độ dài câu và độ hiếm của từ **không** phải bằng chứng cho Lexical Resource. Bằng chứng là **dùng đúng**. Khi đếm được từ 6 lỗi collocation/chọn từ trở lên thì L2 = 0, và Lexical Resource không thể vượt 60% dù bài đọc rất trôi chảy.

**Bắt buộc: rà collocation thành một lượt riêng.** Sau khi chấm nội dung, đọc lại bài **chỉ để soi cụm từ**, kiểm tra từng cụm "nghe kêu" xem có thật sự tồn tại trong tiếng Anh chuẩn không. Không làm lượt này thì sẽ bỏ sót, vì bài trôi chảy khiến lỗi collocation trông như đúng.

Bẫy thường gặp ở học sinh Việt Nam viết "nâng cao" — mỗi cụm dưới đây là **lỗi `wd`**, không phải điểm mạnh:

| Học sinh hay viết | Vấn đề |
|---|---|
| a hotly debatable matter | phải là *hotly debated* |
| I am in accordance with | *in accordance with* dùng cho quy định, không dùng cho quan điểm |
| a double-edged knife | thành ngữ đúng là *double-edged sword* |
| at their fingertip | phải là *at their fingertips* |
| has ameliorated her pronunciation | *ameliorate* không đi với kỹ năng người học |
| the demerits are equally weighty | *demerit* và *weighty* sai register cho văn nghị luận phổ thông |
| a culprit of sleep deprivation | phải là *a cause of* / *the culprit behind* |
| fall victim of | phải là *fall victim to* |
| a ban for children | phải là *a ban on* |
| thereby deteriorating their performance | *deteriorate* ở đây cần *worsening* / *harming* |
| play a crucial part of | phải là *play a crucial part in* |
| make the situation go out of hand | phải là *get out of hand* |
| budget-efficient, evermore worrying | từ ghép tự chế, không tồn tại |
| the elephant in the room | sai register cho bài luận học thuật |
| in the same task it is pushing forward | dài nhưng không xác định được nghĩa |

Danh sách này là **ví dụ mẫu, không phải danh sách đóng**. Gặp cụm lạ khác cùng kiểu thì xử lý y như vậy.

**Đề:**

> Plastic waste has been a problem concerning many cities and town around the world?
> What are the causes of this? State your opinion

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Trash from human activities, mainly plastic waste, has been an {{wd1|evermore worrying}} issue in today's world. However, this is not the first time the conversation of trash and waste made out of plastic became {{wd1|the elephant in the room}}.

First and foremost, plastic {{gr1|had been}} a {{wd1|budget-efficient}} while also being a durable material option for daily life gadgets. {{ok|Nearly a century on since its creation, almost everything, from the small things such as water bottles and bowls, to things people in the past thought wouldn't have been possible to make, like AI machine farms and chips, are made from plastic.}} Increasing manufacturing creates more {{sp1|wastes}}, {{ok|which has gotten out of control with the emergence of constant industrialization waves, especially in developing countries}}.

Not only that, policies on city development {{gr1|has}} somewhat impacted the pollution propelled by plastic {{sp1|wastes}}. Daily {{sp1|traffics}}, human activities, manufacturing plants,{{pu1|...}} all are factors driving plastic consumption and expulsion upwards{{pu2|,}} In the best case scenario, a well-designed city could {{gr2|have to mitigate}} the amount of plastic waste being poured out into the sea and other neighborhoods with {{ok|state-of-the-art incineration plants}}. But on the other hand, under-developed states can easily {{wd1|make the situation go out of hand}}, as improper policies and unrealistic ambition {{gr1|comes}} into play.

All in all, plastic has played a crucial {{wd1|part of driving}} civil development, while also being infamous for its negative impact {{wd2|in the same task it is pushing forward}}. Whether to be kept in control or not{{pu1|,}} lies in people's {{sp1|hand}}.

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Task Response / Achievement | 80% |
| Coherence & Cohesion | 100% |
| Lexical Resource | 70% |
| Grammatical Range & Accuracy | 80% |
| **Tổng kết** | **80%** |

### 3. Nhận xét

Bài triển khai được hai nguyên nhân rõ ràng với câu phức đa dạng, nhưng quan điểm cá nhân còn mờ nhạt ở cuối bài. Ưu tiên sắp tới: chia động từ theo chủ ngữ và chọn collocation chính xác thay vì từ nghe kêu.

**Checkpoint:**
`T1` = 0.5 (phần "State your opinion" chỉ là một câu trung lập) · `T2` = 0.5 · `T3` = 1 (hai nguyên nhân đều được triển khai) · `T4` = 1 · `T5` = 1 → 4/5 = **80%**.
`CC1`–`CC5` = 1 (4 đoạn; Not only that, But on the other hand, All in all, while) → **100%**.
`L1` = 1 · `L2` = 0 (N_lex = 6: evermore worrying, the elephant in the room, budget-efficient, make the situation go out of hand, part of driving, in the same task it is pushing forward) · `L3` = 1 · `L4` = 1 · `L5` = 0.5 → 3.5/5 = **70%**.
`R1`–`R5` = 1 → 5/5 → trần 7 lỗi ngữ pháp + dấu câu / 249 từ (từ 3 lỗi → tối đa 80) → **80%**.
Tổng kết (80 + 100 + 70 + 80) / 4 = 82.5 → **80%**. **Bài trôi chảy không tự động là bài điểm cao: `L2` = 0 vì đếm đủ 6 lỗi collocation.**

