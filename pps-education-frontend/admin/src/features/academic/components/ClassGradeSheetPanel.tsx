import React, { useEffect, useState } from "react";
import { LineChart, Plus, Send, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import {
  AcademicTermResponse,
  ClassEnrollmentResponse,
  CreateGradeComponentSetupRequest,
  CreateGradeEvaluationComponentRequest,
  GradeComponentSetupResponse,
  GradeEntryResponse,
  GradeEvaluationComponentResponse,
  GradeEvaluationResultResponse,
  addGradeEvaluationComponent,
  createGradeComponentSetup,
  deleteGradeComponentSetup,
  deleteGradeEvaluationComponent,
  listAcademicTerms,
  listClassEnrollments,
  listGradeComponentSetups,
  listGradeEvaluationComponents,
  submitGradesForApproval
} from "../api";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import { useDialog } from "@/components/ui/DialogProvider";
import GradeSheetTable from "./GradeSheetTable";
import ClassGradeComparisonTable from "./ClassGradeComparisonTable";
import GradeExcelImportPanel from "./GradeExcelImportPanel";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";

const inputClass = "bg-slate-50 border border-slate-200 text-xs p-2 rounded-lg focus:outline-none";

interface ClassGradeSheetPanelProps {
  classId: number;
  siteId: number;
  /** SITE_MANAGER xem tab "Sổ điểm" ở Quản lý lớp học chỉ để tham khảo — không nhập/sửa điểm được (khác GradesPage cũ, đã bỏ sót readOnly khi tách component này). */
  readOnly?: boolean;
}

function setupLabel(s: GradeComponentSetupResponse): string {
  return `${s.academicTermName} — ${s.evaluationType === "MID_TERM" ? "Giữa kỳ" : "Cuối kỳ"}`;
}

/**
 * "Bảng nhập điểm (UC-19)" của đúng 1 lớp — tách khỏi GradesPage để dùng lại được ở tab "Sổ điểm"
 * trong ClassDetailPanel (UC-18), tránh lặp lại toàn bộ logic setup sổ điểm/đầu điểm/nhập điểm 2 nơi.
 * V94 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng — consolidate vào academic_terms): "kỳ điểm"
 * cũ theo curriculum đổi thành "setup sổ điểm" gắn trực tiếp (lớp, kỳ học, Giữa/Cuối kỳ).
 */
export default function ClassGradeSheetPanel({ classId, siteId, readOnly = false }: ClassGradeSheetPanelProps) {
  const { hasPermission } = useApp();
  const canManage = hasPermission("academic.grade.setup.create") || hasPermission("academic.grade.manage");
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [setups, setSetups] = useState<GradeComponentSetupResponse[]>([]);
  const [selectedSetupId, setSelectedSetupId] = useState<number | null>(null);
  const [gradeComponents, setGradeComponents] = useState<GradeEvaluationComponentResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showSetupForm, setShowSetupForm] = useState(false);
  const [showComponentForm, setShowComponentForm] = useState(false);
  const [sheetVersion, setSheetVersion] = useState(0);
  const [loadedEntries, setLoadedEntries] = useState<GradeEntryResponse[]>([]);
  const [loadedResults, setLoadedResults] = useState<GradeEvaluationResultResponse[]>([]);
  const [submittingGrades, setSubmittingGrades] = useState(false);
  // UC-19/20 (bổ sung, 2026-07-29): xem tổng hợp điểm cả lớp qua mọi kỳ để so sánh tiến bộ — CHỈ XEM,
  // tách khỏi màn nhập điểm theo từng setup ở dưới (đổi qua đổi lại bằng nút này, không hiện đồng thời).
  const [showComparison, setShowComparison] = useState(false);
  const { message: toastMessage, showToast } = useToast();
  const { confirmDialog } = useDialog();

  useEffect(() => {
    listClassEnrollments(classId).then(setEnrollments).catch(() => undefined);
  }, [classId]);

  useEffect(() => {
    setSelectedSetupId(null);
    setGradeComponents([]);
    listGradeComponentSetups(classId).then(setSetups).catch(() => undefined);
  }, [classId]);

  useEffect(() => {
    setGradeComponents([]);
    if (!selectedSetupId) return;
    listGradeEvaluationComponents(selectedSetupId)
      .then(setGradeComponents)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được đầu điểm."));
  }, [selectedSetupId]);

  /** UC-19 (bổ sung): chỉ xoá được setup RỖNG (chưa có đầu điểm/điểm tổng kết/cửa sổ nhập điểm) — BE tự chặn 422 nếu không đủ điều kiện. */
  const handleDeleteSetup = async () => {
    if (!selectedSetupId) return;
    const setup = setups.find((s) => s.id === selectedSetupId);
    if (!(await confirmDialog(`Xoá setup "${setup ? setupLabel(setup) : selectedSetupId}"? Chỉ xoá được khi setup này còn rỗng (chưa có đầu điểm/điểm tổng kết).`, { danger: true }))) return;
    setError(null);
    try {
      await deleteGradeComponentSetup(selectedSetupId);
      setSetups((prev) => prev.filter((s) => s.id !== selectedSetupId));
      setSelectedSetupId(null);
      showToast("Đã xoá setup sổ điểm thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Xoá setup sổ điểm thất bại.");
    }
  };

  /** UC-19 (bổ sung): chỉ xoá được đầu điểm CHƯA có điểm nhập nào — BE tự chặn 422 nếu đã có điểm. */
  const handleDeleteComponent = async (component: GradeEvaluationComponentResponse) => {
    if (!(await confirmDialog(`Xoá đầu điểm "${component.name}"? Chỉ xoá được khi đầu điểm này chưa có điểm nhập nào.`, { danger: true }))) return;
    setError(null);
    try {
      await deleteGradeEvaluationComponent(component.id);
      setGradeComponents((prev) => prev.filter((c) => c.id !== component.id));
      showToast("Đã xoá đầu điểm thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Xoá đầu điểm thất bại.");
    }
  };

  const submittableEntryIds = loadedEntries.filter((e) => e.status === "DRAFT" || e.status === "REJECTED").map((e) => e.id);
  const submittableResultIds = loadedResults.filter((r) => r.status === "DRAFT" || r.status === "REJECTED").map((r) => r.id);
  const submittableCount = submittableEntryIds.length + submittableResultIds.length;

  /** UC-19 Main Flow bước 4 (V44): Giáo viên gửi duyệt tất cả điểm Nháp/Bị từ chối của setup đang xem — chuyển sang Chờ duyệt (SUBMITTED). */
  const handleSubmitForApproval = async () => {
    if (submittableCount === 0) return;
    setSubmittingGrades(true);
    setError(null);
    try {
      await submitGradesForApproval({ gradeEntryIds: submittableEntryIds, gradeEvaluationResultIds: submittableResultIds });
      setSheetVersion((v) => v + 1);
      showToast("Đã gửi duyệt điểm thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Gửi duyệt điểm thất bại.");
    } finally {
      setSubmittingGrades(false);
    }
  };

  return (
    <div className="space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="flex items-center justify-between flex-wrap gap-2">
        <span className="text-xs font-bold text-slate-700 font-display">
          {showComparison ? "Tổng hợp điểm qua các kỳ" : "Bảng nhập điểm"}
        </span>
        <div className="flex items-center gap-2">
          {!showComparison && (
            <Select
              value={selectedSetupId ?? ""}
              onChange={(e) => setSelectedSetupId(e.target.value ? Number(e.target.value) : null)}
              className={inputClass}
            >
              <option value="">-- Chọn kỳ + Giữa/Cuối kỳ --</option>
              {setups.map((s) => (
                <option key={s.id} value={s.id}>
                  {setupLabel(s)}
                </option>
              ))}
            </Select>
          )}
          <Button type="button" size="sm" variant="secondary" onClick={() => setShowComparison((v) => !v)}>
            <LineChart className="w-3.5 h-3.5" />
            {showComparison ? "Quay lại nhập điểm" : "Xem tổng hợp qua các kỳ"}
          </Button>
        </div>
      </div>

      {showComparison ? (
        <ClassGradeComparisonTable classId={classId} enrollments={enrollments} />
      ) : (
        <>
      {canManage && !readOnly && (
        <div className="flex gap-2 flex-wrap items-center">
          <Button type="button" size="sm" variant="secondary" onClick={() => setShowSetupForm((v) => !v)}>
            <Plus className="w-3.5 h-3.5" />
            Thêm setup sổ điểm
          </Button>
          {selectedSetupId && (
            <>
              <Button type="button" size="sm" variant="secondary" onClick={() => setShowComponentForm((v) => !v)}>
                <Plus className="w-3.5 h-3.5" />
                Thêm đầu điểm
              </Button>
              <Button type="button" size="sm" variant="secondary" onClick={handleDeleteSetup} className="text-rose-600 hover:bg-rose-50">
                <X className="w-3.5 h-3.5" />
                Xoá setup này
              </Button>
            </>
          )}
        </div>
      )}
      {canManage && !readOnly && selectedSetupId && gradeComponents.length > 0 && (
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
      <Modal open={showSetupForm} onClose={() => setShowSetupForm(false)} title="Thêm setup sổ điểm">
        <CreateSetupForm
          classId={classId}
          siteId={siteId}
          onDone={(s) => {
            setSetups((prev) => [...prev, s]);
            setShowSetupForm(false);
            showToast("Đã tạo setup sổ điểm thành công!");
          }}
          onCancel={() => setShowSetupForm(false)}
        />
      </Modal>
      <Modal open={showComponentForm && !!selectedSetupId} onClose={() => setShowComponentForm(false)} title="Thêm đầu điểm">
        {selectedSetupId && (
          <CreateComponentForm
            setupId={selectedSetupId}
            onDone={(c) => {
              setGradeComponents((prev) => [...prev, c]);
              setShowComponentForm(false);
              showToast("Đã tạo đầu điểm thành công!");
            }}
            onCancel={() => setShowComponentForm(false)}
          />
        )}
      </Modal>

      {selectedSetupId ? (
        gradeComponents.length === 0 ? (
          <p className="text-xs text-slate-400 italic p-6 text-center">
            Setup sổ điểm này chưa có đầu điểm nào được cấu hình{canManage ? " — dùng nút \"Thêm đầu điểm\" ở trên." : "."}
          </p>
        ) : (
          <GradeSheetTable
            key={`${classId}-${selectedSetupId}-${sheetVersion}`}
            classId={classId}
            setupId={selectedSetupId}
            components={gradeComponents}
            enrollments={enrollments}
            readOnly={readOnly}
            onLoaded={(entries, results) => {
              setLoadedEntries(entries);
              setLoadedResults(results);
            }}
          />
        )
      ) : (
        <p className="text-xs text-slate-400 italic p-6 text-center">Chọn setup sổ điểm để bắt đầu nhập điểm.</p>
      )}

      {!readOnly && selectedSetupId && gradeComponents.length > 0 && (
        <div className="flex justify-end">
          <Button type="button" size="sm" variant="primary" disabled={submittingGrades || submittableCount === 0} onClick={handleSubmitForApproval}>
            <Send className="w-3.5 h-3.5" />
            {submittingGrades ? "Đang gửi duyệt..." : submittableCount === 0 ? "Không còn điểm cần gửi duyệt" : `Gửi duyệt (${submittableCount})`}
          </Button>
        </div>
      )}

      {!readOnly && selectedSetupId && gradeComponents.length > 0 && (
        <GradeExcelImportPanel
          classId={classId}
          setupId={selectedSetupId}
          components={gradeComponents}
          enrollments={enrollments}
          onImported={() => {
            setSheetVersion((v) => v + 1);
            showToast("Đã nhập điểm từ Excel thành công!");
          }}
        />
      )}
        </>
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

function CreateSetupForm({ classId, siteId, onDone, onCancel }: { classId: number; siteId: number; onDone: (s: GradeComponentSetupResponse) => void; onCancel: () => void }) {
  const [terms, setTerms] = useState<AcademicTermResponse[]>([]);
  const [form, setForm] = useState({
    academicTermId: "",
    evaluationType: "MID_TERM" as CreateGradeComponentSetupRequest["evaluationType"],
    weightInFinal: "",
    rosterAsOfDate: new Date().toISOString().slice(0, 10),
    commentRequired: false
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listAcademicTerms(siteId).then(setTerms).catch(() => undefined);
  }, [siteId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.academicTermId) {
      setError("Vui lòng chọn kỳ học.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateGradeComponentSetupRequest = {
        academicTermId: Number(form.academicTermId),
        evaluationType: form.evaluationType,
        weightInFinal: form.weightInFinal ? Number(form.weightInFinal) : undefined,
        rosterAsOfDate: form.rosterAsOfDate,
        commentRequired: form.commentRequired
      };
      const created = await createGradeComponentSetup(classId, request);
      onDone(created);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tạo setup sổ điểm thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-white border border-slate-200 rounded-lg p-3 space-y-2">
      {error && <div className="text-[11px] text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
      <div className="grid grid-cols-2 gap-2">
        <Select value={form.academicTermId} onChange={(e) => setForm({ ...form, academicTermId: e.target.value })} className={inputClass}>
          <option value="">-- Chọn kỳ học --</option>
          {terms.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </Select>
        <Select value={form.evaluationType} onChange={(e) => setForm({ ...form, evaluationType: e.target.value as CreateGradeComponentSetupRequest["evaluationType"] })} className={inputClass}>
          <option value="MID_TERM">Giữa kỳ</option>
          <option value="END_TERM">Cuối kỳ</option>
        </Select>
        <input type="number" step="0.01" value={form.weightInFinal} onChange={(e) => setForm({ ...form, weightInFinal: e.target.value })} placeholder="Trọng số (%)" className={inputClass} />
        <DatePicker value={form.rosterAsOfDate} onChange={(v) => setForm({ ...form, rosterAsOfDate: v })} />
      </div>
      <label className="flex items-center gap-2 text-[11px] text-slate-600 cursor-pointer">
        <input type="checkbox" checked={form.commentRequired} onChange={(e) => setForm({ ...form, commentRequired: e.target.checked })} className="h-3.5 w-3.5" />
        Bắt buộc nhập Nhận xét khi nhập Overall/Level
      </label>
      <div className="flex gap-2">
        <Button type="button" size="sm" variant="secondary" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit" size="sm" variant="primary" disabled={submitting}>
          {submitting ? "Đang lưu..." : "Tạo setup"}
        </Button>
      </div>
    </form>
  );
}

function CreateComponentForm({ setupId, onDone, onCancel }: { setupId: number; onDone: (c: GradeEvaluationComponentResponse) => void; onCancel: () => void }) {
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
      const request: CreateGradeEvaluationComponentRequest = {
        code: form.code,
        name: form.name.trim(),
        maxScore: form.maxScore ? Number(form.maxScore) : undefined
      };
      const created = await addGradeEvaluationComponent(setupId, request);
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
        <Select value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} className={inputClass}>
          <option value="SPEAKING">Speaking</option>
          <option value="WRITING">Writing</option>
          <option value="LISTENING">Listening</option>
          <option value="READING">Reading</option>
          <option value="GRAMMAR">Grammar</option>
          <option value="PROJECT">Project</option>
          <option value="OTHER">Khác</option>
        </Select>
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
