---
description: Kiểm tra chênh lệch commit giữa main và develop, đề xuất sync qua PR (không bao giờ push thẳng vào 2 nhánh này)
---

# Sync main ↔ develop

Mục tiêu: phát hiện commit bị lệch giữa `main` và `develop`, và đồng bộ lại
bằng PR — tuân thủ `CONTRIBUTING.md` (mục 1): `main`/`develop` bị chặn push
trực tiếp, chỉ nhận merge qua PR + CI xanh.

Bối cảnh vai trò 2 nhánh (xem `CONTRIBUTING.md` mục 1):
- `develop` → `main`: luồng bình thường, đưa tính năng đã xong lên staging
  (PR `develop → main`, mục 3 của CONTRIBUTING.md).
- `main` → `develop`: xảy ra khi `main` có commit mà `develop` chưa có —
  do `hotfix/*` merge thẳng vào `production` + `main` không qua `develop`,
  HOẶC do có dev khác merge/push thẳng vào `main` để tiện triển khai gấp mà
  bỏ qua `develop`. Cần backport lại vào `develop` để tránh mất các thay
  đổi đó khi `develop` lên staging lần sau (PR develop→main sẽ ghi đè/bỏ
  sót nếu develop không có).

⚠️ Nhiễu cần lọc: khi PR `develop → main` được merge, GitHub tự tạo 1 merge
commit chỉ tồn tại trên `main` (vd. "Merge pull request #423 from
.../develop"). Đây KHÔNG phải commit bị lệch thật — nó chỉ là merge commit
rỗng, nội dung bên trong vốn đã có sẵn trên `develop` từ trước khi merge.
Phải lọc bỏ loại này khỏi danh sách "cần backport", chỉ giữ lại commit có
nội dung thật (commit thường, hoặc merge commit mang theo nhánh riêng như
`feature/*`/`hotfix/*` merge thẳng vào `main`).

## Các bước thực hiện

1. Chạy `git fetch origin` để cập nhật `origin/main`, `origin/develop` mới
   nhất. Dùng `origin/main`, `origin/develop` cho toàn bộ so sánh bên dưới
   (không dùng nhánh local có thể đã cũ).
2. Lấy danh sách thô: `git log origin/develop..origin/main --oneline` —
   toàn bộ commit có trong `main` nhưng CHƯA có trong `develop` (bao gồm cả
   merge commit rỗng lẫn commit nội dung thật).
3. Lọc nhiễu khỏi danh sách bước 2: chạy thêm
   `git log origin/develop..origin/main --no-merges --oneline` (commit
   thường, không phải merge — chắc chắn là nội dung thật cần backport).
   Với các merge commit bị loại ra ở `--no-merges`, kiểm tra từng cái bằng
   `git log -1 --format=%P <hash>` rồi `git merge-base --is-ancestor
   <second-parent> origin/develop` — nếu second-parent ĐÃ là ancestor của
   `origin/develop` (đúng là merge commit rỗng dạng "Merge PR từ develop")
   thì bỏ qua, không tính là lệch; nếu KHÔNG phải ancestor của develop (vd.
   merge thẳng 1 nhánh `feature/*`/`hotfix/*` lạ vào main) thì vẫn giữ lại
   trong danh sách "cần backport" vì nó mang nội dung mới.
4. Chạy `git log origin/main..origin/develop --oneline` — danh sách commit
   có trong `develop` nhưng CHƯA có trong `main` (tính năng đang chờ lên
   staging — đây là trạng thái bình thường, không phải lỗi, không cần lọc).
5. In báo cáo rõ ràng gồm 2 phần:
   - **main → develop (cần backport)**: liệt kê các commit còn lại sau khi
     lọc ở bước 3 (nội dung thật), kèm hash ngắn + subject + tên tác giả
     (`git log -1 --format=%an <hash>`, vì có thể là dev khác chứ không chỉ
     mình bạn). Nếu có merge commit bị lọc bỏ vì là noise, ghi thêm 1 dòng
     phụ "(đã lọc bỏ N merge commit rỗng do PR develop→main trước đó)" để
     người dùng biết đã lọc, không phải bỏ sót. Nếu danh sách sau lọc rỗng:
     ghi "Không có commit nội dung nào bị lệch — develop đã có đầy đủ."
   - **develop → main (đang chờ lên staging)**: liệt kê commit từ bước 4,
     chỉ mang tính thông tin. Nếu rỗng: ghi "main đã cập nhật đầy đủ so với
     develop."
6. Nếu sau khi lọc ở bước 3 vẫn CÓ commit nội dung thật cần backport: hỏi
   người dùng có muốn tạo nhánh sync và mở PR để backport không. KHÔNG tự ý
   thực hiện khi chưa có xác nhận.
7. Nếu người dùng xác nhận backport main → develop:
   a. `git checkout -b chore/sync-main-to-develop-<YYYYMMDD> origin/develop`
   b. `git merge origin/main` — nếu conflict: DỪNG lại, liệt kê rõ file
      conflict, KHÔNG tự ý resolve, để người dùng xử lý thủ công rồi chạy
      lại từ bước push.
   c. Nếu merge sạch: `git push -u origin <tên nhánh sync>`.
   d. Mở PR bằng `gh pr create --base develop --head <tên nhánh sync>
      --title "chore: sync main vào develop <YYYY-MM-DD>"` kèm mô tả ngắn
      liệt kê các commit nội dung thật đã backport (bỏ qua liệt kê merge
      commit rỗng). KHÔNG tự merge PR — để CI chạy và người review/người
      dùng tự bấm merge trên GitHub.
8. Nếu người dùng muốn đẩy `develop` lên `main` (staging) ngay: nhắc rằng
   đây là hành động release thật (mục 3 CONTRIBUTING.md, `main` auto-deploy
   lên server staging thật khi PR được merge) — chỉ mở PR `develop → main`
   khi người dùng xác nhận rõ ràng muốn release, dùng
   `gh pr create --base main --head develop --title "release: đưa develop lên staging <YYYY-MM-DD>"`.
   KHÔNG tự merge PR này trong bất kỳ trường hợp nào.
9. Sau khi mở PR (ở bước 7 hoặc 8), in ra link PR và tóm tắt: đã mở PR
   hướng nào, còn thao tác gì người dùng cần tự làm (review, merge trên
   GitHub, theo dõi CI/CD).

## Lưu ý an toàn

- KHÔNG BAO GIỜ `git push` trực tiếp vào `main` hoặc `develop` — 2 nhánh
  này bị chặn push trực tiếp theo `CONTRIBUTING.md`. Mọi thay đổi phải qua
  nhánh trung gian (`chore/sync-...`) + PR.
- KHÔNG BAO GIỜ tự merge PR đã mở (dù backport hay release) — merge lên
  `main` kích hoạt auto-deploy lên server staging thật, phải để người dùng
  tự quyết định thời điểm merge.
- Nếu merge conflict khi backport, dừng ngay và để người dùng tự xử lý —
  không tự ý ưu tiên bên nào hay bỏ qua conflict.
- Không đụng tới `production` trong command này — release lên `production`
  là quy trình riêng (mục 4 CONTRIBUTING.md), không nằm trong phạm vi sync
  main/develop.
