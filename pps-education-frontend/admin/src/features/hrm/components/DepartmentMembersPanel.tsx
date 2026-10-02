import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Search, UserMinus, UserPlus } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import { useDialog } from "@/components/ui/DialogProvider";
import {
  DepartmentMemberResponse,
  DepartmentResponse,
  addDepartmentMembers,
  listDepartmentMembers,
  removeDepartmentMember,
  searchDepartmentMemberCandidates
} from "../api";
import FloatingError from "@/components/ui/FloatingError";

interface DepartmentMembersPanelProps {
  department: DepartmentResponse;
  canManage: boolean;
  onChanged: (message: string) => void;
}

/**
 * Thành viên của 1 phòng ban (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-10-01): trước đây chỉ gán
 * được phòng ban từng người ở hồ sơ cán bộ. Thêm nhiều nhân sự cùng lúc / gỡ khỏi phòng — vẫn là cột
 * employees.department_id, nên hồ sơ cán bộ, tổng quan công việc và lịch sử thay đổi theo phòng ban đồng bộ.
 */
export default function DepartmentMembersPanel({ department, canManage, onChanged }: DepartmentMembersPanelProps) {
  const { t } = useTranslation("hrm-org");
  const { confirmDialog } = useDialog();
  const [members, setMembers] = useState<DepartmentMemberResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  const load = () => {
    setError(null);
    listDepartmentMembers(department.id)
      .then(setMembers)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("departmentsTab.members.loadError")));
  };
  useEffect(load, [department.id]);

  const handleRemove = async (member: DepartmentMemberResponse) => {
    if (!(await confirmDialog(t("departmentsTab.members.removeConfirm", { name: member.fullName, department: department.name }), { danger: true }))) {
      return;
    }
    try {
      await removeDepartmentMember(department.id, member.employeeId);
      load();
      onChanged(t("departmentsTab.members.removedToast", { name: member.fullName }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("departmentsTab.members.removeError"));
    }
  };

  return (
    <div className="mt-2 border border-slate-100 rounded-lg bg-slate-50/60 p-3 space-y-2">
      <div className="flex items-center justify-between gap-2">
        <span className="text-[10px] font-bold uppercase text-slate-500">
          {t("departmentsTab.members.title", { count: members?.length ?? 0 })}
        </span>
        {canManage && (
          <Button size="sm" variant="secondary" onClick={() => setAdding(true)}>
            <UserPlus className="w-3.5 h-3.5" />
            {t("departmentsTab.members.addButton")}
          </Button>
        )}
      </div>

      <FloatingError message={error} onClose={() => setError(null)} />

      {members == null ? (
        <p className="text-[11px] text-slate-400">{t("departmentsTab.loading")}</p>
      ) : members.length === 0 ? (
        <p className="text-[11px] text-slate-400 italic">{t("departmentsTab.members.empty")}</p>
      ) : (
        <div className="divide-y divide-slate-100 bg-white border border-slate-100 rounded-lg">
          {members.map((m) => (
            <div key={m.employeeId} className="flex items-center justify-between gap-2 px-3 py-2 text-xs">
              <div className="min-w-0">
                <span className="font-semibold text-slate-800">{m.fullName}</span>
                <span className="text-[10px] text-slate-400 ml-2">{m.employeeCode}</span>
                {m.positionName && <span className="text-[10px] text-slate-500 ml-2">· {m.positionName}</span>}
                {department.headUserId === m.userId && (
                  <span className="ml-2 text-[10px] font-bold text-brand-red">{t("departmentsTab.members.headBadge")}</span>
                )}
              </div>
              {canManage && (
                <button
                  onClick={() => handleRemove(m)}
                  title={t("departmentsTab.members.removeButton")}
                  className="text-slate-400 hover:text-rose-600 shrink-0"
                >
                  <UserMinus className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {adding && (
        <AddMembersModal
          department={department}
          onClose={() => setAdding(false)}
          onAdded={(count) => {
            setAdding(false);
            load();
            onChanged(t("departmentsTab.members.addedToast", { count }));
          }}
        />
      )}
    </div>
  );
}

function AddMembersModal({
  department,
  onClose,
  onAdded
}: {
  department: DepartmentResponse;
  onClose: () => void;
  onAdded: (count: number) => void;
}) {
  const { t } = useTranslation("hrm-org");
  const [queryInput, setQueryInput] = useState("");
  const [candidates, setCandidates] = useState<DepartmentMemberResponse[]>([]);
  const [selected, setSelected] = useState<Map<number, DepartmentMemberResponse>>(new Map());
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const timer = setTimeout(() => {
      setLoading(true);
      searchDepartmentMemberCandidates(department.id, queryInput)
        .then(setCandidates)
        .catch((err) => setError(err instanceof ApiError ? err.message : t("departmentsTab.members.searchError")))
        .finally(() => setLoading(false));
    }, 300);
    return () => clearTimeout(timer);
  }, [department.id, queryInput, t]);

  const toggle = (c: DepartmentMemberResponse) => {
    setSelected((prev) => {
      const next = new Map(prev);
      if (next.has(c.employeeId)) next.delete(c.employeeId);
      else next.set(c.employeeId, c);
      return next;
    });
  };

  const movingCount = [...selected.values()].filter((c) => c.departmentId != null).length;

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      await addDepartmentMembers(department.id, [...selected.keys()]);
      onAdded(selected.size);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("departmentsTab.members.addError"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={t("departmentsTab.members.modalTitle", { department: department.name })}
      description={t("departmentsTab.members.modalDescription")}
      size="lg"
    >
      <div className="space-y-3">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
          <input
            autoFocus
            value={queryInput}
            onChange={(e) => setQueryInput(e.target.value)}
            placeholder={t("departmentsTab.members.searchPlaceholder")}
            className="w-full bg-slate-50 border border-slate-200 text-xs pl-8 pr-3 py-2.5 rounded-lg focus:outline-none"
          />
        </div>

        <div className="max-h-[45vh] overflow-y-auto border border-slate-100 rounded-lg divide-y divide-slate-100">
          {loading && candidates.length === 0 ? (
            <p className="text-[11px] text-slate-400 p-3">{t("departmentsTab.loading")}</p>
          ) : candidates.length === 0 ? (
            <p className="text-[11px] text-slate-400 italic p-3">{t("departmentsTab.members.noCandidates")}</p>
          ) : (
            candidates.map((c) => (
              <label key={c.employeeId} className="flex items-center gap-3 px-3 py-2 text-xs cursor-pointer hover:bg-slate-50">
                <input type="checkbox" checked={selected.has(c.employeeId)} onChange={() => toggle(c)} />
                <div className="min-w-0 flex-1">
                  <span className="font-semibold text-slate-800">{c.fullName}</span>
                  <span className="text-[10px] text-slate-400 ml-2">{c.employeeCode}</span>
                  {c.positionName && <span className="text-[10px] text-slate-500 ml-2">· {c.positionName}</span>}
                </div>
                <span className={c.departmentName ? "text-[10px] text-amber-600 shrink-0" : "text-[10px] text-slate-400 shrink-0"}>
                  {c.departmentName
                    ? t("departmentsTab.members.currentDepartment", { department: c.departmentName })
                    : t("departmentsTab.members.noDepartment")}
                </span>
              </label>
            ))
          )}
        </div>

        {movingCount > 0 && (
          <p className="text-[11px] text-amber-700 bg-amber-50 border border-amber-100 p-2 rounded-lg">
            {t("departmentsTab.members.moveWarning", { count: movingCount })}
          </p>
        )}
        <FloatingError message={error} onClose={() => setError(null)} />

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>{t("departmentsTab.members.cancel")}</Button>
          <Button variant="primary" onClick={handleSave} disabled={selected.size === 0 || saving}>
            <UserPlus className="w-3.5 h-3.5" />
            {saving ? t("departmentsTab.members.saving") : t("departmentsTab.members.confirmAdd", { count: selected.size })}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
