import React, { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { AudioLines, Download, History, RotateCcw, Save, Undo2, Upload } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { Button, Modal, notifySuccess } from "@/components/ui";
import FloatingError from "@/components/ui/FloatingError";
import { useDialog } from "@/components/ui/DialogProvider";
import {
  exportPpsScoringConfig,
  getPpsScoringConfig,
  getPpsScoringConfigHistory,
  PpsConfigField,
  PpsScoringConfigHistoryItem,
  PpsScoringConfigResponse,
  resetPpsScoringConfig,
  savePpsScoringConfig
} from "../ppsScoringConfigApi";
import {
  CrossError,
  Draft,
  FieldError,
  groupsOf,
  importConfig,
  overridesOf,
  parseDraft,
  toDraft,
  unsavedPaths,
  validate
} from "../ppsScoringConfigLogic";

/** Số hiển thị gọn: bỏ số 0 thừa sau dấu thập phân ("1.50" thành "1.5"). */
function fmt(n: number): string {
  return Number.isFinite(n) ? String(Math.round(n * 1e6) / 1e6) : "—";
}

/**
 * Cấu hình bộ chấm phát âm PPS Hybrid v2 (V225). Biểu mẫu vẽ từ danh sách tham số backend trả về (nhãn, nhóm, khoảng cho phép, mặc định),
 * nên thêm tham số mới ở backend là trang tự có. Backend kiểm tra lại mọi giá trị; ở đây kiểm tra trước để báo lỗi ngay khi gõ.
 * Xem: system.settings.view. Sửa, lưu, khôi phục, nhập: system.settings.manage.
 */
export default function PpsScoringConfigPage() {
  const { t } = useTranslation("system-admin-pps-config");
  const { hasPermission } = useApp();
  const { confirmDialog } = useDialog();
  const canManage = hasPermission("system.settings.manage");

  const [server, setServer] = useState<PpsScoringConfigResponse | null>(null);
  const [draft, setDraft] = useState<Draft>({});
  const [note, setNote] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [importInfo, setImportInfo] = useState<string | null>(null);

  const [historyOpen, setHistoryOpen] = useState(false);
  const [history, setHistory] = useState<PpsScoringConfigHistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  const fileInput = useRef<HTMLInputElement>(null);

  const adopt = (response: PpsScoringConfigResponse) => {
    setServer(response);
    setDraft(toDraft(response.fields));
    setNote("");
    setImportInfo(null);
  };

  useEffect(() => {
    getPpsScoringConfig()
      .then(adopt)
      .catch((err) => setError(err instanceof ApiError ? err.message : t("ppsConfigPage.loadError")))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const fields: PpsConfigField[] = server?.fields ?? [];
  const values = useMemo(() => parseDraft(draft, fields), [draft, fields]);
  const validation = useMemo(() => validate(fields, values, server?.weightGroups ?? []), [fields, values, server]);
  const unsaved = useMemo(() => new Set(unsavedPaths(fields, values)), [fields, values]);
  const groups = useMemo(() => groupsOf(fields), [fields]);
  const changedCount = Object.keys(server?.overrides ?? {}).length;
  const dirty = unsaved.size > 0;

  const weightName = (prefix: string) => t(`ppsConfigPage.weightGroup.${prefix.replace(/\./g, "_")}`);

  const fieldErrorText = (e: FieldError): string =>
    e.kind === "notNumber"
      ? t("ppsConfigPage.errors.notNumber")
      : t("ppsConfigPage.errors.range", { min: fmt(e.min), max: fmt(e.max) });

  const crossErrorText = (e: CrossError): string => {
    if (e.kind === "weightSum") return t("ppsConfigPage.errors.weightSum", { name: weightName(e.prefix), sum: fmt(e.sum) });
    return t(e.kind === "topFloor" ? "ppsConfigPage.errors.topFloor" : "ppsConfigPage.errors.pauseOrder");
  };

  const handleSave = () => {
    if (!server || !validation.ok || !dirty) return;
    setSaving(true);
    setError(null);
    savePpsScoringConfig(overridesOf(fields, values), note)
      .then((r) => {
        adopt(r);
        notifySuccess(t("ppsConfigPage.saved"));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("ppsConfigPage.saveErrorDefault")))
      .finally(() => setSaving(false));
  };

  const handleResetAll = async () => {
    if (!(await confirmDialog(t("ppsConfigPage.resetConfirm")))) return;
    setSaving(true);
    setError(null);
    resetPpsScoringConfig()
      .then((r) => {
        adopt(r);
        notifySuccess(t("ppsConfigPage.resetDone"));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("ppsConfigPage.saveErrorDefault")))
      .finally(() => setSaving(false));
  };

  const handleExport = () => {
    exportPpsScoringConfig()
      .then((data) => {
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `pps-scoring-config-${new Date().toISOString().slice(0, 10)}.json`;
        a.click();
        URL.revokeObjectURL(url);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("ppsConfigPage.loadError")));
  };

  const handleImportFile = (file: File | undefined) => {
    if (!file || !server) return;
    file
      .text()
      .then((text) => {
        let raw: unknown;
        try {
          raw = JSON.parse(text);
        } catch {
          setError(t("ppsConfigPage.importFile.invalidJson"));
          return;
        }
        const result = importConfig(raw, server.fields);
        if (result.unknownKeys.length > 0) {
          setError(t("ppsConfigPage.importFile.unknownKeys", { keys: result.unknownKeys.join(", ") }));
          return;
        }
        if (result.notNumberKeys.length > 0) {
          setError(t("ppsConfigPage.importFile.notNumberKeys", { keys: result.notNumberKeys.join(", ") }));
          return;
        }
        setError(null);
        setDraft(result.draft);
        setImportInfo(t("ppsConfigPage.importFile.done", { count: result.importedCount }));
      })
      .finally(() => {
        if (fileInput.current) fileInput.current.value = "";
      });
  };

  const openHistory = () => {
    setHistoryOpen(true);
    setHistoryLoading(true);
    getPpsScoringConfigHistory()
      .then(setHistory)
      .catch(() => {
        setHistory([]);
        setError(t("ppsConfigPage.history.loadError"));
      })
      .finally(() => setHistoryLoading(false));
  };

  const labelOf = (path: string) => fields.find((f) => f.path === path)?.label ?? path;
  const defaultOf = (path: string) => fields.find((f) => f.path === path)?.defaultValue;

  const changesOf = (item: PpsScoringConfigHistoryItem) => {
    const paths = Array.from(new Set([...Object.keys(item.previous), ...Object.keys(item.overrides)]));
    return paths.map((path) => ({
      path,
      from: item.previous[path] ?? defaultOf(path),
      to: item.overrides[path] ?? defaultOf(path)
    }));
  };

  return (
    <div className="space-y-4 animate-in fade-in duration-200">
      <div>
        <h2 className="text-lg font-bold text-slate-800 uppercase tracking-wider block">{t("ppsConfigPage.title")}</h2>
        <p className="text-[12px] text-slate-400 mt-0.5 max-w-3xl">{t("ppsConfigPage.description")}</p>
      </div>

      <FloatingError message={error} onClose={() => setError(null)} />

      {server && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-soft p-4 space-y-3">
          <div className="flex flex-wrap items-center gap-2 text-[13px]">
            <span className="px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-semibold">
              {changedCount > 0 ? t("ppsConfigPage.summary.changed", { count: changedCount }) : t("ppsConfigPage.summary.allDefault")}
            </span>
            {dirty && (
              <span className="px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 font-semibold">
                {t("ppsConfigPage.summary.unsaved", { count: unsaved.size })}
              </span>
            )}
            <span className="text-slate-400">
              {server.updatedByName && server.updatedAt
                ? t("ppsConfigPage.lastUpdated", { name: server.updatedByName, time: new Date(server.updatedAt).toLocaleString("vi-VN") })
                : t("ppsConfigPage.neverUpdated")}
            </span>
          </div>

          {importInfo && <p className="text-[13px] text-sky-700 bg-sky-50 border border-sky-100 rounded-lg px-3 py-2">{importInfo}</p>}

          {validation.crossErrors.length > 0 && (
            <ul className="text-[13px] text-rose-600 bg-rose-50 border border-rose-100 rounded-lg px-3 py-2 space-y-0.5 list-disc list-inside">
              {validation.crossErrors.map((e, i) => (
                <li key={i}>{crossErrorText(e)}</li>
              ))}
            </ul>
          )}

          {canManage && (
            <div>
              <label className="text-[12px] uppercase font-bold text-slate-500 block mb-1">{t("ppsConfigPage.noteLabel")}</label>
              <input
                type="text"
                value={note}
                maxLength={500}
                onChange={(e) => setNote(e.target.value)}
                placeholder={t("ppsConfigPage.notePlaceholder")}
                className="w-full bg-slate-50 border border-slate-200 text-sm p-2.5 rounded-lg focus:outline-none"
              />
            </div>
          )}

          <div className="flex flex-wrap items-center gap-2">
            {canManage && (
              <>
                <Button variant="dark" onClick={handleSave} disabled={saving || !dirty || !validation.ok}>
                  <Save className="w-3.5 h-3.5" />
                  {saving ? t("ppsConfigPage.actions.saving") : t("ppsConfigPage.actions.save")}
                </Button>
                <Button variant="secondary" onClick={() => adopt(server)} disabled={saving || !dirty}>
                  <Undo2 className="w-3.5 h-3.5" />
                  {t("ppsConfigPage.actions.discard")}
                </Button>
                <Button variant="danger" onClick={handleResetAll} disabled={saving || changedCount === 0}>
                  <RotateCcw className="w-3.5 h-3.5" />
                  {t("ppsConfigPage.actions.resetAll")}
                </Button>
              </>
            )}
            <div className="flex-1" />
            <Button variant="secondary" onClick={handleExport}>
              <Download className="w-3.5 h-3.5" />
              {t("ppsConfigPage.actions.export")}
            </Button>
            {canManage && (
              <>
                <Button variant="secondary" onClick={() => fileInput.current?.click()} disabled={saving}>
                  <Upload className="w-3.5 h-3.5" />
                  {t("ppsConfigPage.actions.import")}
                </Button>
                <input
                  ref={fileInput}
                  type="file"
                  accept="application/json,.json"
                  className="hidden"
                  onChange={(e) => handleImportFile(e.target.files?.[0])}
                />
              </>
            )}
            <Button variant="secondary" onClick={openHistory}>
              <History className="w-3.5 h-3.5" />
              {t("ppsConfigPage.actions.history")}
            </Button>
          </div>
        </div>
      )}

      {loading && <p className="text-sm text-slate-400">…</p>}

      {groups.map(({ group, fields: groupFields }) => {
        const prefixes = (server?.weightGroups ?? []).filter((p) => groupFields.some((f) => f.path.startsWith(`${p}.`)));
        return (
          <div key={group} className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
            <div className="px-4 py-2.5 bg-slate-50 border-b border-slate-100 flex flex-wrap items-center gap-2">
              <AudioLines className="w-4 h-4 text-slate-400" />
              <h3 className="text-[13px] font-bold uppercase tracking-wider text-slate-600">{group}</h3>
              {prefixes.map((p) => {
                const off = validation.crossErrors.some((e) => e.kind === "weightSum" && e.prefix === p);
                return (
                  <span
                    key={p}
                    className={`text-[12px] font-semibold px-2 py-0.5 rounded-full ${off ? "bg-rose-100 text-rose-700" : "bg-emerald-100 text-emerald-700"}`}
                  >
                    {weightName(p)}: {fmt(validation.weightSums[p] ?? 0)}
                  </span>
                );
              })}
            </div>
            <div className="divide-y divide-slate-100">
              {groupFields.map((f) => {
                const err = validation.fieldErrors[f.path];
                const differsFromDefault = Math.abs(values[f.path] - f.defaultValue) > 1e-9;
                return (
                  <div key={f.path} className="grid gap-2 md:grid-cols-[1fr_190px_170px] items-start px-4 py-3">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <span className="text-[14px] font-semibold text-slate-700">{f.label}</span>
                        {f.changed && !unsaved.has(f.path) && (
                          <span className="text-[11px] font-semibold px-1.5 py-0.5 rounded bg-sky-100 text-sky-700">
                            {t("ppsConfigPage.field.changedBadge")}
                          </span>
                        )}
                        {unsaved.has(f.path) && (
                          <span className="text-[11px] font-semibold px-1.5 py-0.5 rounded bg-amber-100 text-amber-700">
                            {t("ppsConfigPage.field.unsavedBadge")}
                          </span>
                        )}
                      </div>
                      {f.help && <p className="text-[12px] text-slate-400 mt-0.5">{f.help}</p>}
                      <p className="text-[11px] text-slate-300 font-mono mt-0.5">{f.path}</p>
                    </div>
                    <div>
                      <input
                        type="number"
                        inputMode="decimal"
                        step={f.step}
                        min={f.min}
                        max={f.max}
                        value={draft[f.path] ?? ""}
                        disabled={!canManage || saving}
                        onChange={(e) => setDraft((d) => ({ ...d, [f.path]: e.target.value }))}
                        className={`w-full bg-slate-50 border text-sm p-2 rounded-lg font-mono focus:outline-none disabled:opacity-70 ${err ? "border-rose-300" : "border-slate-200"}`}
                      />
                      {err ? (
                        <p className="text-[12px] text-rose-600 mt-1">{fieldErrorText(err)}</p>
                      ) : (
                        <p className="text-[11px] text-slate-400 mt-1">{t("ppsConfigPage.field.range", { min: fmt(f.min), max: fmt(f.max) })}</p>
                      )}
                    </div>
                    <div className="flex items-center gap-2 text-[12px] text-slate-400">
                      <span>{t("ppsConfigPage.field.default", { value: fmt(f.defaultValue) })}</span>
                      {canManage && differsFromDefault && (
                        <Button
                          size="sm"
                          variant="ghost"
                          title={t("ppsConfigPage.actions.resetField")}
                          onClick={() => setDraft((d) => ({ ...d, [f.path]: String(f.defaultValue) }))}
                          disabled={saving}
                        >
                          <RotateCcw className="w-3.5 h-3.5" />
                        </Button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}

      <Modal open={historyOpen} onClose={() => setHistoryOpen(false)} title={t("ppsConfigPage.history.title")} size="lg">
        {historyLoading ? (
          <p className="text-sm text-slate-400">{t("ppsConfigPage.history.loading")}</p>
        ) : history.length === 0 ? (
          <p className="text-sm text-slate-400 italic">{t("ppsConfigPage.history.empty")}</p>
        ) : (
          <div className="space-y-2">
            {history.map((h) => {
              const changes = changesOf(h);
              return (
                <div key={h.id} className="border border-slate-100 rounded-lg p-3 text-sm space-y-1.5">
                  <div className="flex items-center justify-between text-[12px] text-slate-400">
                    <span className="font-semibold text-slate-600">{h.changedByName}</span>
                    <span>{new Date(h.createdAt).toLocaleString("vi-VN")}</span>
                  </div>
                  {h.note && <p className="text-[13px] text-slate-600 italic">{h.note}</p>}
                  {changes.length === 0 ? (
                    <p className="text-[13px] text-slate-400">{t("ppsConfigPage.history.noChange")}</p>
                  ) : (
                    <ul className="space-y-0.5">
                      {changes.map((c) => (
                        <li key={c.path} className="flex flex-wrap items-center gap-2 text-[13px]">
                          <span className="text-slate-600">{labelOf(c.path)}</span>
                          <span className="font-mono text-rose-500 line-through">{c.from === undefined ? "—" : fmt(c.from)}</span>
                          <span className="text-slate-300">→</span>
                          <span className="font-mono font-bold text-emerald-600">{c.to === undefined ? "—" : fmt(c.to)}</span>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </Modal>
    </div>
  );
}
