import React, { useEffect, useState } from "react";
import { Plus, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import {
  ClassEnrollmentResponse,
  CreateGradeComponentRequest,
  CreateGradePeriodRequest,
  GradeComponentResponse,
  GradePeriodResponse,
  addGradeComponent,
  createGradePeriod,
  deleteGradeComponent,
  deleteGradePeriod,
  listClassEnrollments,
  listGradeComponents,
  listGradePeriods
} from "../api";
import Button from "@/components/ui/Button";
import GradeSheetTable from "./GradeSheetTable";
import GradeExcelImportPanel from "./GradeExcelImportPanel";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";

const inputClass = "bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none";

interface ClassGradeSheetPanelProps {
  classId: number;
  curriculumId: number;
  /** SITE_MANAGER xem tab "Sổ điểm" ở Quản lý lớp học chỉ để tham khảo — không nhập/sửa điểm được (khác GradesPage cũ, đã bỏ sót readOnly khi tách component này). */
  readOnly?: boolean;
}

/**
 * "Bảng nhập điểm (UC-19)" của đúng 1 lớp — tách khỏi GradesPage để dùng lại được ở tab "Sổ điểm"
 * trong ClassDetailPanel (UC-18), tránh lặp lại toàn bộ logic kỳ điểm/đầu điểm/nhập điểm 2 nơi.
 */
export default function ClassGradeSheetPanel({ classId, curriculumId, readOnly = false }: ClassGradeSheetPanelProps) {
  const { hasPermission } = useApp();
  const canManage = hasPermission("academic.grade.manage");
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [gradePeriods, setGradePeriods] = useState<GradePeriodResponse[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [gradeComponents, setGradeComponents] = useState<GradeComponentResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showPeriodForm, setShowPeriodForm] = useState(false);
  const [showComponentForm, setShowComponentForm] = useState(false);
  const [sheetVersion, setSheetVersion] = useState(0);
  const { message: toastMessage, showToast } = useToast();

  useEffect(() => {
    listClassEnrollments(classId).then(setEnrollments).catch(() => undefined);
  }, [classId]);

  useEffect(() => {
    setSelectedPeriodId(null);
    setGradeComponents([]);
    listGradePeriods(curriculumId).then(setGradePeriods).catch(() => undefined);
  }, [curriculumId]);

  useEffect(() => {
    setGradeComponents([]);
    if (!selectedPeriodId) return;
    listGradeComponents(selectedPeriodId)
      .then(setGradeComponents)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được đầu điểm."));
  }, [selectedPeriodId]);

  /** UC-19 (bổ sung): chỉ xoá được kỳ RỖNG (chưa có đầu điểm/điểm tổng kết/cửa sổ nhập điểm) — BE tự chặn 422 nếu không đủ điều kiện. */
  const handleDeletePeriod = async () => {
    if (!selectedPeriodId) return;
    const period = gradePeriods.find((p) => p.id === selectedPeriodId);
    if (!window.confirm(`Xoá kỳ điểm "${period?.name ?? selectedPeriodId}"? Chỉ xoá được khi kỳ này còn rỗng (chưa có đầu điểm/điểm tổng kết).`)) return;
    setError(null);
    try {
      await deleteGradePeriod(selectedPeriodId);
      setGradePeriods((prev) => prev.filter((p) => p.id !== selectedPeriodId));
      setSelectedPeriodId(null);
      showToast("Đã xoá kỳ điểm thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Xoá kỳ điểm thất bại.");
    }
  };

  /** UC-19 (bổ sung): chỉ xoá được đầu điểm CHƯA có điểm nhập nào — BE tự chặn 422 nếu đã có điểm. */
  const handleDeleteComponent = async (component: GradeComponentResponse) => {
    if (!window.confirm(`Xoá đầu điểm "${component.name}"? Chỉ xoá được khi đầu điểm này chưa có điểm nhập nào.`)) return;
    setError(null);
    try {
      await deleteGradeComponent(component.id);
      setGradeComponents((prev) => prev.filter((c) => c.id !== component.id));
      showToast("Đã xoá đầu điểm thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Xoá đầu điểm thất bại.");
    }
  };

  return (
    <div className="space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="flex items-center justify-between flex-wrap gap-2">
        <span className="text-xs font-bold text-slate-700 font-display">Bảng nhập điểm (UC-19)</span>
        <select
          value={selectedPeriodId ?? ""}
          onChange={(e) => setSelectedPeriodId(e.target.value ? Number(e.target.value) : null)}
          className={inputClass}
        >
          <option value="">-- Chọn kỳ điểm --</option>
          {gradePeriods.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
      </div>

      {canManage && !readOnly && (
        <div className="flex gap-2 flex-wrap items-center">
          <Button type="button" size="sm" variant="secondary" onClick={() => setShowPeriodForm((v) => !v)}>
            <Plus className="w-3.5 h-3.5" />
            Thêm kỳ điểm
          </Button>
          {selectedPeriodId && (
            <>
              <Button type="button" size="sm" variant="secondary" onClick={() => setShowComponentForm((v) => !v)}>
                <Plus className="w-3.5 h-3.5" />
                Thêm đầu điểm
              </Button>
              <Button type="button" size="sm" variant="secondary" onClick={handleDeletePeriod} className="text-rose-600 hover:bg-rose-50">
                <X className="w-3.5 h-3.5" />
                Xoá kỳ điểm này
              </Button>
            </>
          )}
        </div>
      )}
      {canManage && !readOnly && selectedPeriodId && gradeComponents.length > 0 && (
        <div className="flex gap-1.5 flex-wrap">
          {gradeComponents.map((c) => (
            <span key={c.id} className="flex items-center gap-1 bg-slate-100 border border-slate-200 text-slate-600 text-[11px] font-semibold px-2 py-1 rounded-lg">
              {c.name}
              <button type="button" onClick={() => handleDeleteComponent(c)} title="Xoá đầu điểm (chỉ khi chưa có điểm nhập)" className="hover:text-rose-600">
                <X className="w-3 h-3" />
              </button>
            </span>
          ))}
        </div>
      )}
      {showPeriodForm && (
        <CreatePeriodForm
          curriculumId={curriculumId}
          onDone={(p) => {
            setGradePeriods((prev) => [...prev, p]);
            setShowPeriodForm(false);
            showToast("Đã tạo kỳ điểm thành công!");
          }}
          onCancel={() => setShowPeriodForm(false)}
        />
      )}
      {showComponentForm && selectedPeriodId && (
        <CreateComponentForm
          gradePeriodId={selectedPeriodId}
          onDone={(c) => {
            setGradeComponents((prev) => [...prev, c]);
            setShowComponentForm(false);
            showToast("Đã tạo đầu điểm thành công!");
          }}
          onCancel={() => setShowComponentForm(false)}
        />
      )}

      {selectedPeriodId ? (
        gradeComponents.length === 0 ? (
          <p className="text-xs text-slate-400 italic p-6 text-center">
            Kỳ điểm này chưa có đầu điểm nào được cấu hình{canManage ? " — dùng nút \"Thêm đầu điểm\" ở trên." : "."}
          </p>
        ) : (
          <GradeSheetTable
            key={`${classId}-${selectedPeriodId}-${sheetVersion}`}
            classId={classId}
            gradePeriodId={selectedPeriodId}
            components={gradeComponents}
            enrollments={enrollments}
            readOnly={readOnly}
          />
        )
      ) : (
        <p className="text-xs text-slate-400 italic p-6 text-center">Chọn kỳ điểm để bắt đầu nhập điểm.</p>
      )}

      {!readOnly && selectedPeriodId && gradeComponents.length > 0 && (
        <GradeExcelImportPanel
          classId={classId}
          gradePeriodId={selectedPeriodId}
          components={gradeComponents}
          enrollments={enrollments}
          onImported={() => {
            setSheetVersion((v) => v + 1);
            showToast("Đã nhập điểm từ Excel thành công!");
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

function CreatePeriodForm({ curriculumId, onDone, onCancel }: { curriculumId: number; onDone: (p: GradePeriodResponse) => void; onCancel: () => void }) {
  const [form, setForm] = useState({ code: "MID_1", name: "", weightInFinal: "" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim() || !form.weightInFinal) {
      setError("Vui lòng điền đủ tên và điểm kỳ vọng");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateGradePeriodRequest = { code: form.code, name: form.name.trim(), weightInFinal: Number(form.weightInFinal) };
      const created = await createGradePeriod(curriculumId, request);
      onDone(created);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tạo kỳ điểm thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-white border border-slate-200 rounded-lg p-3 space-y-2">
      {error && <div className="text-[11px] text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
      <div className="grid grid-cols-3 gap-2">
        <select value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} className={inputClass}>
          <option value="MID_1">MID_1 — Giữa kỳ 1</option>
          <option value="END_1">END_1 — Cuối kỳ 1</option>
          <option value="MID_2">MID_2 — Giữa kỳ 2</option>
          <option value="END_2">END_2 — Cuối kỳ 2</option>
          <option value="OTHER">OTHER — Khác</option>
        </select>
        <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Tên kỳ điểm" className={inputClass} />
        <input type="number" step="0.01" value={form.weightInFinal} onChange={(e) => setForm({ ...form, weightInFinal: e.target.value })} placeholder="Điểm kỳ vọng" className={inputClass} />
      </div>
      <div className="flex gap-2">
        <Button type="button" size="sm" variant="secondary" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit" size="sm" variant="primary" disabled={submitting}>
          {submitting ? "Đang lưu..." : "Tạo kỳ điểm"}
        </Button>
      </div>
    </form>
  );
}

function CreateComponentForm({ gradePeriodId, onDone, onCancel }: { gradePeriodId: number; onDone: (c: GradeComponentResponse) => void; onCancel: () => void }) {
  const [form, setForm] = useState({ code: "OTHER", name: "", maxScore: "10" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) {
      setError("Vui lòng điền tên đầu điểm.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateGradeComponentRequest = {
        code: form.code,
        name: form.name.trim(),
        maxScore: form.maxScore ? Number(form.maxScore) : undefined
      };
      const created = await addGradeComponent(gradePeriodId, request);
      onDone(created);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tạo đầu điểm thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-white border border-slate-200 rounded-lg p-3 space-y-2">
      {error && <div className="text-[11px] text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
      <div className="grid grid-cols-3 gap-2">
        <select value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} className={inputClass}>
          <option value="SPEAKING">Speaking</option>
          <option value="WRITING">Writing</option>
          <option value="LISTENING">Listening</option>
          <option value="READING">Reading</option>
          <option value="GRAMMAR">Grammar</option>
          <option value="PROJECT">Project</option>
          <option value="OTHER">Khác</option>
        </select>
        <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Tên đầu điểm" className={inputClass} />
        <input type="number" step="0.01" value={form.maxScore} onChange={(e) => setForm({ ...form, maxScore: e.target.value })} placeholder="Điểm tối đa" className={inputClass} />
      </div>
      <div className="flex gap-2">
        <Button type="button" size="sm" variant="secondary" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit" size="sm" variant="primary" disabled={submitting}>
          {submitting ? "Đang lưu..." : "Tạo đầu điểm"}
        </Button>
      </div>
    </form>
  );
}
