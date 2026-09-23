# TỪ ĐIỂN TAGGING KEY GRAMMAR — KHỐI 8 — hệ Cambridge · 2026–2027

## §0. Cấu hình

```text
Mã khối:        g8b1
Nguồn:          SYLLABUS GRADE (26-27).xlsx · sheet "G8 - Cambridge"
Rubric đi kèm:  Writing_Rubric_Grade8_Cambridge_B1_v3.md
Cách tagging:   manual — giáo viên chọn thẳng mã cấu trúc từ từ điển (§3)
Số cấu trúc:    20 (trong đó 4 cấu trúc nền)
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
| `adj_adv` | Tính từ và trạng từ (-ly) |  |
| `comp_all` | So sánh bằng, so sánh hơn, so sánh nhất |  |
| `present_tenses` | Các thì hiện tại (đơn, tiếp diễn, hoàn thành) | ✓ |
| `past_simple` | Quá khứ đơn | ✓ |
| `used_to` | Used to |  |
| `past_cont` | Quá khứ tiếp diễn |  |
| `past_perf` | Quá khứ hoàn thành |  |
| `future_forms` | Các cách diễn đạt tương lai |  |
| `art` | Mạo từ a / an / the (và không dùng mạo từ) | ✓ |
| `determiners` | Từ hạn định và đại từ | ✓ |
| `modal` | Động từ khuyết thiếu |  |
| `reason_purpose` | Từ nối chỉ nguyên nhân, kết quả, mục đích |  |
| `contrast_pairs` | Từ nối tương phản và cặp liên từ |  |
| `ing_form` | Dạng V-ing (danh động từ) |  |
| `to_or_ing` | To V hay V-ing |  |
| `passive_simple` | Câu bị động (các thì đơn) |  |
| `causative` | Have something done |  |
| `result_struct` | so … that, such … that, too … to, enough … to |  |
| `rel_clause` | Mệnh đề quan hệ |  |
| `reported` | Câu tường thuật |  |

*Cấu trúc nền*: gần như bài nào cũng có. Vẫn đếm bình thường nhưng nhận xét không được khen học sinh chỉ vì đã dùng.

### Định nghĩa từng mã

#### `adj_adv` — Tính từ và trạng từ (-ly)

- **Nhận diện:** tính từ đứng trước danh từ hoặc sau `be / look / feel`; trạng từ -ly bổ nghĩa cho động từ.
- **Tính là dùng đúng khi:** tính từ đi với danh từ, trạng từ đi với động từ (sai: `she sings beautiful`).

#### `comp_all` — So sánh bằng, so sánh hơn, so sánh nhất

- **Nhận diện:** `(not) as … as`, `-er than / more … than`, `the -est / the most`.
- **Tính là dùng đúng khi:** đúng dạng tính từ ngắn/dài, đủ `as … as` / `than` / `the`, không dùng kép (sai: `more bigger`).

#### `present_tenses` — Các thì hiện tại (đơn, tiếp diễn, hoàn thành) · **cấu trúc nền**

- **Nhận diện:** `V / V-s`, `am/is/are + V-ing`, `have/has + V3`.
- **Tính là dùng đúng khi:** đúng dạng và đúng chức năng (thói quen / đang diễn ra / kinh nghiệm).

#### `past_simple` — Quá khứ đơn · **cấu trúc nền**

- **Nhận diện:** `V2`, `did / didn't + V`.
- **Tính là dùng đúng khi:** đúng dạng V2 và dùng cho việc đã kết thúc.

#### `used_to` — Used to

- **Nhận diện:** `used to + V`, `didn't use to + V`.
- **Tính là dùng đúng khi:** theo sau là động từ nguyên mẫu; dùng cho thói quen trong quá khứ.

#### `past_cont` — Quá khứ tiếp diễn

- **Nhận diện:** `was/were + V-ing`.
- **Tính là dùng đúng khi:** đủ `was/were`, dùng cho hành động đang diễn ra ở một thời điểm trong quá khứ.

#### `past_perf` — Quá khứ hoàn thành

- **Nhận diện:** `had + V3`.
- **Tính là dùng đúng khi:** đúng V3 và diễn tả việc xảy ra trước một việc khác trong quá khứ.

#### `future_forms` — Các cách diễn đạt tương lai

- **Nhận diện:** `will + V`, `will be + V-ing`, `be going to + V`, hiện tại tiếp diễn chỉ kế hoạch, hiện tại đơn chỉ lịch trình.
- **Tính là dùng đúng khi:** đúng dạng và có mốc tương lai rõ ràng.

#### `art` — Mạo từ a / an / the (và không dùng mạo từ) · **cấu trúc nền**

- **Nhận diện:** `a`, `an`, `the` đứng trước danh từ.
- **Tính là dùng đúng khi:** `a/an` theo âm đầu; `the` cho vật đã xác định; không dùng `a/an` với danh từ không đếm được hoặc số nhiều.

#### `determiners` — Từ hạn định và đại từ · **cấu trúc nền**

- **Nhận diện:** `this/that/these/those, each, every, all, both, some, any, no`, tính từ sở hữu, và đại từ tương ứng.
- **Tính là dùng đúng khi:** đúng số (`each/every` + số ít, `both` + số nhiều).

#### `modal` — Động từ khuyết thiếu

- **Nhận diện:** `can, could, may, might, must, have to, should, ought to, don't have to, don't need to, needn't` + V.
- **Tính là dùng đúng khi:** theo sau là động từ nguyên mẫu không `to` (trừ `have to / ought to`) và đúng chức năng.

#### `reason_purpose` — Từ nối chỉ nguyên nhân, kết quả, mục đích

- **Nhận diện:** `because, because of, as, since, therefore, to, in order to`.
- **Tính là dùng đúng khi:** `because` + mệnh đề, `because of` + cụm danh từ; `to / in order to` + V.

#### `contrast_pairs` — Từ nối tương phản và cặp liên từ

- **Nhận diện:** `but, although, however, in spite of / despite`, `both … and`, `either … or`, `neither … nor`.
- **Tính là dùng đúng khi:** đúng loại theo sau (mệnh đề hay cụm danh từ) và cặp liên từ đủ hai vế.

#### `ing_form` — Dạng V-ing (danh động từ)

- **Nhận diện:** V-ing làm chủ ngữ, tân ngữ, hoặc đứng sau giới từ.
- **Tính là dùng đúng khi:** đúng vị trí và không nhầm với tiếp diễn.

#### `to_or_ing` — To V hay V-ing

- **Nhận diện:** động từ đứng trước quyết định dạng theo sau (`enjoy doing`, `want to do`, `stop doing / stop to do`).
- **Tính là dùng đúng khi:** đúng dạng theo động từ đứng trước.

#### `passive_simple` — Câu bị động (các thì đơn)

- **Nhận diện:** `am/is/are/was/were/will be + V3`.
- **Tính là dùng đúng khi:** đủ `be`, đúng V3, chủ ngữ là đối tượng chịu tác động.

#### `causative` — Have something done

- **Nhận diện:** `have/has/had + tân ngữ + V3`.
- **Tính là dùng đúng khi:** đúng thứ tự và đúng V3.

#### `result_struct` — so … that, such … that, too … to, enough … to

- **Nhận diện:** `so + adj + that`, `such + (a) + adj + N + that`, `too + adj + to V`, `adj + enough + to V`.
- **Tính là dùng đúng khi:** đúng loại từ theo sau và đúng vị trí của `enough`.

#### `rel_clause` — Mệnh đề quan hệ

- **Nhận diện:** `who, which, that, whose, where` (xác định và không xác định).
- **Tính là dùng đúng khi:** đúng đại từ quan hệ cho người/vật/nơi chốn; mệnh đề không xác định có dấu phẩy và không dùng `that`.

#### `reported` — Câu tường thuật

- **Nhận diện:** `said / told … (that)`, `asked if / whether / wh-` + mệnh đề lùi thì.
- **Tính là dùng đúng khi:** lùi thì đúng, đổi đại từ và trạng từ thời gian phù hợp.

## §4. Trình tự tham khảo theo syllabus

Chỉ để giáo viên tham khảo khi chọn mã — **hệ thống không tra bảng này**.

| # | Chủ đề trong sheet | Key grammar | Mã gợi ý |
|---:|---|---|---|
| 1 | Hobbies | Tính từ và trạng từ (-ly) | `adj_adv` |
| 2 | Hobbies | So sánh bằng, so sánh hơn, so sánh nhất | `comp_all` |
| 3 | Healthy Living | Các thì hiện tại (đơn, tiếp diễn, hoàn thành); Quá khứ đơn · *toàn cấu trúc nền* | `present_tenses`, `past_simple` |
| 5 | Community Service | Used to; Quá khứ đơn; Quá khứ tiếp diễn; Quá khứ hoàn thành | `used_to`, `past_simple`, `past_cont`, `past_perf` |
| 7 | Music and Arts | Các cách diễn đạt tương lai | `future_forms` |
| 8 | Music and Arts | Mạo từ a / an / the (và không dùng mạo từ) · *toàn cấu trúc nền* | `art` |
| 9 | Food and Drinks | Từ hạn định và đại từ · *toàn cấu trúc nền* | `determiners` |
| 10 | Food and Drinks | Động từ khuyết thiếu | `modal` |
| 11 | A Visit to School | Từ nối chỉ nguyên nhân, kết quả, mục đích | `reason_purpose` |
| 12 | A Visit to School | Từ nối tương phản và cặp liên từ | `contrast_pairs` |
| 13 | Traffic | Dạng V-ing (danh động từ) | `ing_form` |
| 14 | Traffic | To V hay V-ing | `to_or_ing` |
| 15 | Films | Câu bị động (các thì đơn); Have something done | `passive_simple`, `causative` |
| 16 | Films | so … that, such … that, too … to, enough … to | `result_struct` |
| 17 | Festivals Around the World | Mệnh đề quan hệ | `rel_clause` |
| 18 | Festivals Around the World | Câu tường thuật | `reported` |

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
dictionary: "KeyGrammar_Grade8_Cambridge.md"
grade_code: g8b1
rubric: "Writing_Rubric_Grade8_Cambridge_B1_v3.md"
tagging: manual
pass_rule:
  count: correct_uses          # chỉ đếm lần dùng ĐÚNG
  scope: assigned_total        # cộng dồn mọi cấu trúc được giao
  pass_if_at_least: 2          # < 2 là Chưa đạt, phải viết lại bài
  ratio: correct / (correct + incorrect)
  grammar_cap: {0: 40, 1: 50}  # trần % tiêu chí ngữ pháp khi Chưa đạt
structures:
  - id: adj_adv
    name: "Tính từ và trạng từ (-ly)"
    base: false
  - id: comp_all
    name: "So sánh bằng, so sánh hơn, so sánh nhất"
    base: false
  - id: present_tenses
    name: "Các thì hiện tại (đơn, tiếp diễn, hoàn thành)"
    base: true
  - id: past_simple
    name: "Quá khứ đơn"
    base: true
  - id: used_to
    name: "Used to"
    base: false
  - id: past_cont
    name: "Quá khứ tiếp diễn"
    base: false
  - id: past_perf
    name: "Quá khứ hoàn thành"
    base: false
  - id: future_forms
    name: "Các cách diễn đạt tương lai"
    base: false
  - id: art
    name: "Mạo từ a / an / the (và không dùng mạo từ)"
    base: true
  - id: determiners
    name: "Từ hạn định và đại từ"
    base: true
  - id: modal
    name: "Động từ khuyết thiếu"
    base: false
  - id: reason_purpose
    name: "Từ nối chỉ nguyên nhân, kết quả, mục đích"
    base: false
  - id: contrast_pairs
    name: "Từ nối tương phản và cặp liên từ"
    base: false
  - id: ing_form
    name: "Dạng V-ing (danh động từ)"
    base: false
  - id: to_or_ing
    name: "To V hay V-ing"
    base: false
  - id: passive_simple
    name: "Câu bị động (các thì đơn)"
    base: false
  - id: causative
    name: "Have something done"
    base: false
  - id: result_struct
    name: "so … that, such … that, too … to, enough … to"
    base: false
  - id: rel_clause
    name: "Mệnh đề quan hệ"
    base: false
  - id: reported
    name: "Câu tường thuật"
    base: false
```

## §7. Ghi chú cần giáo viên kiểm lại

- **Vì sao không dùng Unit / Sub-topic:** sheet G8 Cambridge có ghi Unit và Sub-topic, nhưng tên Unit trùng hoàn toàn với sách Global Success lớp 7 (Hobbies, Healthy Living, Community Service…) — nhiều khả năng chép nhầm từ sheet khối 7. Theo xác nhận của giáo viên, khối 8 không tagging theo Unit.
- Cột J của sheet ghi các dạng Reading của IELTS (Short answer, Flow chart…) dù đây là hệ Cambridge — đã bỏ qua.
- Dòng 7 của §4 ghi *"future continuos"* — lỗi gõ của *future continuous*.
- Cột A và cột B là danh sách tham khảo không gắn với unit — đã bỏ qua.
- **Dòng 1 của §4:** Syllabus ghi *"Simple tenses: Adj and Adv"* — giáo viên đã xác nhận là **Tính từ và Trạng từ**.
- **Dòng 12 của §4:** Syllabus ghi *"both... end"* — hiểu là `both … and`.
