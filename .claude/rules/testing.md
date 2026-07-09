---
paths:
  - "pps-education-backend/src/test/java/**/*.java"
---

# Quy ước test — mỗi luồng trong UC là 1 test case

## Đặt tên test theo UC/luồng

Method test đặt tên theo mẫu `<method>_<UCxx>_<luồng>_<kỳ vọng>`, ví dụ với
UC-01:

```java
@Test void login_UC01_MainFlow_returnsTokensOnValidCredentials() { ... }
@Test void login_UC01_A1_rejectsWrongPassword() { ... }
@Test void login_UC01_A2_locksAccountAfter5FailedAttempts() { ... }
@Test void login_UC01_A3_rejectsInactiveAccount() { ... }
```

## Bắt buộc phủ đủ

- **Main Flow**: ít nhất 1 test case happy path.
- **Mỗi Alternate Flow (A1, A2, ...)** trong file `docs/uc/phan-he-NN-*.md`
  của UC đó: ít nhất 1 test case riêng — không gộp nhiều luồng vào 1 test
  bằng if/else trong test.
- **Postcondition**: assertion trong test phải kiểm tra đúng những gì
  Postcondition liệt kê (không chỉ kiểm tra "không throw exception").

## Không dùng H2 cho integration test

Dùng Testcontainers với image `postgis/postgis:16-3.4` (khớp môi trường
CI/production) cho mọi test chạm DB thật — H2 không mô phỏng đúng
`GEOGRAPHY`, `JSONB`, hay các ràng buộc UNIQUE partial index đã dùng trong
migration (xem `V2__facility_core.sql`, `V3__hr_core.sql`). H2 chỉ chấp
nhận được cho unit test thuần Service logic không chạm DB (dùng mock
Repository).

## Trước khi mở PR

Chạy `mvn clean verify`, xác nhận:
- Mọi Alternate Flow của UC vừa implement có test tương ứng.
- Không giảm coverage của module đang sửa so với trước (không bắt buộc %
  cụ thể ở giai đoạn này, nhưng không được xóa test cũ để "cho xanh").
