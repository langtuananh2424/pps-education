-- =====================================================================
-- V40: PHAN HE 6 - BO TRONG SO THANH PHAN DIEM (grade_components.
-- weight_in_period). BO SUNG NGOAI SDD GOC - da xac nhan voi nguoi dung.
--
-- Ly do: Overall/Level cong bo cho Phu huynh (grade_period_results) tu
-- truoc den nay LUON do Giao vien tu tinh roi nhap tay/import Excel
-- (UC-53) - he thong KHONG tu tinh lai theo cong thuc/trong so nao ca.
-- Trong so nay chi dung cho (a) validate tong <= 100% luc cau hinh khung
-- (UC-16/A2) va (b) tinh PeriodAverageResponse (diem trung binh tam thoi)
-- - da xac nhan qua rf khong noi nao trong frontend goi API nay. Nguoi
-- dung quyet dinh bo han trong so nay, Overall chi con la gia tri GV tu
-- nhap/import, khong con cong thuc tu tinh nao.
--
-- KHONG dung toi grade_periods.weight_in_final (trong so cap KY danh
-- gia, phuc vu grade_final_summaries - chua trien khai) - day la 2 cot
-- khac nhau hoan toan.
-- =====================================================================

ALTER TABLE grade_components DROP COLUMN weight_in_period;
