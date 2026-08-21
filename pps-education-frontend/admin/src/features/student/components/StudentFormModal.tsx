import React, { useEffect, useState } from "react";
import { Plus } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import AccountSelector, { AccountSelection } from "@/features/system-admin/components/AccountSelector";
import { createParent, createStudent, CreateStudentRequest, linkParent, listSites, SiteOption } from "../api";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import DatePicker from "@/components/ui/DatePicker";
import AvatarUploadField from "@/components/ui/AvatarUploadField";
import { uploadMedia } from "@/features/lms/api";
import Select from "@/components/ui/Select";

const TODAY_ISO = new Date().toISOString().slice(0, 10);

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-xs p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface StudentFormModalProps {
  onClose: () => void;
  onCreated: (id: number) => void;
}

/** UC-13 Main Flow bước 1-3: khởi tạo hồ sơ học sinh, kèm tài khoản mới hoặc gán tài khoản có sẵn — có thể liên kết luôn phụ huynh. */
export default function StudentFormModal({ onClose, onCreated }: StudentFormModalProps) {
  const { t } = useTranslation("student");
  const [account, setAccount] = useState<AccountSelection>({ newAccount: { username: "", email: "", fullName: "", phone: "", password: "" } });
  const [sites, setSites] = useState<SiteOption[]>([]);
  const [form, setForm] = useState({
    studentCode: "",
    dateOfBirth: "",
    gender: "",
    primarySiteId: "",
    originalSchool: "",
    originalClass: "",
    enrollmentDate: "",
    notes: "",
    portraitUrl: ""
  });

  const [addParent, setAddParent] = useState(false);
  const [parentAccount, setParentAccount] = useState<AccountSelection>({ newAccount: { username: "", email: "", fullName: "", phone: "", password: "" } });
  const [parentInfo, setParentInfo] = useState({ relationship: "MOTHER", isPrimaryContact: true, isFinancialResponsible: true });

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [touched, setTouched] = useState({ studentCode: false, dateOfBirth: false, enrollmentDate: false });
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const markTouched = (field: keyof typeof touched) => setTouched((t) => ({ ...t, [field]: true }));

  const studentCodeInvalid = (touched.studentCode || submitAttempted) && !form.studentCode.trim();
  const dateOfBirthInvalid = (touched.dateOfBirth || submitAttempted) && !form.dateOfBirth;
  const enrollmentDateInvalid = (touched.enrollmentDate || submitAttempted) && !form.enrollmentDate;

  useEffect(() => {
    listSites().then(setSites).catch(() => undefined);
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitAttempted(true);
    if (!form.studentCode.trim() || !form.dateOfBirth || !form.enrollmentDate) {
      setError(t("studentForm.requiredFieldsError"));
      return;
    }
    if (!account.userId && (!account.newAccount?.username || !account.newAccount?.email || !account.newAccount?.fullName)) {
      setError(t("studentForm.studentAccountError"));
      return;
    }
    if (addParent && !parentAccount.userId && (!parentAccount.newAccount?.username || !parentAccount.newAccount?.email || !parentAccount.newAccount?.fullName)) {
      setError(t("studentForm.parentAccountError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateStudentRequest = {
        userId: account.userId,
        newAccount: account.userId ? undefined : account.newAccount,
        studentCode: form.studentCode.trim(),
        dateOfBirth: form.dateOfBirth,
        gender: (form.gender || undefined) as CreateStudentRequest["gender"],
        primarySiteId: form.primarySiteId ? Number(form.primarySiteId) : undefined,
        originalSchool: form.originalSchool.trim() || undefined,
        originalClass: form.originalClass.trim() || undefined,
        enrollmentDate: form.enrollmentDate,
        notes: form.notes.trim() || undefined,
        portraitUrl: form.portraitUrl || undefined
      };
      const student = await createStudent(request);

      if (addParent) {
        const parent = await createParent({
          userId: parentAccount.userId,
          newAccount: parentAccount.userId ? undefined : parentAccount.newAccount
        });
        await linkParent(student.id, {
          parentId: parent.id,
          relationship: parentInfo.relationship as "FATHER" | "MOTHER" | "GUARDIAN" | "OTHER",
          isPrimaryContact: parentInfo.isPrimaryContact,
          isFinancialResponsible: parentInfo.isFinancialResponsible
        });
      }

      onCreated(student.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("studentForm.createError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t("studentForm.modalTitle")} size="lg">
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <div className="space-y-2 border-t border-slate-100 pt-4">
          <span className="text-[10px] font-bold uppercase text-slate-500">{t("studentForm.avatarLabel")}</span>
          <AvatarUploadField
            value={form.portraitUrl}
            onChange={(url) => setForm({ ...form, portraitUrl: url })}
            onUpload={(file) => uploadMedia(file, "STUDENT")}
            fallbackName={account.newAccount?.fullName || t("studentForm.avatarFallback")}
          />
        </div>

        <div className="space-y-2">
          <span className="text-[10px] font-bold uppercase text-slate-500">{t("studentForm.accountSectionTitle")}</span>
          <AccountSelector value={account} onChange={setAccount} submitAttempted={submitAttempted} />
        </div>

        <div className="space-y-3 border-t border-slate-100 pt-4">
          <span className="text-[10px] font-bold uppercase text-slate-500">{t("studentForm.infoSectionTitle")}</span>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass}>{t("studentForm.codeLabel")}</label>
              <input
                value={form.studentCode}
                onChange={(e) => setForm({ ...form, studentCode: e.target.value })}
                onBlur={() => markTouched("studentCode")}
                className={`${studentCodeInvalid ? inputErrorClass : inputClass} font-mono`}
              />
              {studentCodeInvalid && <p className="text-[10px] text-rose-600 mt-1">{t("studentForm.codeRequired")}</p>}
            </div>
            <div>
              <label className={labelClass}>{t("studentForm.genderLabel")}</label>
              <Select value={form.gender} onChange={(e) => setForm({ ...form, gender: e.target.value })} className={inputClass}>
                <option value="">{t("gender.unknown")}</option>
                <option value="MALE">{t("gender.MALE")}</option>
                <option value="FEMALE">{t("gender.FEMALE")}</option>
                <option value="OTHER">{t("gender.OTHER")}</option>
              </Select>
            </div>
            <div>
              <label className={labelClass}>{t("studentForm.dobLabel")}</label>
              <DatePicker
                value={form.dateOfBirth}
                onChange={(v) => {
                  setForm({ ...form, dateOfBirth: v });
                  markTouched("dateOfBirth");
                }}
                max={TODAY_ISO}
                hasError={dateOfBirthInvalid}
              />
              {dateOfBirthInvalid && <p className="text-[10px] text-rose-600 mt-1">{t("studentForm.dobRequired")}</p>}
            </div>
            <div>
              <label className={labelClass}>{t("studentForm.enrollmentDateLabel")}</label>
              <DatePicker
                value={form.enrollmentDate}
                onChange={(v) => {
                  setForm({ ...form, enrollmentDate: v });
                  markTouched("enrollmentDate");
                }}
                hasError={enrollmentDateInvalid}
              />
              {enrollmentDateInvalid && <p className="text-[10px] text-rose-600 mt-1">{t("studentForm.enrollmentDateRequired")}</p>}
            </div>
            <div>
              <label className={labelClass}>{t("studentForm.primarySiteLabel")}</label>
              <Select value={form.primarySiteId} onChange={(e) => setForm({ ...form, primarySiteId: e.target.value })} className={inputClass}>
                <option value="">{t("studentForm.primarySiteUnassigned")}</option>
                {sites.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <label className={labelClass}>{t("studentForm.originalSchoolLabel")}</label>
              <input value={form.originalSchool} onChange={(e) => setForm({ ...form, originalSchool: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>{t("studentForm.originalClassLabel")}</label>
              <input value={form.originalClass} onChange={(e) => setForm({ ...form, originalClass: e.target.value })} className={inputClass} />
            </div>
            <div>
              <label className={labelClass}>{t("studentForm.notesLabel")}</label>
              <input value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} className={inputClass} />
            </div>
          </div>
        </div>

        <div className="border-t border-slate-100 pt-4 space-y-3">
          <label className="flex items-center gap-2 text-xs font-bold text-slate-700 uppercase">
            <input type="checkbox" checked={addParent} onChange={(e) => setAddParent(e.target.checked)} />
            {t("studentForm.linkParentCheckbox")}
          </label>
          {addParent && (
            <div className="space-y-3 bg-slate-50/60 border border-slate-200 rounded-xl p-3">
              <AccountSelector value={parentAccount} onChange={setParentAccount} submitAttempted={submitAttempted} />
              <div className="grid grid-cols-3 gap-2 items-end">
                <div>
                  <label className={labelClass}>{t("studentForm.relationshipLabel")}</label>
                  <Select value={parentInfo.relationship} onChange={(e) => setParentInfo({ ...parentInfo, relationship: e.target.value })} className={inputClass}>
                    <option value="FATHER">{t("relationship.FATHER")}</option>
                    <option value="MOTHER">{t("relationship.MOTHER")}</option>
                    <option value="GUARDIAN">{t("relationship.GUARDIAN")}</option>
                    <option value="OTHER">{t("relationship.OTHER")}</option>
                  </Select>
                </div>
                <label className="flex items-center gap-1.5 text-[11px] font-semibold text-slate-700 pb-2.5">
                  <input type="checkbox" checked={parentInfo.isPrimaryContact} onChange={(e) => setParentInfo({ ...parentInfo, isPrimaryContact: e.target.checked })} />
                  {t("studentForm.primaryContactCheckbox")}
                </label>
                <label className="flex items-center gap-1.5 text-[11px] font-semibold text-slate-700 pb-2.5">
                  <input type="checkbox" checked={parentInfo.isFinancialResponsible} onChange={(e) => setParentInfo({ ...parentInfo, isFinancialResponsible: e.target.checked })} />
                  {t("studentForm.financialResponsibleCheckbox")}
                </label>
              </div>
            </div>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            {t("studentForm.cancel")}
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            <Plus className="w-3.5 h-3.5" />
            {submitting ? t("studentForm.creating") : t("studentForm.createButton")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
