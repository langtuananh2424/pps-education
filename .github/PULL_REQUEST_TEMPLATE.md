## Mô tả
<!-- Tóm tắt thay đổi -->

## UC/FR liên quan
<!-- VD: UC-02, UC-03 (FR-PER-01, FR-PER-02) -->

## Cách test
<!-- Các bước để reviewer tự verify -->

## Checklist
- [ ] `mvn clean verify` chạy xanh ở local
- [ ] Có unit test / integration test cho logic mới
- [ ] Nếu đổi schema: thêm file Flyway `Vn__...sql` mới, KHÔNG sửa file cũ
- [ ] Đã tự kiểm tra trên `docker compose up` local
- [ ] Không commit secret (JWT_SECRET, DB password, API key...)
