import React, { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ClipboardList, Plus, Trash2, ArrowRightCircle } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";
import DatePicker from "@/components/ui/DatePicker";
import Modal from "@/components/ui/Modal";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { useDialog } from "@/components/ui/DialogProvider";
import { listStudents, StudentResponse } from "@/features/student/api";
import {
  AcademicYearResponse,
  ClassResponse,
  CreateEntranceAssessmentComponentRequest,
  EntranceAssessmentComponentResponse,
  EntranceAssessmentResultResponse,
  EntranceAssessmentSetupResponse,
  EntranceScaleType,
  addEntranceAssessmentComponent,
  createEntranceAssessmentSetup,
  deleteEntranceAssessmentComponent,
  deleteEntranceAssessmentResult,
  deleteEntranceAssessmentSetup,
  listAcademicYears,
  listClasses,
  listEntranceAssessmentResults,
  listEntranceAssessmentSetups,
  markEntranceAssessmentResultPlaced,
  updateEntranceAssessmentComponent,
  updateEntranceAssessmentSetup,
  upsertEntranceAssessmentResult
} from "../api";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";
const SCALES: EntranceScaleType[] = ["POINT_10", "PERCENT", "IELTS"];

/**
 * UC-18c: Đánh giá đầu vào & đề xuất xếp lớp (bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng 2026-08-28). Bộ đề đầu vào theo điểm trường +
 * năm học; tự setup kỹ năng + điểm giống Sổ điểm nhưng KHÔNG neo vào
 * lớp/kỳ. Kết quả lưu trình độ/lớp đề xuất + nút chuyển sang xếp lớp
 * (UC-18). Không có quy trình duyệt.
 */
export default function EntranceAssessmentPage() {
  const { t } = useTranslation("academic-curriculum");
  const navigate = useNavigate();
  const { selectedCampusId, hasPermission } = useApp();
  const { confirmDialog } = useDialog();
  const { message: toastMessage, showToast } = useToast();

  const siteId = selectedCampusId !== "ALL" ? Number(selectedCampusId) : null;
  const canConfig = hasPermission("academic.entrance.setup.create") || hasPermission("academic.entrance.setup.update");
  const canScore = hasPermission("academic.entrance.score.manage");

  const [years, setYears] = useState<AcademicYearResponse[]>([]);
  const [yearFilter, setYearFilter] = useState("");
  const [setups, setSetups] = useState<EntranceAssessmentSetupResponse[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [results, setResults] = useState<EntranceAssessmentResultResponse[]>([]);
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [setupModal, setSetupModal] = useState<{ mode: "create" | "edit" } | null>(null);
  const [resultModal, setResultModal] = useState<{ editing: EntranceAssessmentResultResponse | null } | null>(null);

  const selectedSetup = useMemo(() => setups.find((s) => s.id === selectedId) ?? null, [setups, selectedId]);

  useEffect(() => {
    listAcademicYears().then(setYears).catch(() => setYears([]));
  }, []);

  const loadSetups = () => {
    if (siteId == null) return;
    setLoading(true);
    setError(null);
    listEntranceAssessmentSetups(siteId, yearFilter ? Number(yearFilter) : undefined)
      .then((res) => {
        setSetups(res);
        if (res.length > 0 && (selectedId == null || !res.some((s) => s.id === selectedId))) setSelectedId(res[0].id);
        if (res.length === 0) setSelectedId(null);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("entranceAssessment.loadFailed")))
      .finally(() => setLoading(false));
  };
  useEffect(loadSetups, [siteId, yearFilter]);

  useEffect(() => {
    if (siteId != null) listClasses({ siteId }).then(setClasses).catch(() => setClasses([]));
  }, [siteId]);

  const loadResults = () => {
    if (selectedId == null) {
      setResults([]);
      return;
    }
    listEntranceAssessmentResults(selectedId).then(setResults).catch(() => setResults([]));
  };
  useEffect(loadResults, [selectedId]);

  if (siteId == null) {
    return (
      <div className="space-y-6">
        <PageHeader t={t} />
        <div className="text-xs text-amber-700 bg-amber-50 border border-amber-100 p-3 rounded-lg">
          {t("entranceAssessment.selectSitePrompt")}
        </div>
      </div>
    );
  }

  const handleDeleteSetup = async () => {
    if (!selectedSetup) return;
    if (!(await confirmDialog(t("entranceAssessment.deleteSetupConfirm", { name: selectedSetup.name }), { danger: true }))) return;
    try {
      await deleteEntranceAssessmentSetup(selectedSetup.id);
      setSelectedId(null);
      loadSetups();
      showToast(t("entranceAssessment.deletedToast"));
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : t("entranceAssessment.saveFailed"));
    }
  };

  const handleMarkPlaced = async (r: EntranceAssessmentResultResponse) => {
    try {
      await markEntranceAssessmentResultPlaced(r.id);
      showToast(t("entranceAssessment.markedPlacedToast"));
      navigate("/academic/classes");
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : t("entranceAssessment.saveFailed"));
    }
  };

  const handleDeleteResult = async (r: EntranceAssessmentResultResponse) => {
    if (!(await confirmDialog(t("entranceAssessment.deleteResultConfirm", { name: r.candidateName }), { danger: true }))) return;
    try {
      await deleteEntranceAssessmentResult(r.id);
      loadResults();
      showToast(t("entranceAssessment.deletedToast"));
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : t("entranceAssessment.saveFailed"));
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader t={t} />
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        {/* Danh sách bộ đề */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col min-h-[520px]">
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 space-y-2">
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-bold text-slate-700 font-display">{t("entranceAssessment.setupListTitle")}</span>
              {hasPermission("academic.entrance.setup.create") && (
                <Button size="sm" variant="primary" onClick={() => setSetupModal({ mode: "create" })}>
                  <Plus className="w-3.5 h-3.5" />
                  {t("entranceAssessment.addSetup")}
                </Button>
              )}
            </div>
            <Select value={yearFilter} onChange={(e) => setYearFilter(e.target.value)} className={inputClass}>
              <option value="">{t("entranceAssessment.allYears")}</option>
              {years.map((y) => (
                <option key={y.id} value={y.id}>
                  {y.code} — {y.name}
                </option>
              ))}
            </Select>
          </div>
          <div className="divide-y divide-slate-100 overflow-y-auto flex-1 max-h-[620px]">
            {loading ? (
              <p className="p-6 text-xs text-slate-400">{t("entranceAssessment.loading")}</p>
            ) : setups.length === 0 ? (
              <div className="p-8 text-center text-xs text-slate-400 italic flex flex-col items-center gap-2">
                <ClipboardList className="w-8 h-8 text-slate-300" />
                {t("entranceAssessment.empty")}
              </div>
            ) : (
              setups.map((s) => (
                <button
                  key={s.id}
                  onClick={() => setSelectedId(s.id)}
                  className={`w-full text-left p-4 border-l-4 transition-all ${
                    s.id === selectedId ? "bg-slate-50/90 border-brand-orange" : "hover:bg-slate-50/40 border-transparent"
                  }`}
                >
                  <div className="text-xs font-bold text-slate-900">{s.name}</div>
                  <div className="text-[10px] text-slate-400 mt-1">
                    {s.academicYearCode} · {t(`entranceAssessment.scale.${s.scaleType}`)} · {s.components.length} {t("entranceAssessment.componentUnit")}
                  </div>
                </button>
              ))
            )}
          </div>
        </div>

        {/* Chi tiết bộ đề */}
        <div className="lg:col-span-3 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col min-h-[520px]">
          {!selectedSetup ? (
            <div className="flex-1 flex flex-col items-center justify-center p-12 text-center text-slate-400 space-y-3">
              <ClipboardList className="w-12 h-12 text-slate-300" />
              <p className="text-xs">{t("entranceAssessment.pickSetupPrompt")}</p>
            </div>
          ) : (
            <div className="flex-1 overflow-y-auto p-5 space-y-5 max-h-[720px]">
              <div className="flex items-start justify-between gap-2 flex-wrap">
                <div>
                  <h2 className="text-sm font-bold text-slate-800">{selectedSetup.name}</h2>
                  <p className="text-[11px] text-slate-500 mt-0.5">
                    {selectedSetup.siteName} · {selectedSetup.academicYearCode} · {t(`entranceAssessment.scale.${selectedSetup.scaleType}`)}
                  </p>
                </div>
                {canConfig && (
                  <div className="flex gap-2">
                    {hasPermission("academic.entrance.setup.update") && (
                      <button onClick={() => setSetupModal({ mode: "edit" })} className="text-brand-red text-[11px] font-bold hover:underline">
                        {t("entranceAssessment.editSetup")}
                      </button>
                    )}
                    {hasPermission("academic.entrance.setup.delete") && (
                      <button onClick={handleDeleteSetup} className="text-rose-500 hover:text-rose-700">
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    )}
                  </div>
                )}
              </div>

              <ComponentsSection
                t={t}
                setup={selectedSetup}
                canConfig={hasPermission("academic.entrance.setup.update")}
                onChanged={loadSetups}
                showToast={showToast}
              />

              <ResultsSection
                t={t}
                setup={selectedSetup}
                results={results}
                canScore={canScore}
                onAdd={() => setResultModal({ editing: null })}
                onEdit={(r) => setResultModal({ editing: r })}
                onDelete={handleDeleteResult}
                onMarkPlaced={handleMarkPlaced}
              />
            </div>
          )}
        </div>
      </div>

      {setupModal && (
        <SetupFormModal
          t={t}
          siteId={siteId}
          years={years}
          initial={setupModal.mode === "edit" ? selectedSetup : null}
          onClose={() => setSetupModal(null)}
          onSaved={(saved) => {
            setSetupModal(null);
            setSelectedId(saved.id);
            loadSetups();
            showToast(t("entranceAssessment.savedToast"));
          }}
        />
      )}

      {resultModal && selectedSetup && (
        <ResultFormModal
          t={t}
          setup={selectedSetup}
          siteId={siteId}
          classes={classes}
          initial={resultModal.editing}
          onClose={() => setResultModal(null)}
          onSaved={() => {
            setResultModal(null);
            loadResults();
            showToast(t("entranceAssessment.savedToast"));
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

function PageHeader({ t }: { t: (k: string) => string }) {
  return (
    <div className="border-b border-slate-200 pb-4">
      <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">{t("entranceAssessment.pageTitle")}</h1>
      <p className="text-xs text-slate-500 mt-1">{t("entranceAssessment.pageDescription")}</p>
    </div>
  );
}

// ===================== Đầu điểm =====================

function ComponentsSection({
  t,
  setup,
  canConfig,
  onChanged,
  showToast
}: {
  t: (k: string, o?: Record<string, unknown>) => string;
  setup: EntranceAssessmentSetupResponse;
  canConfig: boolean;
  onChanged: () => void;
  showToast: (m: string) => void;
}) {
  const { confirmDialog } = useDialog();
  const [adding, setAdding] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);

  const submit = async (
    values: CreateEntranceAssessmentComponentRequest,
    existing?: EntranceAssessmentComponentResponse
  ) => {
    try {
      if (existing) {
        await updateEntranceAssessmentComponent(existing.id, {
          name: values.name,
          maxScore: values.maxScore,
          skillId: values.skillId ?? null,
          displayOrder: values.displayOrder
        });
      } else {
        await addEntranceAssessmentComponent(setup.id, values);
      }
      setAdding(false);
      setEditingId(null);
      onChanged();
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : t("entranceAssessment.saveFailed"));
    }
  };

  const remove = async (c: EntranceAssessmentComponentResponse) => {
    if (!(await confirmDialog(t("entranceAssessment.deleteComponentConfirm", { name: c.name }), { danger: true }))) return;
    try {
      await deleteEntranceAssessmentComponent(c.id);
      onChanged();
    } catch (err) {
      showToast(err instanceof ApiError ? err.message : t("entranceAssessment.saveFailed"));
    }
  };

  return (
    <section className="space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-bold uppercase text-slate-500">{t("entranceAssessment.componentsTitle")}</span>
        {canConfig && !adding && (
          <Button size="sm" variant="secondary" onClick={() => setAdding(true)}>
            <Plus className="w-3.5 h-3.5" />
            {t("entranceAssessment.addComponent")}
          </Button>
        )}
      </div>

      {setup.components.length === 0 && !adding && (
        <p className="text-xs text-slate-400 italic">{t("entranceAssessment.noComponents")}</p>
      )}

      <div className="space-y-2">
        {setup.components.map((c) =>
          editingId === c.id ? (
            <ComponentForm key={c.id} t={t} initial={c} onCancel={() => setEditingId(null)} onSubmit={(v) => submit(v, c)} />
          ) : (
            <div key={c.id} className="border border-slate-200 rounded-lg p-3 flex items-center justify-between gap-2 text-xs">
              <div>
                <span className="font-mono font-bold text-slate-800">{c.code}</span>
                <span className="text-slate-600"> — {c.name}</span>
                <span className="text-slate-400"> · max {c.maxScore}</span>
              </div>
              {canConfig && (
                <div className="flex gap-2 shrink-0">
                  <button onClick={() => setEditingId(c.id)} className="text-brand-red font-bold text-[11px] hover:underline">
                    {t("entranceAssessment.edit")}
                  </button>
                  <button onClick={() => remove(c)} className="text-rose-500 hover:text-rose-700">
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              )}
            </div>
          )
        )}
        {adding && <ComponentForm t={t} onCancel={() => setAdding(false)} onSubmit={(v) => submit(v)} />}
      </div>
    </section>
  );
}

function ComponentForm({
  t,
  initial,
  onCancel,
  onSubmit
}: {
  t: (k: string) => string;
  initial?: EntranceAssessmentComponentResponse;
  onCancel: () => void;
  onSubmit: (values: CreateEntranceAssessmentComponentRequest) => void;
}) {
  const [code, setCode] = useState(initial?.code ?? "");
  const [name, setName] = useState(initial?.name ?? "");
  const [maxScore, setMaxScore] = useState(String(initial?.maxScore ?? "10"));
  const [displayOrder, setDisplayOrder] = useState(String(initial?.displayOrder ?? "0"));
  const isEdit = !!initial;

  return (
    <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 grid grid-cols-2 gap-3">
      <div>
        <label className={labelClass}>{t("entranceAssessment.form.componentCode")}</label>
        <input value={code} disabled={isEdit} onChange={(e) => setCode(e.target.value)} className={`${inputClass} font-mono`} />
      </div>
      <div>
        <label className={labelClass}>{t("entranceAssessment.form.componentName")}</label>
        <input value={name} onChange={(e) => setName(e.target.value)} className={inputClass} />
      </div>
      <div>
        <label className={labelClass}>{t("entranceAssessment.form.maxScore")}</label>
        <input type="number" value={maxScore} onChange={(e) => setMaxScore(e.target.value)} className={inputClass} />
      </div>
      <div>
        <label className={labelClass}>{t("entranceAssessment.form.displayOrder")}</label>
        <input type="number" value={displayOrder} onChange={(e) => setDisplayOrder(e.target.value)} className={inputClass} />
      </div>
      <div className="col-span-2 flex gap-2">
        <Button type="button" size="sm" variant="secondary" onClick={onCancel}>
          {t("entranceAssessment.form.cancel")}
        </Button>
        <Button
          type="button"
          size="sm"
          variant="primary"
          disabled={(!isEdit && !code.trim()) || !name.trim() || !(Number(maxScore) > 0)}
          onClick={() =>
            onSubmit({
              code: code.trim(),
              name: name.trim(),
              maxScore: Number(maxScore),
              displayOrder: Number(displayOrder) || 0
            })
          }
        >
          {t("entranceAssessment.form.save")}
        </Button>
      </div>
    </div>
  );
}

// ===================== Kết quả thí sinh =====================

function ResultsSection({
  t,
  setup,
  results,
  canScore,
  onAdd,
  onEdit,
  onDelete,
  onMarkPlaced
}: {
  t: (k: string, o?: Record<string, unknown>) => string;
  setup: EntranceAssessmentSetupResponse;
  results: EntranceAssessmentResultResponse[];
  canScore: boolean;
  onAdd: () => void;
  onEdit: (r: EntranceAssessmentResultResponse) => void;
  onDelete: (r: EntranceAssessmentResultResponse) => void;
  onMarkPlaced: (r: EntranceAssessmentResultResponse) => void;
}) {
  return (
    <section className="space-y-2 border-t border-slate-100 pt-4">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-bold uppercase text-slate-500">
          {t("entranceAssessment.resultsTitle", { count: results.length })}
        </span>
        {canScore && setup.components.length > 0 && (
          <Button size="sm" variant="secondary" onClick={onAdd}>
            <Plus className="w-3.5 h-3.5" />
            {t("entranceAssessment.addResult")}
          </Button>
        )}
      </div>

      {setup.components.length === 0 ? (
        <p className="text-xs text-amber-600 italic">{t("entranceAssessment.needComponentsFirst")}</p>
      ) : results.length === 0 ? (
        <p className="text-xs text-slate-400 italic">{t("entranceAssessment.noResults")}</p>
      ) : (
        <div className="space-y-2">
          {results.map((r) => (
            <div key={r.id} className="border border-slate-200 rounded-lg p-3 text-xs space-y-1">
              <div className="flex items-center justify-between gap-2 flex-wrap">
                <div className="font-bold text-slate-800">
                  {r.candidateName}
                  <span className="ml-2 font-normal text-slate-400">
                    {r.studentId ? t("entranceAssessment.subjectStudentTag") : t("entranceAssessment.subjectLeadTag")}
                  </span>
                  {r.placedFlag && (
                    <span className="ml-2 text-[10px] font-bold text-emerald-600 bg-emerald-50 border border-emerald-100 px-1.5 py-0.5 rounded">
                      {t("entranceAssessment.placedBadge")}
                    </span>
                  )}
                </div>
                {canScore && (
                  <div className="flex items-center gap-2 shrink-0">
                    {!r.placedFlag && (
                      <button onClick={() => onMarkPlaced(r)} className="text-emerald-600 hover:text-emerald-800 text-[11px] font-bold flex items-center gap-1">
                        <ArrowRightCircle className="w-3.5 h-3.5" />
                        {t("entranceAssessment.toPlacement")}
                      </button>
                    )}
                    <button onClick={() => onEdit(r)} className="text-brand-red font-bold text-[11px] hover:underline">
                      {t("entranceAssessment.edit")}
                    </button>
                    <button onClick={() => onDelete(r)} className="text-rose-500 hover:text-rose-700">
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                )}
              </div>
              <div className="text-slate-500">
                {t("entranceAssessment.assessedOn", { date: r.assessedDate })}
                {r.overallScore != null && ` · ${t("entranceAssessment.overallShort")}: ${r.overallScore}`}
                {r.recommendedLevel && ` · ${r.recommendedLevel}`}
                {r.recommendedClassName && ` → ${r.recommendedClassName}`}
              </div>
              <div className="flex flex-wrap gap-x-3 gap-y-0.5 text-slate-500">
                {r.scores.map((s) => (
                  <span key={s.componentId}>
                    {s.componentCode}: {s.absenceFlag ? t("entranceAssessment.absentShort") : s.score ?? "—"}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

// ===================== Modals =====================

function SetupFormModal({
  t,
  siteId,
  years,
  initial,
  onClose,
  onSaved
}: {
  t: (k: string) => string;
  siteId: number;
  years: AcademicYearResponse[];
  initial: EntranceAssessmentSetupResponse | null;
  onClose: () => void;
  onSaved: (s: EntranceAssessmentSetupResponse) => void;
}) {
  const [name, setName] = useState(initial?.name ?? "");
  const [academicYearId, setAcademicYearId] = useState(initial ? String(initial.academicYearId) : "");
  const [scaleType, setScaleType] = useState<EntranceScaleType>(initial?.scaleType ?? "POINT_10");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isEdit = !!initial;

  const submit = async () => {
    if (!name.trim() || (!isEdit && !academicYearId)) {
      setError(t("entranceAssessment.form.requiredError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const saved = isEdit
        ? await updateEntranceAssessmentSetup(initial!.id, { name: name.trim(), scaleType })
        : await createEntranceAssessmentSetup({ siteId, academicYearId: Number(academicYearId), name: name.trim(), scaleType });
      onSaved(saved);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("entranceAssessment.saveFailed"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={isEdit ? t("entranceAssessment.editSetupTitle") : t("entranceAssessment.addSetupTitle")}>
      <div className="space-y-3">
        {error && <p className="text-xs text-rose-600">{error}</p>}
        <div>
          <label className={labelClass}>{t("entranceAssessment.form.setupName")}</label>
          <input value={name} onChange={(e) => setName(e.target.value)} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("entranceAssessment.form.academicYear")}</label>
          <Select value={academicYearId} onChange={(e) => setAcademicYearId(e.target.value)} className={inputClass} disabled={isEdit}>
            <option value="">{t("entranceAssessment.form.academicYearPlaceholder")}</option>
            {years.map((y) => (
              <option key={y.id} value={y.id}>
                {y.code} — {y.name}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className={labelClass}>{t("entranceAssessment.form.scaleType")}</label>
          <Select value={scaleType} onChange={(e) => setScaleType(e.target.value as EntranceScaleType)} className={inputClass}>
            {SCALES.map((s) => (
              <option key={s} value={s}>
                {t(`entranceAssessment.scale.${s}`)}
              </option>
            ))}
          </Select>
        </div>
        <div className="flex gap-2">
          <Button size="sm" variant="secondary" onClick={onClose}>
            {t("entranceAssessment.form.cancel")}
          </Button>
          <Button size="sm" variant="primary" disabled={submitting} onClick={submit}>
            {t("entranceAssessment.form.save")}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

function ResultFormModal({
  t,
  setup,
  siteId,
  classes,
  initial,
  onClose,
  onSaved
}: {
  t: (k: string, o?: Record<string, unknown>) => string;
  setup: EntranceAssessmentSetupResponse;
  siteId: number;
  classes: ClassResponse[];
  initial: EntranceAssessmentResultResponse | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [subjectKind, setSubjectKind] = useState<"STUDENT" | "LEAD">(initial?.leadId ? "LEAD" : "STUDENT");
  const [studentId, setStudentId] = useState<number | null>(initial?.studentId ?? null);
  const [leadId, setLeadId] = useState(initial?.leadId ? String(initial.leadId) : "");
  const [candidateName, setCandidateName] = useState(initial?.candidateName ?? "");
  const [assessedDate, setAssessedDate] = useState(initial?.assessedDate ?? new Date().toISOString().slice(0, 10));
  const [overallScore, setOverallScore] = useState(initial?.overallScore != null ? String(initial.overallScore) : "");
  const [recommendedLevel, setRecommendedLevel] = useState(initial?.recommendedLevel ?? "");
  const [recommendedClassId, setRecommendedClassId] = useState(initial?.recommendedClassId ? String(initial.recommendedClassId) : "");
  const [note, setNote] = useState(initial?.note ?? "");
  const [scores, setScores] = useState<Record<number, { score: string; absence: boolean }>>(() => {
    const map: Record<number, { score: string; absence: boolean }> = {};
    setup.components.forEach((c) => {
      const existing = initial?.scores.find((s) => s.componentId === c.id);
      map[c.id] = { score: existing?.score != null ? String(existing.score) : "", absence: existing?.absenceFlag ?? false };
    });
    return map;
  });
  const [studentQuery, setStudentQuery] = useState("");
  const [studentResults, setStudentResults] = useState<StudentResponse[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const searchStudent = (q: string) => {
    setStudentQuery(q);
    if (!q.trim()) {
      setStudentResults([]);
      return;
    }
    listStudents(q.trim(), siteId).then((res) => setStudentResults(res.slice(0, 8))).catch(() => setStudentResults([]));
  };

  const submit = async () => {
    const hasStudent = subjectKind === "STUDENT" && studentId != null;
    const hasLead = subjectKind === "LEAD" && !!leadId.trim();
    if (!candidateName.trim() || !assessedDate || (!hasStudent && !hasLead)) {
      setError(t("entranceAssessment.form.resultRequiredError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await upsertEntranceAssessmentResult(setup.id, {
        studentId: hasStudent ? studentId : null,
        leadId: hasLead ? Number(leadId) : null,
        candidateName: candidateName.trim(),
        assessedDate,
        overallScore: overallScore ? Number(overallScore) : null,
        recommendedLevel: recommendedLevel.trim() || null,
        recommendedClassId: recommendedClassId ? Number(recommendedClassId) : null,
        note: note.trim() || null,
        scores: setup.components.map((c) => ({
          componentId: c.id,
          score: scores[c.id]?.score ? Number(scores[c.id].score) : null,
          absenceFlag: scores[c.id]?.absence ?? false
        }))
      });
      onSaved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("entranceAssessment.saveFailed"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={initial ? t("entranceAssessment.editResultTitle") : t("entranceAssessment.addResultTitle")} size="lg">
      <div className="space-y-3">
        {error && <p className="text-xs text-rose-600">{error}</p>}

        <div className="flex gap-4 text-xs">
          <label className="flex items-center gap-1.5">
            <input type="radio" checked={subjectKind === "STUDENT"} onChange={() => setSubjectKind("STUDENT")} />
            {t("entranceAssessment.form.subjectStudent")}
          </label>
          <label className="flex items-center gap-1.5">
            <input type="radio" checked={subjectKind === "LEAD"} onChange={() => setSubjectKind("LEAD")} />
            {t("entranceAssessment.form.subjectLead")}
          </label>
        </div>

        {subjectKind === "STUDENT" ? (
          <div className="relative">
            <label className={labelClass}>{t("entranceAssessment.form.studentSearch")}</label>
            <input
              value={studentQuery}
              onChange={(e) => searchStudent(e.target.value)}
              placeholder={t("entranceAssessment.form.studentSearchPlaceholder")}
              className={inputClass}
            />
            {studentId != null && <p className="text-[10px] text-emerald-600 mt-1">{t("entranceAssessment.form.studentPicked", { name: candidateName })}</p>}
            {studentResults.length > 0 && (
              <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg divide-y divide-slate-100 max-h-56 overflow-y-auto">
                {studentResults.map((s) => (
                  <button
                    key={s.id}
                    type="button"
                    onClick={() => {
                      setStudentId(s.id);
                      setCandidateName(s.fullName ?? s.studentCode);
                      setStudentResults([]);
                      setStudentQuery(s.fullName ?? s.studentCode);
                    }}
                    className="w-full text-left px-3 py-2 hover:bg-slate-50 text-xs"
                  >
                    {s.fullName} <span className="text-slate-400">({s.studentCode})</span>
                  </button>
                ))}
              </div>
            )}
          </div>
        ) : (
          <div>
            <label className={labelClass}>{t("entranceAssessment.form.leadId")}</label>
            <input value={leadId} onChange={(e) => setLeadId(e.target.value)} type="number" className={inputClass} />
            <p className="text-[10px] text-slate-400 mt-1">{t("entranceAssessment.form.leadIdHint")}</p>
          </div>
        )}

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("entranceAssessment.form.candidateName")}</label>
            <input value={candidateName} onChange={(e) => setCandidateName(e.target.value)} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("entranceAssessment.form.assessedDate")}</label>
            <DatePicker value={assessedDate} onChange={setAssessedDate} />
          </div>
        </div>

        <div>
          <span className={labelClass}>{t("entranceAssessment.form.scores")}</span>
          <div className="space-y-1.5">
            {setup.components.map((c) => (
              <div key={c.id} className="flex items-center gap-2 text-xs">
                <span className="w-32 shrink-0 truncate">
                  {c.code} <span className="text-slate-400">/{c.maxScore}</span>
                </span>
                <input
                  type="number"
                  disabled={scores[c.id]?.absence}
                  value={scores[c.id]?.score ?? ""}
                  onChange={(e) => setScores({ ...scores, [c.id]: { score: e.target.value, absence: scores[c.id]?.absence ?? false } })}
                  className={`${inputClass} max-w-[110px]`}
                />
                <label className="flex items-center gap-1 text-[10px] text-slate-500">
                  <input
                    type="checkbox"
                    checked={scores[c.id]?.absence ?? false}
                    onChange={(e) => setScores({ ...scores, [c.id]: { score: scores[c.id]?.score ?? "", absence: e.target.checked } })}
                  />
                  {t("entranceAssessment.form.absent")}
                </label>
              </div>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("entranceAssessment.form.overallScore")}</label>
            <input type="number" value={overallScore} onChange={(e) => setOverallScore(e.target.value)} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("entranceAssessment.form.recommendedLevel")}</label>
            <input value={recommendedLevel} onChange={(e) => setRecommendedLevel(e.target.value)} className={inputClass} />
          </div>
          <div className="col-span-2">
            <label className={labelClass}>{t("entranceAssessment.form.recommendedClass")}</label>
            <Select value={recommendedClassId} onChange={(e) => setRecommendedClassId(e.target.value)} className={inputClass}>
              <option value="">{t("entranceAssessment.form.recommendedClassNone")}</option>
              {classes.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.classCode} — {c.name}
                </option>
              ))}
            </Select>
          </div>
          <div className="col-span-2">
            <label className={labelClass}>{t("entranceAssessment.form.note")}</label>
            <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={2} className={inputClass} />
          </div>
        </div>

        <div className="flex gap-2">
          <Button size="sm" variant="secondary" onClick={onClose}>
            {t("entranceAssessment.form.cancel")}
          </Button>
          <Button size="sm" variant="primary" disabled={submitting} onClick={submit}>
            {t("entranceAssessment.form.save")}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
