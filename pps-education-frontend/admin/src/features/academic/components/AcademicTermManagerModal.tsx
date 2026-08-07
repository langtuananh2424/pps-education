import React, { useEffect, useState } from "react";
import { CalendarRange, Plus } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import DatePicker from "@/components/ui/DatePicker";
import { AcademicTermResponse, createAcademicTerm, listAcademicTerms, updateAcademicTerm } from "../api";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-sm p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-sm uppercase font-bold text-slate-500 block mb-1";

interface AcademicTermManagerModalProps {
  siteId: number;
  siteName: string;
  onClose: () => void;
}

/**
 * UC-18 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-31) —
 * quản lý "Giai đoạn/Học kỳ" của 1 điểm trường (VD: Giữa kỳ 1, Cuối kỳ 1).
 * Độc lập với lớp học — không gán kỳ trực tiếp vào 1 lớp ở đây. Hồ sơ
 * lớp/học sinh theo từng kỳ (báo cáo & thống kê) là 1 phân hệ riêng sẽ
 * triển khai sau, dùng lại [startDate, endDate] của kỳ để lọc dữ liệu đã
 * có ngày tháng sẵn (ghi danh, phân công giáo viên, điểm danh, nhận xét).
 */
export default function AcademicTermManagerModal({ siteId, siteName, onClose }: AcademicTermManagerModalProps) {
  const [terms, setTerms] = useState<AcademicTermResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);

  const load = () => {
    setLoading(true);
    listAcademicTerms(siteId)
      .then(setTerms)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách kỳ học."))
      .finally(() => setLoading(false));
  };
  useEffect(load, [siteId]);

  return (
    <Modal open onClose={onClose} title={`Quản lý học kỳ — ${siteName}`} size="lg">
      {error && <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-3">{error}</div>}

      <div className="flex items-center justify-between mb-3">
        <span className="text-sm font-bold uppercase text-slate-500">Các kỳ đã tạo ({terms.length})</span>
        {!showCreate && (
          <Button size="sm" variant="secondary" onClick={() => setShowCreate(true)}>
            <Plus className="w-3.5 h-3.5" />
            Thêm kỳ
          </Button>
        )}
      </div>

      {loading ? (
        <p className="text-sm text-slate-500">Đang tải...</p>
      ) : terms.length === 0 && !showCreate ? (
        <div className="text-sm text-slate-400 italic text-center py-6 flex flex-col items-center gap-2">
          <CalendarRange className="w-8 h-8 text-slate-300" />
          Điểm trường này chưa có kỳ học nào.
        </div>
      ) : (
        <div className="space-y-2 mb-3">
          {terms.map((t) =>
            editingId === t.id ? (
              <TermForm
                key={t.id}
                initial={t}
                onCancel={() => setEditingId(null)}
                onSubmit={async (values) => {
                  await updateAcademicTerm(t.id, values);
                  setEditingId(null);
                  load();
                }}
              />
            ) : (
              <div key={t.id} className="border border-slate-200 rounded-lg p-3 text-sm flex items-center justify-between gap-2">
                <div>
                  <div className="font-bold text-slate-800">
                    {t.code} — {t.name}
                  </div>
                  <div className="text-sm text-slate-500 mt-0.5">
                    {t.startDate} → {t.endDate}
                  </div>
                </div>
                <button onClick={() => setEditingId(t.id)} className="text-brand-red font-bold text-sm hover:underline shrink-0">
                  Sửa
                </button>
              </div>
            )
          )}
        </div>
      )}

      {showCreate && (
        <TermForm
          onCancel={() => setShowCreate(false)}
          onSubmit={async (values) => {
            await createAcademicTerm({ ...values, siteId });
            setShowCreate(false);
            load();
          }}
        />
      )}
    </Modal>
  );
}

interface TermFormValues {
  code: string;
  name: string;
  startDate: string;
  endDate: string;
}

function TermForm({
  initial,
  onCancel,
  onSubmit
}: {
  initial?: AcademicTermResponse;
  onCancel: () => void;
  onSubmit: (values: TermFormValues) => Promise<void>;
}) {
  const [code, setCode] = useState(initial?.code ?? "");
  const [name, setName] = useState(initial?.name ?? "");
  const [startDate, setStartDate] = useState(initial?.startDate ?? "");
  const [endDate, setEndDate] = useState(initial?.endDate ?? "");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isEdit = !!initial;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if ((!isEdit && !code.trim()) || !name.trim() || !startDate || !endDate) {
      setError("Vui lòng điền đủ các trường bắt buộc.");
      return;
    }
    if (endDate < startDate) {
      setError("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const values: TermFormValues = { code: code.trim(), name: name.trim(), startDate, endDate };
      await onSubmit(values);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu kỳ học thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <p className="text-sm text-rose-600">{error}</p>}
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>Mã kỳ * {isEdit && <span className="normal-case font-normal text-slate-400">(không sửa được)</span>}</label>
          <input value={code} onChange={(e) => setCode(e.target.value)} disabled={isEdit} placeholder="VD: GK1-2627" className={`${inputClass} font-mono`} />
        </div>
        <div>
          <label className={labelClass}>Tên kỳ *</label>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="VD: Giữa kỳ 1 (2026-2027)" className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Ngày bắt đầu *</label>
          <DatePicker value={startDate} onChange={setStartDate} max={endDate || undefined} />
        </div>
        <div>
          <label className={labelClass}>Ngày kết thúc *</label>
          <DatePicker value={endDate} onChange={setEndDate} min={startDate || undefined} />
        </div>
      </div>
      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? "Đang lưu..." : isEdit ? "Lưu thay đổi" : "Tạo kỳ học"}
        </Button>
      </div>
    </form>
  );
}
