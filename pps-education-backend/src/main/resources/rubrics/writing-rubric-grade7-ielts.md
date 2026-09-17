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
| **G3 – Copied input** | Có chuỗi trùng ≥5 từ liên tiếp với đề/nguồn | Loại khỏi `N_net`; không tính làm bằng chứng LR/Language/GRA |
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
| **CC2** | **Paragraphing / grouping** | Hợp lý với độ dài và task | Tạm được | Không có tổ chức rõ |
| **CC3** | **Linking** | ≥3 correct links, ≥2 types | 1–2 | 0 / mostly wrong |
| **CC4** | **Reference / pronouns** | ≥2 correct cohesive uses | 1 | 0 / reference often unclear |
| **CC5** | **Repetition control** | 0–1 unnecessary repeated idea | 2 | ≥3 / ảnh hưởng coherence |

### LEXICAL RESOURCE — LR

| ID | Checkpoint | **1** | **0.5** | **0** |
|---|---|---|---|---|
| **L1** | **Vocabulary sufficiency** | Đủ từ để diễn đạt mọi ý chính | Thiếu từ ở 1–2 chỗ nhưng ý vẫn hiểu | Thiếu từ làm ≥2 ý không rõ |
| **L2** | **Word choice** | `N_lex` 0–2 | 3–4 | ≥5 / repeated meaning loss |
| **L3** | **Variety / repetition** | Có variation; basic content words không lặp nặng | Một ít variation, còn lặp | Rất hạn chế / copy-heavy |
| **L4** | **Spelling / word formation** | ≤3 lỗi / 50 từ | 4–6 | ≥7 / cản meaning |
| **L5** | **Paraphrase / flexibility** | Có ≥1 cách diễn đạt lại phù hợp | Có attempt nhưng gượng / n.a. | Cần paraphrase nhưng chỉ copy/lặp |

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
| Criterion | % |
|---|---:|
| TR/TA | |
| CC | |
| LR | |
| GRA | |
| **Final** | **__%** |

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

