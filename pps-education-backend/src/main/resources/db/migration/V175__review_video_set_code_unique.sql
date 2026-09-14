-- Bo sung ngoai SDD goc, da xac nhan voi nguoi dung 2026-09-14 -- UC-73
-- (Import Excel hang loat "bo" video on tap, mirror UC-72 Kho de). Import
-- can tra cuu review_video_sets theo code de idempotent (nhieu dong Excel
-- cung Ma bo = nhieu video trong CUNG 1 bo, import lai file sau nay phai
-- tim lai dung bo cu, khong tao trung) -- can UNIQUE(code) de tra cuu on
-- dinh, mirror exams.code/exercises.code (da UNIQUE tu truoc). Truoc gio
-- code khong UNIQUE vi tao tay tung Bo mot, chua co nhu cau tra cuu lai
-- theo code.
ALTER TABLE review_video_sets
    ADD CONSTRAINT uq_review_video_sets_code UNIQUE (code);
