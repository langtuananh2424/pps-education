import React, { useEffect, useState } from "react";
import { GraduationCap, Plus } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";
import Badge from "@/components/ui/Badge";
import { AcademicYearResponse, createAcademicYear, listAcademicYears, updateAcademicYear } from "../api";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

const statusLabels: Record<AcademicYearResponse["status"], string> = {
  PLANNED: "Dự kiến",
  ACTIVE: "Đang dùng",
  CLOSED: "Đã đóng"
};

interface AcademicYearManagerModalProps {
  onClose: () => void;
}

/**
 * V102 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-07) —
 * quản lý danh mục "Năm học" dùng chung toàn hệ thống (nguồn cho dropdown
 * chọn năm học khi tạo lớp/chuyển lớp hàng loạt), khác Kỳ học (site-scoped).
 */
export default function AcademicYearManagerModal({ onClose }: AcademicYearManagerModalProps) {
  const [years, setYears] = useState<AcademicYearResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);

  const load = () => {
    setLoading(true);
    listAcademicYears()
      .then(setYears)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách năm học."))
      .finally(() => setLoading(false));
  };
  useEffect(load, []);

  return (
    <Modal open onClose={onClose} title="Quản lý năm học" size="lg">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg mb-3">{error}</div>}

      <div className="flex items-center justify-between mb-3">
        <span className="text-[10px] font-bold uppercase text-slate-500">Các năm học đã tạo ({years.length})</span>
        {!showCreate && (
          <Button size="sm" variant="secondary" onClick={() => setShowCreate(true)}>
            <Plus className="w-3.5 h-3.5" />
            Thêm năm học
          </Button>
        )}
      </div>

      {loading ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : years.length === 0 && !showCreate ? (
        <div className="text-xs text-slate-400 italic text-center py-6 flex flex-col items-center gap-2">
          <GraduationCap className="w-8 h-8 text-slate-300" />
          Chưa có năm học nào.
        </div>
      ) : (
        <div className="space-y-2 mb-3">
          {years.map((y) =>
            editingId === y.id ? (
              <YearForm
                key={y.id}
                initial={y}
                onCancel={() => setEditingId(null)}
                onSubmit={async (values) => {
                  await updateAcademicYear(y.id, values);
                  setEditingId(null);
                  load();
                }}
              />
            ) : (
              <div key={y.id} className="border border-slate-200 rounded-lg p-3 text-xs flex items-center justify-between gap-2">
                <div>
                  <div className="font-bold text-slate-800 flex items-center gap-2">
                    {y.code} — {y.name}
                    <Badge variant={y.status === "ACTIVE" ? "success" : y.status === "CLOSED" ? "neutral" : "info"}>
                      {statusLabels[y.status]}
                    </Badge>
                  </div>
                  {(y.startDate || y.endDate) && (
                    <div className="text-[10px] text-slate-500 mt-0.5">
                      {y.startDate ?? "?"} → {y.endDate ?? "?"}
                    </div>
                  )}
                </div>
                <button onClick={() => setEditingId(y.id)} className="text-brand-red font-bold text-[11px] hover:underline shrink-0">
                  Sửa
                </button>
              </div>
            )
          )}
        </div>
      )}

      {showCreate && (
        <YearForm
          onCancel={() => setShowCreate(false)}
          onSubmit={async (values) => {
            await createAcademicYear(values);
            setShowCreate(false);
            load();
          }}
        />
      )}
    </Modal>
  );
}

interface YearFormValues {
  code: string;
  name: string;
  startDate?: string;
  endDate?: string;
  status: AcademicYearResponse["status"];
}

function YearForm({
  initial,
  onCancel,
  onSubmit
}: {
  initial?: AcademicYearResponse;
  onCancel: () => void;
  onSubmit: (values: YearFormValues) => Promise<void>;
}) {
  const [code, setCode] = useState(initial?.code ?? "");
  const [name, setName] = useState(initial?.name ?? "");
  const [startDate, setStartDate] = useState(initial?.startDate ?? "");
  const [endDate, setEndDate] = useState(initial?.endDate ?? "");
  const [status, setStatus] = useState<AcademicYearResponse["status"]>(initial?.status ?? "PLANNED");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isEdit = !!initial;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if ((!isEdit && !code.trim()) || !name.trim()) {
      setError("Vui lòng điền đủ các trường bắt buộc.");
      return;
    }
    if (startDate && endDate && endDate < startDate) {
      setError("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const values: YearFormValues = { code: code.trim(), name: name.trim(), startDate: startDate || undefined, endDate: endDate || undefined, status };
      await onSubmit(values);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu năm học thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <p className="text-xs text-rose-600">{error}</p>}
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>Mã năm học * {isEdit && <span className="normal-case font-normal text-slate-400">(không sửa được)</span>}</label>
          <input value={code} onChange={(e) => setCode(e.target.value)} disabled={isEdit} placeholder="VD: 2026-2027" className={`${inputClass} font-mono`} />
        </div>
        <div>
          <label className={labelClass}>Tên năm học *</label>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="VD: Năm học 2026-2027" className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>Ngày bắt đầu</label>
          <DatePicker value={startDate} onChange={setStartDate} max={endDate || undefined} />
        </div>
        <div>
          <label className={labelClass}>Ngày kết thúc</label>
          <DatePicker value={endDate} onChange={setEndDate} min={startDate || undefined} />
        </div>
        {isEdit && (
          <div>
            <label className={labelClass}>Trạng thái</label>
            <Select value={status} onChange={(e) => setStatus(e.target.value as AcademicYearResponse["status"])} className={inputClass}>
              {(Object.keys(statusLabels) as AcademicYearResponse["status"][]).map((s) => (
                <option key={s} value={s}>
                  {statusLabels[s]}
                </option>
              ))}
            </Select>
          </div>
        )}
      </div>
      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? "Đang lưu..." : isEdit ? "Lưu thay đổi" : "Tạo năm học"}
        </Button>
      </div>
    </form>
  );
}
