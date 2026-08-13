import React, { useEffect, useMemo, useState } from "react";
import { Plus, Users } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  bulkAssignEmployeeShift,
  BulkAssignShiftResponse,
  createShift,
  deactivateShift,
  EmployeeResponse,
  listEmployees,
  listShifts,
  ShiftResponse,
  updateShift
} from "../api";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { useDialog } from "@/components/ui/DialogProvider";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";
const TODAY_ISO = new Date().toISOString().slice(0, 10);

/** 1=T2...7=CN, khớp shifts.applies_to_weekdays (CSV). */
const WEEKDAYS: { value: number; label: string }[] = [
  { value: 1, label: "T2" },
  { value: 2, label: "T3" },
  { value: 3, label: "T4" },
  { value: 4, label: "T5" },
  { value: 5, label: "T6" },
  { value: 6, label: "T7" },
  { value: 7, label: "CN" }
];

/** Bổ sung 2026-08-13 — danh mục ca làm việc + gán ca hàng loạt cho nhân sự. Xem docs/uc/phan-he-04-nhan-su.md (khối bổ sung dưới UC-09). */
export default function ShiftsTab() {
  const [shifts, setShifts] = useState<ShiftResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState(false);
  const [assigning, setAssigning] = useState(false);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const { message: toastMessage, showToast } = useToast();
  const { confirmDialog } = useDialog();

  const load = () => {
    setLoading(true);
    listShifts()
      .then((res) => {
        setShifts(res);
        if (selectedId == null && res.length > 0) setSelectedId(res[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách ca làm việc."))
      .finally(() => setLoading(false));
  };
  useEffect(load, []);

  const selected = shifts.find((s) => s.id === selectedId) ?? null;

  const handleDeactivate = async (id: number) => {
    if (!(await confirmDialog("Tắt ca này? Nhân sự đang áp dụng ca này sẽ không bị đổi ca tự động — chỉ ngăn gán ca mới.", { danger: true }))) return;
    setError(null);
    try {
      await deactivateShift(id);
      load();
      showToast("Đã tắt ca làm việc.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tắt ca thất bại.");
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <span className="text-[10px] font-bold uppercase text-slate-500">Danh mục Ca làm việc ({shifts.length})</span>
          <Button size="sm" variant="secondary" onClick={() => setCreating(true)}>
            <Plus className="w-3.5 h-3.5" />
            Thêm ca
          </Button>
        </div>

        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <Modal open={creating} onClose={() => setCreating(false)} title="Thêm ca làm việc">
          <ShiftForm
            onDone={() => {
              setCreating(false);
              load();
              showToast("Đã tạo ca làm việc thành công!");
            }}
            onCancel={() => setCreating(false)}
          />
        </Modal>

        {loading ? (
          <p className="text-xs text-slate-500">Đang tải...</p>
        ) : shifts.length === 0 ? (
          <p className="text-xs text-slate-400 italic">Chưa có ca làm việc nào.</p>
        ) : (
          <div className="space-y-2">
            {shifts.map((s) => (
              <button
                key={s.id}
                onClick={() => setSelectedId(s.id)}
                className={`w-full text-left border rounded-lg p-3 text-xs flex items-center justify-between transition-all ${
                  selectedId === s.id ? "border-brand-orange bg-orange-50/40" : "border-slate-200 hover:bg-slate-50"
                }`}
              >
                <div>
                  <span className="font-mono font-bold text-brand-red bg-orange-50 border border-orange-100 px-1.5 py-0.5 rounded text-[10px] mr-2">{s.code}</span>
                  <span className="font-bold text-slate-800">{s.name}</span>
                  <span className="text-slate-500 ml-2">
                    {s.checkInTime.slice(0, 5)}–{s.checkOutTime.slice(0, 5)}
                  </span>
                </div>
                {!s.active && <Badge variant="neutral">Đã tắt</Badge>}
              </button>
            ))}
          </div>
        )}
      </div>

      <div>
        {selected ? (
          <div className="border border-slate-200 rounded-lg p-4 space-y-3">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-[10px] font-bold uppercase text-slate-500">Chi tiết ca</span>
                <h4 className="text-sm font-bold text-slate-800 mt-0.5">{selected.name}</h4>
              </div>
              {selected.active ? <Badge variant="success">Đang áp dụng</Badge> : <Badge variant="neutral">Đã tắt</Badge>}
            </div>

            <dl className="text-xs space-y-1.5">
              <div className="flex justify-between">
                <dt className="text-slate-500">Giờ vào / ra</dt>
                <dd className="font-semibold text-slate-700">{selected.checkInTime.slice(0, 5)} — {selected.checkOutTime.slice(0, 5)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500">Áp dụng thứ</dt>
                <dd className="font-semibold text-slate-700">
                  {selected.appliesToWeekdays
                    .split(",")
                    .filter(Boolean)
                    .map((d) => WEEKDAYS.find((w) => w.value === Number(d))?.label ?? d)
                    .join(", ")}
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500">Tuần</dt>
                <dd className="font-semibold text-slate-700">
                  {selected.weekParity === "ALL" ? "Mọi tuần" : selected.weekParity === "ODD" ? "Tuần lẻ" : "Tuần chẵn"}
                </dd>
              </div>
            </dl>

            <div className="flex flex-wrap gap-2 pt-2">
              <Button size="sm" variant="secondary" onClick={() => setEditing(true)}>
                Sửa
              </Button>
              {selected.active && (
                <>
                  <Button size="sm" variant="primary" onClick={() => setAssigning(true)}>
                    <Users className="w-3.5 h-3.5" />
                    Gán ca cho nhân viên
                  </Button>
                  <Button size="sm" variant="secondary" onClick={() => handleDeactivate(selected.id)}>
                    Tắt ca
                  </Button>
                </>
              )}
            </div>

            <Modal open={editing} onClose={() => setEditing(false)} title={`Sửa ca "${selected.code}"`}>
              <ShiftForm
                shift={selected}
                onDone={() => {
                  setEditing(false);
                  load();
                  showToast("Đã cập nhật ca làm việc.");
                }}
                onCancel={() => setEditing(false)}
              />
            </Modal>

            <Modal open={assigning} onClose={() => setAssigning(false)} title={`Gán ca "${selected.name}" cho nhân viên`}>
              <BulkAssignForm
                shift={selected}
                onDone={(result) => {
                  setAssigning(false);
                  const failedNote = result.failures.length > 0 ? ` — ${result.failures.length} người lỗi.` : "";
                  showToast(`Đã gán ca cho ${result.successCount} nhân viên.${failedNote}`);
                }}
                onCancel={() => setAssigning(false)}
              />
            </Modal>
          </div>
        ) : (
          <div className="border border-slate-200 rounded-lg p-8 text-center text-xs text-slate-400 italic">Chọn 1 ca bên trái để xem chi tiết.</div>
        )}
      </div>

      <Toast message={toastMessage} />
    </div>
  );
}

function ShiftForm({ shift, onDone, onCancel }: { shift?: ShiftResponse; onDone: () => void; onCancel: () => void }) {
  const [code, setCode] = useState(shift?.code ?? "");
  const [name, setName] = useState(shift?.name ?? "");
  const [checkInTime, setCheckInTime] = useState(shift?.checkInTime.slice(0, 5) ?? "08:00");
  const [checkOutTime, setCheckOutTime] = useState(shift?.checkOutTime.slice(0, 5) ?? "17:00");
  const [weekdays, setWeekdays] = useState<Set<number>>(
    new Set((shift?.appliesToWeekdays ?? "1,2,3,4,5,6").split(",").filter(Boolean).map(Number))
  );
  const [weekParity, setWeekParity] = useState<ShiftResponse["weekParity"]>(shift?.weekParity ?? "ALL");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toggleWeekday = (day: number) => {
    setWeekdays((prev) => {
      const next = new Set(prev);
      if (next.has(day)) next.delete(day);
      else next.add(day);
      return next;
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || (!shift && !code.trim())) {
      setError("Vui lòng điền đủ mã và tên ca.");
      return;
    }
    if (weekdays.size === 0) {
      setError("Chọn ít nhất 1 thứ áp dụng ca.");
      return;
    }
    const appliesToWeekdays = Array.from(weekdays).sort().join(",");
    setSubmitting(true);
    setError(null);
    try {
      if (shift) {
        await updateShift(shift.id, {
          name: name.trim(),
          checkInTime,
          checkOutTime,
          appliesToWeekdays,
          weekParity,
          active: shift.active
        });
      } else {
        await createShift({ code: code.trim(), name: name.trim(), checkInTime, checkOutTime, appliesToWeekdays, weekParity });
      }
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu ca làm việc thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      <div className="grid grid-cols-2 gap-2">
        <div>
          <span className={labelClass}>Mã ca</span>
          <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="VD: CA-SANG" disabled={!!shift} className={`${inputClass} font-mono ${shift ? "opacity-60" : ""}`} />
        </div>
        <div>
          <span className={labelClass}>Tên ca</span>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="VD: Ca sáng" className={inputClass} />
        </div>
        <div>
          <span className={labelClass}>Giờ vào</span>
          <input type="time" value={checkInTime} onChange={(e) => setCheckInTime(e.target.value)} className={inputClass} />
        </div>
        <div>
          <span className={labelClass}>Giờ ra</span>
          <input type="time" value={checkOutTime} onChange={(e) => setCheckOutTime(e.target.value)} className={inputClass} />
        </div>
      </div>

      <div>
        <span className={labelClass}>Áp dụng thứ</span>
        <div className="flex flex-wrap gap-1.5">
          {WEEKDAYS.map((d) => (
            <button
              key={d.value}
              type="button"
              onClick={() => toggleWeekday(d.value)}
              className={`px-2.5 py-1.5 rounded-lg text-xs font-bold border transition-all ${
                weekdays.has(d.value) ? "border-brand-orange bg-orange-50 text-brand-red" : "border-slate-200 text-slate-500 hover:bg-slate-50"
              }`}
            >
              {d.label}
            </button>
          ))}
        </div>
      </div>

      <div>
        <span className={labelClass}>Tuần áp dụng</span>
        <Select value={weekParity} onChange={(e) => setWeekParity(e.target.value as ShiftResponse["weekParity"])} className={inputClass}>
          <option value="ALL">Mọi tuần</option>
          <option value="ODD">Chỉ tuần lẻ (luân phiên)</option>
          <option value="EVEN">Chỉ tuần chẵn (luân phiên)</option>
        </Select>
        <p className="text-[10px] text-slate-400 mt-1">
          Dùng cho ca luân phiên (VD thứ 7 cách tuần): tạo 1 ca "T7 tuần lẻ" và 1 ca "T7 tuần chẵn" nếu cần 2 khung giờ khác nhau, hoặc chỉ 1 ca ODD nếu tuần chẵn nghỉ hẳn.
          Trường hợp xếp lịch ngoại lệ (2 tuần liên tiếp lệch pattern) chưa có màn cấu hình riêng — báo lại nếu cần.
        </p>
      </div>

      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? "Đang lưu..." : shift ? "Lưu thay đổi" : "Tạo ca"}
        </Button>
      </div>
    </form>
  );
}

function BulkAssignForm({ shift, onDone, onCancel }: { shift: ShiftResponse; onDone: (result: BulkAssignShiftResponse) => void; onCancel: () => void }) {
  const [employees, setEmployees] = useState<EmployeeResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [effectiveFrom, setEffectiveFrom] = useState(TODAY_ISO);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listEmployees()
      .then(setEmployees)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách nhân viên."))
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return employees;
    return employees.filter((e) => e.fullName.toLowerCase().includes(q) || e.employeeCode.toLowerCase().includes(q));
  }, [employees, query]);

  const allFilteredSelected = filtered.length > 0 && filtered.every((e) => selectedIds.has(e.id));

  const toggleOne = (id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleAllFiltered = () => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (allFilteredSelected) filtered.forEach((e) => next.delete(e.id));
      else filtered.forEach((e) => next.add(e.id));
      return next;
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedIds.size === 0) {
      setError("Chọn ít nhất 1 nhân viên.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const result = await bulkAssignEmployeeShift({ employeeIds: Array.from(selectedIds), shiftId: shift.id, effectiveFrom });
      onDone(result);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gán ca hàng loạt thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div>
        <span className={labelClass}>Ngày hiệu lực</span>
        <DatePicker value={effectiveFrom} onChange={setEffectiveFrom} min={TODAY_ISO} />
      </div>

      <div>
        <span className={labelClass}>Chọn nhân viên</span>
        <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Tìm theo tên/mã nhân viên..." className={inputClass} />
      </div>

      {loading ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : (
        <div className="border border-slate-200 rounded-lg max-h-64 overflow-y-auto bg-white">
          <label className="flex items-center gap-2 px-3 py-2 border-b border-slate-100 text-xs font-bold text-slate-600 sticky top-0 bg-white">
            <input type="checkbox" checked={allFilteredSelected} onChange={toggleAllFiltered} />
            Chọn tất cả ({filtered.length})
          </label>
          {filtered.map((emp) => (
            <label key={emp.id} className="flex items-center gap-2 px-3 py-1.5 hover:bg-slate-50 cursor-pointer text-xs">
              <input type="checkbox" checked={selectedIds.has(emp.id)} onChange={() => toggleOne(emp.id)} />
              <span className="font-mono text-[10px] text-brand-red bg-orange-50 border border-orange-100 px-1 py-0.5 rounded">{emp.employeeCode}</span>
              <span className="font-semibold text-slate-700">{emp.fullName}</span>
            </label>
          ))}
        </div>
      )}

      <p className="text-[10px] text-slate-400">Đã chọn: {selectedIds.size}</p>

      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting || selectedIds.size === 0}>
          {submitting ? "Đang gán..." : `Gán ca cho ${selectedIds.size} người`}
        </Button>
      </div>
    </form>
  );
}
