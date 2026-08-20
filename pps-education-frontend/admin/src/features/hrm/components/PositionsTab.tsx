import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Plus, Trash2 } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { listRoles, RoleResponse } from "@/features/system-admin/api";
import {
  createPosition,
  deletePosition,
  getPositionDefaultRoles,
  listPositions,
  PositionResponse,
  updatePosition,
  updatePositionDefaultRoles
} from "../api";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import { useDialog } from "@/components/ui/DialogProvider";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-xs p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

/** UC-08 bổ sung (V36): danh mục Chức vụ + gán role mặc định — chọn chức vụ này khi tạo/sửa nhân sự thì tự gán các role ở đây. */
export default function PositionsTab() {
  const { t } = useTranslation("hrm-org");
  const [positions, setPositions] = useState<PositionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const { message: toastMessage, showToast } = useToast();
  const { confirmDialog } = useDialog();

  const load = () => {
    setLoading(true);
    listPositions()
      .then((res) => {
        setPositions(res);
        if (selectedId == null && res.length > 0) setSelectedId(res[0].id);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("positionsTab.loadError")))
      .finally(() => setLoading(false));
  };
  useEffect(load, []);

  const selected = positions.find((p) => p.id === selectedId) ?? null;

  const handleDelete = async (id: number) => {
    if (!(await confirmDialog(t("positionsTab.deleteConfirm"), { danger: true }))) return;
    setError(null);
    try {
      await deletePosition(id);
      if (selectedId === id) setSelectedId(null);
      load();
      showToast(t("positionsTab.deletedToast"));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("positionsTab.deleteError"));
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <span className="text-[10px] font-bold uppercase text-slate-500">{t("positionsTab.sectionTitle", { count: positions.length })}</span>
          <Button size="sm" variant="secondary" onClick={() => setCreating(true)}>
            <Plus className="w-3.5 h-3.5" />
            {t("positionsTab.addButton")}
          </Button>
        </div>

        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <Modal open={creating} onClose={() => setCreating(false)} title={t("positionsTab.modalTitle")}>
          <PositionForm
            onDone={() => {
              setCreating(false);
              load();
              showToast(t("positionsTab.createdToast"));
            }}
            onCancel={() => setCreating(false)}
          />
        </Modal>

        {loading ? (
          <p className="text-xs text-slate-500">{t("positionsTab.loading")}</p>
        ) : positions.length === 0 ? (
          <p className="text-xs text-slate-400 italic">{t("positionsTab.empty")}</p>
        ) : (
          <div className="space-y-2">
            {positions.map((p) => (
              <button
                key={p.id}
                onClick={() => setSelectedId(p.id)}
                className={`w-full text-left border rounded-lg p-3 text-xs flex items-center justify-between transition-all ${
                  selectedId === p.id ? "border-brand-orange bg-orange-50/40" : "border-slate-200 hover:bg-slate-50"
                }`}
              >
                <div>
                  <span className="font-mono font-bold text-brand-red bg-orange-50 border border-orange-100 px-1.5 py-0.5 rounded text-[10px] mr-2">{p.code}</span>
                  <span className="font-bold text-slate-800">{p.name}</span>
                </div>
                <span onClick={(e) => { e.stopPropagation(); handleDelete(p.id); }} className="text-rose-500 hover:text-rose-700 cursor-pointer">
                  <Trash2 className="w-3.5 h-3.5" />
                </span>
              </button>
            ))}
          </div>
        )}
      </div>

      <div>
        {selected ? (
          <DefaultRolesPanel key={selected.id} position={selected} />
        ) : (
          <div className="border border-slate-200 rounded-lg p-8 text-center text-xs text-slate-400 italic">{t("positionsTab.selectHint")}</div>
        )}
      </div>

      <Toast message={toastMessage} />
    </div>
  );
}

function PositionForm({ onDone, onCancel }: { onDone: () => void; onCancel: () => void }) {
  const { t } = useTranslation("hrm-org");
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!code.trim() || !name.trim()) {
      setError(t("positionForm.validationError"));
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await createPosition({ code: code.trim(), name: name.trim() });
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("positionForm.saveError"));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      <div className="grid grid-cols-2 gap-2">
        <input value={code} onChange={(e) => setCode(e.target.value)} placeholder={t("positionForm.codePlaceholder")} className={`${inputClass} font-mono`} />
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder={t("positionForm.namePlaceholder")} className={inputClass} />
      </div>
      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          {t("positionForm.cancelButton")}
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? t("positionForm.saving") : t("positionForm.createButton")}
        </Button>
      </div>
    </form>
  );
}

function DefaultRolesPanel({ position }: { position: PositionResponse }) {
  const { t } = useTranslation("hrm-org");
  const [allRoles, setAllRoles] = useState<RoleResponse[]>([]);
  const [selectedRoleIds, setSelectedRoleIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    setLoading(true);
    setSuccess(false);
    Promise.all([listRoles(), getPositionDefaultRoles(position.id)])
      .then(([roles, defaults]) => {
        setAllRoles(roles);
        setSelectedRoleIds(new Set(defaults.defaultRoles.map((r) => r.id)));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("defaultRolesPanel.loadError")))
      .finally(() => setLoading(false));
  }, [position.id]);

  const toggle = (roleId: number) => {
    setSelectedRoleIds((prev) => {
      const next = new Set(prev);
      if (next.has(roleId)) next.delete(roleId);
      else next.add(roleId);
      return next;
    });
  };

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    setSuccess(false);
    try {
      await updatePositionDefaultRoles(position.id, Array.from(selectedRoleIds));
      setSuccess(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("defaultRolesPanel.saveError"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="border border-slate-200 rounded-lg p-4 space-y-3">
      <div>
        <span className="text-[10px] font-bold uppercase text-slate-500">{t("defaultRolesPanel.sectionTitle")}</span>
        <h4 className="text-sm font-bold text-slate-800 mt-0.5">{position.name}</h4>
        <p className="text-[10px] text-slate-400 mt-1">{t("defaultRolesPanel.hint")}</p>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      {success && <div className="text-xs text-emerald-700 bg-emerald-50 border border-emerald-100 p-2.5 rounded-lg">{t("defaultRolesPanel.savedSuccess")}</div>}

      {loading ? (
        <p className="text-xs text-slate-500">{t("defaultRolesPanel.loading")}</p>
      ) : (
        <div className="space-y-1.5 max-h-72 overflow-y-auto">
          {allRoles.map((r) => (
            <label key={r.id} className="flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-slate-50 cursor-pointer text-xs">
              <input type="checkbox" checked={selectedRoleIds.has(r.id)} onChange={() => toggle(r.id)} />
              <span className="font-semibold text-slate-700">{r.name}</span>
              <Badge variant="neutral">{r.code}</Badge>
            </label>
          ))}
        </div>
      )}

      <Button variant="primary" size="sm" onClick={handleSave} disabled={saving || loading}>
        {saving ? t("defaultRolesPanel.saving") : t("defaultRolesPanel.saveButton")}
      </Button>
    </div>
  );
}
