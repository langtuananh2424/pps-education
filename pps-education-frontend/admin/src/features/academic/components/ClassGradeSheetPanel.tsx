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

const evaluationTypeLabel: Record<"MID_TERM" | "END_TERM", string> = { MID_TERM: "Giữa kỳ", END_TERM: "Cuối kỳ" };

/** V97: hệ thống không tính OVERALL cả kỳ (bỏ trọng số) — mỗi setup Giữa/Cuối kỳ tự chọn 1 thang điểm riêng, hiển thị song song 2 section. */
const scaleTypeLabels: Record<GradeComponentSetupResponse["scaleType"], string> = {
  POINT_10: "Điểm 10 (thang 0–10)",
  PERCENT: "Điểm % (thang 0–100)",
  IELTS: "Thang IELTS (band 1.0–9.0)"
};

interface ClassGradeSheetPanelProps {
  classId: number;
  siteId: number;
  /** SITE_MANAGER xem tab "Sổ điểm" ở Quản lý lớp học chỉ để tham khảo — không nhập/sửa điểm được (khác GradesPage cũ, đã bỏ sót readOnly khi tách component này). */
  readOnly?: boolean;
}

/**
 * "Bảng nhập điểm (UC-19)" của đúng 1 lớp — tách khỏi GradesPage để dùng lại được ở tab "Sổ điểm"
 * trong ClassDetailPanel (UC-18), tránh lặp lại toàn bộ logic setup sổ điểm/đầu điểm/nhập điểm 2 nơi.
 * V97 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): chọn 1 kỳ học -> hiển thị song song 2
 * section Giữa kỳ/Cuối kỳ (không còn gộp chung 1 dropdown "kỳ + Giữa/Cuối kỳ", không còn tính OVERALL
 * cả kỳ từ 2 phần này -- mỗi setup có Overall riêng theo thang điểm riêng).
 */
export default function ClassGradeSheetPanel({ classId, siteId, readOnly = false }: ClassGradeSheetPanelProps) {
  const { hasPermission } = useApp();
  const canManage = hasPermission("academic.grade.setup.create") || hasPermission("academic.grade.manage");
  const [enrollments, setEnrollments] = useState<ClassEnrollmentResponse[]>([]);
  const [terms, setTerms] = useState<AcademicTermResponse[]>([]);
  const [selectedTermId, setSelectedTermId] = useState<number | null>(null);
  const [setups, setSetups] = useState<GradeComponentSetupResponse[]>([]);
  const [showComparison, setShowComparison] = useState(false);
  const { message: toastMessage, showToast } = useToast();

  useEffect(() => {
    listClassEnrollments(classId).then(setEnrollments).catch(() => undefined);
  }, [classId]);

  useEffect(() => {
    listAcademicTerms(siteId)
      .then((tList) => {
        setTerms(tList);
        if (tList.length > 0) {
          setSelectedTermId(tList[0].id);
        }
      })
      .catch(() => undefined);
  }, [siteId]);

  useEffect(() => {
    listGradeComponentSetups(classId).then(setSetups).catch(() => undefined);
  }, [classId]);

  const selectedTerm = terms.find((t) => t.id === selectedTermId) ?? null;

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <span className="text-xs font-bold text-slate-700 font-display">
          {showComparison ? "Tổng hợp điểm qua các kỳ" : "Bảng nhập điểm"}
        </span>
        <div className="flex items-center gap-2">
          {!showComparison && (
            <Select
              value={selectedTermId ?? ""}
              onChange={(e) => setSelectedTermId(e.target.value ? Number(e.target.value) : null)}
              className={inputClass}
            >
              <option value="">-- Chọn kỳ học --</option>
              {terms.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
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
      ) : selectedTermId ? (
        <div className="space-y-4">
          {(["MID_TERM", "END_TERM"] as const).map((evaluationType) => (
            <GradeSetupSection
              key={evaluationType}
              classId={classId}
              academicTermId={selectedTermId}
              academicTermName={selectedTerm?.name ?? ""}
              evaluationType={evaluationType}
              setup={setups.find((s) => s.academicTermId === selectedTermId && s.evaluationType === evaluationType)}
              enrollments={enrollments}
              readOnly={readOnly}
              canManage={canManage}
              showToast={showToast}
              onSetupCreated={(s) => setSetups((prev) => [...prev, s])}
              onSetupDeleted={(id) => setSetups((prev) => prev.filter((s) => s.id !== id))}
            />
          ))}
        </div>
      ) : (
        <p className="text-xs text-slate-400 italic p-6 text-center">Chọn kỳ học để xem/nhập điểm Giữa kỳ và Cuối kỳ.</p>
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

interface GradeSetupSectionProps {
  classId: number;
  academicTermId: number;
  academicTermName: string;
  evaluationType: "MID_TERM" | "END_TERM";
  setup: GradeComponentSetupResponse | undefined;
  enrollments: ClassEnrollmentResponse[];
  readOnly: boolean;
  canManage: boolean;
  showToast: (message: string) => void;
  onSetupCreated: (s: GradeComponentSetupResponse) => void;
  onSetupDeleted: (id: number) => void;
}

/** 1 section = đúng 1 setup (lớp, kỳ học, Giữa/Cuối kỳ) — tự quản lý đầu điểm/bảng nhập/gửi duyệt/import Excel riêng. */
function GradeSetupSection({
  classId,
  academicTermId,
  evaluationType,
  setup,
  enrollments,
  readOnly,
  canManage,
  showToast,
  onSetupCreated,
  onSetupDeleted
}: GradeSetupSectionProps) {
  const [gradeComponents, setGradeComponents] = useState<GradeEvaluationComponentResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showSetupForm, setShowSetupForm] = useState(false);
  const [showComponentForm, setShowComponentForm] = useState(false);
  const [sheetVersion, setSheetVersion] = useState(0);
  const [loadedEntries, setLoadedEntries] = useState<GradeEntryResponse[]>([]);
  const [loadedResults, setLoadedResults] = useState<GradeEvaluationResultResponse[]>([]);
  const [submittingGrades, setSubmittingGrades] = useState(false);
  const { confirmDialog } = useDialog();

  useEffect(() => {
    setGradeComponents([]);
    if (!setup) return;
    listGradeEvaluationComponents(setup.id)
      .then(setGradeComponents)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được đầu điểm."));
  }, [setup?.id]);

  const handleDeleteSetup = async () => {
    if (!setup) return;
    if (
      !(await confirmDialog(`Xoá setup "${evaluationTypeLabel[evaluationType]}"? Chỉ xoá được khi setup này còn rỗng (chưa có đầu điểm/điểm tổng kết).`, {
        danger: true
      }))
    )
      return;
    setError(null);
    try {
      await deleteGradeComponentSetup(setup.id);
      onSetupDeleted(setup.id);
      showToast("Đã xoá setup sổ điểm thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Xoá setup sổ điểm thất bại.");
    }
  };

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
    <div className="border border-slate-200 rounded-2xl p-4 space-y-3">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <span className="text-xs font-bold text-slate-700 font-display">
          {evaluationTypeLabel[evaluationType]}
          {setup && <span className="ml-2 font-normal text-slate-400">— {scaleTypeLabels[setup.scaleType]}</span>}
        </span>
        {canManage && !readOnly && (
          <div className="flex gap-2 flex-wrap items-center">
            {!setup ? (
              <Button type="button" size="sm" variant="secondary" onClick={() => setShowSetupForm((v) => !v)}>
                <Plus className="w-3.5 h-3.5" />
                Tạo setup {evaluationTypeLabel[evaluationType]}
              </Button>
            ) : (
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
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {canManage && !readOnly && setup && gradeComponents.length > 0 && (
        <div className="flex gap-1.5 flex-wrap">
          {gradeComponents.map((c) => (
            <span key={c.id} className="flex items-center gap-1 bg-slate-100 border border-slate-200 text-slate-600 text-[11px] font-semibold px-2 py-1 rounded-lg">
              {c.name} ({c.maxScore})
              <button type="button" onClick={() => handleDeleteComponent(c)} title="Xoá đầu điểm (chỉ khi chưa có điểm nhập)" className="hover:text-rose-600">
                <X className="w-3 h-3" />
              </button>
            </span>
          ))}
        </div>
      )}

      <Modal open={showSetupForm} onClose={() => setShowSetupForm(false)} title={`Tạo setup sổ điểm — ${evaluationTypeLabel[evaluationType]}`}>
        <CreateSetupForm
          classId={classId}
          academicTermId={academicTermId}
          evaluationType={evaluationType}
          onDone={(s) => {
            onSetupCreated(s);
            setShowSetupForm(false);
            showToast("Đã tạo setup sổ điểm thành công!");
          }}
          onCancel={() => setShowSetupForm(false)}
        />
      </Modal>
      <Modal open={showComponentForm && !!setup} onClose={() => setShowComponentForm(false)} title="Thêm đầu điểm">
        {setup && (
          <CreateComponentForm
            setupId={setup.id}
            scaleType={setup.scaleType}
            onDone={(c) => {
              setGradeComponents((prev) => [...prev, c]);
              setShowComponentForm(false);
              showToast("Đã tạo đầu điểm thành công!");
            }}
            onCancel={() => setShowComponentForm(false)}
          />
        )}
      </Modal>

      {!setup ? (
        <p className="text-xs text-slate-400 italic p-4 text-center">
          Chưa có setup sổ điểm cho {evaluationTypeLabel[evaluationType]}
          {canManage ? " — dùng nút phía trên để tạo." : "."}
        </p>
      ) : gradeComponents.length === 0 ? (
        <p className="text-xs text-slate-400 italic p-4 text-center">
          Setup này chưa có đầu điểm nào được cấu hình{canManage ? " — dùng nút \"Thêm đầu điểm\" ở trên." : "."}
        </p>
      ) : (
        <>
          <GradeSheetTable
            key={`${classId}-${setup.id}-${sheetVersion}`}
            classId={classId}
            setupId={setup.id}
            scaleType={setup.scaleType}
            components={gradeComponents}
            enrollments={enrollments}
            readOnly={readOnly}
            onLoaded={(entries, results) => {
              setLoadedEntries(entries);
              setLoadedResults(results);
            }}
          />

          {!readOnly && (
            <div className="flex justify-end">
              <Button type="button" size="sm" variant="primary" disabled={submittingGrades || submittableCount === 0} onClick={handleSubmitForApproval}>
                <Send className="w-3.5 h-3.5" />
                {submittingGrades ? "Đang gửi duyệt..." : submittableCount === 0 ? "Không còn điểm cần gửi duyệt" : `Gửi duyệt (${submittableCount})`}
              </Button>
            </div>
          )}

          {!readOnly && (
            <GradeExcelImportPanel
              classId={classId}
              setupId={setup.id}
              components={gradeComponents}
              onImported={() => {
                setSheetVersion((v) => v + 1);
                showToast("Đã nhập điểm từ Excel thành công!");
              }}
            />
          )}
        </>
      )}
    </div>
  );
}

function CreateSetupForm({
  classId,
  academicTermId,
  evaluationType,
  onDone,
  onCancel
}: {
  classId: number;
  academicTermId: number;
  evaluationType: CreateGradeComponentSetupRequest["evaluationType"];
  onDone: (s: GradeComponentSetupResponse) => void;
  onCancel: () => void;
}) {
  const [form, setForm] = useState({
    scaleType: "POINT_10" as CreateGradeComponentSetupRequest["scaleType"],
    rosterAsOfDate: new Date().toISOString().slice(0, 10),
    commentRequired: false
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateGradeComponentSetupRequest = {
        academicTermId,
        evaluationType,
        scaleType: form.scaleType,
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
        <Select
          value={form.scaleType}
          onChange={(e) => setForm({ ...form, scaleType: e.target.value as CreateGradeComponentSetupRequest["scaleType"] })}
          className={inputClass}
        >
          <option value="POINT_10">Điểm 10</option>
          <option value="PERCENT">Điểm %</option>
          <option value="IELTS">Thang IELTS</option>
        </Select>
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

/** V97: maxScore không còn nhập tay -- tự động khớp thang điểm (scaleType) của setup, tránh 422 do lệch thang. */
function CreateComponentForm({
  setupId,
  scaleType,
  onDone,
  onCancel
}: {
  setupId: number;
  scaleType: GradeComponentSetupResponse["scaleType"];
  onDone: (c: GradeEvaluationComponentResponse) => void;
  onCancel: () => void;
}) {
  const [form, setForm] = useState({ code: "OTHER", name: "" });
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
        name: form.name.trim()
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
      <p className="text-[11px] text-slate-500">
        Thang điểm: <span className="font-semibold text-slate-700">{scaleTypeLabels[scaleType]}</span> — điểm tối đa tự động theo thang của setup.
      </p>
      <div className="grid grid-cols-2 gap-2">
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
