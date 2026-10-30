# pps_v2_golden.json

Bộ ca kiểm thử khớp số giữa bộ chấm Java (`PpsHybridScorer`) và bộ chấm gốc bằng Python của PPS Speaking Lab
(`pps_hybrid_scorer.score_result`). Mỗi phần tử: `input` (kết quả phân tích một bài), `grade`, `target_wpm`,
`config` (tham số khác mặc định, hoặc null) và `expected` (đầu ra của Python, null nếu không có điểm v2).

File được sinh một lần từ lab (174 ca: bài ngẫu nhiên, 8 cấu hình tham số khác mặc định, ca biên không có GOPT / không có
tiếng nói / Kaldi không gióng được / GOPT thiếu từ) và coi là cố định. Không sửa tay. Sinh lại chỉ khi công thức v2 đổi có chủ đích,
và phải đổi cả hai phía.

# target_merge_golden.json

Cùng cách làm cho `TargetMerger` (port của `target_merge.py`): 270 ca ghép bài viết với lời nói, các ca số thành chữ
(`words_of`) và các ca từ chèn có/không giống tiếng Anh (`plausible`). Khác lab ở một chỗ duy nhất, có chủ ý: số có nhiều
dấu chấm như "1.2.3" làm bản Python lỗi, bản Java bỏ dấu chấm thừa; golden không chứa ca này.
