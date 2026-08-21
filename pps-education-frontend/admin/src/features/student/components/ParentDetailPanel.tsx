import React, { useEffect, useState } from "react";
import { Save, Search, UserPlus, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { getParentById, linkParent, listStudents, ParentResponse, StudentResponse, unlinkParent, updateParent } from "../api";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import type { ParentAggregate } from "../pages/ParentsPage";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { useDialog } from "@/components/ui/DialogProvider";
import AvatarUploadField from "@/components/ui/AvatarUploadField";
import { uploadMedia } from "@/features/lms/api";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface ParentDetailPanelProps {
  parent: ParentAggregate;
  onChanged: () => void;
}

export default function ParentDetailPanel({ parent, onChanged }: ParentDetailPanelProps) {
  const { t } = useTranslation("student");
  const { message: toastMessage, showToast } = useToast();

  return (
    <div className="lg:col-span-3 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col">
      <div className="p-5 border-b border-slate-200 bg-slate-50/20">
        <h2 className="text-sm font-bold text-slate-800">{parent.parentFullName}</h2>
        <p className="text-[10px] text-slate-400 mt-0.5">{t("parentDetail.idLabel", { id: parent.parentId })}</p>
      </div>

      <div className="flex-1 p-5 overflow-y-auto max-h-[560px] space-y-6">
        <ProfileSection parentId={parent.parentId} showToast={showToast} />
        <ChildrenSection parent={parent} onChanged={onChanged} showToast={showToast} />
      </div>

      <Toast message={toastMessage} />
    </div>
  );
}

function ProfileSection({ parentId, showToast }: { parentId: number; showToast: (msg: string) => void }) {
  const { t } = useTranslation("student");
  const [profile, setProfile] = useState<ParentResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({ occupation: "", workplace: "", address: "", notes: "", portraitUrl: "" });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setEditing(false);
    getParentById(parentId)
      .then(setProfile)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("parentDetail.profile.loadError")))
      .finally(() => setLoading(false));
  }, [parentId]);

  const startEdit = () => {
    setForm({
      occupation: profile?.occupation ?? "",
      workplace: profile?.workplace ?? "",
      address: profile?.address ?? "",
      notes: profile?.notes ?? "",
      portraitUrl: profile?.portraitUrl ?? ""
    });
    setEditing(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const updated = await updateParent(parentId, {
        occupation: form.occupation.trim() || undefined,
        workplace: form.workplace.trim() || undefined,
        address: form.address.trim() || undefined,
        notes: form.notes.trim() || undefined,
        portraitUrl: form.portraitUrl || undefined
      });
      setProfile(updated);
      setEditing(false);
      showToast(t("parentDetail.profile.savedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("parentDetail.profile.saveError"));
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div className="text-xs text-slate-400">{t("parentDetail.profile.loading")}</div>;
  }

  if (!editing) {
    return (
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <span className="text-[10px] font-bold uppercase text-slate-500">{t("parentDetail.profile.sectionTitle")}</span>
          <Button size="sm" variant="secondary" onClick={startEdit}>
            {t("parentDetail.profile.editButton")}
          </Button>
        </div>
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
        {profile?.portraitUrl && (
          <img src={profile.portraitUrl} alt="" className="w-16 h-16 rounded-full object-cover border-2 border-white shadow-soft" />
        )}
        <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-slate-600 bg-slate-50 border border-slate-200 rounded-lg p-3">
          <span>{t("parentDetail.profile.occupationLabel")}<strong>{profile?.occupation || "—"}</strong></span>
          <span>{t("parentDetail.profile.workplaceLabel")}<strong>{profile?.workplace || "—"}</strong></span>
          <span className="col-span-2">{t("parentDetail.profile.addressLabel")}<strong>{profile?.address || "—"}</strong></span>
          {profile?.notes && <span className="col-span-2">{t("parentDetail.profile.notesLabel")}{profile.notes}</span>}
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <span className="text-[10px] font-bold uppercase text-slate-500">{t("parentDetail.profile.editSectionTitle")}</span>
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
      <AvatarUploadField
        value={form.portraitUrl}
        onChange={(url) => setForm({ ...form, portraitUrl: url })}
        onUpload={(file) => uploadMedia(file, "PARENT")}
        fallbackName={profile?.fullName ?? t("parentDetail.profile.avatarFallback")}
      />
      <div className="grid grid-cols-2 gap-3">
        <input value={form.occupation} onChange={(e) => setForm({ ...form, occupation: e.target.value })} placeholder={t("parentDetail.profile.occupationPlaceholder")} className={inputClass} />
        <input value={form.workplace} onChange={(e) => setForm({ ...form, workplace: e.target.value })} placeholder={t("parentDetail.profile.workplacePlaceholder")} className={inputClass} />
        <input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} placeholder={t("parentDetail.profile.addressPlaceholder")} className={`${inputClass} col-span-2`} />
        <textarea value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} placeholder={t("parentDetail.profile.notesPlaceholder")} rows={2} className={`${inputClass} col-span-2`} />
      </div>
      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={() => setEditing(false)}>
          {t("parentDetail.profile.cancel")}
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={saving}>
          <Save className="w-3.5 h-3.5" />
          {saving ? t("parentDetail.profile.saving") : t("parentDetail.profile.saveButton")}
        </Button>
      </div>
    </form>
  );
}

function ChildrenSection({
  parent,
  onChanged,
  showToast
}: {
  parent: ParentAggregate;
  onChanged: () => void;
  showToast: (msg: string) => void;
}) {
  const { t } = useTranslation("student");
  const [linking, setLinking] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<StudentResponse[]>([]);
  const [selectedStudent, setSelectedStudent] = useState<StudentResponse | null>(null);
  const [relationship, setRelationship] = useState("MOTHER");
  const [isPrimaryContact, setIsPrimaryContact] = useState(false);
  const [isFinancialResponsible, setIsFinancialResponsible] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { confirmDialog } = useDialog();

  const handleSearch = (q: string) => {
    setQuery(q);
    if (!q.trim()) {
      setResults([]);
      return;
    }
    listStudents(q.trim()).then(setResults);
  };

  const handleLink = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedStudent) {
      setError(t("parentDetail.children.validationError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await linkParent(selectedStudent.id, {
        parentId: parent.parentId,
        relationship: relationship as "FATHER" | "MOTHER" | "GUARDIAN" | "OTHER",
        isPrimaryContact,
        isFinancialResponsible
      });
      setLinking(false);
      setSelectedStudent(null);
      setQuery("");
      onChanged();
      showToast(t("parentDetail.children.linkedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("parentDetail.children.linkError"));
    } finally {
      setSubmitting(false);
    }
  };

  const handleUnlink = async (studentId: number, parentStudentId: number) => {
    if (!(await confirmDialog(t("parentDetail.children.unlinkConfirm"), { danger: true }))) return;
    try {
      await unlinkParent(studentId, parentStudentId);
      onChanged();
      showToast(t("parentDetail.children.unlinkedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("parentDetail.children.unlinkError"));
    }
  };

  return (
    <div className="space-y-3 border-t border-slate-100 pt-4">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-bold uppercase text-slate-500">{t("parentDetail.children.sectionTitle", { count: parent.children.length })}</span>
        <Button size="sm" variant="secondary" onClick={() => setLinking(true)}>
          <UserPlus className="w-3.5 h-3.5" />
          {t("parentDetail.children.linkButton")}
        </Button>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      {parent.children.length === 0 ? (
        <p className="text-xs text-slate-400 italic">{t("parentDetail.children.empty")}</p>
      ) : (
        <div className="space-y-2">
          {parent.children.map((c) => (
            <div key={c.parentStudentId} className="border border-slate-200 rounded-lg p-3 text-xs flex items-center justify-between">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="font-bold text-slate-800">{c.studentName}</span>
                <span className="font-mono text-slate-400">{c.studentCode}</span>
                <Badge variant="info">{t(`relationship.${c.relationship}`, c.relationship)}</Badge>
                {c.isPrimaryContact && <Badge variant="brand">{t("parentDetail.children.primaryContactBadge")}</Badge>}
                {c.isFinancialResponsible && <Badge variant="warning">{t("parentDetail.children.financialResponsibleBadge")}</Badge>}
              </div>
              <button onClick={() => handleUnlink(c.studentId, c.parentStudentId)} className="text-rose-500 hover:text-rose-700">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          ))}
        </div>
      )}

      <Modal open={linking} onClose={() => setLinking(false)} title={t("parentDetail.children.modalTitle")}>
        <form onSubmit={handleLink} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
          {selectedStudent ? (
            <div className="flex items-center justify-between bg-emerald-50 border border-emerald-100 text-emerald-700 text-xs font-semibold px-3 py-2 rounded-lg">
              <span>{selectedStudent.fullName} ({selectedStudent.studentCode})</span>
              <button type="button" onClick={() => setSelectedStudent(null)} className="text-emerald-600 hover:text-rose-600">
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          ) : (
            <div className="relative">
              <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
              <input value={query} onChange={(e) => handleSearch(e.target.value)} placeholder={t("parentDetail.children.searchPlaceholder")} className={`${inputClass} pl-8`} />
              {results.length > 0 && (
                <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg divide-y divide-slate-100 max-h-56 overflow-y-auto">
                  {results.map((s) => (
                    <button
                      key={s.id}
                      type="button"
                      onClick={() => {
                        setSelectedStudent(s);
                        setResults([]);
                      }}
                      className="w-full text-left px-3 py-2 hover:bg-slate-50 text-xs"
                    >
                      {s.fullName} <span className="text-slate-400">({s.studentCode})</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}

          <div className="grid grid-cols-3 gap-2 items-end">
            <Select value={relationship} onChange={(e) => setRelationship(e.target.value)} className={inputClass}>
              <option value="FATHER">{t("relationship.FATHER")}</option>
              <option value="MOTHER">{t("relationship.MOTHER")}</option>
              <option value="GUARDIAN">{t("relationship.GUARDIAN")}</option>
              <option value="OTHER">{t("relationship.OTHER")}</option>
            </Select>
            <label className="flex items-center gap-1.5 text-[11px] font-semibold text-slate-700 pb-2.5">
              <input type="checkbox" checked={isPrimaryContact} onChange={(e) => setIsPrimaryContact(e.target.checked)} />
              {t("parentDetail.children.primaryContactCheckbox")}
            </label>
            <label className="flex items-center gap-1.5 text-[11px] font-semibold text-slate-700 pb-2.5">
              <input type="checkbox" checked={isFinancialResponsible} onChange={(e) => setIsFinancialResponsible(e.target.checked)} />
              {t("parentDetail.children.financialResponsibleCheckbox")}
            </label>
          </div>

          <div className="flex gap-2">
            <Button type="button" variant="secondary" size="sm" onClick={() => setLinking(false)}>
              {t("parentDetail.children.cancel")}
            </Button>
            <Button type="submit" variant="primary" size="sm" disabled={submitting}>
              {submitting ? t("parentDetail.children.saving") : t("parentDetail.children.linkSubmitButton")}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
