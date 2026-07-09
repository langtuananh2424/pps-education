# Kiến trúc phân lớp — BẮT BUỘC, không có ngoại lệ

Áp dụng cho toàn bộ code Java trong `pps-education-backend/`. Vi phạm quy
tắc này là lỗi nghiêm trọng cần sửa trước khi merge, không phải "tối ưu sau".

## Hướng phụ thuộc — chỉ 1 chiều

```
Controller  →  Service  →  Repository  →  Database
   ↓              ↓
  DTO         Domain (Entity)
```

- Controller **chỉ** gọi Service, không bao giờ gọi Repository trực tiếp.
- Service **chỉ** gọi Repository (của module mình + module khác nếu cần
  phối hợp nghiệp vụ), không bao giờ inject `EntityManager`/JPA thô để né
  Repository.
- Repository **chỉ** là interface `JpaRepository<T, ID>` + query method —
  không chứa business logic (không if/else nghiệp vụ, không gọi Service).
- Không có chiều ngược: Repository không được biết tới Service/Controller.

## Ranh giới DTO — Controller không bao giờ lộ Entity

- Input của Controller: `record ...Request` trong package `dto/`.
- Output của Controller: `record ...Response` trong package `dto/`.
- Entity (`domain/`) **không bao giờ** xuất hiện trong chữ ký method của
  Controller (không tham số, không kiểu trả về) — kể cả khi "tiện" vì cấu
  trúc giống hệt DTO. Lý do: JPA lazy-loading + JSON serialization của
  Entity trực tiếp gây lỗi khó debug (LazyInitializationException) và rò
  rỉ field nội bộ (password_hash, token_hash...) ra API response.

## Vị trí business logic

- 100% business logic (điều kiện rẽ nhánh, validate, tính toán) nằm ở
  Service — theo đúng Main Flow/Alternate Flow của UC tương ứng (xem
  `.claude/rules/business-fidelity.md`).
- Controller chỉ: nhận request → gọi 1 method Service → map response. Không
  if/else nghiệp vụ trong Controller.

## Transaction

- `@Transactional` đặt ở method Service thực hiện ghi dữ liệu — không đặt ở
  Controller, không đặt ở Repository.
- 1 use case = 1 transaction boundary ở method Service public gọi từ
  Controller (ví dụ UC-34 chuyển đổi lead phải là 1 transaction duy nhất,
  không tách thành nhiều lần gọi Service riêng rồi tự ghép ở Controller).

## Dependency Injection

- Luôn dùng **constructor injection** (constructor tường minh hoặc
  `@RequiredArgsConstructor` của Lombok với field `private final`) — không
  dùng `@Autowired` trên field.

## Package hiện tại (chưa tách theo module)

```
vn.com.pps.education.{config,controller,service,repository,domain,dto,security,exception,common}
```

Giữ nguyên cấu trúc này cho tới khi PM quyết định tách package-by-feature
(dự kiến đánh giá lại khi bắt đầu Backend Phase B — xem tài liệu Kế hoạch
phân kỳ). Không tự ý đổi cấu trúc package giữa chừng.
