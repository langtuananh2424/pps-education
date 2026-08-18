import React, { useEffect, useState } from "react";
import { LineChart, Plus, Send, X } from "lucide-react";
import { useTranslation } from "react-i18next";
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

type TFunc = (key: string, options?: Record<string, unknown>) => string;

function evaluationTypeLabel(t: TFunc, type: "MID_TERM" | "END_TERM"): string {
  return t(`common.evaluationType.${type}`);
}

/** V97: hệ thống không tính OVERALL cả kỳ (bỏ trọng số) — mỗi setup Giữa/Cuối kỳ tự chọn 1 thang điểm riêng, hiển thị song song 2 section. */
function scaleTypeLabel(t: TFunc, scaleType: GradeComponentSetupResponse["scaleType"]): string {
  const map: Record<GradeComponentSetupResponse["scaleType"], string> = {
    POINT_10: t("sheetPanel.scaleLabelPoint10"),
    PERCENT: t("sheetPanel.scaleLabelPercent"),
    IELTS: t("sheetPanel.scaleLabelIelts")
  };
  return map[scaleType];
}

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
  const { t } = useTranslation("academic-grades");
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
          {showComparison ? t("sheetPanel.titleComparison") : t("sheetPanel.titleEntry")}
        </span>
        <div className="flex items-center gap-2">
          {!showComparison && (
            <Select
              value={selectedTermId ?? ""}
              onChange={(e) => setSelectedTermId(e.target.value ? Number(e.target.value) : null)}
              className={inputClass}
            >
              <option value="">{t("sheetPanel.selectTermPlaceholder")}</option>
              {terms.map((term) => (
                <option key={term.id} value={term.id}>
                  {term.name}
                </option>
              ))}
            </Select>
          )}
          <Button type="button" size="sm" variant="secondary" onClick={() => setShowComparison((v) => !v)}>
            <LineChart className="w-3.5 h-3.5" />
            {showComparison ? t("sheetPanel.backToEntry") : t("sheetPanel.viewComparison")}
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
        <p className="text-xs text-slate-400 italic p-6 text-center">{t("sheetPanel.selectTermPrompt")}</p>
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
  const { t } = useTranslation("academic-grades");

  useEffect(() => {
    setGradeComponents([]);
    if (!setup) return;
    listGradeEvaluationComponents(setup.id)
      .then(setGradeComponents)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("sheetPanel.loadComponentsError")));
  }, [setup?.id]);

  const handleDeleteSetup = async () => {
    if (!setup) return;
    if (
      !(await confirmDialog(t("sheetPanel.confirmDeleteSetup", { label: evaluationTypeLabel(t, evaluationType) }), {
        danger: true
      }))
    )
      return;
    setError(null);
    try {
      await deleteGradeComponentSetup(setup.id);
      onSetupDeleted(setup.id);
      showToast(t("sheetPanel.deleteSetupSuccess"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("sheetPanel.deleteSetupError"));
    }
  };

  const handleDeleteComponent = async (component: GradeEvaluationComponentResponse) => {
    if (!(await confirmDialog(t("sheetPanel.confirmDeleteComponent", { name: component.name }), { danger: true }))) return;
    setError(null);
    try {
      await deleteGradeEvaluationComponent(component.id);
      setGradeComponents((prev) => prev.filter((c) => c.id !== component.id));
      showToast(t("sheetPanel.deleteComponentSuccess"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("sheetPanel.deleteComponentError"));
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
      showToast(t("sheetPanel.submitSuccess"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("sheetPanel.submitError"));
    } finally {
      setSubmittingGrades(false);
    }
  };

  return (
    <div className="border border-slate-200 rounded-2xl p-4 space-y-3">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <span className="text-xs font-bold text-slate-700 font-display">
          {evaluationTypeLabel(t, evaluationType)}
          {setup && <span className="ml-2 font-normal text-slate-400">— {scaleTypeLabel(t, setup.scaleType)}</span>}
        </span>
        {canManage && !readOnly && (
          <div className="flex gap-2 flex-wrap items-center">
            {!setup ? (
              <Button type="button" size="sm" variant="secondary" onClick={() => setShowSetupForm((v) => !v)}>
                <Plus className="w-3.5 h-3.5" />
                {t("sheetPanel.createSetupButton", { label: evaluationTypeLabel(t, evaluationType) })}
              </Button>
            ) : (
              <>
                <Button type="button" size="sm" variant="secondary" onClick={() => setShowComponentForm((v) => !v)}>
                  <Plus className="w-3.5 h-3.5" />
                  {t("sheetPanel.addComponentButton")}
                </Button>
                <Button type="button" size="sm" variant="secondary" onClick={handleDeleteSetup} className="text-rose-600 hover:bg-rose-50">
                  <X className="w-3.5 h-3.5" />
                  {t("sheetPanel.deleteSetupButton")}
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
              <button type="button" onClick={() => handleDeleteComponent(c)} title={t("sheetPanel.deleteComponentTitle")} className="hover:text-rose-600">
                <X className="w-3 h-3" />
              </button>
            </span>
          ))}
        </div>
      )}

      <Modal
        open={showSetupForm}
        onClose={() => setShowSetupForm(false)}
        title={t("sheetPanel.createSetupModalTitle", { label: evaluationTypeLabel(t, evaluationType) })}
      >
        <CreateSetupForm
          classId={classId}
          academicTermId={academicTermId}
          evaluationType={evaluationType}
          onDone={(s) => {
            onSetupCreated(s);
            setShowSetupForm(false);
            showToast(t("sheetPanel.createSetupSuccess"));
          }}
          onCancel={() => setShowSetupForm(false)}
        />
      </Modal>
      <Modal open={showComponentForm && !!setup} onClose={() => setShowComponentForm(false)} title={t("sheetPanel.addComponentModalTitle")}>
        {setup && (
          <CreateComponentForm
            setupId={setup.id}
            scaleType={setup.scaleType}
            onDone={(c) => {
              setGradeComponents((prev) => [...prev, c]);
              setShowComponentForm(false);
              showToast(t("sheetPanel.createComponentSuccess"));
            }}
            onCancel={() => setShowComponentForm(false)}
          />
        )}
      </Modal>

      {!setup ? (
        <p className="text-xs text-slate-400 italic p-4 text-center">
          {t("sheetPanel.noSetupPrefix", { label: evaluationTypeLabel(t, evaluationType) })}
          {canManage ? t("sheetPanel.noSetupWithPermission") : t("sheetPanel.noSetupWithoutPermission")}
        </p>
      ) : gradeComponents.length === 0 ? (
        <p className="text-xs text-slate-400 italic p-4 text-center">
          {t("sheetPanel.noComponentsPrefix")}
          {canManage ? t("sheetPanel.noComponentsWithPermission") : t("sheetPanel.noComponentsWithoutPermission")}
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
                {submittingGrades
                  ? t("sheetPanel.submitting")
                  : submittableCount === 0
                    ? t("sheetPanel.noneToSubmit")
                    : t("sheetPanel.submitCount", { count: submittableCount })}
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
                showToast(t("sheetPanel.importSuccess"));
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
  const { t } = useTranslation("academic-grades");

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
      setError(err instanceof ApiError ? err.message : t("sheetPanel.createSetupError"));
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
          <option value="POINT_10">{t("sheetPanel.scaleOptionPoint10")}</option>
          <option value="PERCENT">{t("sheetPanel.scaleOptionPercent")}</option>
          <option value="IELTS">{t("sheetPanel.scaleOptionIelts")}</option>
        </Select>
        <DatePicker value={form.rosterAsOfDate} onChange={(v) => setForm({ ...form, rosterAsOfDate: v })} />
      </div>
      <label className="flex items-center gap-2 text-[11px] text-slate-600 cursor-pointer">
        <input type="checkbox" checked={form.commentRequired} onChange={(e) => setForm({ ...form, commentRequired: e.target.checked })} className="h-3.5 w-3.5" />
        {t("sheetPanel.commentRequiredCheckbox")}
      </label>
      <div className="flex gap-2">
        <Button type="button" size="sm" variant="secondary" onClick={onCancel}>
          {t("common.cancel")}
        </Button>
        <Button type="submit" size="sm" variant="primary" disabled={submitting}>
          {submitting ? t("common.saving") : t("sheetPanel.createSetupButtonLabel")}
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
  const { t } = useTranslation("academic-grades");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) {
      setError(t("sheetPanel.componentNameRequired"));
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
      setError(err instanceof ApiError ? err.message : t("sheetPanel.createComponentError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-white border border-slate-200 rounded-lg p-3 space-y-2">
      {error && <div className="text-[11px] text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
      <p className="text-[11px] text-slate-500">
        {t("sheetPanel.scaleInfo", { scale: scaleTypeLabel(t, scaleType) })}
      </p>
      <div className="grid grid-cols-2 gap-2">
        <Select value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} className={inputClass}>
          <option value="SPEAKING">{t("sheetPanel.componentCodeSpeaking")}</option>
          <option value="WRITING">{t("sheetPanel.componentCodeWriting")}</option>
          <option value="LISTENING">{t("sheetPanel.componentCodeListening")}</option>
          <option value="READING">{t("sheetPanel.componentCodeReading")}</option>
          <option value="GRAMMAR">{t("sheetPanel.componentCodeGrammar")}</option>
          <option value="PROJECT">{t("sheetPanel.componentCodeProject")}</option>
          <option value="OTHER">{t("sheetPanel.componentCodeOther")}</option>
        </Select>
        <input
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
          placeholder={t("sheetPanel.componentNamePlaceholder")}
          className={inputClass}
        />
      </div>
      <div className="flex gap-2">
        <Button type="button" size="sm" variant="secondary" onClick={onCancel}>
          {t("common.cancel")}
        </Button>
        <Button type="submit" size="sm" variant="primary" disabled={submitting}>
          {submitting ? t("common.saving") : t("sheetPanel.createComponentButtonLabel")}
        </Button>
      </div>
    </form>
  );
}
