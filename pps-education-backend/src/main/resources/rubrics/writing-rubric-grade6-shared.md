# WRITING RUBRIC — GRADE 6 · CAMBRIDGE A2 KEY FOR SCHOOLS · v3

## §0. Cấu hình

```text
Input: original task + original student writing
Target: Cambridge A2 Key for Schools / CEFR A2
Criteria: Content (C), Organisation (O), Language (L)
Checkpoint: 1 / 0.5 / 0
Word limit: use the task; if absent -> email ≥25 words, story ≥35 words
Temperature: 0
Model call: 1 call / script
```

Rubric dùng chung cho học sinh khá và trung bình. `1` phản ánh **A2 vững**; `0.5` phản ánh **A2 đang hình thành/A1+**; không yêu cầu complex grammar để đạt `1`.

## §1. Quy ước chấm

```text
N_total = tổng số từ học sinh viết.
N_copy = số từ thuộc chuỗi trùng ≥5 từ liên tiếp với đề/nguồn cho sẵn.
N_net = N_total - N_copy.
N_sent = tổng số câu.
N_complete = số câu/mệnh đề có chủ ngữ + động từ chia + ý hoàn chỉnh.
N_sp = lỗi chính tả/word formation.
N_pu = lỗi dấu câu/viết hoa (mỗi chỗ thiếu dấu chấm, mỗi chữ đầu câu
       hoặc tên riêng viết thường đều tính là một lỗi).
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
| **C1** | **Required content** | Đủ tất cả ý/picture points | Thiếu 1 | Thiếu ≥2 / hiểu sai chính |
| **C2** | **Relevance** | ≥90% câu liên quan | 70–89% | <70% |
| **C3** | **Simple development** | ≥2 ý có detail/reason | 1 ý có detail | Không ý nào có detail / chỉ liệt kê |
| **C4** | **Information clarity** | 0 điểm thông tin phải đoán | 1 điểm phải đoán | ≥2 điểm phải đoán |
| **C5** | **Target reader informed** | Người đọc hiểu đủ thông tin cần thiết | Hiểu phần lớn | Chỉ hiểu một phần nhỏ |

**Chuẩn Content ở khối 6 — đọc trước khi chấm C3, C4, C5:**

- Bài **đủ ý, người đọc hiểu đúng, nhưng không ý nào có detail** → Content tổng thể là **60%** (tổng 3.0/5). Cách chấm cụ thể: `C1` = 1 (đủ ý), `C2` = 1 (đúng đề), `C3` = 0 (không ý nào có detail), `C4` = 0.5, `C5` = 0.5 (người đọc nắm được nội dung chính nhưng bức tranh còn sơ sài).
- Vì sao khối 6 được 60% trong khi khối 7 chỉ 50% cho cùng hiện tượng: đề chỉ cho **25–35 từ**, chỗ để phát triển ý ít hơn hẳn. Nhưng đây là **nới một bậc, không phải miễn trừ** — `C3` vẫn = 0.
- `C4` và `C5` hỏi **người đọc có hiểu không**, không hỏi câu có đúng ngữ pháp không. Câu thiếu động từ to be hay thiếu dấu chấm mà người đọc vẫn hiểu đúng sự việc thì `C4` và `C5` **vẫn được điểm** — lỗi đó đã bị tính trọn ở Language rồi.
- Chỉ hạ `C4`/`C5` khi người đọc **thực sự không xác định được** học sinh muốn nói gì.

### ORGANISATION — O

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **O1** | **Basic linking** — liệt kê từng từ nối dùng đúng (and, but, because, so, then, when…) | ≥ 3 lần đúng, ≥ 2 loại | 1–2 lần đúng | 0 / phần lớn sai |
| **O2** | **Logical order** | Mạch rõ toàn bài | 1 chỗ nhảy ý | Khó theo dõi |
| **O3** | **Sentence boundaries** — câu có ranh giới rõ = **viết hoa chữ đầu câu và có dấu kết câu**; câu nối bằng dấu phẩy (comma splice) không tính | ≥ 80% câu | 50–79% | < 50% |
| **O4** | **Task layout / genre** — đếm thành phần, mỗi phần phải có mặt **và** đúng hình thức (viết hoa). Email: lời chào + tên · câu mở / câu đầu tiên đúng chủ đề · câu chào cuối · ký tên. Story: mở đầu có thời gian hoặc nhân vật · diễn biến · kết thúc | Đủ, đúng hình thức | Thiếu / sai 1–2 | Thiếu / sai ≥ 3 |
| **O5** | **Simple cohesion** — liệt kê từng đại từ / mốc thời gian nối ý với câu trước (it, they, she, then, after that…) | ≥ 2 | 1 | 0 / gây rối |

### LANGUAGE — L

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **L1** | **Vocabulary sufficiency** | Đủ từ để diễn đạt mọi ý bắt buộc | Thiếu từ ở 1–2 chỗ nhưng vẫn hiểu | Thiếu từ làm ≥2 ý không truyền đạt được |
| **L2** | **Word choice** | `N_lex` 0–1 | 2–3 | ≥4 / repeated meaning loss |
| **L3** | **Spelling / word formation / dấu câu** | `N_sp + N_pu` ≤3 lỗi / 50 từ | 4–6 | ≥7 / thường cản meaning |
| **L4** | **Simple grammar control** | ≥75% complete/basic clauses acceptable | 50–74% | <50% |
| **L5** | **Sentence range** | Complete simple sentences + ≥1 accurate joined/extended sentence | Mostly complete simple sentences | Fragments / isolated phrases dominate |

**A2 principle:** everyday vocabulary + simple grammar được dùng tương đối phù hợp là trọng tâm; lỗi vẫn được chấp nhận nếu meaning còn xác định được.

## §4. Quy đổi

| Total /5 | 5 | 4.5 | 4 | 3.5 | 3 | 2.5 | 2 | 1.5 | 1 | 0.5 | 0 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **%** | 100 | 90 | 80 | 70 | 60 | 50 | 40 | 30 | 20 | 10 | 0 |

`Final % = average(C, O, L)`, làm tròn xuống bội số 5. Áp gate cap sau khi chấm checkpoint.


### Internal anchor

| % | Interpretation |
|---:|---|
| **80–100** | Secure/strong A2 |
| **60–75** | Working at A2 |
| **40–55** | Developing toward A2 |
| **0–35** | Below current A2 target / insufficient evidence |

## §5. Output bắt buộc

```markdown
### 1. Bài viết đã đánh dấu
<giữ nguyên bài gốc; chỉ chèn token màu, không viết phần sửa>

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | |
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

Chỉ in khi lượt giao bài **có gắn key grammar** (mã tra trong `KeyGrammar_Grade6.md`). Không gắn thì bỏ hẳn phần này.

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

Ba bài dưới đây là **mốc chuẩn của khối 6**. Cách dùng: đọc bài học sinh → đối chiếu với **dòng Checkpoint** của các neo để quyết định từng checkpoint cho 1 / 0.5 / 0 → cộng → quy đổi §4 → áp trần. **Không có bước điều chỉnh sau khi cộng** — % của tiêu chí chính là phép cộng checkpoint (hệ thống bỏ qua mọi điều chỉnh như vậy).

### Bài mỏng nội dung nhưng vẫn hiểu được — thang chuẩn theo khối

**"Mỏng"** = mọi ý bắt buộc đều có mặt, người đọc hiểu đúng, nhưng **không ý nào được phát triển** bằng lý do, ví dụ hay chi tiết cụ thể.

Với khối này: **Content = 70%** (tổng 3.5/5).

Đề chỉ cho 25–35 từ — không đủ chỗ để phát triển ý. Ở khối này, nêu đủ ý một cách rõ ràng **đã là đạt yêu cầu**, nên `C3` = 0.5 chứ không phải 0, và `C4`, `C5` vẫn giữ nguyên nếu người đọc hiểu đúng.

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

### Trần 100% cho tiêu chí ngôn ngữ — bắt buộc

Các ngưỡng % ở §3 đo **"đạt mục tiêu khối"**, không đo **"không sai"**. Một bài có 10/11 động từ đúng
vẫn vượt mọi ngưỡng và lên 100% — nhưng 100% phải có nghĩa là **không có lỗi**. Áp trần sau khi cộng điểm:

| Số lỗi ngữ pháp + chính tả + dùng từ + dấu câu | Language |
|---:|---|
| 0 | được tới **100%** |
| 1–2 | tối đa **90%** |
| 3–4 | tối đa **80%** |
| ≥5 | theo checkpoint như bình thường |

Số lỗi phải **bằng đúng** số token lỗi tương ứng đánh dấu ở mục 1.

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

Ba tiêu chí **không phải bằng nhau**. Một bài có thể đủ ý và mạch rõ nhưng hỏng ngữ pháp hệ thống. Đừng kéo các hàng về gần nhau cho cân đối.

Trần cứng theo mức độ hỏng động từ, áp **trước** khi tính trung bình:

| Tỉ lệ động từ chính sai thì/dạng | Language | Content |
|---|---|---|
| > 50% | tối đa **20%** | tối đa **40%** — *chỉ khi* `Chỗ người đọc phải đoán nội dung` ≥ 2 |
| 34–50% | tối đa **40%** | tối đa **60%** — *chỉ khi* `Chỗ người đọc phải đoán nội dung` ≥ 2 |

**Cột Language áp vô điều kiện. Cột Content chỉ áp khi mục 0 trích được ≥ 2 chỗ người đọc phải đoán nội dung.**
Lý do: ở khối 6 bài chỉ 25–35 từ và mốc thời gian thường đã nằm sẵn trong bài ("last weekend", "last saturday"), nên động từ sai thì **không tự động** làm người đọc mất thông tin. Khi người đọc vẫn dựng lại được trọn sự việc — như **NEO A1** — thì lỗi động từ đã được tính đủ ở Language, không trừ thêm ở Content. Khi thật sự phải đoán ≥ 2 chỗ, trần Content mới áp.

### Quy tắc chống bị ngôn ngữ đánh lừa — bắt buộc

Một cụm chỉ được tô `{{ok|...}}` khi nó **vừa đúng vừa tự nhiên**. Cụm "nghe có vẻ cao cấp" nhưng sai collocation, là từ ghép tự chế hoặc sai register thì là **lỗi `wd`, không phải điểm mạnh**.

Độ dài câu và độ hiếm của từ **không** phải bằng chứng cho Language — bằng chứng là **dùng đúng**.

**Đề dùng chung cho ba neo:**

> You want to invite your friend Linh to your birthday party. Write an email to Linh. Say: when the party is, where it is, what you will do at the party. Write 25 words or more.

---

### NEO A — 40%

**Bài gốc (24 từ):**

> hi linh
> my birthday party is in my house. i very happy. you come pls. my house near scool. it is big. see you

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{pu2|hi linh}}

{{pu1|my}} birthday party is in my house. {{pu1|i}} {{gr2|very happy}}. {{pu1|you}} come {{wd1|pls}}. {{pu1|my}} house {{gr2|near}} {{sp1|scool}}. {{pu1|it}} is big. {{pu1|see}} you

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 50% |
| Organisation | 40% |
| Language | 40% |
| **Tổng kết** | **40%** |

### 3. Nhận xét

Em đã mời bạn và nói được nơi tổ chức tiệc. Ưu tiên sắp tới: bổ sung thời gian và hoạt động ở bữa tiệc, và viết hoa đầu câu.

**Checkpoint:**
`C1` = 0 (thiếu 2 ý: khi nào, làm gì) · `C2` = 1 · `C3` = 0 · `C4` = 1 · `C5` = 0.5 → 2.5/5 = **50%** (thiếu ý nên không phải bài "mỏng" — trần 60% không áp).
`O1` = 0 (không từ nối) · `O2` = 1 · `O3` = 0 (0/6 câu viết hoa đầu câu) · `O4` = 0.5 (lời chào viết thường, không ký tên) · `O5` = 0.5 (1: "it") → 2/5 = **40%**.
`L1` = 1 · `L2` = 1 · `L3` = 0 (`N_sp + N_pu` = 8 / 24 từ) · `L4` = 0.5 (3/5 mệnh đề đúng) · `L5` = 0 (chỉ câu đơn / cụm rời) → 2.5/5 = 50% → động từ sai 2/5 = 40% → **trần 40%**.
Tổng kết (50 + 40 + 40) / 3 = 43.3 → **40%**.

---

### NEO A0 — 20% · đáy thang

Neo này định nghĩa **đáy của thang điểm**, phân biệt "rất yếu" với "chưa đủ dữ liệu để chấm" (0% theo cổng G1/G4). Bài có tiếng Anh thật, nhận ra được ý định, nhưng thiếu hai ý bắt buộc, người đọc phải đoán nhiều chỗ, và gần như không câu nào hoàn chỉnh.

**Bài gốc (22 từ — 88% yêu cầu, không kích hoạt G1):**

> hi nga i want you com my birtday. we play and we eat. i very happy. you com pls you com. bye nga

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{pu1|hi}} nga {{pu1|i}} want you {{sp2|com}} my {{sp1|birtday}}. {{gr1|we play}} and {{gr1|we eat}}. {{pu1|i}} {{gr2|very happy}}. {{pu1|you}} {{sp2|com}} {{wd1|pls}} you {{sp2|com}}. bye nga

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 20% |
| Organisation | 30% |
| Language | 20% |
| **Tổng kết** | **20%** |

### 3. Nhận xét

Em đã viết được lời mời bạn đến dự tiệc. Ưu tiên sắp tới: nói rõ tiệc vào lúc nào và ở đâu, và mỗi câu phải có đủ chủ ngữ và động từ.

**Checkpoint:**
`C1` = 0 (thiếu 2 ý: khi nào, ở đâu) · `C2` = 1 · `C3` = 0 · `C4` = 0 (≥ 2 chỗ phải đoán: "i want you com my birtday", "you com pls you com") · `C5` = 0 → 1/5 = **20%**.
`O1` = 0.5 (1 "and") · `O2` = 0.5 · `O3` = 0 · `O4` = 0.5 · `O5` = 0 → 1.5/5 = **30%**.
Language: động từ sai > 50% → **trần 20%**.
Tổng kết (20 + 30 + 20) / 3 = 23.3 → **20%**. **Đây là 20%, không phải 0%: bài vẫn là tiếng Anh, vẫn đủ dài, vẫn nhận ra được ý định.**

---

### NEO A1 — 45% · đủ tình tiết, hiểu được, nhưng hỏng ngôn ngữ

Neo này **tách Content khỏi Language**, và là cặp đối chứng bắt buộc phải đọc cùng NEO A.

So sánh với NEO A (40%): cả hai bài đều viết thường, đều cụt câu, đều thiếu động từ. Nhưng **NEO A thiếu hẳn một ý bắt buộc** (không nói khi nào tổ chức tiệc), còn bài dưới đây **có đủ mọi tình tiết** và người đọc dựng lại được trọn vẹn câu chuyện.

Vì vậy Content của hai bài **phải khác nhau**: NEO A được 40%, bài này được **60%**. Đừng nhìn vẻ ngoài lộn xộn rồi cho cùng một mức.

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{pu2|last saturday mai}} {{gr2|find}} {{gr1|a old box}} in the garden {{pu2|the box}} {{gr2|have}} many {{gr1|photo}} inside {{pu2|mai}} {{gr2|very happy}} {{pu2|she show}} the box to her mother {{pu2|they put}} {{gr1|photo}} on wall {{pu2|it nice}}

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 60% |
| Organisation | 60% |
| Language | 20% |
| **Tổng kết** | **45%** |

### 3. Nhận xét

Câu chuyện của em có đủ tình tiết từ lúc tìm thấy hộp đến lúc treo ảnh lên tường. Ưu tiên sắp tới: chấm câu và viết hoa, rồi chia động từ ở thì quá khứ.

**Vì sao Content là 60% chứ không phải 40% như NEO A:** câu chuyện có đủ bốn tình tiết nối tiếp nhau (tìm thấy hộp → mở ra thấy ảnh → khoe mẹ → treo ảnh lên tường), người đọc hiểu đúng toàn bộ, **không thiếu ý nào** — khác hẳn NEO A vốn thiếu hẳn ý "khi nào". Nên `C1` = 1 và `C2` = 1. Nhưng `C3` = 0 vì không tình tiết nào có chi tiết mở rộng, `C4` = 0.5 và `C5` = 0.5 vì bức tranh còn sơ sài. Tổng 3.0/5 → **60%**.

Toàn bộ lỗi ngôn ngữ — thiếu dấu chấm, viết thường, thiếu động từ to be, sai thì — **đã bị tính trọn ở Language (20%)** và ở `O3` của Organisation. **Không được tính thêm một lần nữa ở Content.**

**Checkpoint:**
`C1` = 1 · `C2` = 1 · `C3` = 0 · `C4` = 0.5 · `C5` = 0.5 → 3/5 = **60%** (động từ sai > 50% nhưng chỉ 1 chỗ phải đoán → Content **không** bị trần ở khối 6).
`O1` = 0 (không từ nối) · `O2` = 1 · `O3` = 0 (không câu nào có ranh giới) · `O4` = 1 (mở đầu có thời gian "last saturday", diễn biến, kết thúc) · `O5` = 1 (the box, she, they, it) → 3/5 = **60%**.
Language: động từ sai > 50% → **trần 20%**.
Tổng kết (60 + 60 + 20) / 3 = 46.7 → **45%**.

---

### NEO B — 60%

**Bài gốc (30 từ):**

> Hi Linh,
> My birthday party is on Saturday at 5 o'clock. it is in my house. We will eat cake. We will play game in the garden. please come
> Nam

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Hi Linh,

{{ok|My birthday party is on Saturday at 5 o'clock.}} {{pu1|it}} is {{wd1|in}} my house. We will eat cake. We will play {{gr1|game}} in the garden. {{pu1|please come}}

Nam

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 60% |
| Organisation | 50% |
| Language | 80% |
| **Tổng kết** | **60%** |

### 3. Nhận xét

Em nêu đủ ba ý bắt buộc và câu đều hoàn chỉnh. Ưu tiên sắp tới: thêm một chi tiết cụ thể và dùng từ nối để nối các câu ngắn.

**Checkpoint:**
`C1` = 1 · `C2` = 1 · `C3` = 0 ("Saturday at 5 o'clock", "my house" là **trả lời** trực tiếp, không có chi tiết thêm) · `C4` = 1 · `C5` = 1 → 4/5 = 80% → **trần bài mỏng khối 6: 60%**.
`O1` = 0 (không từ nối) · `O2` = 1 · `O3` = 0.5 (3/5 câu viết hoa và có dấu kết câu = 60%) · `O4` = 0.5 (không có câu chào cuối trước tên) · `O5` = 0.5 (1: "it") → 2.5/5 = **50%**.
`L1` = 1 · `L2` = 1 · `L3` = 0.5 (`N_sp + N_pu` = 2 / 30 từ = 3.3 / 50 từ) · `L4` = 1 · `L5` = 0.5 (chỉ câu đơn) → 4/5 = 80%; 4 lỗi → trần 80% → **80%**.
Tổng kết (60 + 50 + 80) / 3 = 63.3 → **60%**.

---

### NEO C — 80%

**Bài gốc (44 từ):**

> Hi Linh,
> My birtday party is on Saturday at half past five. It is at my house on Tran Phu Street. We will eat cake and play game in the garden, because the weather is sunny now.
> Please bring your camera!
> See you soon,
> Nam

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Hi Linh,

My {{sp1|birtday}} party is on Saturday at half past five. {{ok|It is at my house on Tran Phu Street.}} We will eat cake and play {{gr1|game}} in the garden{{pu1|,}} {{ok|because the weather is sunny now}}.

Please bring your camera!

See you soon,
Nam

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 90% |
| Organisation | 80% |
| Language | 80% |
| **Tổng kết** | **80%** |

### 3. Nhận xét

Em nêu đủ ba ý, có địa chỉ cụ thể và câu mở rộng với because rất tự nhiên. Ưu tiên sắp tới: danh từ số nhiều và bỏ dấu phẩy thừa trước because.

**Checkpoint:**
`C1` = 1 · `C2` = 1 · `C3` = 0.5 (1 ý có chi tiết thêm: "on Tran Phu Street" — tên riêng; giờ tổ chức là trả lời trực tiếp) · `C4` = 1 · `C5` = 1 → 4.5/5 = **90%**.
`O1` = 0.5 (2 lần: and, because) · `O2` = 1 · `O3` = 1 · `O4` = 1 (Hi Linh · câu mở · See you soon · Nam) · `O5` = 0.5 (1: "It") → 4/5 = **80%**.
`L1`–`L5` = 1 (`N_sp + N_pu` = 2 / 44 từ) → 5/5; 3 lỗi → **trần 80%**.
Tổng kết (90 + 80 + 80) / 3 = 83.3 → **80%**.

---

### NEO D — 60% · đủ ý nhưng không chấm câu, không viết hoa

Neo này chặn một lỗ hổng thật: bài **không sai chính tả, không sai chia động từ, đủ cả ba ý**, nên nếu chỉ đếm checkpoint một cách máy móc thì lên tới 80%. Nhưng cả bài viết một mạch, không một dấu chấm, không một chữ hoa, kể cả tên riêng. Không giáo viên nào chấp nhận mức "A2 vững" cho bài như vậy.

Từ v3 này, `N_pu` được tính vào **L3** cùng với chính tả. Bài dưới đây có `N_pu` ≈ 9 trên 38 từ → L3 = 0.

**Đề:**

> You want to invite your friend Nga to your birthday party. Write an email to Nga. Say: when the party is, where it is, what you will do at the party. Write 25 words or more.

**Bài gốc (38 từ):**

> hi nga my birthday party is on sunday at four o'clock it is at my house near the market we will eat pizza and sing karaoke in the garden please come and bring your sister see you lan

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

{{pu2|hi nga my birthday party is on sunday at four o'clock}} {{pu2|it is at my house near the market}} {{pu2|we will eat pizza and sing karaoke in the garden}} {{pu2|please come and bring your sister}} {{pu2|see you lan}}

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 60% |
| Organisation | 50% |
| Language | 80% |
| **Tổng kết** | **60%** |

### 3. Nhận xét

Em nêu đủ cả ba ý và còn thêm được chi tiết hay về món ăn và trò chơi. Ưu tiên sắp tới: chấm câu và viết hoa đầu câu cùng tên riêng, vì cả bài chưa có dấu chấm nào.

**Vì sao 70%:** Content đạt 100% vì đủ ba ý bắt buộc, có chi tiết mở rộng và người đọc không phải đoán gì. Organisation 60% vì O3 (ranh giới câu) = 0 — cả bài không có ranh giới câu nào — dù O1, O2, O4, O5 vẫn ổn. Language 60% vì L3 = 0 do `N_pu` quá ngưỡng, trong khi L1, L2, L4 đều đạt (không sai chính tả, không sai ngữ pháp). **Điểm mấu chốt: dấu câu là lỗi Language, không chỉ là lỗi Organisation.**

---

**Checkpoint:**
`C1` = 1 · `C2` = 1 · `C3` = 0 (pizza, karaoke là **trả lời** câu hỏi "làm gì"; "near the market" không phải tên riêng hay con số) · `C4` = 1 · `C5` = 1 → 4/5 = 80% → **trần bài mỏng khối 6: 60%**.
`O1` = 0.5 (2 lần "and") · `O2` = 1 · `O3` = 0 (không câu nào có ranh giới) · `O4` = 0.5 (lời chào và tên viết thường) · `O5` = 0.5 (1: "it") → 2.5/5 = **50%**.
`L1` = 1 · `L2` = 1 · `L3` = 0 (`N_pu` ≈ 9 / 38 từ) · `L4` = 1 · `L5` = 1 → 4/5 = **80%** (≥ 5 lỗi → theo checkpoint).
Tổng kết (60 + 50 + 80) / 3 = 63.3 → **60%**. **Dấu câu bị tính ở cả O3 (ranh giới câu) và L3 — đó là hai checkpoint khác nhau đo hai thứ khác nhau, không phải trừ hai lần cùng một lỗi.**

### NEO A2 — 65% · đủ ý CÓ chi tiết, nhưng gần như mọi động từ sai thì

Cặp đối chứng bắt buộc với **NEO A1 (45%)**. Cả hai bài đều hỏng động từ gần như toàn bộ. Khác nhau ở nội dung:
NEO A1 chỉ liệt kê tình tiết (`C3` = 0); bài dưới đây có **chi tiết thật** — tên nơi chốn, thời lượng, món ăn, đồ vật.

**Phép thử của neo này:** trần động từ (>50% sai) **không** kéo Content xuống, vì người đọc không phải đoán chỗ nào —
mốc thời gian "last weekend" nằm sẵn trong bài. Lỗi động từ đã bị tính trọn ở Language (20%).
(Kiểm chứng vòng 7: 3/3 lượt trên hai model khác nhau đều ra đúng 100/80/20 → 65.)

**Đề:**

> Your English friend Sam wants to know about your last weekend. Write an email to Sam. In your email: say where you went, say who you went with, say what you did. Write 25 words or more.

**Bài gốc (45 từ):**

> Hi Sam,
> Last weekend I go to Vung Tau beach with my family. We go by car, it take 2 hours. I swim in the sea and eat seafood, it very delicious. My brother buy a kite. I am very happy.
> See you soon,
> Lan

**Output chuẩn:**

### 1. Bài viết đã đánh dấu

Hi Sam,

Last weekend I {{gr1|go}} to {{ok|Vung Tau beach}} with my family. We {{gr1|go}} by car{{pu1|, it}} {{gr1|take}} 2 hours. I {{gr1|swim}} in the sea and {{gr1|eat}} seafood{{pu1|, it}} {{gr2|very delicious}}. My brother {{gr1|buy}} a kite. {{ok|I am very happy.}}

See you soon,
Lan

### 2. Điểm
| Tiêu chí | % |
|---|---:|
| Content | 100% |
| Organisation | 80% |
| Language | 20% |
| **Tổng kết** | **65%** |

### 3. Nhận xét

Em kể đủ ba ý với nhiều chi tiết cụ thể về chuyến đi Vũng Tàu. Ưu tiên sắp tới: chia động từ ở thì quá khứ khi kể chuyện đã xảy ra.

**Checkpoint:**
`C1` = 1 (đủ nơi, người, việc) · `C2` = 1 · `C3` = 1 (chi tiết: Vung Tau, 2 hours, seafood, kite) · `C4` = 1 (0 chỗ phải đoán) · `C5` = 1 → **5/5 = 100%**.
Trần động từ: 7/8 động từ sai (>50%) → Language tối đa 20%; Content **không** bị trần vì `Chỗ người đọc phải đoán` = 0.
`O1` = 0.5 (chỉ "and") · `O2` = 1 · `O3` = 0.5 (2 comma splice / 5 câu → 60% câu rõ ranh giới) · `O4` = 1 · `O5` = 1 → **4/5 = 80%**.
Language → **20%** (trần động từ). Tổng kết (100 + 80 + 20) / 3 = 66.7 → **65%**.
