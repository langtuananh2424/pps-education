import React, { useEffect, useMemo, useState } from "react";
import { History, Save, Settings2 } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  getSystemSettingHistory,
  listSystemSettings,
  SystemSettingHistoryResponse,
  SystemSettingResponse,
  updateSystemSetting
} from "../api";
import { Button, EmptyState, Modal, TableContainer, Td, Th } from "@/components/ui";

const CATEGORY_LABELS: Record<string, string> = {
  ATTENDANCE: "Chấm công",
  SECURITY: "Bảo mật",
  NOTIFICATION: "Thông báo",
  FEATURE_FLAG: "Cờ tính năng",
  ACADEMIC: "Học thuật",
  FINANCE: "Tài chính",
  TASK: "Công việc"
};

/** Suy ra input phù hợp từ giá trị JSON hiện tại — không có schema per-key nên chỉ dựa vào kiểu JS thực tế. */
type ValueKind = "boolean" | "number" | "string";

function kindOf(value: boolean | number | string): ValueKind {
  if (typeof value === "boolean") return "boolean";
  if (typeof value === "number") return "number";
  return "string";
}

/**
 * Cài đặt hệ thống — bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-08-08. Chỉ sửa giá trị các key có sẵn (không thêm/xoá key qua UI —
 * key mới vẫn tạo qua migration như trước). Xem SystemSettingController.
 */
export default function SystemSettingsPage() {
  const [settings, setSettings] = useState<SystemSettingResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [editing, setEditing] = useState<SystemSettingResponse | null>(null);
  const [draftValue, setDraftValue] = useState<string>("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const [historyFor, setHistoryFor] = useState<SystemSettingResponse | null>(null);
  const [history, setHistory] = useState<SystemSettingHistoryResponse[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  const load = () => {
    setLoading(true);
    setError(null);
    listSystemSettings()
      .then(setSettings)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách cấu hình."))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const grouped = useMemo(() => {
    const map = new Map<string, SystemSettingResponse[]>();
    for (const s of settings) {
      const list = map.get(s.category) ?? [];
      list.push(s);
      map.set(s.category, list);
    }
    return Array.from(map.entries()).sort(([a], [b]) => a.localeCompare(b));
  }, [settings]);

  const openEdit = (setting: SystemSettingResponse) => {
    setEditing(setting);
    setSaveError(null);
    setDraftValue(String(setting.settingValue));
  };

  const closeEdit = () => {
    setEditing(null);
    setSaveError(null);
  };

  const handleSave = () => {
    if (!editing) return;
    const kind = kindOf(editing.settingValue);
    let parsedValue: boolean | number | string;
    if (kind === "boolean") {
      parsedValue = draftValue === "true";
    } else if (kind === "number") {
      const n = Number(draftValue);
      if (Number.isNaN(n)) {
        setSaveError("Giá trị phải là số hợp lệ.");
        return;
      }
      parsedValue = n;
    } else {
      parsedValue = draftValue;
    }

    setSaving(true);
    setSaveError(null);
    updateSystemSetting(editing.settingKey, parsedValue)
      .then((updated) => {
        setSettings((prev) => prev.map((s) => (s.settingKey === updated.settingKey ? updated : s)));
        closeEdit();
      })
      .catch((err) => setSaveError(err instanceof ApiError ? err.message : "Lưu thất bại."))
      .finally(() => setSaving(false));
  };

  const openHistory = (setting: SystemSettingResponse) => {
    setHistoryFor(setting);
    setHistoryLoading(true);
    getSystemSettingHistory(setting.settingKey)
      .then(setHistory)
      .catch(() => setHistory([]))
      .finally(() => setHistoryLoading(false));
  };

  return (
    <div className="space-y-4 animate-in fade-in duration-200">
      <div>
        <h2 className="text-sm font-bold text-slate-800 uppercase tracking-wider block">Cài đặt hệ thống</h2>
        <p className="text-[10px] text-slate-400 mt-0.5">
          Bật/tắt tính năng, chỉnh ngưỡng số/cấu hình runtime — không cần deploy lại. Chỉ sửa giá trị key có sẵn.
        </p>
      </div>

      {error && <div className="p-3 text-xs text-rose-600 bg-rose-50 border border-rose-100 rounded-lg">{error}</div>}

      {!loading && settings.length === 0 && !error ? (
        <EmptyState icon={Settings2} title="Chưa có cấu hình nào" description="Bảng system_settings hiện đang trống." />
      ) : (
        grouped.map(([category, items]) => (
          <div key={category} className="bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
            <div className="px-4 py-2.5 bg-slate-50 border-b border-slate-100">
              <h3 className="text-[11px] font-bold uppercase tracking-wider text-slate-600">
                {CATEGORY_LABELS[category] ?? category}
              </h3>
            </div>
            <TableContainer className="rounded-none border-0">
              <thead>
                <tr>
                  <Th>Key</Th>
                  <Th>Mô tả</Th>
                  <Th>Giá trị</Th>
                  <Th>Cập nhật lần cuối</Th>
                  <Th />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {items.map((s) => (
                  <tr key={s.settingKey} className="hover:bg-slate-50/50 transition-colors">
                    <Td className="font-mono text-[11px] font-bold text-slate-700">{s.settingKey}</Td>
                    <Td className="text-[11px] text-slate-500 max-w-md">{s.description ?? "—"}</Td>
                    <Td className="font-mono font-bold">{String(s.settingValue)}</Td>
                    <Td className="text-[11px] text-slate-400">
                      {s.updatedByName ? (
                        <>
                          {s.updatedByName}
                          <br />
                          {s.updatedAt ? new Date(s.updatedAt).toLocaleString("vi-VN") : ""}
                        </>
                      ) : (
                        "—"
                      )}
                    </Td>
                    <Td>
                      <div className="flex items-center gap-1.5 justify-end">
                        <Button size="sm" variant="ghost" onClick={() => openHistory(s)} title="Lịch sử thay đổi">
                          <History className="w-3.5 h-3.5" />
                        </Button>
                        <Button size="sm" variant="secondary" onClick={() => openEdit(s)}>
                          Sửa
                        </Button>
                      </div>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </TableContainer>
          </div>
        ))
      )}

      <Modal
        open={editing !== null}
        onClose={closeEdit}
        title={`Sửa cấu hình: ${editing?.settingKey ?? ""}`}
        description={editing?.description ?? undefined}
        footer={
          <>
            <Button variant="secondary" onClick={closeEdit}>
              Hủy
            </Button>
            <Button variant="dark" onClick={handleSave} disabled={saving}>
              <Save className="w-3.5 h-3.5" />
              {saving ? "Đang lưu..." : "Lưu"}
            </Button>
          </>
        }
      >
        {editing && (
          <div className="space-y-3">
            {saveError && <div className="p-2.5 text-xs text-rose-600 bg-rose-50 border border-rose-100 rounded-lg">{saveError}</div>}
            <div>
              <label className="text-[10px] uppercase font-bold text-slate-500 block mb-1">Giá trị hiện tại</label>
              <p className="text-xs font-mono text-slate-400 mb-2">{String(editing.settingValue)}</p>

              {kindOf(editing.settingValue) === "boolean" ? (
                <select
                  value={draftValue}
                  onChange={(e) => setDraftValue(e.target.value)}
                  className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
                >
                  <option value="true">Bật (true)</option>
                  <option value="false">Tắt (false)</option>
                </select>
              ) : (
                <input
                  type={kindOf(editing.settingValue) === "number" ? "number" : "text"}
                  value={draftValue}
                  onChange={(e) => setDraftValue(e.target.value)}
                  className="w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none"
                />
              )}
            </div>
          </div>
        )}
      </Modal>

      <Modal
        open={historyFor !== null}
        onClose={() => setHistoryFor(null)}
        title={`Lịch sử: ${historyFor?.settingKey ?? ""}`}
        size="lg"
      >
        {historyLoading ? (
          <p className="text-xs text-slate-400">Đang tải...</p>
        ) : history.length === 0 ? (
          <p className="text-xs text-slate-400 italic">Chưa có lần thay đổi nào.</p>
        ) : (
          <div className="space-y-2">
            {history.map((h) => (
              <div key={h.id} className="flex items-center justify-between text-xs border border-slate-100 rounded-lg p-2.5">
                <div className="flex items-center gap-2">
                  <span className="font-mono text-rose-500 line-through">{String(h.oldValue)}</span>
                  <span className="text-slate-300">→</span>
                  <span className="font-mono font-bold text-emerald-600">{String(h.newValue)}</span>
                </div>
                <div className="text-[10px] text-slate-400 text-right">
                  <div className="font-semibold text-slate-600">{h.changedByName}</div>
                  <div>{new Date(h.createdAt).toLocaleString("vi-VN")}</div>
                </div>
              </div>
            ))}
          </div>
        )}
      </Modal>
    </div>
  );
}
