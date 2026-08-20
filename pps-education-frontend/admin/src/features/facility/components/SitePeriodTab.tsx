import React, { useEffect, useMemo, useState } from "react";
import { Plus, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  createSitePeriodTemplate,
  CreateSitePeriodTemplateRequest,
  DayPart,
  dayPartLabels,
  dayPartOrder,
  deleteSitePeriodTemplate,
  listSitePeriodTemplates,
  SitePeriodTemplateResponse,
  updateSitePeriodTemplate
} from "../api";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import Select from "@/components/ui/Select";
import Time24Input from "@/components/ui/Time24Input";
import { useDialog } from "@/components/ui/DialogProvider";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

/**
 * "Tiết học theo điểm trường" (bổ sung ngoài SDD gốc, xác nhận với người
 * dùng 2026-08-19/2026-08-20) — mỗi điểm trường tự cấu hình danh sách
 * tiết cố định, CHIA THEO BUỔI (Sáng/Chiều/Tối — mỗi buổi đánh số tiết
 * riêng, khớp thời khóa biểu giấy thực tế), dùng làm nguồn "chọn tiết"
 * khi xếp lịch buổi học (UC-48/56/57) và làm hàng của lưới thời khóa
 * biểu ("Lịch làm việc").
 */
export default function SitePeriodTab({ siteId, showToast }: { siteId: number; showToast: (msg: string) => void }) {
  const [items, setItems] = useState<SitePeriodTemplateResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<SitePeriodTemplateResponse | "new" | null>(null);
  const { confirmDialog } = useDialog();

  const load = () => {
    setLoading(true);
    listSitePeriodTemplates(siteId)
      .then(setItems)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách tiết học."))
      .finally(() => setLoading(false));
  };
  useEffect(load, [siteId]);

  const groups = useMemo(() => {
    const map = new Map<DayPart, SitePeriodTemplateResponse[]>();
    dayPartOrder.forEach((dp) => map.set(dp, []));
    items.forEach((item) => map.get(item.dayPart)?.push(item));
    return map;
  }, [items]);

  const handleDelete = async (item: SitePeriodTemplateResponse) => {
    if (
      !(await confirmDialog(
        `Xoá Tiết ${item.periodNumber} buổi ${dayPartLabels[item.dayPart]} (${item.startTime.slice(0, 5)}–${item.endTime.slice(0, 5)})?`,
        { danger: true }
      ))
    )
      return;
    try {
      await deleteSitePeriodTemplate(siteId, item.id);
      load();
      showToast("Đã xoá tiết học thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Xoá tiết học thất bại.");
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-bold uppercase text-slate-500">Tiết học ({items.length})</span>
        <Button size="sm" variant="secondary" onClick={() => setEditing("new")}>
          <Plus className="w-3.5 h-3.5" />
          Thêm tiết
        </Button>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {loading ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : items.length === 0 ? (
        <p className="text-xs text-slate-400 italic">
          Điểm trường này chưa có tiết học nào — cần thêm trước khi xếp lịch buổi học ở phân hệ Học thuật.
        </p>
      ) : (
        <div className="space-y-4">
          {dayPartOrder.map((dp) => {
            const dpItems = groups.get(dp) ?? [];
            if (dpItems.length === 0) return null;
            return (
              <div key={dp} className="space-y-2">
                <span className="text-[10px] font-bold uppercase text-brand-red">Buổi {dayPartLabels[dp]}</span>
                {dpItems.map((item) => (
                  <div key={item.id} className="border border-slate-200 rounded-lg p-3 flex items-center justify-between gap-2">
                    <button type="button" onClick={() => setEditing(item)} className="text-left flex-1">
                      <p className="text-xs font-bold text-slate-800">{item.label ?? `Tiết ${item.periodNumber}`}</p>
                      <p className="text-[10px] text-slate-400 mt-0.5">
                        {item.startTime.slice(0, 5)}–{item.endTime.slice(0, 5)}
                      </p>
                    </button>
                    <button onClick={() => handleDelete(item)} className="text-rose-500 hover:text-rose-700 shrink-0">
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      )}

      {editing && (
        <SitePeriodFormModal
          siteId={siteId}
          existing={editing === "new" ? null : editing}
          nextPeriodNumberFor={(dayPart) => Math.max(0, ...items.filter((i) => i.dayPart === dayPart).map((i) => i.periodNumber)) + 1}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            load();
            showToast(editing === "new" ? "Đã thêm tiết học thành công!" : "Đã lưu tiết học thành công!");
          }}
        />
      )}
    </div>
  );
}

function SitePeriodFormModal({
  siteId,
  existing,
  nextPeriodNumberFor,
  onClose,
  onSaved
}: {
  siteId: number;
  existing: SitePeriodTemplateResponse | null;
  nextPeriodNumberFor: (dayPart: DayPart) => number;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [dayPart, setDayPart] = useState<DayPart>(existing?.dayPart ?? "MORNING");
  const [periodNumber, setPeriodNumber] = useState(existing?.periodNumber ?? nextPeriodNumberFor(dayPart));
  const [label, setLabel] = useState(existing?.label ?? "");
  const [startTime, setStartTime] = useState(existing?.startTime.slice(0, 5) ?? "");
  const [endTime, setEndTime] = useState(existing?.endTime.slice(0, 5) ?? "");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!existing) setPeriodNumber(nextPeriodNumberFor(dayPart));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dayPart]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!startTime || !endTime) {
      setError("Vui lòng nhập đủ giờ bắt đầu/kết thúc.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      if (existing) {
        await updateSitePeriodTemplate(siteId, existing.id, { label: label.trim() || undefined, startTime, endTime });
      } else {
        const request: CreateSitePeriodTemplateRequest = { dayPart, periodNumber, label: label.trim() || undefined, startTime, endTime };
        await createSitePeriodTemplate(siteId, request);
      }
      onSaved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu tiết học thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={existing ? `Sửa Tiết ${existing.periodNumber} buổi ${dayPartLabels[existing.dayPart]}` : "Thêm tiết học"}>
      <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        {!existing && (
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass}>Buổi *</label>
              <Select value={dayPart} onChange={(e) => setDayPart(e.target.value as DayPart)} className={inputClass}>
                {dayPartOrder.map((dp) => (
                  <option key={dp} value={dp}>
                    {dayPartLabels[dp]}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <label className={labelClass}>Số tiết *</label>
              <input
                type="number"
                min={1}
                value={periodNumber}
                onChange={(e) => setPeriodNumber(Number(e.target.value))}
                className={inputClass}
                required
              />
            </div>
          </div>
        )}
        <div>
          <label className={labelClass}>Tên hiển thị (tuỳ chọn)</label>
          <input value={label} onChange={(e) => setLabel(e.target.value)} className={inputClass} placeholder={`VD: Tiết ${periodNumber}`} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Giờ bắt đầu *</label>
            <Time24Input value={startTime} onChange={setStartTime} className={inputClass} required />
          </div>
          <div>
            <label className={labelClass}>Giờ kết thúc *</label>
            <Time24Input value={endTime} onChange={setEndTime} className={inputClass} required />
          </div>
        </div>
        <Button type="submit" size="sm" variant="primary" disabled={submitting}>
          {submitting ? "Đang lưu..." : "Lưu"}
        </Button>
      </form>
    </Modal>
  );
}
