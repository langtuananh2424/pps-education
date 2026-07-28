-- =====================================================================
-- V48: ANH DAI DIEN (employees/parents) VA ANH BIA TAI LIEU THAM KHAO
-- (curriculum_documents). BO SUNG NGOAI SDD GOC - da xac nhan voi
-- nguoi dung (2026-07-23).
--
-- students.portrait_url da co san tu V11 (mau tham chieu). Bo sung
-- employees va parents cho dong bo - anh dai dien upload that qua
-- POST /api/media/upload (MediaStorageService/MediaModule, luu tren
-- Cloudflare R2), khong phai field nhap tay URL.
-- =====================================================================

ALTER TABLE employees ADD COLUMN portrait_url VARCHAR(500) NULL;
ALTER TABLE parents ADD COLUMN portrait_url VARCHAR(500) NULL;

-- Anh bia tung tai lieu trong kho tai lieu tham khao (UC-60) - hien thi
-- dang thumbnail/cover khi liet ke danh sach, doc lap voi file_url (noi
-- dung tai lieu thuc te).
ALTER TABLE curriculum_documents ADD COLUMN cover_image_url VARCHAR(500) NULL;
