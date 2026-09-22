# TỪ ĐIỂN TAGGING KEY GRAMMAR — KHỐI 7 — hệ Cambridge · 2026–2027

## §0. Cấu hình

```text
Mã khối:        g7b1
Nguồn:          SYLLABUS GRADE (26-27).xlsx · sheet "G7 - Cambridge"
Rubric đi kèm:  Writing_Rubric_Grade7_Cambridge_B1_Foundation_v3.md
Cách tagging:   unit_subtopic — chọn Unit → Sub-topic, hệ thống điền sẵn mã cấu trúc (§4)
Số cấu trúc:    16 (trong đó 4 cấu trúc nền)
Vai trò:        Filter 2 — chạy SAU khi đã chấm xong theo rubric khối
Ảnh hưởng điểm: CHỈ KHI CHƯA ĐẠT — tiêu chí ngữ pháp trần 40% (0 lần) hoặc 50% (1 lần).
Kết quả:        Đạt / Chưa đạt, kèm tỉ lệ dùng đúng, ở một phần nhận xét riêng. Chưa đạt → viết lại bài.
```

Key grammar **gắn vào lượt giao bài**, không gắn vào đề và không gắn vào tuần.
Đề bài giữ nguyên; lượt giao bài mang thêm trường `key_grammar.ids` (xem `00_DAC_TA_GIAO_NHAN.md`).
Lượt giao bài không có `key_grammar` → **bỏ qua filter 2**, không in phần key grammar.

## §1. Tagging khi giao bài

1. Giáo viên chọn **Unit**, rồi chọn **Sub-topic** của bài viết.
2. Hệ thống điền sẵn các mã cấu trúc theo bảng §4.
3. Giáo viên có thể bỏ bớt hoặc thêm mã từ §3. Khi đó gói giao bài ghi `"edited": true` để còn đối chiếu.
4. Không muốn kiểm key grammar cho lượt này → bỏ hết mã.

Khuyến nghị khi chọn mã:

- Nên có **ít nhất một cấu trúc không phải cấu trúc nền**. Nếu chỉ chọn cấu trúc nền (như các thì đơn, mạo từ) thì gần như bài nào cũng Đạt, filter 2 mất tác dụng.
- Chọn cấu trúc **hợp với dạng đề**. Ví dụ đề kể chuyện hợp với các thì quá khứ; đề mô tả biểu đồ hợp với so sánh.
- Không chọn quá 3 mã: nhận xét key grammar chỉ có 2 câu.

## §2. Cách chấm key grammar

### Tiêu chí đạt

```text
N_kg_ok   = số lần dùng ĐÚNG một cấu trúc key grammar được giao
N_kg_bad  = số lần CÓ DÙNG nhưng SAI
N_kg      = N_kg_ok + N_kg_bad

Cộng dồn MỌI cấu trúc key grammar được giao.

N_kg_ok >= 2  →  ĐẠT
N_kg_ok <  2  →  CHƯA ĐẠT

Tỉ lệ = N_kg_ok / N_kg   (N_kg = 0 thì ghi "không dùng lần nào")
```

Ví dụ bài được giao ba cấu trúc (hiện tại đơn, quá khứ đơn, tương lai đơn): bài chỉ dùng đúng hiện tại đơn 2 lần là **đạt**, dù hai thì còn lại không xuất hiện.

### Quy tắc đếm

- **Một lần dùng = một mệnh đề hoặc một cụm** chứa cấu trúc đó. Hai cấu trúc trong cùng một câu tính là hai lần.
- **Phải trích nguyên văn** từng lần dùng. Không trích được thì không tính.
- **Dùng đúng** = đúng dạng **và** đúng ngữ cảnh, theo mục "Tính là dùng đúng khi" ở §3.
- **Dùng sai** = rõ ràng học sinh đang cố dùng cấu trúc đó nhưng sai dạng. Ví dụ khi được giao hiện tại hoàn thành, `she have gone` là một lần dùng sai.
- **Không tính** các chuỗi chép từ đề (cổng G3 của rubric).
- **Cấu trúc nền** (đánh dấu ở §3) là những cấu trúc gần như bài nào cũng có, như mạo từ hay thì hiện tại đơn. Vẫn đếm bình thường, nhưng nhận xét **không được khen** học sinh vì đã dùng chúng. Nhận xét chỉ ra học sinh đã dùng **có chủ đích** hay chưa.

### Ảnh hưởng đến điểm

| Số lần dùng đúng | Kết quả | Tiêu chí ngữ pháp | Các tiêu chí khác |
|---:|---|---|---|
| 0 | **Chưa đạt** | **trần 40%** | không đổi |
| 1 | **Chưa đạt** | **trần 50%** | không đổi |
| ≥ 2 | **Đạt** | không đổi | không đổi |

Điểm đã thấp hơn trần thì giữ nguyên.

Tiêu chí ngữ pháp là **Grammatical Range & Accuracy** ở hệ IELTS, **Language** ở hệ Cambridge.
Trần này áp **sau** các trần khác của rubric, lấy mức thấp nhất.

Ngoài trần 40% / 50% này, từng lỗi dùng sai key grammar **đã được tính** ở tiêu chí ngữ pháp như mọi lỗi khác — không trừ thêm lần nữa.

## §3. Từ điển cấu trúc

### Bảng mã

| Mã | Tên cấu trúc | Cấu trúc nền |
|---|---|:---:|
| `simple_tenses` | Các thì đơn: hiện tại đơn, quá khứ đơn, tương lai đơn | ✓ |
| `pron_all` | Đại từ (mọi dạng) | ✓ |
| `noun_count` | Danh từ đếm được / không đếm được, số nhiều |  |
| `quant` | Lượng từ |  |
| `prep_tp` | Giới từ chỉ thời gian và nơi chốn | ✓ |
| `comparison` | So sánh: like, different from, so sánh bằng, hơn, nhất |  |
| `contrast` | Từ nối chỉ tương phản: although / though, however, despite / in spite of |  |
| `modal` | Động từ khuyết thiếu |  |
| `art` | Mạo từ a / an / the (và không dùng mạo từ) | ✓ |
| `cont_tenses` | Các thì tiếp diễn (hiện tại, quá khứ, tương lai) |  |
| `pres_perf` | Hiện tại hoàn thành |  |
| `past_perf` | Quá khứ hoàn thành |  |
| `passive_simple` | Câu bị động (các thì đơn) |  |
| `rel_clause` | Mệnh đề quan hệ |  |
| `cond_0123` | Câu điều kiện loại 0, 1, 2, 3 |  |
| `reported` | Câu tường thuật |  |

*Cấu trúc nền*: gần như bài nào cũng có. Vẫn đếm bình thường nhưng nhận xét không được khen học sinh chỉ vì đã dùng.

### Định nghĩa từng mã

#### `simple_tenses` — Các thì đơn: hiện tại đơn, quá khứ đơn, tương lai đơn · **cấu trúc nền**

- **Nhận diện:** `V / V-s`, `V2 / did`, `will + V`.
- **Tính là dùng đúng khi:** đúng dạng và đúng mốc thời gian của ngữ cảnh.

#### `pron_all` — Đại từ (mọi dạng) · **cấu trúc nền**

- **Nhận diện:** nhân xưng, tân ngữ, tính từ sở hữu, đại từ sở hữu, phản thân (`myself`…), chỉ định, bất định.
- **Tính là dùng đúng khi:** đúng ngôi, đúng số, và người đọc xác định được đại từ thay cho danh từ nào.

#### `noun_count` — Danh từ đếm được / không đếm được, số nhiều

- **Nhận diện:** danh từ đứng sau số đếm hoặc lượng từ: `three books`, `some water`, `many students`.
- **Tính là dùng đúng khi:** danh từ đếm được có dạng số nhiều khi cần; danh từ không đếm được không thêm -s.

#### `quant` — Lượng từ

- **Nhận diện:** `much, many, a lot of, lots of, some, any, a few, few, a little, little, no` + danh từ.
- **Tính là dùng đúng khi:** đúng loại danh từ (`many` + đếm được, `much` + không đếm được); `any` trong câu phủ định/câu hỏi.

#### `prep_tp` — Giới từ chỉ thời gian và nơi chốn · **cấu trúc nền**

- **Nhận diện:** `in / on / at` + thời gian; `in / on / at / under / next to / behind / between / among / into`… + nơi chốn.
- **Tính là dùng đúng khi:** đúng giới từ cho đúng loại thời gian/nơi chốn (`on Monday`, `at 7 o'clock`, `in the garden`).

#### `comparison` — So sánh: like, different from, so sánh bằng, hơn, nhất

- **Nhận diện:** `like`, `different from`, `(not) as … as`, `-er than / more … than`, `the -est / the most`.
- **Tính là dùng đúng khi:** đúng dạng tính từ ngắn/dài và đủ `than` / `the`.

#### `contrast` — Từ nối chỉ tương phản: although / though, however, despite / in spite of

- **Nhận diện:** `although / though + mệnh đề`, `However,`, `despite / in spite of + danh từ / V-ing`.
- **Tính là dùng đúng khi:** `although` + mệnh đề, `despite` + cụm danh từ; không dùng `although … but` trong cùng câu.

#### `modal` — Động từ khuyết thiếu

- **Nhận diện:** `can, could, may, might, must, have to, should, ought to, don't have to, don't need to, needn't` + V.
- **Tính là dùng đúng khi:** theo sau là động từ nguyên mẫu không `to` (trừ `have to / ought to`) và đúng chức năng.

#### `art` — Mạo từ a / an / the (và không dùng mạo từ) · **cấu trúc nền**

- **Nhận diện:** `a`, `an`, `the` đứng trước danh từ.
- **Tính là dùng đúng khi:** `a/an` theo âm đầu; `the` cho vật đã xác định; không dùng `a/an` với danh từ không đếm được hoặc số nhiều.

#### `cont_tenses` — Các thì tiếp diễn (hiện tại, quá khứ, tương lai)

- **Nhận diện:** `am/is/are + V-ing`, `was/were + V-ing`, `will be + V-ing`.
- **Tính là dùng đúng khi:** đủ trợ động từ `be` và đúng mốc thời gian.

#### `pres_perf` — Hiện tại hoàn thành

- **Nhận diện:** `have/has + V3`.
- **Tính là dùng đúng khi:** đúng V3, đúng `have/has` theo chủ ngữ, không đi với mốc quá khứ xác định (sai: `I have seen it yesterday`).

#### `past_perf` — Quá khứ hoàn thành

- **Nhận diện:** `had + V3`.
- **Tính là dùng đúng khi:** đúng V3 và diễn tả việc xảy ra trước một việc khác trong quá khứ.

#### `passive_simple` — Câu bị động (các thì đơn)

- **Nhận diện:** `am/is/are/was/were/will be + V3`.
- **Tính là dùng đúng khi:** đủ `be`, đúng V3, chủ ngữ là đối tượng chịu tác động.

#### `rel_clause` — Mệnh đề quan hệ

- **Nhận diện:** `who, which, that, whose, where` (xác định và không xác định).
- **Tính là dùng đúng khi:** đúng đại từ quan hệ cho người/vật/nơi chốn; mệnh đề không xác định có dấu phẩy và không dùng `that`.

#### `cond_0123` — Câu điều kiện loại 0, 1, 2, 3

- **Nhận diện:** như loại 0–2, thêm `If + had V3, would have V3`.
- **Tính là dùng đúng khi:** hai vế khớp đúng loại.

#### `reported` — Câu tường thuật

- **Nhận diện:** `said / told … (that)`, `asked if / whether / wh-` + mệnh đề lùi thì.
- **Tính là dùng đúng khi:** lùi thì đúng, đổi đại từ và trạng từ thời gian phù hợp.

## §4. Bảng Unit → Sub-topic → mã cấu trúc

Bảng này là nguồn gợi ý tự động ở bước 2 của §1.

| Unit | Sub-topic | Key grammar | Mã | Ghi chú |
|---|---|---|---|---|
| 1 · Hobbies | My favorite hobbies | Các thì đơn: hiện tại đơn, quá khứ đơn, tương lai đơn · *toàn cấu trúc nền* | `simple_tenses` |  |
| 1 · Hobbies | Personal growth values | Các thì đơn: hiện tại đơn, quá khứ đơn, tương lai đơn · *toàn cấu trúc nền* | `simple_tenses` | dùng lại sub-topic trước |
| 2 · Healthy Living | Common health issues | Đại từ (mọi dạng) · *toàn cấu trúc nền* | `pron_all` |  |
| 2 · Healthy Living | Healthy habits and nutrition | Danh từ đếm được / không đếm được, số nhiều; Lượng từ | `noun_count`, `quant` |  |
| 3 · Community Service | Community support | Giới từ chỉ thời gian và nơi chốn · *toàn cấu trúc nền* | `prep_tp` |  |
| 3 · Community Service | Helping people in need | So sánh: like, different from, so sánh bằng, hơn, nhất | `comparison` |  |
| 4 · Music and Arts | Musical performance experiences | Từ nối chỉ tương phản: although / though, however, despite / in spite of | `contrast` |  |
| 4 · Music and Arts | Exploring artistic works | Từ nối chỉ tương phản: although / though, however, despite / in spite of | `contrast` | dùng lại sub-topic trước |
| 5 · Food and Drinks | Popular dishes and ingredients | Động từ khuyết thiếu | `modal` |  |
| 5 · Food and Drinks | Favorite drinks | Mạo từ a / an / the (và không dùng mạo từ) · *toàn cấu trúc nền* | `art` |  |
| 6 · A Visit to School | School facilities | Các thì tiếp diễn (hiện tại, quá khứ, tương lai) | `cont_tenses` |  |
| 6 · A Visit to School | Student academic life | Các thì tiếp diễn (hiện tại, quá khứ, tương lai) | `cont_tenses` | dùng lại sub-topic trước |
| 7 · Traffic | Traffic rules | Hiện tại hoàn thành; Quá khứ hoàn thành | `pres_perf`, `past_perf` |  |
| 7 · Traffic | Daily transport experiences | Hiện tại hoàn thành; Quá khứ hoàn thành | `pres_perf`, `past_perf` | dùng lại sub-topic trước |
| 8 · Films | Movie genre selections | Câu bị động (các thì đơn) | `passive_simple` |  |
| 8 · Films | Personal film reviews | Câu bị động (các thì đơn) | `passive_simple` | dùng lại sub-topic trước |
| 9 · Festivals Around the World | World festival traditions | Mệnh đề quan hệ | `rel_clause` |  |
| 9 · Festivals Around the World | Festive celebration activities | Mệnh đề quan hệ | `rel_clause` | dùng lại sub-topic trước |
| 10 · Energy Sources | Different sources | Câu điều kiện loại 0, 1, 2, 3 | `cond_0123` |  |
| 10 · Energy Sources | Energy saving solutions | Câu điều kiện loại 0, 1, 2, 3 | `cond_0123` | dùng lại sub-topic trước |
| 11 · Traveling in the Future | Innovative travel modes | Câu tường thuật | `reported` |  |
| 11 · Traveling in the Future | Sustainable smart transportation | Câu tường thuật | `reported` | dùng lại sub-topic trước |

## §5. Output

Phần key grammar in **ngay dưới nhận xét chung**, trong mục 3 của output rubric:

```markdown
### 3. Nhận xét

**Nhận xét chung:** <≤2 câu, ≤50 từ — như quy định của rubric khối>

**Key grammar: <tên các cấu trúc được giao>**
**<Đạt | Chưa đạt>** · dùng đúng <N_kg_ok>/<N_kg> lần (<tỉ lệ>%)
<2 câu, khoảng 50 từ, tiếng Việt — xem quy định bên dưới>
```

**Nội dung 2 câu:**

| Kết quả | Câu 1 | Câu 2 |
|---|---|---|
| **Đạt** | chỉ ra học sinh đã dùng cấu trúc ở đâu (trích nguyên văn ngắn) | nhận định cách dùng: tự nhiên hay còn gượng, dùng đủ các cấu trúc được giao hay chỉ một |
| **Chưa đạt** | nói rõ mới dùng đúng bao nhiêu lần (trích chỗ dùng sai nếu có) | **bắt buộc yêu cầu học sinh viết lại bài**, dùng đúng ít nhất 2 lần cấu trúc được giao |

**Cấm gợi ý cách sửa:** không viết câu mẫu, không đưa phiên bản viết lại của câu nào, không nói câu nào nên
đổi thành gì, không đưa ví dụ cấu trúc. Nhận xét chỉ nêu **kết quả** và **yêu cầu** — cùng nguyên tắc
"chỉ ra vị trí, không đưa phần sửa" của phần đánh dấu lỗi.

Nếu học sinh không dùng lần nào: ghi `**Chưa đạt** · dùng đúng 0/0 lần (không dùng lần nào)`.

Hệ thống hiển thị thêm một dải cảnh báo *"cần viết lại bài"* khi kết quả là Chưa đạt.

### Đánh dấu trong bài (mục 1)

| Trường hợp | Token | Màu |
|---|---|---|
| Dùng **đúng** key grammar | `{{kg\|…}}` | xanh, nhãn **KG** |
| Dùng **sai** key grammar | `{{gr2\|…}}` | đỏ — giữ nguyên mã lỗi ngữ pháp |

Cú pháp đầy đủ: `{{kg|đoạn văn bản}}`. Token này **thay cho** `{{ok|…}}` ở những chỗ đó; không bọc hai token lồng nhau.

### Kiểm đếm nội bộ (mục 0, bị ẩn với học sinh)

```text
Key grammar được giao: <danh sách mã cấu trúc>
Dùng đúng: <N_kg_ok> — trích nguyên văn từng lần, ghi mã cấu trúc
Dùng sai:  <N_kg_bad> — trích nguyên văn từng lần, ghi mã cấu trúc
Kết luận:  <Đạt | Chưa đạt> · <N_kg_ok>/<N_kg>
```

## §6. Dữ liệu cho hệ thống

```yaml
dictionary: "KeyGrammar_Grade7_Cambridge.md"
grade_code: g7b1
rubric: "Writing_Rubric_Grade7_Cambridge_B1_Foundation_v3.md"
tagging: unit_subtopic
pass_rule:
  count: correct_uses          # chỉ đếm lần dùng ĐÚNG
  scope: assigned_total        # cộng dồn mọi cấu trúc được giao
  pass_if_at_least: 2          # < 2 là Chưa đạt, phải viết lại bài
  ratio: correct / (correct + incorrect)
  grammar_cap: {0: 40, 1: 50}  # trần % tiêu chí ngữ pháp khi Chưa đạt
structures:
  - id: simple_tenses
    name: "Các thì đơn: hiện tại đơn, quá khứ đơn, tương lai đơn"
    base: true
  - id: pron_all
    name: "Đại từ (mọi dạng)"
    base: true
  - id: noun_count
    name: "Danh từ đếm được / không đếm được, số nhiều"
    base: false
  - id: quant
    name: "Lượng từ"
    base: false
  - id: prep_tp
    name: "Giới từ chỉ thời gian và nơi chốn"
    base: true
  - id: comparison
    name: "So sánh: like, different from, so sánh bằng, hơn, nhất"
    base: false
  - id: contrast
    name: "Từ nối chỉ tương phản: although / though, however, despite / in spite of"
    base: false
  - id: modal
    name: "Động từ khuyết thiếu"
    base: false
  - id: art
    name: "Mạo từ a / an / the (và không dùng mạo từ)"
    base: true
  - id: cont_tenses
    name: "Các thì tiếp diễn (hiện tại, quá khứ, tương lai)"
    base: false
  - id: pres_perf
    name: "Hiện tại hoàn thành"
    base: false
  - id: past_perf
    name: "Quá khứ hoàn thành"
    base: false
  - id: passive_simple
    name: "Câu bị động (các thì đơn)"
    base: false
  - id: rel_clause
    name: "Mệnh đề quan hệ"
    base: false
  - id: cond_0123
    name: "Câu điều kiện loại 0, 1, 2, 3"
    base: false
  - id: reported
    name: "Câu tường thuật"
    base: false
units:
  - unit: 1
    unit_name: "Hobbies"
    sub_topics:
      - name: "My favorite hobbies"
        key_grammar: [simple_tenses]
      - name: "Personal growth values"
        key_grammar: [simple_tenses]
  - unit: 2
    unit_name: "Healthy Living"
    sub_topics:
      - name: "Common health issues"
        key_grammar: [pron_all]
      - name: "Healthy habits and nutrition"
        key_grammar: [noun_count, quant]
  - unit: 3
    unit_name: "Community Service"
    sub_topics:
      - name: "Community support"
        key_grammar: [prep_tp]
      - name: "Helping people in need"
        key_grammar: [comparison]
  - unit: 4
    unit_name: "Music and Arts"
    sub_topics:
      - name: "Musical performance experiences"
        key_grammar: [contrast]
      - name: "Exploring artistic works"
        key_grammar: [contrast]
  - unit: 5
    unit_name: "Food and Drinks"
    sub_topics:
      - name: "Popular dishes and ingredients"
        key_grammar: [modal]
      - name: "Favorite drinks"
        key_grammar: [art]
  - unit: 6
    unit_name: "A Visit to School"
    sub_topics:
      - name: "School facilities"
        key_grammar: [cont_tenses]
      - name: "Student academic life"
        key_grammar: [cont_tenses]
  - unit: 7
    unit_name: "Traffic"
    sub_topics:
      - name: "Traffic rules"
        key_grammar: [pres_perf, past_perf]
      - name: "Daily transport experiences"
        key_grammar: [pres_perf, past_perf]
  - unit: 8
    unit_name: "Films"
    sub_topics:
      - name: "Movie genre selections"
        key_grammar: [passive_simple]
      - name: "Personal film reviews"
        key_grammar: [passive_simple]
  - unit: 9
    unit_name: "Festivals Around the World"
    sub_topics:
      - name: "World festival traditions"
        key_grammar: [rel_clause]
      - name: "Festive celebration activities"
        key_grammar: [rel_clause]
  - unit: 10
    unit_name: "Energy Sources"
    sub_topics:
      - name: "Different sources"
        key_grammar: [cond_0123]
      - name: "Energy saving solutions"
        key_grammar: [cond_0123]
  - unit: 11
    unit_name: "Traveling in the Future"
    sub_topics:
      - name: "Innovative travel modes"
        key_grammar: [reported]
      - name: "Sustainable smart transportation"
        key_grammar: [reported]
```

## §7. Ghi chú cần giáo viên kiểm lại

- Cột A và cột B là danh sách tham khảo không gắn với unit — đã bỏ qua.
- Ngữ pháp từng tuần **giống hệt** hệ IELTS; hai hệ chỉ khác nhau ở phần Reading.
- **Unit 2 · Healthy habits and nutrition:** Mục tiêu Writing của syllabus ghi *"Sử dụng đúng đại từ và lượng từ trong bài viết"* — tức là mong cả đại từ (Unit 2 · Common health issues). Bảng §4 chỉ gợi ý ngữ pháp của chính sub-topic này; giáo viên có thể thêm mã khi giao bài.
- **Unit 5 · Favorite drinks:** Mục tiêu Writing ghi *"Sử dụng đúng mạo từ và modal verbs trong bài viết"* — tức là mong cả modal verbs (Unit 5 · Popular dishes and ingredients). Bảng §4 chỉ gợi ý ngữ pháp của chính sub-topic này; giáo viên có thể thêm mã khi giao bài.
