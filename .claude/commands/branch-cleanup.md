---
description: Dọn dẹp nhanh các local git branch — hiện checklist, chỉ xoá branch được tick chọn
---

# Dọn dẹp branch

Mục tiêu: giúp người dùng xoá nhanh các local branch không còn cần, an toàn
(không bao giờ tự xoá mà không hỏi trước).

## Các bước thực hiện

1. Chạy `git fetch --prune` để cập nhật trạng thái remote-tracking.
2. Chạy `git branch -vv` để lấy danh sách local branch kèm branch đang track,
   và trạng thái `[gone]` nếu remote đã bị xoá.
3. Xác định branch hiện tại (`git branch --show-current`) — KHÔNG bao giờ đưa
   vào danh sách xoá.
4. Xác định branch đã merge vào `main` và `develop`:
   `git branch --merged main` và `git branch --merged develop`.
5. Loại trừ vĩnh viễn khỏi danh sách đề xuất xoá: `main`, `develop`,
   `production`, branch hiện tại.
6. Lấy email của người dùng hiện tại bằng `git config user.email`. Với mỗi
   branch còn lại, kiểm tra author của commit đầu (tính từ điểm phân nhánh
   với main/develop) hoặc commit gần nhất bằng
   `git log <branch> -1 --format=%ae` (và có thể đối chiếu thêm
   `git log main..<branch> --format=%ae` nếu cần chắc chắn cả nhánh là của
   mình). CHỈ đưa vào danh sách đề xuất xoá (kể cả nhóm "cần xem lại") những
   branch mà toàn bộ commit riêng của nhánh đó là do email này tạo. Branch có
   commit của người khác → loại khỏi checklist, ghi chú rõ "branch của người
   khác — bỏ qua", không hỏi xoá.
7. Phân loại mỗi branch còn lại (đã qua bước lọc tác giả) thành:
   - **An toàn (đề xuất tick sẵn)**: đã merge vào main/develop, HOẶC remote
     tracking bị `[gone]`.
   - **Cần xem lại (không tick sẵn)**: chưa merge, còn remote, hoặc không rõ
     trạng thái — có thể là việc đang làm dở.
8. In ra một checklist dạng Markdown, mỗi dòng gồm: tên branch, trạng thái
   ngắn gọn (vd. "đã merge vào main", "remote gone", "chưa merge, commit gần
   nhất 2026-08-01"), và tick sẵn (`- [x]`) cho nhóm an toàn, để trống
   (`- [ ]`) cho nhóm cần xem lại. Ví dụ:

   ```
   - [x] feature/UC-12-old-thing — đã merge vào main, remote gone
   - [x] chore/tmp-fix — remote gone
   - [ ] feature/UC-30-in-progress — chưa merge, commit gần nhất 3 ngày trước
   ```

9. Hỏi người dùng xác nhận: nói rõ trong câu hỏi rằng xác nhận sẽ xoá CẢ
   branch local LẪN branch tương ứng trên `origin`. Họ có thể trả lời bằng
   cách liệt kê tên branch muốn xoá (mặc định là toàn bộ nhóm đã tick sẵn ở
   bước 8 nếu họ chỉ trả lời "ok"/"đồng ý"), hoặc sửa lại danh sách (bỏ
   bớt/thêm branch). KHÔNG được tự ý xoá bất kỳ branch nào (local hay
   origin) trước khi có xác nhận rõ ràng.
10. Sau khi có xác nhận, với mỗi branch:
    a. Xoá branch local bằng `git branch -d <tên>` (an toàn, báo lỗi nếu
       chưa merge). Nếu branch đó nằm trong nhóm "chưa merge" mà người dùng
       vẫn muốn xoá, hỏi lại riêng branch đó trước khi dùng `git branch -D`
       (force).
    b. Ngay sau khi xoá local thành công, xoá luôn branch cùng tên trên
       origin bằng `git push origin --delete <tên>` (bỏ qua bước này nếu
       branch không tồn tại trên origin, hoặc nếu xoá local ở bước a thất
       bại).
11. In tóm tắt: đã xoá branch nào (ghi rõ cả local và origin), giữ lại
    branch nào, branch nào bị bỏ qua vì là của người khác, còn branch nào
    chưa xử lý.

## Lưu ý an toàn

- Không bao giờ xoá `main`, `develop`, `production`, hoặc branch đang
  checkout — kể cả trên origin.
- Chỉ xoá branch của chính người dùng (author trùng `git config user.email`).
  Không bao giờ đụng tới hoặc đề xuất xoá branch có commit của người khác,
  kể cả khi branch đó đã merge hoặc remote đã gone.
- Không dùng `git branch -D` (force) khi chưa hỏi riêng người dùng cho từng
  trường hợp branch chưa merge.
- Việc xoá branch trên `origin` luôn đi kèm và chỉ thực hiện ngay sau khi
  xoá local thành công, dựa trên CÙNG một lần xác nhận ở bước 9 — không tự
  ý xoá origin nếu người dùng chưa xác nhận, và không xoá origin cho branch
  nào chưa xoá được ở local.
