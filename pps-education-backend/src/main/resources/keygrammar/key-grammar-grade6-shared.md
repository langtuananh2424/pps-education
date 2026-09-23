# TỪ ĐIỂN TAGGING KEY GRAMMAR — KHỐI 6 (dùng chung Cambridge + IELTS) · 2026–2027

## §0. Cấu hình

```text
Mã khối:        g6
Nguồn:          SYLLABUS GRADE (26-27).xlsx · sheet "G6 (chung)"
Rubric đi kèm:  Writing_Rubric_Grade6_Cambridge_A2_v3.md
Cách tagging:   unit_subtopic — chọn Unit → Sub-topic, hệ thống điền sẵn mã cấu trúc (§4)
Số cấu trúc:    23 (trong đó 7 cấu trúc nền)
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
| `pron_basic` | Đại từ nhân xưng, sở hữu, chỉ định và "one" | ✓ |
| `art` | Mạo từ a / an / the (và không dùng mạo từ) | ✓ |
| `pron_indef` | Đại từ bất định |  |
| `noun_count` | Danh từ đếm được / không đếm được, số nhiều |  |
| `quant` | Lượng từ |  |
| `num` | Số đếm và số thứ tự | ✓ |
| `be_verb` | Động từ to be và động từ thường | ✓ |
| `prep_tp` | Giới từ chỉ thời gian và nơi chốn | ✓ |
| `adj_adv` | Tính từ và trạng từ (-ly) |  |
| `verb_pattern` | Động từ + V-ing / to V |  |
| `adv_place` | Trạng ngữ chỉ nơi chốn và vị trí |  |
| `simple_tenses` | Các thì đơn: hiện tại đơn, quá khứ đơn, tương lai đơn | ✓ |
| `cont_tenses` | Các thì tiếp diễn (hiện tại, quá khứ, tương lai) |  |
| `going_to` | Be going to (dự định gần) |  |
| `pres_perf` | Hiện tại hoàn thành |  |
| `past_simple` | Quá khứ đơn | ✓ |
| `comp_sup` | So sánh hơn và so sánh nhất của tính từ |  |
| `modal` | Động từ khuyết thiếu |  |
| `passive_simple` | Câu bị động (các thì đơn) |  |
| `passive_agent` | Câu bị động với by / with / without |  |
| `cond_012` | Câu điều kiện loại 0, 1, 2 |  |
| `past_perf` | Quá khứ hoàn thành |  |
| `reported` | Câu tường thuật |  |

*Cấu trúc nền*: gần như bài nào cũng có. Vẫn đếm bình thường nhưng nhận xét không được khen học sinh chỉ vì đã dùng.

### Định nghĩa từng mã

#### `pron_basic` — Đại từ nhân xưng, sở hữu, chỉ định và "one" · **cấu trúc nền**

- **Nhận diện:** `I/me/my/mine`, `he/him/his`…; `this/that/these/those`; `one/ones` thay cho danh từ.
- **Tính là dùng đúng khi:** đúng ngôi, đúng số, đúng vị trí (chủ ngữ / tân ngữ / đứng trước danh từ / đứng một mình).

#### `art` — Mạo từ a / an / the (và không dùng mạo từ) · **cấu trúc nền**

- **Nhận diện:** `a`, `an`, `the` đứng trước danh từ.
- **Tính là dùng đúng khi:** `a/an` theo âm đầu; `the` cho vật đã xác định; không dùng `a/an` với danh từ không đếm được hoặc số nhiều.

#### `pron_indef` — Đại từ bất định

- **Nhận diện:** `someone, somebody, something, anyone, anything, everyone, everything, no one, nothing`…
- **Tính là dùng đúng khi:** động từ theo sau chia số ít; `any-` dùng trong câu phủ định hoặc câu hỏi.

#### `noun_count` — Danh từ đếm được / không đếm được, số nhiều

- **Nhận diện:** danh từ đứng sau số đếm hoặc lượng từ: `three books`, `some water`, `many students`.
- **Tính là dùng đúng khi:** danh từ đếm được có dạng số nhiều khi cần; danh từ không đếm được không thêm -s.

#### `quant` — Lượng từ

- **Nhận diện:** `much, many, a lot of, lots of, some, any, a few, few, a little, little, no` + danh từ.
- **Tính là dùng đúng khi:** đúng loại danh từ (`many` + đếm được, `much` + không đếm được); `any` trong câu phủ định/câu hỏi.

#### `num` — Số đếm và số thứ tự · **cấu trúc nền**

- **Nhận diện:** `two, twenty-one, first, second`…
- **Tính là dùng đúng khi:** đúng dạng viết; danh từ đi sau số ≥2 ở số nhiều.

#### `be_verb` — Động từ to be và động từ thường · **cấu trúc nền**

- **Nhận diện:** mọi động từ chính của câu.
- **Tính là dùng đúng khi:** không dùng song song `be` với động từ thường (sai: `I am go`); không thiếu `be` trước tính từ (sai: `she very happy`).

#### `prep_tp` — Giới từ chỉ thời gian và nơi chốn · **cấu trúc nền**

- **Nhận diện:** `in / on / at` + thời gian; `in / on / at / under / next to / behind / between / among / into`… + nơi chốn.
- **Tính là dùng đúng khi:** đúng giới từ cho đúng loại thời gian/nơi chốn (`on Monday`, `at 7 o'clock`, `in the garden`).

#### `adj_adv` — Tính từ và trạng từ (-ly)

- **Nhận diện:** tính từ đứng trước danh từ hoặc sau `be / look / feel`; trạng từ -ly bổ nghĩa cho động từ.
- **Tính là dùng đúng khi:** tính từ đi với danh từ, trạng từ đi với động từ (sai: `she sings beautiful`).

#### `verb_pattern` — Động từ + V-ing / to V

- **Nhận diện:** `like/enjoy/love + V-ing`, `want/decide/hope + to V`…
- **Tính là dùng đúng khi:** đúng dạng theo động từ đứng trước.

#### `adv_place` — Trạng ngữ chỉ nơi chốn và vị trí

- **Nhận diện:** `here, there, upstairs, outside, in the corner, at the back`…
- **Tính là dùng đúng khi:** đúng vị trí trong câu và đúng nghĩa.

#### `simple_tenses` — Các thì đơn: hiện tại đơn, quá khứ đơn, tương lai đơn · **cấu trúc nền**

- **Nhận diện:** `V / V-s`, `V2 / did`, `will + V`.
- **Tính là dùng đúng khi:** đúng dạng và đúng mốc thời gian của ngữ cảnh.

#### `cont_tenses` — Các thì tiếp diễn (hiện tại, quá khứ, tương lai)

- **Nhận diện:** `am/is/are + V-ing`, `was/were + V-ing`, `will be + V-ing`.
- **Tính là dùng đúng khi:** đủ trợ động từ `be` và đúng mốc thời gian.

#### `going_to` — Be going to (dự định gần)

- **Nhận diện:** `am/is/are going to + V`.
- **Tính là dùng đúng khi:** đủ `be` và động từ nguyên mẫu.

#### `pres_perf` — Hiện tại hoàn thành

- **Nhận diện:** `have/has + V3`.
- **Tính là dùng đúng khi:** đúng V3, đúng `have/has` theo chủ ngữ, không đi với mốc quá khứ xác định (sai: `I have seen it yesterday`).

#### `past_simple` — Quá khứ đơn · **cấu trúc nền**

- **Nhận diện:** `V2`, `did / didn't + V`.
- **Tính là dùng đúng khi:** đúng dạng V2 và dùng cho việc đã kết thúc.

#### `comp_sup` — So sánh hơn và so sánh nhất của tính từ

- **Nhận diện:** `-er than / more … than`, `the -est / the most`.
- **Tính là dùng đúng khi:** đúng dạng tính từ ngắn/dài, không dùng kép (sai: `more bigger`).

#### `modal` — Động từ khuyết thiếu

- **Nhận diện:** `can, could, may, might, must, have to, should, ought to, don't have to, don't need to, needn't` + V.
- **Tính là dùng đúng khi:** theo sau là động từ nguyên mẫu không `to` (trừ `have to / ought to`) và đúng chức năng.

#### `passive_simple` — Câu bị động (các thì đơn)

- **Nhận diện:** `am/is/are/was/were/will be + V3`.
- **Tính là dùng đúng khi:** đủ `be`, đúng V3, chủ ngữ là đối tượng chịu tác động.

#### `passive_agent` — Câu bị động với by / with / without

- **Nhận diện:** `be + V3 + by / with / without`.
- **Tính là dùng đúng khi:** đúng cấu trúc bị động và đúng giới từ.

#### `cond_012` — Câu điều kiện loại 0, 1, 2

- **Nhận diện:** `If + hiện tại, hiện tại` · `If + hiện tại, will + V` · `If + quá khứ, would + V`.
- **Tính là dùng đúng khi:** hai vế khớp đúng loại.

#### `past_perf` — Quá khứ hoàn thành

- **Nhận diện:** `had + V3`.
- **Tính là dùng đúng khi:** đúng V3 và diễn tả việc xảy ra trước một việc khác trong quá khứ.

#### `reported` — Câu tường thuật

- **Nhận diện:** `said / told … (that)`, `asked if / whether / wh-` + mệnh đề lùi thì.
- **Tính là dùng đúng khi:** lùi thì đúng, đổi đại từ và trạng từ thời gian phù hợp.

## §4. Bảng Unit → Sub-topic → mã cấu trúc

Bảng này là nguồn gợi ý tự động ở bước 2 của §1.

| Unit | Sub-topic | Key grammar | Mã | Ghi chú |
|---|---|---|---|---|
| 1 · My New School | School activities and subjects | Đại từ nhân xưng, sở hữu, chỉ định và "one"; Mạo từ a / an / the (và không dùng mạo từ) · *toàn cấu trúc nền* | `pron_basic`, `art` |  |
| 1 · My New School | School objects and environment | Đại từ bất định; Danh từ đếm được / không đếm được, số nhiều; Lượng từ | `pron_indef`, `noun_count`, `quant` |  |
| 2 · My House | Furniture and household items | Số đếm và số thứ tự; Động từ to be và động từ thường · *toàn cấu trúc nền* | `num`, `be_verb` |  |
| 2 · My House | Types of houses and locations | Giới từ chỉ thời gian và nơi chốn; Tính từ và trạng từ (-ly) | `prep_tp`, `adj_adv` |  |
| 3 · My Friends | Appearance | Động từ + V-ing / to V; Trạng ngữ chỉ nơi chốn và vị trí | `verb_pattern`, `adv_place` |  |
| 3 · My Friends | Personality | Động từ + V-ing / to V; Trạng ngữ chỉ nơi chốn và vị trí | `verb_pattern`, `adv_place` | ôn tập — dùng lại sub-topic trước |
| 4 · My Neighborhood | Places | Các thì đơn: hiện tại đơn, quá khứ đơn, tương lai đơn · *toàn cấu trúc nền* | `simple_tenses` |  |
| 4 · My Neighborhood | Description | Các thì đơn: hiện tại đơn, quá khứ đơn, tương lai đơn · *toàn cấu trúc nền* | `simple_tenses` | dùng lại sub-topic trước |
| 5 · Natural Wonders | Natural features | Các thì tiếp diễn (hiện tại, quá khứ, tương lai); Be going to (dự định gần) | `cont_tenses`, `going_to` |  |
| 5 · Natural Wonders | Activities | Các thì tiếp diễn (hiện tại, quá khứ, tương lai) | `cont_tenses` | dùng lại sub-topic trước |
| 6 · Our Tet Holiday | Activities during Tet | Hiện tại hoàn thành; Quá khứ đơn | `pres_perf`, `past_simple` |  |
| 6 · Our Tet Holiday | Traditions and items of Tet | Hiện tại hoàn thành; Quá khứ đơn | `pres_perf`, `past_simple` | dùng lại sub-topic trước |
| 7 · Television | Types of TV shows | So sánh hơn và so sánh nhất của tính từ | `comp_sup` |  |
| 7 · Television | Watching habit | Động từ khuyết thiếu | `modal` |  |
| 8 · Sports and Games | Sports and games | Câu bị động (các thì đơn) | `passive_simple` |  |
| 8 · Sports and Games | Equipment and facilities | Câu bị động với by / with / without | `passive_agent` |  |
| 9 · Cities of the World | Landmarks and attractions | Câu điều kiện loại 0, 1, 2 | `cond_012` |  |
| 9 · Cities of the World | City atmosphere and lifestyle | Câu điều kiện loại 0, 1, 2 | `cond_012` | dùng lại sub-topic trước |
| 10 · Our Greener World | Sub-topic 1 (syllabus chưa ghi tên) | Quá khứ đơn; Quá khứ hoàn thành | `past_simple`, `past_perf` | tên sub-topic lỗi ở nguồn — xem §7 |
| 10 · Our Greener World | Sub-topic 2 (syllabus chưa ghi tên) | Câu tường thuật | `reported` | tên sub-topic lỗi ở nguồn — xem §7 |

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
dictionary: "KeyGrammar_Grade6.md"
grade_code: g6
rubric: "Writing_Rubric_Grade6_Cambridge_A2_v3.md"
tagging: unit_subtopic
pass_rule:
  count: correct_uses          # chỉ đếm lần dùng ĐÚNG
  scope: assigned_total        # cộng dồn mọi cấu trúc được giao
  pass_if_at_least: 2          # < 2 là Chưa đạt, phải viết lại bài
  ratio: correct / (correct + incorrect)
  grammar_cap: {0: 40, 1: 50}  # trần % tiêu chí ngữ pháp khi Chưa đạt
structures:
  - id: pron_basic
    name: "Đại từ nhân xưng, sở hữu, chỉ định và \"one\""
    base: true
  - id: art
    name: "Mạo từ a / an / the (và không dùng mạo từ)"
    base: true
  - id: pron_indef
    name: "Đại từ bất định"
    base: false
  - id: noun_count
    name: "Danh từ đếm được / không đếm được, số nhiều"
    base: false
  - id: quant
    name: "Lượng từ"
    base: false
  - id: num
    name: "Số đếm và số thứ tự"
    base: true
  - id: be_verb
    name: "Động từ to be và động từ thường"
    base: true
  - id: prep_tp
    name: "Giới từ chỉ thời gian và nơi chốn"
    base: true
  - id: adj_adv
    name: "Tính từ và trạng từ (-ly)"
    base: false
  - id: verb_pattern
    name: "Động từ + V-ing / to V"
    base: false
  - id: adv_place
    name: "Trạng ngữ chỉ nơi chốn và vị trí"
    base: false
  - id: simple_tenses
    name: "Các thì đơn: hiện tại đơn, quá khứ đơn, tương lai đơn"
    base: true
  - id: cont_tenses
    name: "Các thì tiếp diễn (hiện tại, quá khứ, tương lai)"
    base: false
  - id: going_to
    name: "Be going to (dự định gần)"
    base: false
  - id: pres_perf
    name: "Hiện tại hoàn thành"
    base: false
  - id: past_simple
    name: "Quá khứ đơn"
    base: true
  - id: comp_sup
    name: "So sánh hơn và so sánh nhất của tính từ"
    base: false
  - id: modal
    name: "Động từ khuyết thiếu"
    base: false
  - id: passive_simple
    name: "Câu bị động (các thì đơn)"
    base: false
  - id: passive_agent
    name: "Câu bị động với by / with / without"
    base: false
  - id: cond_012
    name: "Câu điều kiện loại 0, 1, 2"
    base: false
  - id: past_perf
    name: "Quá khứ hoàn thành"
    base: false
  - id: reported
    name: "Câu tường thuật"
    base: false
units:
  - unit: 1
    unit_name: "My New School"
    sub_topics:
      - name: "School activities and subjects"
        key_grammar: [pron_basic, art]
      - name: "School objects and environment"
        key_grammar: [pron_indef, noun_count, quant]
  - unit: 2
    unit_name: "My House"
    sub_topics:
      - name: "Furniture and household items"
        key_grammar: [num, be_verb]
      - name: "Types of houses and locations"
        key_grammar: [prep_tp, adj_adv]
  - unit: 3
    unit_name: "My Friends"
    sub_topics:
      - name: "Appearance"
        key_grammar: [verb_pattern, adv_place]
      - name: "Personality"
        key_grammar: [verb_pattern, adv_place]
  - unit: 4
    unit_name: "My Neighborhood"
    sub_topics:
      - name: "Places"
        key_grammar: [simple_tenses]
      - name: "Description"
        key_grammar: [simple_tenses]
  - unit: 5
    unit_name: "Natural Wonders"
    sub_topics:
      - name: "Natural features"
        key_grammar: [cont_tenses, going_to]
      - name: "Activities"
        key_grammar: [cont_tenses]
  - unit: 6
    unit_name: "Our Tet Holiday"
    sub_topics:
      - name: "Activities during Tet"
        key_grammar: [pres_perf, past_simple]
      - name: "Traditions and items of Tet"
        key_grammar: [pres_perf, past_simple]
  - unit: 7
    unit_name: "Television"
    sub_topics:
      - name: "Types of TV shows"
        key_grammar: [comp_sup]
      - name: "Watching habit"
        key_grammar: [modal]
  - unit: 8
    unit_name: "Sports and Games"
    sub_topics:
      - name: "Sports and games"
        key_grammar: [passive_simple]
      - name: "Equipment and facilities"
        key_grammar: [passive_agent]
  - unit: 9
    unit_name: "Cities of the World"
    sub_topics:
      - name: "Landmarks and attractions"
        key_grammar: [cond_012]
      - name: "City atmosphere and lifestyle"
        key_grammar: [cond_012]
  - unit: 10
    unit_name: "Our Greener World"
    sub_topics:
      - name: "Sub-topic 1 (syllabus chưa ghi tên)"
        key_grammar: [past_simple, past_perf]
      - name: "Sub-topic 2 (syllabus chưa ghi tên)"
        key_grammar: [reported]
```

## §7. Ghi chú cần giáo viên kiểm lại

- Tiêu đề cột B của sheet ghi *"BOOK MAP GLOBAL SUCCES GRADE 7"* dù đây là sheet khối 6.
- Cột A và cột B là danh sách tham khảo không gắn với unit — đã bỏ qua theo thống nhất.
- Ô ngữ pháp của Unit 1 · School activities and subjects ghi *"+ I, mine, my, this, one)"*, thiếu phần mở ngoặc — hiểu là đại từ nhân xưng, sở hữu, chỉ định và "one".
- **Unit 3 · Personality:** Syllabus ghi *"Review Parts of Speech"* — không phải một cấu trúc đếm được. Tạm dùng lại ngữ pháp Unit 3 · Appearance.
- **Unit 5 · Activities:** Syllabus ghi *"Practice Continuous Tenses"* — chỉ luyện thì tiếp diễn, không có `be going to`.
- **Unit 10 · Sub-topic 1 (syllabus chưa ghi tên):** Ô sub-topic ghi *"PAST SIMPLE VS PAST PERFECT Word list - Lớp 6 - A2.xlsx"* — có vẻ nhầm cột và lạc tên file.
- **Unit 10 · Sub-topic 2 (syllabus chưa ghi tên):** Ô sub-topic ghi *"REPORTED SPEECH Word list - Lớp 6 - A2.xlsx"* — cùng lỗi như sub-topic 1.
