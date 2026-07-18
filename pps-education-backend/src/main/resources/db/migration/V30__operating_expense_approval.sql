-- =====================================================================
-- V30: UC-31 (Ghi nhan chi van hanh) khong mo ta luong duyet, nhung
-- schema goc (V25) da co san operating_expenses.status
-- (RECORDED/APPROVED/REJECTED) va approved_by khong dung toi.
--
-- SRS mo ta actor "Ban giam doc": "...phe duyet cac quyet dinh quan
-- trong co tinh chien luoc (chi phi lon, hop dong lien ket...)" - khop
-- dung voi viec duyet chi van hanh. Da xac nhan voi user: bo sung
-- permission rieng (finance.expense.approve, gan EXECUTIVE) khac voi
-- finance.manage (STAFF bo phan Ke toan - ghi nhan), va them cot
-- rejection_reason vi schema goc khong co cho luu ly do tu choi.
-- =====================================================================

ALTER TABLE operating_expenses ADD COLUMN rejection_reason TEXT NULL;

INSERT INTO permissions (code, name, module, description) VALUES
('finance.expense.approve', 'Duyet/tu choi chi van hanh', 'FINANCE', 'UC-31 bo sung - Ban giam doc');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE p.code = 'finance.expense.approve' AND r.code IN ('EXECUTIVE');
