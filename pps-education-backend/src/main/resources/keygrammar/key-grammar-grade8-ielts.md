# TỪ ĐIỂN TAGGING KEY GRAMMAR — KHỐI 8 — hệ IELTS · 2026–2027

## §0. Cấu hình

```text
Mã khối:        g8
Nguồn:          SYLLABUS GRADE (26-27).xlsx · sheet "G8 - IELTS (advanced) + G8 - IELTS (Standard)"
Rubric đi kèm:  Writing_Rubric_Grade8_IELTS_Foundation_v3.md
Cách tagging:   manual — giáo viên chọn thẳng mã cấu trúc từ từ điển (§3)
Số cấu trúc:    20 (trong đó 6 cấu trúc nền)
Vai trò:        Filter 2 — chạy SAU khi đã chấm xong theo rubric khối
Ảnh hưởng điểm: CHỈ KHI CHƯA ĐẠT — tiêu chí ngữ pháp trần 40% (0 lần) hoặc 50% (1 lần).
Kết quả:        Đạt / Chưa đạt, kèm tỉ lệ dùng đúng, ở một phần nhận xét riêng. Chưa đạt → viết lại bài.
```

Key grammar **gắn vào lượt giao bài**, không gắn vào đề và không gắn vào tuần.
Đề bài giữ nguyên; lượt giao bài mang thêm trường `key_grammar.ids` (xem `00_DAC_TA_GIAO_NHAN.md`).
Lượt giao bài không có `key_grammar` → **bỏ qua filter 2**, không in phần key grammar.

## §1. Tagging khi giao bài

1. Khối này **không dùng Unit / Sub-topic** để tagging. Giáo viên chọn thẳng **1–3 mã** từ §3.
2. Bảng §4 là **trình tự tham khảo** lấy từ syllabus, giúp giáo viên biết đang học đến cấu trúc nào. Hệ thống **không** tra bảng này tự động.
3. Không muốn kiểm key grammar cho lượt này → không chọn mã nào.

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
| `passive_sc` | Câu bị động (thì đơn và thì tiếp diễn) |  |
| `sent_struct` | Cấu trúc câu cơ bản | ✓ |
| `art` | Mạo từ a / an / the (và không dùng mạo từ) | ✓ |
| `rel_clause` | Mệnh đề quan hệ |  |
| `determiners` | Từ hạn định và đại từ | ✓ |
| `linking` | Từ nối và liên kết câu |  |
| `modal` | Động từ khuyết thiếu |  |
| `comp_all` | So sánh bằng, so sánh hơn, so sánh nhất |  |
| `perfect` | Các thì hoàn thành |  |
| `verb_tenses` | Các thì động từ (ôn tập chung) | ✓ |
| `perfect_cont` | Các thì hoàn thành tiếp diễn |  |
| `noun_phrase` | Cụm danh từ |  |
| `dummy_subject` | Chủ ngữ giả (It / There) |  |
| `gerund_subject` | V-ing làm chủ ngữ |  |
| `cond_12` | Câu điều kiện loại 1 và 2 |  |
| `modal_adv` | Động từ khuyết thiếu nâng cao |  |
| `comp_adv` | So sánh nâng cao |  |
| `sent_struct_adv` | Cấu trúc câu nâng cao |  |
| `integration` | Tích hợp ngữ pháp (mọi cấu trúc đã học) | ✓ |

*Cấu trúc nền*: gần như bài nào cũng có. Vẫn đếm bình thường nhưng nhận xét không được khen học sinh chỉ vì đã dùng.

### Định nghĩa từng mã

#### `simple_tenses` — Các thì đơn: hiện tại đơn, quá khứ đơn, tương lai đơn · **cấu trúc nền**

- **Nhận diện:** `V / V-s`, `V2 / did`, `will + V`.
- **Tính là dùng đúng khi:** đúng dạng và đúng mốc thời gian của ngữ cảnh.

#### `passive_sc` — Câu bị động (thì đơn và thì tiếp diễn)

- **Nhận diện:** `am/is/are/was/were + V3`, `am/is/are/was/were being + V3`, `will be + V3`.
- **Tính là dùng đúng khi:** đủ `be` (và `being` với thì tiếp diễn), đúng V3.

#### `sent_struct` — Cấu trúc câu cơ bản · **cấu trúc nền**

- **Nhận diện:** câu đơn đủ thành phần (S+V, S+V+O, S+V+C) và câu ghép nối bằng `and / but / so / or`.
- **Tính là dùng đúng khi:** đủ chủ ngữ và động từ chia; câu ghép có liên từ và dấu câu phù hợp.

#### `art` — Mạo từ a / an / the (và không dùng mạo từ) · **cấu trúc nền**

- **Nhận diện:** `a`, `an`, `the` đứng trước danh từ.
- **Tính là dùng đúng khi:** `a/an` theo âm đầu; `the` cho vật đã xác định; không dùng `a/an` với danh từ không đếm được hoặc số nhiều.

#### `rel_clause` — Mệnh đề quan hệ

- **Nhận diện:** `who, which, that, whose, where` (xác định và không xác định).
- **Tính là dùng đúng khi:** đúng đại từ quan hệ cho người/vật/nơi chốn; mệnh đề không xác định có dấu phẩy và không dùng `that`.

#### `determiners` — Từ hạn định và đại từ · **cấu trúc nền**

- **Nhận diện:** `this/that/these/those, each, every, all, both, some, any, no`, tính từ sở hữu, và đại từ tương ứng.
- **Tính là dùng đúng khi:** đúng số (`each/every` + số ít, `both` + số nhiều).

#### `linking` — Từ nối và liên kết câu

- **Nhận diện:** `and, but, so, because, however, therefore, firstly, in addition, for example, as a result`…
- **Tính là dùng đúng khi:** đúng chức năng (bổ sung / tương phản / nguyên nhân / kết quả / ví dụ) và đúng dấu câu.

#### `modal` — Động từ khuyết thiếu

- **Nhận diện:** `can, could, may, might, must, have to, should, ought to, don't have to, don't need to, needn't` + V.
- **Tính là dùng đúng khi:** theo sau là động từ nguyên mẫu không `to` (trừ `have to / ought to`) và đúng chức năng.

#### `comp_all` — So sánh bằng, so sánh hơn, so sánh nhất

- **Nhận diện:** `(not) as … as`, `-er than / more … than`, `the -est / the most`.
- **Tính là dùng đúng khi:** đúng dạng tính từ ngắn/dài, đủ `as … as` / `than` / `the`, không dùng kép (sai: `more bigger`).

#### `perfect` — Các thì hoàn thành

- **Nhận diện:** `have/has + V3`, `had + V3`, `will have + V3`.
- **Tính là dùng đúng khi:** đúng V3, đúng trợ động từ, không đi với mốc quá khứ xác định ở hiện tại hoàn thành.

#### `verb_tenses` — Các thì động từ (ôn tập chung) · **cấu trúc nền**

- **Nhận diện:** mọi động từ chính chia theo thì.
- **Tính là dùng đúng khi:** đúng dạng và đúng mốc thời gian của ngữ cảnh.

#### `perfect_cont` — Các thì hoàn thành tiếp diễn

- **Nhận diện:** `have/has been + V-ing`, `had been + V-ing`.
- **Tính là dùng đúng khi:** đủ `been` và V-ing, diễn tả hành động kéo dài đến một mốc.

#### `noun_phrase` — Cụm danh từ

- **Nhận diện:** danh từ có bổ ngữ trước và/hoặc sau: `the rapid growth of online shopping`, `a small village near the coast`.
- **Tính là dùng đúng khi:** đúng trật tự từ hạn định → tính từ → danh từ → cụm giới từ / mệnh đề.

#### `dummy_subject` — Chủ ngữ giả (It / There)

- **Nhận diện:** `It is + adj + to V / that …`, `There is / There are …`.
- **Tính là dùng đúng khi:** `There is` + số ít, `There are` + số nhiều; `It is … to V` đủ thành phần.

#### `gerund_subject` — V-ing làm chủ ngữ

- **Nhận diện:** câu bắt đầu bằng V-ing làm chủ ngữ: `Reading books improves vocabulary.`
- **Tính là dùng đúng khi:** động từ chính chia số ít.

#### `cond_12` — Câu điều kiện loại 1 và 2

- **Nhận diện:** `If + hiện tại, will + V` · `If + quá khứ, would + V`.
- **Tính là dùng đúng khi:** hai vế khớp đúng loại.

#### `modal_adv` — Động từ khuyết thiếu nâng cao

- **Nhận diện:** `should have / might have / must have / could have + V3`, `be able to`, `needn't have`…
- **Tính là dùng đúng khi:** đúng dạng `have + V3` sau modal và đúng chức năng (suy đoán, trách nhẹ, khả năng trong quá khứ).

#### `comp_adv` — So sánh nâng cao

- **Nhận diện:** `the more …, the more …`, `-er and -er`, `much / far + so sánh hơn`, `twice as … as`.
- **Tính là dùng đúng khi:** đúng cấu trúc hai vế và đúng dạng tính từ.

#### `sent_struct_adv` — Cấu trúc câu nâng cao

- **Nhận diện:** câu phức có mệnh đề phụ (`because / when / if / although / while`…), mệnh đề quan hệ, cụm phân từ.
- **Tính là dùng đúng khi:** mệnh đề phụ đủ chủ ngữ và động từ, gắn đúng với mệnh đề chính.

#### `integration` — Tích hợp ngữ pháp (mọi cấu trúc đã học) · **cấu trúc nền**

- **Nhận diện:** bất kỳ cấu trúc nào đã học trong năm.
- **Tính là dùng đúng khi:** đúng dạng và đúng chức năng.

## §4. Trình tự tham khảo theo syllabus

Chỉ để giáo viên tham khảo khi chọn mã — **hệ thống không tra bảng này**.

| # | Chủ đề | Nhóm khá học | Nhóm cơ bản học | Mã của cả hai nhóm — chọn phần đúng nhóm lớp |
|---:|---|---|---|---|
| 1 | Holidays, Travel & Tourism | Simple tenses | Simple tenses | `simple_tenses` |
| 2 | Holidays, Travel & Tourism | Review (dòng 1) | Review (dòng 1) | `simple_tenses` |
| 3 | Places and Buildings | Passive: simple & continuous | Sentence structures (cơ bản) | `passive_sc`, `sent_struct` |
| 4 | Places and Buildings | Review (dòng 3) | Sentence structures + Articles | `passive_sc`, `sent_struct`, `art` |
| 5 | Education and Employment | Relative clauses | Determiners | `rel_clause`, `determiners` |
| 6 | Education and Employment | Review (dòng 5) | Review (dòng 5) | `rel_clause`, `determiners` |
| 7 | Food | Linking words | Modal verbs (cơ bản) | `linking`, `modal` |
| 8 | Food | Review (dòng 7) * | Modal verbs + Cohesion | `linking`, `modal` |
| 9 | Consumerism | Comparison | Linking devices & Cohesion (cơ bản) | `comp_all`, `linking` |
| 10 | Consumerism | Review (dòng 9) | Review tổng hợp HK1 * | `comp_all`, `linking` |
| 11 | (syllabus không ghi tên) | Perfect tenses | Review thì động từ + Articles | `perfect`, `verb_tenses`, `art` |
| 12 | (syllabus không ghi tên) | Review (dòng 11) | Review Determiners | `perfect`, `determiners` |
| 13 | (syllabus không ghi tên) | Perfect continuous | Passive (gồm thì tiếp diễn) | `perfect_cont`, `passive_sc` |
| 14 | (syllabus không ghi tên) | Review Perfect tenses * | Review (dòng 13) | `perfect`, `perfect_cont`, `passive_sc` |
| 15 | (syllabus không ghi tên) | Noun phrases | Relative clauses | `noun_phrase`, `rel_clause` |
| 16 | (syllabus không ghi tên) | Review (dòng 15) | Review (dòng 15) | `noun_phrase`, `rel_clause` |
| 17 | (syllabus không ghi tên) | Dummy subject + Gerund subject | Conditionals type 1–2 | `dummy_subject`, `gerund_subject`, `cond_12` |
| 18 | (syllabus không ghi tên) | Review (dòng 17) | Modal nâng cao + Linking (trung cấp) | `dummy_subject`, `gerund_subject`, `modal_adv`, `linking` |
| 19 | (syllabus không ghi tên) | Advanced comparison * | Advanced sentence structures | `comp_adv`, `sent_struct_adv` |
| 20 | (syllabus không ghi tên) | Review (dòng 19) | Advanced sentence structures | `comp_adv`, `sent_struct_adv` |
| 21 | (syllabus không ghi tên) | Grammar integration | Grammar integration | `integration` |
| 22 | (syllabus không ghi tên) | Review (dòng 21) | Grammar integration | `integration` |

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
dictionary: "KeyGrammar_Grade8_IELTS.md"
grade_code: g8
rubric: "Writing_Rubric_Grade8_IELTS_Foundation_v3.md"
tagging: manual
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
  - id: passive_sc
    name: "Câu bị động (thì đơn và thì tiếp diễn)"
    base: false
  - id: sent_struct
    name: "Cấu trúc câu cơ bản"
    base: true
  - id: art
    name: "Mạo từ a / an / the (và không dùng mạo từ)"
    base: true
  - id: rel_clause
    name: "Mệnh đề quan hệ"
    base: false
  - id: determiners
    name: "Từ hạn định và đại từ"
    base: true
  - id: linking
    name: "Từ nối và liên kết câu"
    base: false
  - id: modal
    name: "Động từ khuyết thiếu"
    base: false
  - id: comp_all
    name: "So sánh bằng, so sánh hơn, so sánh nhất"
    base: false
  - id: perfect
    name: "Các thì hoàn thành"
    base: false
  - id: verb_tenses
    name: "Các thì động từ (ôn tập chung)"
    base: true
  - id: perfect_cont
    name: "Các thì hoàn thành tiếp diễn"
    base: false
  - id: noun_phrase
    name: "Cụm danh từ"
    base: false
  - id: dummy_subject
    name: "Chủ ngữ giả (It / There)"
    base: false
  - id: gerund_subject
    name: "V-ing làm chủ ngữ"
    base: false
  - id: cond_12
    name: "Câu điều kiện loại 1 và 2"
    base: false
  - id: modal_adv
    name: "Động từ khuyết thiếu nâng cao"
    base: false
  - id: comp_adv
    name: "So sánh nâng cao"
    base: false
  - id: sent_struct_adv
    name: "Cấu trúc câu nâng cao"
    base: false
  - id: integration
    name: "Tích hợp ngữ pháp (mọi cấu trúc đã học)"
    base: true
```

## §7. Ghi chú cần giáo viên kiểm lại

- **Không còn gộp hai nhóm:** trước đây key grammar mỗi tuần là ngữ pháp nhóm khá cộng nhóm cơ bản. Giờ giáo viên chọn mã theo **đúng nhóm của lớp** khi giao bài, nên học sinh không thể đạt nhờ cấu trúc của nhóm kia. Cột mã ở §4 ghi chung cả hai nhóm, giáo viên chỉ lấy phần của nhóm lớp mình.
- Hai sheet **không ghi tên unit** cho học kỳ 2 (tuần 14–28), chỉ ghi "Unit 6", "Unit 7"…
- Hai sheet có thêm danh sách từ vựng (dòng 80–109) — không liên quan, đã bỏ qua.
- "Sentence structures" và "Grammar integration" là mục rộng, khó đếm. Định nghĩa vận hành đang dùng nằm ở §3; nên xem lại.
- **Dòng 8 của §4:** Nhóm khá chỉ ghi *"Vocabular: Review"*, không nói ngữ pháp — tạm dùng lại dòng 7 của §4.
- **Dòng 10 của §4:** Nhóm cơ bản ghi *"Review tổng hợp ngữ pháp HK1"* — tạm dùng lại dòng 9 của §4 để giữ ngưỡng có ý nghĩa; nếu lấy cả HK1 thì bài nào cũng đạt.
- **Dòng 14 của §4:** Nhóm khá ghi *"Review Perfect Tenses"* — hiểu là ôn cả thì hoàn thành (dòng 11 của §4) và hoàn thành tiếp diễn (dòng 13 của §4).
- **Dòng 19 của §4:** Nhóm khá ghi thêm *"Grammar Integration"* — đã bỏ phần này vì không đếm được; chỉ giữ So sánh nâng cao.
- **Dòng 22 của §4:** Dòng 21–22 cả hai nhóm chỉ ghi *"Grammar integration"* — không có cấu trúc riêng. Nếu giao bài chỉ với mã `integration` thì bài nào có ≥2 câu đúng ngữ pháp cũng **Đạt**: **không nên** dùng `integration` làm mã duy nhất; hãy chọn 1–2 cấu trúc cụ thể đã học.
