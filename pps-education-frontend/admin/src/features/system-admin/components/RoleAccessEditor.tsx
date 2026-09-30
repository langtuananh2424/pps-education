import React, { useEffect, useMemo, useState } from "react";
import { ChevronDown, ChevronRight, Copy, RotateCcw, Save, Search } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";
import Toast from "@/components/ui/Toast";
import { useToast } from "@/lib/useToast";
import { useDialog } from "@/components/ui/DialogProvider";
import { cn } from "@/lib/cn";
import { navSections } from "@/constants/navigation";
import { PAGE_PERMISSIONS } from "@/constants/pagePermissions";
import {
  DataScope,
  getRolePermissionMatrix,
  PermissionMatrixItem,
  RoleResponse,
  updateRoleDataScope,
  updateRolePermissions
} from "../api";
import PermissionChecklist from "./PermissionChecklist";

/** Vai trò Quản trị viên luôn có mọi quyền — backend cũng chặn sửa (RoleLockedException). */
const LOCKED_ROLE_CODE = "SYS_ADMIN";
const SCOPES: DataScope[] = ["ALL", "SITE", "CLASS"];

/** Các mục sidebar có trong catalog quyền, giữ nguyên nhóm/thứ tự của sidebar thật. */
const TREE = navSections
  .map((section) => ({ ...section, items: section.items.filter((item) => PAGE_PERMISSIONS[item.id]) }))
  .filter((section) => section.items.length > 0);
const CATALOG_CODES = new Set(Object.values(PAGE_PERMISSIONS).flatMap((p) => [...p.viewCodes, ...p.actions.flatMap((a) => a.codes)]));

interface RoleAccessEditorProps {
  role: RoleResponse;
  roles: RoleResponse[];
  onRoleChanged: () => void;
}

/**
 * V202 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-30) — chỉnh 1 vai trò theo 2 phần:
 * phạm vi dữ liệu + cây sidebar (tick mục = hiện menu và vào được trang; tick nút = hiện nút trong trang).
 * Quyền không thuộc sidebar (ngân hàng câu hỏi, luyện Nghe-Nói...) chỉnh ở khối "Quyền khác" bên dưới.
 */
export default function RoleAccessEditor({ role, roles, onRoleChanged }: RoleAccessEditorProps) {
  const { t } = useTranslation(["system-admin-roles", "layout"]);
  const { message: toastMessage, showToast } = useToast();
  const { confirmDialog } = useDialog();
  const locked = role.code === LOCKED_ROLE_CODE;

  const [items, setItems] = useState<PermissionMatrixItem[]>([]);
  const [savedCodes, setSavedCodes] = useState<Set<string>>(new Set());
  const [codes, setCodes] = useState<Set<string>>(new Set());
  const [scope, setScope] = useState<DataScope>(role.dataScope);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [onlyTicked, setOnlyTicked] = useState(false);
  const [open, setOpen] = useState<Record<string, boolean>>({});
  const [templateRoleId, setTemplateRoleId] = useState("");

  useEffect(() => {
    setLoading(true);
    setError(null);
    setScope(role.dataScope);
    setOpen({});
    getRolePermissionMatrix(role.id)
      .then((matrix) => {
        const granted = new Set(matrix.permissions.filter((p) => p.granted).map((p) => p.code));
        setItems(matrix.permissions);
        setSavedCodes(granted);
        setCodes(new Set(granted));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("roleAccessEditor.loadError")))
      .finally(() => setLoading(false));
  }, [role.id]);

  const codeToId = useMemo(() => new Map(items.map((p) => [p.code, p.permissionId])), [items]);
  const has = (code: string) => locked || codes.has(code);
  const menuOn = (itemId: string) => PAGE_PERMISSIONS[itemId].viewCodes.every(has);
  const actionOn = (actionCodes: string[]) => actionCodes.every(has);

  const dirty = useMemo(() => {
    if (scope !== role.dataScope || codes.size !== savedCodes.size) return true;
    for (const code of codes) if (!savedCodes.has(code)) return true;
    return false;
  }, [codes, savedCodes, scope, role.dataScope]);

  /** Mã còn được dùng bởi mục/nút khác đang tick — bỏ tick 1 mục không được làm mất nút dùng chung của mục khác. */
  const codesUsedElsewhere = (next: Set<string>, exceptItemId: string) => {
    const used = new Set<string>();
    Object.entries(PAGE_PERMISSIONS).forEach(([itemId, page]) => {
      if (itemId === exceptItemId || !page.viewCodes.every((c) => next.has(c))) return;
      page.viewCodes.forEach((c) => used.add(c));
      page.actions.filter((a) => a.codes.every((c) => next.has(c))).forEach((a) => a.codes.forEach((c) => used.add(c)));
    });
    return used;
  };

  const setMenu = (itemId: string, on: boolean) => {
    const page = PAGE_PERMISSIONS[itemId];
    setCodes((prev) => {
      const next = new Set(prev);
      if (on) {
        page.viewCodes.forEach((c) => next.add(c));
      } else {
        const keep = codesUsedElsewhere(next, itemId);
        [...page.viewCodes, ...page.actions.flatMap((a) => a.codes)].forEach((c) => !keep.has(c) && next.delete(c));
      }
      return next;
    });
    if (on) setOpen((prev) => ({ ...prev, [itemId]: true }));
  };

  const setAction = (itemId: string, actionCodes: string[], on: boolean) => {
    setCodes((prev) => {
      const next = new Set(prev);
      if (on) {
        actionCodes.forEach((c) => next.add(c));
        PAGE_PERMISSIONS[itemId].viewCodes.forEach((c) => next.add(c));
      } else {
        const keep = codesUsedElsewhere(new Set([...next].filter((c) => !actionCodes.includes(c))), itemId);
        actionCodes.forEach((c) => !keep.has(c) && next.delete(c));
      }
      return next;
    });
  };

  const setSection = (sectionId: string, on: boolean) => {
    const section = TREE.find((s) => s.id === sectionId);
    if (!section) return;
    if (on) {
      setCodes((prev) => {
        const next = new Set(prev);
        section.items.forEach((item) => {
          const page = PAGE_PERMISSIONS[item.id];
          [...page.viewCodes, ...page.actions.flatMap((a) => a.codes)].forEach((c) => next.add(c));
        });
        return next;
      });
    } else {
      section.items.forEach((item) => setMenu(item.id, false));
    }
  };

  const setOtherPermission = (permissionId: number) => {
    const code = items.find((p) => p.permissionId === permissionId)?.code;
    if (!code) return;
    setCodes((prev) => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  };

  const applyTemplate = async () => {
    const template = roles.find((r) => String(r.id) === templateRoleId);
    if (!template) return;
    const proceed = await confirmDialog(t("roleAccessEditor.applyTemplateConfirm", { from: template.name, to: role.name }));
    if (!proceed) return;
    try {
      const matrix = await getRolePermissionMatrix(template.id);
      setCodes(new Set(matrix.permissions.filter((p) => p.granted).map((p) => p.code)));
      setScope(template.dataScope === "SELF" ? "CLASS" : template.dataScope);
      setTemplateRoleId("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("roleAccessEditor.loadError"));
    }
  };

  const save = async () => {
    setSaving(true);
    setError(null);
    const ids = [...codes].map((c) => codeToId.get(c)).filter((id): id is number => id != null);
    try {
      if (scope !== role.dataScope) await updateRoleDataScope(role.id, scope);
      try {
        await updateRolePermissions(role.id, ids, false);
      } catch (err) {
        // UC-03 A1 — bỏ hết quyền của vai trò đang có tài khoản hoạt động cần xác nhận lại.
        if (!(err instanceof ApiError && err.status === 409)) throw err;
        const proceed = await confirmDialog(t("system-admin-roles:rolePermissionsEditor.conflictConfirm", { message: err.message }), { danger: true });
        if (!proceed) return;
        await updateRolePermissions(role.id, ids, true);
      }
      setSavedCodes(new Set(codes));
      showToast(t("roleAccessEditor.saveSuccess"));
      onRoleChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("roleAccessEditor.saveError"));
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <p className="text-xs text-slate-500">{t("roleAccessEditor.loading")}</p>;

  const q = query.trim().toLowerCase();
  const itemLabel = (id: string, fallback: string) => t(`layout:nav.items.${id}`, fallback);
  const menuCount = TREE.reduce((n, s) => n + s.items.filter((i) => menuOn(i.id)).length, 0);
  const menuTotal = TREE.reduce((n, s) => n + s.items.length, 0);
  const otherItems = items.filter((p) => !CATALOG_CODES.has(p.code) && p.code !== "academic.class.manage");

  // Bố cục theo độ rộng thật của khung chỉnh (container query), không theo độ rộng màn hình — khung này nằm
  // sau sidebar và danh sách vai trò nên hẹp hơn nhiều; chỉ hiện cột xem trước khi khung đủ rộng.
  return (
    <div className="@container">
      <div className="grid grid-cols-1 @4xl:grid-cols-[minmax(0,1fr)_220px] gap-5">
        <div className="space-y-5 min-w-0">
          {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
          {locked && (
            <div className="text-xs text-emerald-800 bg-emerald-50 border border-emerald-100 p-2.5 rounded-lg">{t("roleAccessEditor.lockedNotice")}</div>
          )}

          {!locked && (
            <div className="flex flex-wrap items-center justify-end gap-2">
              <Select
                value={templateRoleId}
                onChange={(e) => setTemplateRoleId(e.target.value)}
                className="bg-white border border-slate-200 text-xs px-2 py-1.5 rounded-lg text-slate-700 w-auto"
                aria-label={t("roleAccessEditor.templatePlaceholder")}
              >
                <option value="">{t("roleAccessEditor.templatePlaceholder")}</option>
                {roles
                  .filter((r) => r.id !== role.id && r.dataScope !== "SELF")
                  .map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.name}
                    </option>
                  ))}
              </Select>
              <Button variant="secondary" size="sm" onClick={applyTemplate} disabled={!templateRoleId}>
                <Copy className="w-3.5 h-3.5" />
                {t("roleAccessEditor.applyTemplateButton")}
              </Button>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  setCodes(new Set(savedCodes));
                  setScope(role.dataScope);
                }}
                disabled={!dirty || saving}
              >
                <RotateCcw className="w-3.5 h-3.5" />
                {t("roleAccessEditor.undoButton")}
              </Button>
              <Button variant="primary" size="sm" onClick={save} disabled={!dirty || saving}>
                <Save className="w-3.5 h-3.5" />
                {saving ? t("roleAccessEditor.saving") : t("roleAccessEditor.saveButton")}
              </Button>
            </div>
          )}

          <section className="space-y-2">
            <h3 className="text-xs font-bold text-slate-800">{t("roleAccessEditor.scopeTitle")}</h3>
            <p className="text-[11px] text-slate-500">{t("roleAccessEditor.scopeHint")}</p>
            <div className="grid grid-cols-1 @2xl:grid-cols-3 gap-2">
              {SCOPES.map((value) => (
                <label
                  key={value}
                  className={cn(
                    "flex gap-2.5 items-start border rounded-xl p-3 cursor-pointer bg-white",
                    (locked ? "ALL" : scope) === value ? "border-brand-red bg-orange-50/60" : "border-slate-200",
                    locked && "cursor-not-allowed"
                  )}
                >
                  <input
                    type="radio"
                    name={`scope-${role.id}`}
                    value={value}
                    checked={(locked ? "ALL" : scope) === value}
                    disabled={locked}
                    onChange={() => setScope(value)}
                    className="mt-0.5 accent-brand-red"
                  />
                  <span>
                    <span className="block text-xs font-bold text-slate-800">{t(`roleAccessEditor.scopes.${value}.title`)}</span>
                    <span className="block text-[11px] text-slate-500">{t(`roleAccessEditor.scopes.${value}.description`)}</span>
                  </span>
                </label>
              ))}
            </div>
          </section>

          <section className="space-y-2">
            <h3 className="text-xs font-bold text-slate-800">{t("roleAccessEditor.treeTitle")}</h3>
            <p className="text-[11px] text-slate-500">{t("roleAccessEditor.treeHint")}</p>
            <div className="flex flex-wrap items-center gap-3 bg-slate-50 p-2.5 rounded-xl border border-slate-200/60">
              <div className="relative w-full sm:w-60">
                <Search className="absolute left-2.5 top-2 w-3.5 h-3.5 text-slate-400" />
                <input
                  type="search"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder={t("roleAccessEditor.searchPlaceholder")}
                  className="w-full bg-white border border-slate-200 text-xs pl-8 pr-3 py-1.5 rounded-lg focus:outline-none"
                />
              </div>
              <label className="flex items-center gap-1.5 text-xs text-slate-700 cursor-pointer">
                <input type="checkbox" checked={onlyTicked} onChange={(e) => setOnlyTicked(e.target.checked)} className="accent-brand-red" />
                {t("roleAccessEditor.onlyTicked")}
              </label>
              <span className="text-xs text-slate-600 ml-auto">{t("roleAccessEditor.menuCount", { count: menuCount, total: menuTotal })}</span>
            </div>

            <div className="border border-slate-200 rounded-xl overflow-hidden divide-y divide-slate-200">
              {TREE.map((section) => {
                const visible = section.items.filter((item) => {
                  const page = PAGE_PERMISSIONS[item.id];
                  const hit =
                    !q ||
                    itemLabel(item.id, item.label).toLowerCase().includes(q) ||
                    page.actions.some((a) => a.label.toLowerCase().includes(q) || a.codes.some((c) => c.includes(q)));
                  return hit && (!onlyTicked || menuOn(item.id));
                });
                if (visible.length === 0) return null;
                const onCount = section.items.filter((i) => menuOn(i.id)).length;
                return (
                  <div key={section.id}>
                    <label className="flex items-center gap-2 px-3 py-2 bg-slate-50 text-[11px] font-bold tracking-wider uppercase text-brand-red cursor-pointer">
                      <input
                        type="checkbox"
                        checked={onCount === section.items.length}
                        ref={(el) => {
                          if (el) el.indeterminate = onCount > 0 && onCount < section.items.length;
                        }}
                        disabled={locked}
                        onChange={(e) => setSection(section.id, e.target.checked)}
                        className="accent-brand-red"
                      />
                      <span>{t(`layout:nav.sections.${section.id}`, section.title)}</span>
                      <span className="ml-auto normal-case tracking-normal font-semibold text-slate-500">
                        {t("roleAccessEditor.sectionCount", { count: onCount, total: section.items.length })}
                      </span>
                    </label>
                    {visible.map((item) => {
                      const page = PAGE_PERMISSIONS[item.id];
                      const on = menuOn(item.id);
                      const isOpen = open[item.id] || !!q;
                      const actionsOn = page.actions.filter((a) => actionOn(a.codes)).length;
                      return (
                        <div key={item.id} className="border-t border-slate-100">
                          <div className="flex items-center gap-2 pl-4 pr-3 py-2">
                            <button
                              type="button"
                              onClick={() => setOpen((prev) => ({ ...prev, [item.id]: !prev[item.id] }))}
                              className={cn("text-slate-400 hover:text-slate-700", page.actions.length === 0 && "invisible")}
                              aria-label={t("roleAccessEditor.toggleActions")}
                            >
                              {isOpen ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
                            </button>
                            <input
                              id={`menu-${role.id}-${item.id}`}
                              type="checkbox"
                              checked={on}
                              disabled={locked}
                              onChange={(e) => setMenu(item.id, e.target.checked)}
                              className="accent-brand-red"
                            />
                            <label htmlFor={`menu-${role.id}-${item.id}`} className={cn("text-xs font-semibold cursor-pointer", on ? "text-slate-800" : "text-slate-400")}>
                              {itemLabel(item.id, item.label)}
                            </label>
                            <span className={cn("ml-auto text-[10px] font-semibold rounded-full px-2", actionsOn ? "bg-emerald-50 text-emerald-700" : "bg-slate-100 text-slate-500")}>
                              {page.actions.length
                                ? t("roleAccessEditor.actionCount", { count: actionsOn, total: page.actions.length })
                                : t("roleAccessEditor.viewOnly")}
                            </span>
                          </div>
                          {isOpen &&
                            page.actions
                              .filter((a) => !q || itemLabel(item.id, item.label).toLowerCase().includes(q) || a.label.toLowerCase().includes(q) || a.codes.some((c) => c.includes(q)))
                              .map((action) => {
                                const aOn = actionOn(action.codes);
                                return (
                                  <label key={action.label} className="flex items-center gap-2 pl-14 pr-3 py-1.5 border-t border-dashed border-slate-100 cursor-pointer">
                                    <input
                                      type="checkbox"
                                      checked={aOn}
                                      disabled={locked}
                                      onChange={(e) => setAction(item.id, action.codes, e.target.checked)}
                                      className="accent-brand-red"
                                    />
                                    <span className={cn("text-xs", aOn ? "text-slate-700" : "text-slate-400")}>{action.label}</span>
                                    <code className="ml-auto text-[10px] font-mono text-slate-400 truncate max-w-[45%]">{action.codes.join(", ")}</code>
                                  </label>
                                );
                              })}
                        </div>
                      );
                    })}
                  </div>
                );
              })}
            </div>
          </section>

          {otherItems.length > 0 && (
            <details className="border border-slate-200 rounded-xl p-3">
              <summary className="text-xs font-bold text-slate-800 cursor-pointer">{t("roleAccessEditor.otherTitle", { count: otherItems.length })}</summary>
              <p className="text-[11px] text-slate-500 mt-1 mb-3">{t("roleAccessEditor.otherHint")}</p>
              <PermissionChecklist
                items={otherItems.map((p) => ({ permissionId: p.permissionId, code: p.code, name: p.name, module: p.module }))}
                selectedIds={new Set(otherItems.filter((p) => has(p.code)).map((p) => p.permissionId))}
                onToggle={locked ? () => undefined : setOtherPermission}
                onToggleModuleAll={(ids) => ids.forEach((id) => !locked && setOtherPermission(id))}
              />
            </details>
          )}
        </div>

        <aside>
          <div className="@4xl:sticky top-4 bg-slate-50 border border-slate-200 rounded-2xl p-3 space-y-2">
            <p className="text-[10px] font-bold uppercase tracking-wider text-slate-400">{t("roleAccessEditor.previewTitle")}</p>
            <div className="bg-white border border-slate-200 rounded-xl p-2 space-y-1 max-h-[560px] overflow-y-auto">
              {menuCount === 0 && <p className="text-[11px] text-slate-400 p-2">{t("roleAccessEditor.previewEmpty")}</p>}
              {TREE.map((section) => {
                const shown = section.items.filter((i) => menuOn(i.id));
                if (shown.length === 0) return null;
                return (
                  <div key={section.id}>
                    <p className="text-[10px] font-semibold text-slate-400 px-2 pt-1.5">{t(`layout:nav.sections.${section.id}`, section.title)}</p>
                    {shown.map((item) => (
                      <p key={item.id} className="text-xs text-slate-700 px-2 py-1 rounded-md">
                        {itemLabel(item.id, item.label)}
                      </p>
                    ))}
                  </div>
                );
              })}
            </div>
            <p className="text-[10px] text-slate-500">{t("roleAccessEditor.previewHint")}</p>
          </div>
        </aside>

        <Toast message={toastMessage} />
      </div>
    </div>
  );
}
