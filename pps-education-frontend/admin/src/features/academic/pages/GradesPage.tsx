import React, { useEffect, useState } from "react";
import { Plus } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { UserRole } from "@/types";
import {
  ClassEnrollmentResponse,
  ClassResponse,
  CreateGradeComponentRequest,
  CreateGradePeriodRequest,
  GradeComponentResponse,
  GradeEntryResponse,
  GradePeriodResponse,
  addGradeComponent,
  createGradePeriod,
  listClassEnrollments,
  listClasses,
  listGradeComponents,
  listGradeEntries,
  listGradePeriods
} from "../api";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import GradebookTable from "../components/GradebookTable";
import GradeApprovalQueue from "../components/GradeApprovalQueue";

const inputClass = "bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none";

export default function GradesPage() {
  const { hasPermission, currentUser } = useApp();
  const canManage = hasPermission("academic.grade.manage");
  // Hàng chờ duyệt (UC-20) chỉ có ý nghĩa với Quản lý điểm trường — API tự scope theo site được gán,
  // ẩn hẳn khối này với tài khoản khác để đỡ hiện 1 panel rỗng không liên quan.
  const isSiteManager = currentUser?.roleCodes?.includes(UserRole.SITE_MANAGER) ?? false;

  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null);
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [gradePeriods, setGradePeriods] = useState<GradePeriodResponse[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [gradeComponents, setGradeComponents] = useState<GradeComponentResponse[]>([]);
  const [selectedComponentId, setSelectedComponentId] = useState<number | null>(null);
  const [entries, setEntries] = useState<GradeEntryResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showPeriodForm, setShowPeriodForm] = useState(false);
  const [showComponentForm, setShowComponentForm] = useState(false);

  const selectedClass = classes.find((c) => c.id === selectedClassId) ?? null;

  useEffect(() => {
    listClasses().then(setClasses).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!selectedClassId) return;
    listClassEnrollments(selectedClassId).then(setEnrollments).catch(() => undefined);
  }, [selectedClassId]);

  useEffect(() => {
    setSelectedPeriodId(null);
    setGradeComponents([]);
    setSelectedComponentId(null);
    setEntries([]);
    if (!selectedClass) return;
    listGradePeriods(selectedClass.curriculumId).then(setGradePeriods).catch(() => undefined);
  }, [selectedClass?.curriculumId]);

  useEffect(() => {
    setSelectedComponentId(null);
    setEntries([]);
    if (!selectedPeriodId) return;
    listGradeComponents(selectedPeriodId).then(setGradeComponents).catch(() => undefined);
  }, [selectedPeriodId]);

  const loadEntries = () => {
    if (!selectedClassId || !selectedComponentId) return;
    listGradeEntries(selectedClassId, selectedComponentId)
      .then(setEntries)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được điểm."));
  };
  useEffect(loadEntries, [selectedClassId, selectedComponentId]);

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Sổ điểm hệ thống (UC-19/20)</h1>
        <p className="text-xs text-slate-500 mt-1">Giáo viên nhập điểm thành phần theo lớp, Quản lý điểm trường duyệt trước khi công khai.</p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card padded={false} className={`${isSiteManager ? "lg:col-span-2" : "lg:col-span-3"} overflow-hidden`}>
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 space-y-3">
            <div className="flex items-center justify-between flex-wrap gap-2">
              <span className="text-xs font-bold text-slate-700 font-display">Bảng nhập điểm (UC-19)</span>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
              <select value={selectedClassId ?? ""} onChange={(e) => setSelectedClassId(e.target.value ? Number(e.target.value) : null)} className={inputClass}>
                <option value="">-- Chọn lớp --</option>
                {classes.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.classCode} — {c.name}
                  </option>
                ))}
              </select>
              <select
                value={selectedPeriodId ?? ""}
                onChange={(e) => setSelectedPeriodId(e.target.value ? Number(e.target.value) : null)}
                disabled={!selectedClassId}
                className={`${inputClass} disabled:opacity-50`}
              >
                <option value="">-- Chọn kỳ điểm --</option>
                {gradePeriods.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
              <select
                value={selectedComponentId ?? ""}
                onChange={(e) => setSelectedComponentId(e.target.value ? Number(e.target.value) : null)}
                disabled={!selectedPeriodId}
                className={`${inputClass} disabled:opacity-50`}
              >
                <option value="">-- Chọn đầu điểm --</option>
                {gradeComponents.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} (x{c.weightInPeriod})
                  </option>
                ))}
              </select>
            </div>
            {canManage && selectedClass && (
              <div className="flex gap-2">
                <Button type="button" size="sm" variant="secondary" onClick={() => setShowPeriodForm((v) => !v)}>
                  <Plus className="w-3.5 h-3.5" />
                  Thêm kỳ điểm
                </Button>
                {selectedPeriodId && (
                  <Button type="button" size="sm" variant="secondary" onClick={() => setShowComponentForm((v) => !v)}>
                    <Plus className="w-3.5 h-3.5" />
                    Thêm đầu điểm
                  </Button>
                )}
              </div>
            )}
            {showPeriodForm && selectedClass && (
              <CreatePeriodForm
                curriculumId={selectedClass.curriculumId}
                onDone={(p) => {
                  setGradePeriods((prev) => [...prev, p]);
                  setShowPeriodForm(false);
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
                }}
                onCancel={() => setShowComponentForm(false)}
              />
            )}
          </div>

          {selectedClassId && selectedComponentId ? (
            <GradebookTable classId={selectedClassId} componentId={selectedComponentId} enrollments={enrollments} entries={entries} onChanged={loadEntries} />
          ) : (
            <p className="text-xs text-slate-400 italic p-6 text-center">Chọn lớp → kỳ điểm → đầu điểm để bắt đầu nhập điểm.</p>
          )}
        </Card>

        {isSiteManager && (
          <Card className="space-y-4">
            <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2">
              Duyệt điểm học phần (UC-20)
            </h3>
            <p className="text-xs text-slate-500">Điểm sau khi giáo viên nộp sẽ vào hàng chờ của điểm trường mình phụ trách.</p>
            <GradeApprovalQueue />
          </Card>
        )}
      </div>
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
      setError("Vui lòng điền đủ tên và trọng số.");
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
        <input type="number" step="0.01" value={form.weightInFinal} onChange={(e) => setForm({ ...form, weightInFinal: e.target.value })} placeholder="Trọng số" className={inputClass} />
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
  const [form, setForm] = useState({ code: "OTHER", name: "", weightInPeriod: "", maxScore: "10" });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim() || !form.weightInPeriod) {
      setError("Vui lòng điền đủ tên và trọng số.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateGradeComponentRequest = {
        code: form.code,
        name: form.name.trim(),
        weightInPeriod: Number(form.weightInPeriod),
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
      <div className="grid grid-cols-4 gap-2">
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
        <input type="number" step="0.01" value={form.weightInPeriod} onChange={(e) => setForm({ ...form, weightInPeriod: e.target.value })} placeholder="Trọng số" className={inputClass} />
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
