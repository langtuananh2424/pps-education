# pps_v2_golden.json

Bộ ca kiểm thử khớp số giữa bộ chấm Java (`PpsHybridScorer`) và bộ chấm gốc bằng Python của PPS Speaking Lab
(`pps_hybrid_scorer.score_result`). Mỗi phần tử: `input` (kết quả phân tích một bài), `grade`, `target_wpm`,
`config` (tham số khác mặc định, hoặc null) và `expected` (đầu ra của Python, null nếu không có điểm v2).

File được sinh một lần từ lab (174 ca: bài ngẫu nhiên, 8 cấu hình tham số khác mặc định, ca biên không có GOPT / không có
tiếng nói / Kaldi không gióng được / GOPT thiếu từ) và coi là cố định. Không sửa tay. Sinh lại chỉ khi công thức v2 đổi có chủ đích,
và phải đổi cả hai phía.
