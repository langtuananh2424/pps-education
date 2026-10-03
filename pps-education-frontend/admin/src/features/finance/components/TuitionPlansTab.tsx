import React, { useEffect, useState } from "react";
import { Link2, Plus, Save } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { listClasses, listCurriculums, ClassResponse, CurriculumResponse } from "@/features/academic/api";
import {
  PRICING_MODELS,
  PricingModel,
  TuitionPlanAssignmentResponse,
  TuitionPlanResponse,
  assignTuitionPlan,
  createTuitionPlan,
  listTuitionPlanAssignments,
  listTuitionPlans,
  updateTuitionPlanStatus
} from "../api";
import { formatDate, formatVnd, inputClass, labelClass } from "../format";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import DatePicker from "@/components/ui/DatePicker";
import FloatingError from "@/components/ui/FloatingError";
import Modal from "@/components/ui/Modal";
import Select from "@/components/ui/Select";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Toast from "@/components/ui/Toast";
import { useToast } from "@/lib/useToast";

/**
 * Định mức học phí (hạ tầng UC-30 bước 1): tạo gói, ngừng/áp dụng lại gói, gán gói cho lớp. Cron chỉ sinh
 * hóa đơn cho lớp đang có gói được gán — lớp chưa gán gói sẽ không có hóa đơn.
 */
export default function TuitionPlansTab() {
  const { t } = useTranslation("finance");
  const { hasPermission } = useApp();
  const canCreate = hasPermission("finance.tuition-plan.create");
  const canUpdate = hasPermission("finance.tuition-plan.update");
  const canAssign = hasPermission("finance.tuition-plan.assign");
  const { message: toastMessage, showToast } = useToast();

  const [plans, setPlans] = useState<TuitionPlanResponse[]>([]);
  const [assignments, setAssignments] = useState<TuitionPlanAssignmentResponse[]>([]);
  const [classes, setClasses] = useState<ClassResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [assignOpen, setAssignOpen] = useState(false);

  const load = () => {
    setLoading(true);
    Promise.all([listTuitionPlans(), listTuitionPlanAssignments(), listClasses()])
      .then(([planRes, assignmentRes, classRes]) => {
        setPlans(planRes);
        setAssignments(assignmentRes);
        setClasses(classRes);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("plans.loadError")))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const toggleStatus = async (plan: TuitionPlanResponse) => {
    try {
      const updated = await updateTuitionPlanStatus(plan.id, plan.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
      setPlans((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
      showToast(updated.status === "ACTIVE" ? t("plans.activatedToast") : t("plans.deactivatedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("plans.updateError"));
    }
  };

  const classesWithoutPlan = classes.filter(
    (c) => (c.status === "OPEN_ENROLLMENT" || c.status === "IN_PROGRESS") && !assignments.some((a) => a.classId === c.id)
  );

  const priceLabel = (p: TuitionPlanResponse) =>
    p.pricingModel === "COURSE" || p.pricePerUnit == null
      ? formatVnd(p.basePrice)
      : t(`plans.pricePer.${p.pricingModel}`, { price: formatVnd(p.pricePerUnit) });

  return (
    <div className="space-y-5">
      <FloatingError message={error} onClose={() => setError(null)} />
      <Toast message={toastMessage} />

      <Card padded={false} className="overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between gap-3">
          <span className="text-sm font-bold text-slate-700 font-display">{t("plans.listTitle")}</span>
          {canCreate && (
            <Button size="sm" onClick={() => setCreateOpen(true)}>
              <Plus className="w-4 h-4" /> {t("plans.createButton")}
            </Button>
          )}
        </div>
        {loading ? (
          <p className="text-sm text-slate-500 p-5">{t("common.loading")}</p>
        ) : plans.length === 0 ? (
          <p className="text-sm text-slate-400 italic p-5">{t("plans.empty")}</p>
        ) : (
          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>{t("plans.colCode")}</Th>
                <Th>{t("plans.colName")}</Th>
                <Th>{t("plans.colCurriculum")}</Th>
                <Th>{t("plans.colModel")}</Th>
                <Th className="text-right">{t("plans.colPrice")}</Th>
                <Th>{t("common.status")}</Th>
                {canUpdate && <Th />}
              </tr>
            </thead>
            <tbody>
              {plans.map((p) => (
                <tr key={p.id} className="border-t border-slate-100">
                  <Td className="font-mono text-[13px]">{p.code}</Td>
                  <Td className="font-semibold text-slate-800">{p.name}</Td>
                  <Td>{p.curriculumName}</Td>
                  <Td>{t(`pricingModel.${p.pricingModel}`)}</Td>
                  <Td className="text-right">{priceLabel(p)}</Td>
                  <Td>
                    <Badge variant={p.status === "ACTIVE" ? "success" : "neutral"}>{t(`planStatus.${p.status}`)}</Badge>
                  </Td>
                  {canUpdate && (
                    <Td className="text-right">
                      <Button size="sm" variant="ghost" onClick={() => toggleStatus(p)}>
                        {p.status === "ACTIVE" ? t("plans.deactivate") : t("plans.activate")}
                      </Button>
                    </Td>
                  )}
                </tr>
              ))}
            </tbody>
          </TableContainer>
        )}
      </Card>

      <Card padded={false} className="overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between gap-3">
          <div>
            <span className="text-sm font-bold text-slate-700 font-display">{t("assignments.listTitle")}</span>
            {classesWithoutPlan.length > 0 && (
              <p className="text-[12px] text-amber-600 mt-0.5">{t("assignments.classesWithoutPlan", { count: classesWithoutPlan.length })}</p>
            )}
          </div>
          {canAssign && (
            <Button size="sm" onClick={() => setAssignOpen(true)}>
              <Link2 className="w-4 h-4" /> {t("assignments.assignButton")}
            </Button>
          )}
        </div>
        {assignments.length === 0 ? (
          <p className="text-sm text-slate-400 italic p-5">{t("assignments.empty")}</p>
        ) : (
          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>{t("common.class")}</Th>
                <Th>{t("assignments.colPlan")}</Th>
                <Th className="text-right">{t("assignments.colOverride")}</Th>
                <Th>{t("assignments.colFrom")}</Th>
              </tr>
            </thead>
            <tbody>
              {assignments.map((a) => (
                <tr key={a.id} className="border-t border-slate-100">
                  <Td className="font-semibold text-slate-800">{a.className}</Td>
                  <Td>
                    {a.tuitionPlanName} <span className="font-mono text-[12px] text-slate-400">{a.tuitionPlanCode}</span>
                  </Td>
                  <Td className="text-right">
                    {a.priceOverride != null ? formatVnd(a.priceOverride) : "—"}
                    {a.overrideReason && <div className="text-[12px] text-slate-400">{a.overrideReason}</div>}
                  </Td>
                  <Td>{formatDate(a.effectiveFrom)}</Td>
                </tr>
              ))}
            </tbody>
          </TableContainer>
        )}
      </Card>

      {createOpen && (
        <CreatePlanModal
          onClose={() => setCreateOpen(false)}
          onCreated={(plan) => {
            setPlans((prev) => [plan, ...prev]);
            setCreateOpen(false);
            showToast(t("plans.createdToast"));
          }}
        />
      )}

      {assignOpen && (
        <AssignPlanModal
          plans={plans.filter((p) => p.status === "ACTIVE")}
          classes={classes.filter((c) => c.status !== "CANCELLED" && c.status !== "COMPLETED")}
          onClose={() => setAssignOpen(false)}
          onAssigned={() => {
            setAssignOpen(false);
            showToast(t("assignments.assignedToast"));
            load();
          }}
        />
      )}
    </div>
  );
}

function CreatePlanModal({ onClose, onCreated }: { onClose: () => void; onCreated: (plan: TuitionPlanResponse) => void }) {
  const { t } = useTranslation("finance");
  const [curriculums, setCurriculums] = useState<CurriculumResponse[]>([]);
  const [form, setForm] = useState({
    code: "",
    name: "",
    curriculumId: "",
    pricingModel: "MONTHLY" as PricingModel,
    classTypeFilter: "",
    basePrice: "",
    pricePerUnit: "",
    unitCount: "",
    effectiveFrom: "",
    effectiveTo: ""
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listCurriculums().then(setCurriculums).catch(() => setCurriculums([]));
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.code.trim() || !form.name.trim() || !form.curriculumId || !form.basePrice) {
      setError(t("common.missingFields"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const created = await createTuitionPlan({
        code: form.code.trim(),
        name: form.name.trim(),
        curriculumId: Number(form.curriculumId),
        pricingModel: form.pricingModel,
        classTypeFilter: form.classTypeFilter ? (form.classTypeFilter as "LINKED" | "OPEN") : undefined,
        basePrice: Number(form.basePrice),
        pricePerUnit: form.pricePerUnit ? Number(form.pricePerUnit) : undefined,
        unitCount: form.unitCount ? Number(form.unitCount) : undefined,
        effectiveFrom: form.effectiveFrom || undefined,
        effectiveTo: form.effectiveTo || undefined
      });
      onCreated(created);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("plans.createError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("plans.createTitle")} description={t("plans.createDescription")} size="lg">
      <form onSubmit={handleSubmit} className="space-y-4">
        <FloatingError message={error} onClose={() => setError(null)} />
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("plans.colCode")}</label>
            <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} className={`${inputClass} font-mono`} />
          </div>
          <div>
            <label className={labelClass}>{t("plans.colName")}</label>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("plans.colCurriculum")}</label>
            <Select value={form.curriculumId} onChange={(e) => setForm({ ...form, curriculumId: e.target.value })} className={inputClass}>
              <option value="">{t("common.choose")}</option>
              {curriculums.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.code})
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>{t("plans.classTypeFilter")}</label>
            <Select value={form.classTypeFilter} onChange={(e) => setForm({ ...form, classTypeFilter: e.target.value })} className={inputClass}>
              <option value="">{t("plans.classTypeAny")}</option>
              <option value="LINKED">{t("classType.LINKED")}</option>
              <option value="OPEN">{t("classType.OPEN")}</option>
            </Select>
          </div>
          <div>
            <label className={labelClass}>{t("plans.colModel")}</label>
            <Select value={form.pricingModel} onChange={(e) => setForm({ ...form, pricingModel: e.target.value as PricingModel })} className={inputClass}>
              {PRICING_MODELS.map((m) => (
                <option key={m} value={m}>
                  {t(`pricingModel.${m}`)}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <label className={labelClass}>{t("plans.basePrice")}</label>
            <input type="number" min={0} value={form.basePrice} onChange={(e) => setForm({ ...form, basePrice: e.target.value })} className={inputClass} />
          </div>
          {form.pricingModel !== "COURSE" && (
            <div>
              <label className={labelClass}>{t(`plans.pricePerUnitLabel.${form.pricingModel}`)}</label>
              <input type="number" min={0} value={form.pricePerUnit} onChange={(e) => setForm({ ...form, pricePerUnit: e.target.value })} className={inputClass} />
            </div>
          )}
          {form.pricingModel === "PER_SESSION" && (
            <div>
              <label className={labelClass}>{t("plans.unitCount")}</label>
              <input type="number" min={1} value={form.unitCount} onChange={(e) => setForm({ ...form, unitCount: e.target.value })} className={inputClass} />
            </div>
          )}
          <div>
            <label className={labelClass}>{t("plans.effectiveFrom")}</label>
            <DatePicker value={form.effectiveFrom} onChange={(v) => setForm({ ...form, effectiveFrom: v })} />
          </div>
          <div>
            <label className={labelClass}>{t("plans.effectiveTo")}</label>
            <DatePicker value={form.effectiveTo} onChange={(v) => setForm({ ...form, effectiveTo: v })} min={form.effectiveFrom || undefined} />
          </div>
        </div>
        <p className="text-[12px] text-slate-500">{t("plans.priceHint")}</p>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" disabled={submitting}>
            <Save className="w-4 h-4" /> {t("common.save")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function AssignPlanModal({
  plans,
  classes,
  onClose,
  onAssigned
}: {
  plans: TuitionPlanResponse[];
  classes: ClassResponse[];
  onClose: () => void;
  onAssigned: () => void;
}) {
  const { t } = useTranslation("finance");
  const [classId, setClassId] = useState("");
  const [planId, setPlanId] = useState("");
  const [priceOverride, setPriceOverride] = useState("");
  const [overrideReason, setOverrideReason] = useState("");
  const [effectiveFrom, setEffectiveFrom] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!classId || !planId) {
      setError(t("common.missingFields"));
      return;
    }
    if (priceOverride && !overrideReason.trim()) {
      setError(t("assignments.overrideReasonRequired"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await assignTuitionPlan({
        classId: Number(classId),
        tuitionPlanId: Number(planId),
        priceOverride: priceOverride ? Number(priceOverride) : undefined,
        overrideReason: overrideReason.trim() || undefined,
        effectiveFrom: effectiveFrom || undefined
      });
      onAssigned();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("assignments.assignError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("assignments.assignTitle")} description={t("assignments.assignDescription")}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <FloatingError message={error} onClose={() => setError(null)} />
        <div>
          <label className={labelClass}>{t("common.class")}</label>
          <Select value={classId} onChange={(e) => setClassId(e.target.value)} className={inputClass}>
            <option value="">{t("common.choose")}</option>
            {classes.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.classCode}) · {c.siteName}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className={labelClass}>{t("assignments.colPlan")}</label>
          <Select value={planId} onChange={(e) => setPlanId(e.target.value)} className={inputClass}>
            <option value="">{t("common.choose")}</option>
            {plans.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} ({p.code})
              </option>
            ))}
          </Select>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>{t("assignments.colOverride")}</label>
            <input type="number" min={0} value={priceOverride} onChange={(e) => setPriceOverride(e.target.value)} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>{t("assignments.colFrom")}</label>
            <DatePicker value={effectiveFrom} onChange={setEffectiveFrom} placeholder={t("assignments.fromToday")} />
          </div>
        </div>
        {priceOverride && (
          <div>
            <label className={labelClass}>{t("assignments.overrideReason")}</label>
            <input value={overrideReason} onChange={(e) => setOverrideReason(e.target.value)} className={inputClass} />
          </div>
        )}
        <p className="text-[12px] text-slate-500">{t("assignments.replaceHint")}</p>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" disabled={submitting}>
            <Link2 className="w-4 h-4" /> {t("assignments.assignButton")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
