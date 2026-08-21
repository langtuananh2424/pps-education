import React, { useEffect, useState } from "react";
import { FileText, Plus, Save, Search, ShieldCheck, UserCog, Users, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { searchUsers, UserListItemResponse } from "@/features/system-admin/api";
import {
  AssignSiteTeacherRequest,
  SiteTeacherResponse,
  assignSiteManager,
  assignSiteTeacher,
  createPartnerContract,
  CreatePartnerContractRequest,
  listPartnerContractsBySite,
  listSiteTeachers,
  PartnerContractResponse,
  removeSiteTeacher,
  SiteResponse,
  terminatePartnerContract,
  deletePartnerContract,
  updateSite,
  UpdateSiteRequest
} from "../api";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import { siteStatusLabel, siteStatusVariants, siteTypeLabel } from "./SiteListPanel";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { useDialog } from "@/components/ui/DialogProvider";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

type Tab = "profile" | "manager" | "teachers" | "contracts";

const contractStatusVariants: Record<string, BadgeVariant> = { DRAFT: "neutral", ACTIVE: "success", EXPIRED: "warning", TERMINATED: "danger" };

interface SiteDetailPanelProps {
  site: SiteResponse;
  onChanged: () => void;
}

export default function SiteDetailPanel({ site, onChanged }: SiteDetailPanelProps) {
  const { t } = useTranslation("facility");
  const [tab, setTab] = useState<Tab>("profile");
  const { message: toastMessage, showToast } = useToast();

  return (
    <div className="lg:col-span-3 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col">
      <div className="p-5 border-b border-slate-200 space-y-3 bg-slate-50/20">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div>
            <span className="text-[10px] font-mono font-bold uppercase tracking-wider text-brand-red bg-orange-50 border border-orange-100 px-2 py-0.5 rounded-md">
              {site.code}
            </span>
            <h2 className="text-sm font-bold text-slate-800 mt-1">{site.name}</h2>
          </div>
          <Badge variant={siteStatusVariants[site.status]}>{siteStatusLabel(t, site.status)}</Badge>
        </div>

        <div className="flex border-b border-slate-200 pt-1 gap-5 overflow-x-auto">
          {(
            [
              ["profile", t("siteDetail.tabs.profile"), FileText],
              ["manager", t("siteDetail.tabs.manager"), UserCog],
              ["teachers", t("siteDetail.tabs.teachers"), Users],
              ...(site.siteType === "PARTNER" ? ([["contracts", t("siteDetail.tabs.contracts"), ShieldCheck]] as const) : [])
            ] as const
          ).map(([key, label, Icon]) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`pb-2.5 text-xs font-bold border-b-2 flex items-center gap-1.5 transition-all whitespace-nowrap ${
                tab === key ? "border-brand-red text-brand-red" : "border-transparent text-slate-500 hover:text-slate-700"
              }`}
            >
              <Icon className="w-3.5 h-3.5" />
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex-1 p-5 overflow-y-auto max-h-[620px] lg:max-h-[680px]">
        {tab === "profile" && <ProfileTab site={site} onChanged={onChanged} showToast={showToast} />}
        {tab === "manager" && <ManagerTab site={site} onChanged={onChanged} showToast={showToast} />}
        {tab === "teachers" && <SiteTeachersTab siteId={site.id} showToast={showToast} />}
        {tab === "contracts" && site.siteType === "PARTNER" && <ContractsTab siteId={site.id} showToast={showToast} />}
      </div>

      <Toast message={toastMessage} />
    </div>
  );
}

function ProfileTab({
  site,
  onChanged,
  showToast
}: {
  site: SiteResponse;
  onChanged: () => void;
  showToast: (msg: string) => void;
}) {
  const { t } = useTranslation("facility");
  const [form, setForm] = useState<UpdateSiteRequest>(() => toForm(site));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { confirmDialog } = useDialog();

  useEffect(() => setForm(toForm(site)), [site]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (form.siteType === "OWNED" && site.siteType === "PARTNER") {
      if (!(await confirmDialog(t("siteDetail.profile.changeToOwnedConfirm"), { danger: true }))) return;
    }
    setSaving(true);
    setError(null);
    try {
      await updateSite(site.id, form);
      onChanged();
      showToast(t("siteDetail.profile.savedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("siteDetail.profile.saveError"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className={labelClass}>{t("siteDetail.profile.nameLabel")}</label>
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className={inputClass} required />
        </div>
        <div>
          <label className={labelClass}>{t("siteDetail.profile.siteTypeLabel")}</label>
          <Select value={form.siteType} onChange={(e) => setForm({ ...form, siteType: e.target.value as "OWNED" | "PARTNER" })} className={inputClass}>
            <option value="OWNED">{t("siteType.OWNED")}</option>
            <option value="PARTNER">{t("siteType.PARTNER")}</option>
          </Select>
        </div>
        <div>
          <label className={labelClass}>{t("siteDetail.profile.addressLabel")}</label>
          <input value={form.address ?? ""} onChange={(e) => setForm({ ...form, address: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("siteDetail.profile.districtLabel")}</label>
          <input value={form.district ?? ""} onChange={(e) => setForm({ ...form, district: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("siteDetail.profile.phoneLabel")}</label>
          <input value={form.phone ?? ""} onChange={(e) => setForm({ ...form, phone: e.target.value })} className={inputClass} />
        </div>
        <div>
          <label className={labelClass}>{t("siteDetail.profile.statusLabel")}</label>
          <Select value={form.status ?? "ACTIVE"} onChange={(e) => setForm({ ...form, status: e.target.value as UpdateSiteRequest["status"] })} className={inputClass}>
            <option value="ACTIVE">{t("siteStatus.ACTIVE")}</option>
            <option value="INACTIVE">{t("siteStatus.INACTIVE")}</option>
            <option value="PENDING">{t("siteStatus.PENDING")}</option>
          </Select>
        </div>
      </div>

      {form.siteType === "PARTNER" && (
        <div className="space-y-3 border-t border-slate-100 pt-4">
          <span className="text-[10px] font-bold uppercase text-slate-500">{t("siteDetail.profile.partnerContactTitle")}</span>
          <div className="grid grid-cols-2 gap-3">
            <input
              value={form.partnerInfo?.contactPersonName ?? ""}
              onChange={(e) => setForm({ ...form, partnerInfo: { ...emptyPartnerInfo(form.partnerInfo), contactPersonName: e.target.value } })}
              placeholder={t("siteDetail.profile.contactNamePlaceholder")}
              className={inputClass}
            />
            <input
              value={form.partnerInfo?.contactPersonTitle ?? ""}
              onChange={(e) => setForm({ ...form, partnerInfo: { ...emptyPartnerInfo(form.partnerInfo), contactPersonTitle: e.target.value } })}
              placeholder={t("siteDetail.profile.contactTitlePlaceholder")}
              className={inputClass}
            />
            <input
              value={form.partnerInfo?.contactPhone ?? ""}
              onChange={(e) => setForm({ ...form, partnerInfo: { ...emptyPartnerInfo(form.partnerInfo), contactPhone: e.target.value } })}
              placeholder={t("siteDetail.profile.contactPhonePlaceholder")}
              className={inputClass}
            />
            <input
              type="email"
              value={form.partnerInfo?.contactEmail ?? ""}
              onChange={(e) => setForm({ ...form, partnerInfo: { ...emptyPartnerInfo(form.partnerInfo), contactEmail: e.target.value } })}
              placeholder={t("siteDetail.profile.contactEmailPlaceholder")}
              className={inputClass}
            />
          </div>
        </div>
      )}

      <Button type="submit" variant="primary" size="sm" disabled={saving}>
        <Save className="w-3.5 h-3.5" />
        {saving ? t("siteDetail.profile.saving") : t("siteDetail.profile.saveButton")}
      </Button>
    </form>
  );
}

function emptyPartnerInfo(p?: UpdateSiteRequest["partnerInfo"]) {
  return p ?? { contactPersonName: null, contactPersonTitle: null, contactPhone: null, contactEmail: null, additionalInfo: null };
}

function toForm(s: SiteResponse): UpdateSiteRequest {
  return {
    name: s.name,
    siteType: s.siteType,
    address: s.address ?? undefined,
    district: s.district ?? undefined,
    phone: s.phone ?? undefined,
    status: s.status,
    partnerInfo: s.partnerInfo ?? undefined
  };
}

function ManagerTab({
  site,
  onChanged,
  showToast
}: {
  site: SiteResponse;
  onChanged: () => void;
  showToast: (msg: string) => void;
}) {
  const { t } = useTranslation("facility");
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserListItemResponse[]>([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSearch = (q: string) => {
    setQuery(q);
    if (!q.trim()) {
      setResults([]);
      return;
    }
    searchUsers({ keyword: q.trim() }, 0, 8).then((res) => setResults(res.content));
  };

  const handleAssign = async (userId: number) => {
    setSaving(true);
    setError(null);
    try {
      await assignSiteManager(site.id, userId);
      setQuery("");
      setResults([]);
      onChanged();
      showToast(t("siteDetail.manager.assignedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("siteDetail.manager.assignError"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-1">
        <span className="text-[10px] uppercase font-bold text-slate-500">{t("siteDetail.manager.currentManagerTitle")}</span>
        {site.currentManagerFullName ? (
          <p className="text-sm font-bold text-slate-800">{site.currentManagerFullName}</p>
        ) : (
          <p className="text-xs text-slate-400 italic">{t("siteDetail.manager.noManager")}</p>
        )}
      </div>

      <div className="space-y-2">
        <span className="text-[10px] uppercase font-bold text-slate-500">{t("siteDetail.manager.assignTitle")}</span>
        <div className="relative max-w-md">
          <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
          <input
            value={query}
            onChange={(e) => handleSearch(e.target.value)}
            placeholder={t("siteDetail.manager.searchPlaceholder")}
            className={`${inputClass} pl-8`}
            disabled={saving}
          />
          {results.length > 0 && (
            <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg divide-y divide-slate-100 max-h-56 overflow-y-auto">
              {results.map((u) => (
                <button key={u.id} type="button" onClick={() => handleAssign(u.id)} className="w-full text-left px-3 py-2 hover:bg-slate-50 text-xs">
                  {u.fullName} <span className="text-slate-400">({u.username} · {u.email})</span>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

/** Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-03 — gán/gỡ giáo viên vào điểm trường
 * (site_teachers, UC-36 A2) — khác hẳn tab "Quản lý điểm trường" (site_managers, 1 người/site). 1
 * giáo viên gán được nhiều điểm trường cùng lúc. */
function SiteTeachersTab({ siteId, showToast }: { siteId: number; showToast: (msg: string) => void }) {
  const { t } = useTranslation("facility");
  const [items, setItems] = useState<SiteTeacherResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserListItemResponse[]>([]);
  const [assignedFrom, setAssignedFrom] = useState(new Date().toISOString().slice(0, 10));
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const { confirmDialog } = useDialog();

  const load = () => {
    setLoading(true);
    listSiteTeachers(siteId)
      .then(setItems)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("siteDetail.teachers.loadError")))
      .finally(() => setLoading(false));
  };
  useEffect(load, [siteId]);

  const handleSearch = (q: string) => {
    setQuery(q);
    if (!q.trim()) {
      setResults([]);
      return;
    }
    searchUsers({ keyword: q.trim() }, 0, 8).then((res) => setResults(res.content.filter((u) => u.roles.some((r) => r.code === "TEACHER"))));
  };

  const handleAssign = async (teacherUserId: number) => {
    setSubmitting(true);
    setError(null);
    try {
      const request: AssignSiteTeacherRequest = { teacherUserId, assignedFrom, notes: notes.trim() || undefined };
      await assignSiteTeacher(siteId, request);
      setQuery("");
      setResults([]);
      setNotes("");
      setShowForm(false);
      load();
      showToast(t("siteDetail.teachers.assignedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("siteDetail.teachers.assignError"));
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemove = async (item: SiteTeacherResponse) => {
    if (!(await confirmDialog(t("siteDetail.teachers.removeConfirm", { name: item.teacherFullName }), { danger: true }))) return;
    try {
      await removeSiteTeacher(siteId, item.id);
      load();
      showToast(t("siteDetail.teachers.removedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("siteDetail.teachers.removeError"));
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-bold uppercase text-slate-500">{t("siteDetail.teachers.sectionTitle", { count: items.length })}</span>
        <Button size="sm" variant="secondary" onClick={() => setShowForm(true)}>
          <Plus className="w-3.5 h-3.5" />
          {t("siteDetail.teachers.assignButton")}
        </Button>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {loading ? (
        <p className="text-xs text-slate-500">{t("siteDetail.teachers.loading")}</p>
      ) : items.length === 0 ? (
        <p className="text-xs text-slate-400 italic">{t("siteDetail.teachers.empty")}</p>
      ) : (
        <div className="space-y-2">
          {items.map((item) => (
            <div key={item.id} className="border border-slate-200 rounded-lg p-3 flex items-center justify-between gap-2">
              <div>
                <p className="text-xs font-bold text-slate-800">{item.teacherFullName}</p>
                <p className="text-[10px] text-slate-400 mt-0.5">
                  {t("siteDetail.teachers.fromLabel", { date: item.assignedFrom })}
                  {item.assignedTo ? t("siteDetail.teachers.toSuffix", { date: item.assignedTo }) : ""}
                  {item.notes ? ` · ${item.notes}` : ""}
                </p>
              </div>
              {!item.assignedTo && (
                <button onClick={() => handleRemove(item)} className="text-rose-500 hover:text-rose-700 shrink-0">
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      <Modal open={showForm} onClose={() => setShowForm(false)} title={t("siteDetail.teachers.modalTitle")}>
        <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
          <div>
            <label className={labelClass}>{t("siteDetail.teachers.searchLabel")}</label>
            <div className="relative">
              <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
              <input
                value={query}
                onChange={(e) => handleSearch(e.target.value)}
                placeholder={t("siteDetail.teachers.searchPlaceholder")}
                className={`${inputClass} pl-8`}
                disabled={submitting}
              />
              {results.length > 0 && (
                <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg divide-y divide-slate-100 max-h-56 overflow-y-auto">
                  {results.map((u) => (
                    <button key={u.id} type="button" onClick={() => handleAssign(u.id)} className="w-full text-left px-3 py-2 hover:bg-slate-50 text-xs">
                      {u.fullName} <span className="text-slate-400">({u.username} · {u.email})</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
          <div>
            <label className={labelClass}>{t("siteDetail.teachers.assignedFromLabel")}</label>
            <DatePicker value={assignedFrom} onChange={setAssignedFrom} />
          </div>
          <div>
            <label className={labelClass}>{t("siteDetail.teachers.notesLabel")}</label>
            <input value={notes} onChange={(e) => setNotes(e.target.value)} className={inputClass} placeholder={t("siteDetail.teachers.notesPlaceholder")} />
          </div>
          <p className="text-[10px] text-slate-400 italic">{t("siteDetail.teachers.hint")}</p>
        </div>
      </Modal>
    </div>
  );
}

function ContractsTab({ siteId, showToast }: { siteId: number; showToast: (msg: string) => void }) {
  const { t } = useTranslation("facility");
  const [items, setItems] = useState<PartnerContractResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState({ contractType: "INITIAL", startDate: "", endDate: "", termsSummary: "" });
  const [submitting, setSubmitting] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const { confirmDialog } = useDialog();

  const load = () => {
    setLoading(true);
    listPartnerContractsBySite(siteId)
      .then(setItems)
      .finally(() => setLoading(false));
  };
  useEffect(load, [siteId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.startDate || !form.endDate) {
      setError(t("siteDetail.contracts.dateRangeRequired"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreatePartnerContractRequest = {
        siteId,
        contractType: form.contractType as CreatePartnerContractRequest["contractType"],
        startDate: form.startDate,
        endDate: form.endDate,
        termsSummary: form.termsSummary.trim() || undefined
      };
      await createPartnerContract(request);
      setForm({ contractType: "INITIAL", startDate: "", endDate: "", termsSummary: "" });
      setShowForm(false);
      load();
      showToast(t("siteDetail.contracts.createdToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("siteDetail.contracts.createError"));
    } finally {
      setSubmitting(false);
    }
  };

  const handleTerminate = async (c: PartnerContractResponse) => {
    if (!(await confirmDialog(t("siteDetail.contracts.terminateConfirm", { number: c.contractNumber }), { danger: true }))) return;
    try {
      await terminatePartnerContract(c.id);
      load();
      showToast(t("siteDetail.contracts.terminatedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("siteDetail.contracts.terminateError"));
    }
  };

  const handleDelete = async (c: PartnerContractResponse) => {
    if (!(await confirmDialog(t("siteDetail.contracts.deleteConfirm", { number: c.contractNumber }), { danger: true }))) return;
    try {
      await deletePartnerContract(c.id);
      load();
      showToast(t("siteDetail.contracts.deletedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("siteDetail.contracts.deleteError"));
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-bold uppercase text-slate-500">{t("siteDetail.contracts.sectionTitle", { count: items.length })}</span>
        <Button size="sm" variant="secondary" onClick={() => setShowForm(true)}>
          <Plus className="w-3.5 h-3.5" />
          {t("siteDetail.contracts.addButton")}
        </Button>
      </div>

      <Modal open={showForm} onClose={() => setShowForm(false)} title={t("siteDetail.contracts.modalTitle")}>
        <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
          {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
          <div className="grid grid-cols-2 gap-3">
            <Select value={form.contractType} onChange={(e) => setForm({ ...form, contractType: e.target.value })} className={inputClass}>
              <option value="INITIAL">{t("contractType.INITIAL")}</option>
              <option value="RENEWAL">{t("contractType.RENEWAL")}</option>
              <option value="AMENDMENT">{t("contractType.AMENDMENT")}</option>
            </Select>
            <div />
            <div>
              <label className={labelClass}>{t("siteDetail.contracts.startDateLabel")}</label>
              <DatePicker value={form.startDate} onChange={(v) => setForm({ ...form, startDate: v })} max={form.endDate || undefined} />
            </div>
            <div>
              <label className={labelClass}>{t("siteDetail.contracts.endDateLabel")}</label>
              <DatePicker value={form.endDate} onChange={(v) => setForm({ ...form, endDate: v })} min={form.startDate || undefined} />
            </div>
            <textarea
              value={form.termsSummary}
              onChange={(e) => setForm({ ...form, termsSummary: e.target.value })}
              placeholder={t("siteDetail.contracts.termsSummaryPlaceholder")}
              rows={2}
              className={`${inputClass} col-span-2`}
            />
          </div>
          <Button type="submit" size="sm" variant="primary" disabled={submitting}>
            {submitting ? t("siteDetail.contracts.saving") : t("siteDetail.contracts.addButton")}
          </Button>
        </form>
      </Modal>

      {loading ? (
        <p className="text-xs text-slate-500">{t("siteDetail.contracts.loading")}</p>
      ) : items.length === 0 ? (
        <p className="text-xs text-slate-400 italic">{t("siteDetail.contracts.empty")}</p>
      ) : (
        <div className="space-y-2">
          {items.map((c) => (
            <div key={c.id} className="border border-slate-200 rounded-lg p-3 text-xs space-y-1">
              <div className="flex items-center justify-between flex-wrap gap-2">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="font-mono font-bold text-slate-800">{c.contractNumber}</span>
                  <Badge variant="info">{t(`contractType.${c.contractType}`)}</Badge>
                  <Badge variant={contractStatusVariants[c.status]}>{t(`contractStatus.${c.status}`)}</Badge>
                </div>
                <div className="flex gap-2">
                  {c.status === "ACTIVE" && (
                    <button onClick={() => handleTerminate(c)} className="text-rose-500 hover:text-rose-700 text-[11px] font-semibold">
                      {t("siteDetail.contracts.terminateButton")}
                    </button>
                  )}
                  {c.status === "DRAFT" && (
                    <button onClick={() => handleDelete(c)} className="text-slate-400 hover:text-rose-600">
                      <X className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              </div>
              <p className="text-slate-400">
                {t("siteDetail.contracts.dateRange", { start: c.startDate, end: c.endDate })}
                {c.termsSummary && <span className="ml-2">— {c.termsSummary}</span>}
              </p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
