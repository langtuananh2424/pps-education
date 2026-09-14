-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-09-14 -- UC-73
-- (Import Excel hang loat "bo" video on tap, mirror UC-72 Kho de). Import
-- can tra cuu review_video_sets theo code de idempotent (nhieu dong Excel
-- cung Ma bo = nhieu video trong CUNG 1 bo, import lai file sau nay phai
-- tim lai dung bo cu, khong tao trung) -- can UNIQUE(code) de tra cuu on
-- dinh, mirror exams.code/exercises.code (da UNIQUE tu truoc). Truoc gio
-- code khong UNIQUE vi tao tay tung Bo mot, chua co nhu cau tra cuu lai
-- theo code.
--
-- SUA 2026-09-14 (phat hien qua deploy that bai tren staging/production —
-- du lieu that da co san nhieu Bo trung code tu truoc, VD tao tay
-- "G7-CAM-ADV-C1-U1-SUB1-TKN" nhieu lan khi chua co rang buoc unique;
-- local/dev moi tao chua gap truong hop nay nen ALTER TABLE truoc do chay
-- qua o local). Sua truc tiep V175 (chua tung ap dung thanh cong o bat ky
-- moi truong da chia se nao — chi that bai va rollback tren
-- staging/production), KHONG tao migration V176 rieng, vi Flyway chay
-- migration theo thu tu tang dan: neu tach rieng buoc don rac ra V176,
-- Flyway van co gang chay V175 (ALTER TABLE) TRUOC va that bai y het, V176
-- khong bao gio toi luot chay duoc.
--
-- Buoc 1: don du lieu trung code that su dang co — giu nguyen Bo co id NHO
-- NHAT (tao truoc), doi code cac Bo trung sau thanh "<code>-DUPn" (KHONG
-- xoa Bo nao, khong mat du lieu) — Giao vien tu vao Kho Video doi lai code
-- cho dung sau nay neu can.
WITH duplicates AS (
    SELECT id, code,
           ROW_NUMBER() OVER (PARTITION BY code ORDER BY id) AS rn
    FROM review_video_sets
)
UPDATE review_video_sets rvs
SET code = rvs.code || '-DUP' || duplicates.rn
FROM duplicates
WHERE rvs.id = duplicates.id AND duplicates.rn > 1;

-- Buoc 2: dat rang buoc UNIQUE nhu thiet ke ban dau.
ALTER TABLE review_video_sets
    ADD CONSTRAINT uq_review_video_sets_code UNIQUE (code);
