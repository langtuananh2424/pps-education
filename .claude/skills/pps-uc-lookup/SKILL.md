---
name: pps-uc-lookup
description: Tra cứu đặc tả đầy đủ (Precondition/Main Flow/Alternate Flow/Postcondition) của 1 Use Case cụ thể trong dự án PPS Education theo mã UC (VD UC-01, UC-16b, UC-30). Dùng khi cần implement, review, hoặc viết test cho 1 UC, hoặc khi người dùng hỏi "UC-xx làm gì", "luồng của UC-xx".
---

## Cách tra cứu

1. Chạy `grep -rl "^UC-<số>[a-z]\?: " docs/uc/` để tìm đúng file phân hệ
   chứa UC được hỏi (mã UC không trùng giữa các phân hệ nên luôn ra đúng 1
   file).
2. Đọc phần đó trong file (từ dòng `UC-xx: ...` tới dấu `---` tiếp theo hoặc
   hết file).
3. Nếu task là **implement** UC đó:
   - Đối chiếu các bảng CSDL được nhắc tới trong Precondition/Postcondition
     với đúng file trong `docs/sdd-groups/` (dùng `docs/sdd-groups/README.md`
     để biết bảng đó thuộc nhóm nào).
   - Map Main Flow / Alternate Flow thành các nhánh xử lý trong Service
     layer — mỗi luồng thay thế (A1, A2, ...) nên có 1 exception hoặc
     nhánh rẽ tương ứng, đặt tên rõ ràng theo đúng mô tả trong UC.
4. Nếu task là **review/viết test**: dùng Postcondition làm assertion,
   dùng từng Alternate Flow làm 1 test case riêng.

## Lưu ý
- Không diễn giải lại toàn bộ nội dung UC nếu không cần thiết — chỉ trích
  phần liên quan trực tiếp tới việc đang làm.
- Nếu UC không tìm thấy trong `docs/uc/`, kiểm tra lại mã UC với người dùng
  trước khi đoán.
