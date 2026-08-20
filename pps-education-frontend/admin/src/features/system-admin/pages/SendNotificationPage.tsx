import React, { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Search, Send, UserCheck, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  NOTIFICATION_TYPES,
  NotificationTypeValue,
  searchUsers,
  sendManualNotification,
  SendNotificationResponse,
  UserListItemResponse
} from "../api";
import { Button } from "@/components/ui";
import Select from "@/components/ui/Select";

const RESULT_LIMIT = 50;

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

/** Nhãn loại thông báo dịch qua i18next namespace "system-admin-settings" — chỉ để hiển thị, giá trị gửi lên vẫn đúng enum backend. */
function notificationTypeLabel(t: (key: string) => string, type: NotificationTypeValue): string {
  return t(`notificationType.${type}`);
}

/**
 * Gửi thông báo thủ công — bổ sung ngoài SDD gốc, đã xác nhận với người
 * dùng 2026-08-08. Mục đích test kênh Push/Email/SMS mới build + gửi thông
 * báo tay khi cần. Tái sử dụng notify()/notification_preferences hiện có ở
 * backend — mỗi recipient vẫn nhận đúng theo cấu hình kênh của chính họ.
 */
export default function SendNotificationPage() {
  const { t } = useTranslation("system-admin-settings");
  const [selectedUsers, setSelectedUsers] = useState<UserListItemResponse[]>([]);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserListItemResponse[]>([]);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  const [notificationType, setNotificationType] = useState<NotificationTypeValue>("SYSTEM_ANNOUNCEMENT");
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");

  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const [result, setResult] = useState<SendNotificationResponse | null>(null);

  useEffect(() => {
    if (!searchOpen) return;
    setSearching(true);
    const handle = setTimeout(() => {
      searchUsers({ keyword: query.trim() || undefined }, 0, RESULT_LIMIT)
        .then((res) => setResults(res.content))
        .finally(() => setSearching(false));
    }, 250);
    return () => clearTimeout(handle);
  }, [query, searchOpen]);

  useEffect(() => {
    if (!searchOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setSearchOpen(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [searchOpen]);

  const addUser = (u: UserListItemResponse) => {
    setSelectedUsers((prev) => (prev.some((p) => p.id === u.id) ? prev : [...prev, u]));
    setQuery("");
  };

  const removeUser = (userId: number) => {
    setSelectedUsers((prev) => prev.filter((u) => u.id !== userId));
  };

  const canSend = selectedUsers.length > 0 && title.trim().length > 0 && content.trim().length > 0 && !sending;

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSend) return;
    setSending(true);
    setSendError(null);
    setResult(null);
    sendManualNotification({
      recipientUserIds: selectedUsers.map((u) => u.id),
      notificationType,
      title: title.trim(),
      content: content.trim()
    })
      .then((res) => {
        setResult(res);
        if (res.failures.length === 0) {
          setSelectedUsers([]);
          setTitle("");
          setContent("");
        }
      })
      .catch((err) => setSendError(err instanceof ApiError ? err.message : t("sendNotificationPage.sendErrorDefault")))
      .finally(() => setSending(false));
  };

  return (
    <div className="space-y-4 animate-in fade-in duration-200 max-w-3xl">
      <div>
        <h2 className="text-sm font-bold text-slate-800 uppercase tracking-wider block">
          {t("sendNotificationPage.title")}
        </h2>
        <p className="text-[10px] text-slate-400 mt-0.5">{t("sendNotificationPage.description")}</p>
      </div>

      <form onSubmit={handleSend} className="bg-white p-4 rounded-xl border border-slate-200 shadow-soft space-y-4">
        <div ref={rootRef} className="relative">
          <label className={labelClass}>{t("sendNotificationPage.recipient.label")}</label>

          {selectedUsers.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mb-2">
              {selectedUsers.map((u) => (
                <span
                  key={u.id}
                  className="flex items-center gap-1.5 bg-emerald-50 border border-emerald-100 text-emerald-700 text-[11px] font-semibold pl-2.5 pr-1.5 py-1 rounded-full"
                >
                  <UserCheck className="w-3 h-3" />
                  {u.fullName}
                  <button type="button" onClick={() => removeUser(u.id)} className="text-emerald-600 hover:text-rose-600">
                    <X className="w-3 h-3" />
                  </button>
                </span>
              ))}
            </div>
          )}

          <div className="relative">
            <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
            <input
              value={query}
              autoComplete="off"
              onFocus={() => setSearchOpen(true)}
              onChange={(e) => {
                setQuery(e.target.value);
                setSearchOpen(true);
              }}
              placeholder={t("sendNotificationPage.recipient.searchPlaceholder")}
              className={`${inputClass} pl-8`}
            />
          </div>

          {searchOpen && (
            <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg max-h-64 overflow-y-auto">
              {searching ? (
                <p className="px-3 py-2 text-xs text-slate-400">{t("sendNotificationPage.recipient.searching")}</p>
              ) : results.length === 0 ? (
                <p className="px-3 py-2 text-xs text-slate-400 italic">
                  {t("sendNotificationPage.recipient.noResults")}
                </p>
              ) : (
                <div className="divide-y divide-slate-100">
                  {results.map((u) => {
                    const alreadyAdded = selectedUsers.some((p) => p.id === u.id);
                    return (
                      <button
                        key={u.id}
                        type="button"
                        disabled={alreadyAdded}
                        onClick={() => addUser(u)}
                        className="w-full text-left px-3 py-2 hover:bg-slate-50 text-xs disabled:opacity-40 disabled:cursor-not-allowed"
                      >
                        {u.fullName} <span className="text-slate-400">({u.username} · {u.email})</span>
                        {alreadyAdded && (
                          <span className="text-emerald-600 ml-2">{t("sendNotificationPage.recipient.alreadySelected")}</span>
                        )}
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>

        <div>
          <label className={labelClass}>{t("sendNotificationPage.notificationTypeLabel")}</label>
          <Select
            value={notificationType}
            onChange={(e) => setNotificationType(e.target.value as NotificationTypeValue)}
            className={inputClass}
          >
            {NOTIFICATION_TYPES.map((nt) => (
              <option key={nt} value={nt}>
                {notificationTypeLabel(t, nt)} ({nt})
              </option>
            ))}
          </Select>
        </div>

        <div>
          <label className={labelClass}>{t("sendNotificationPage.titleLabel")}</label>
          <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={500} className={inputClass} />
        </div>

        <div>
          <label className={labelClass}>{t("sendNotificationPage.contentLabel")}</label>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={4}
            className={inputClass}
          />
        </div>

        {sendError && <div className="p-2.5 text-xs text-rose-600 bg-rose-50 border border-rose-100 rounded-lg">{sendError}</div>}

        {result && (
          <div
            className={`p-2.5 text-xs rounded-lg border ${
              result.failures.length === 0
                ? "text-emerald-700 bg-emerald-50 border-emerald-100"
                : "text-amber-700 bg-amber-50 border-amber-100"
            }`}
          >
            {t("sendNotificationPage.result.summary", {
              succeeded: result.succeeded,
              total: result.totalRecipients
            })}
            {result.failures.length > 0 && (
              <ul className="mt-1.5 space-y-0.5">
                {result.failures.map((f) => (
                  <li key={f.recipientUserId} className="text-[11px]">
                    {t("sendNotificationPage.result.failureItem", {
                      userId: f.recipientUserId,
                      reason: f.reason
                    })}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        <div className="flex justify-end">
          <Button type="submit" variant="dark" disabled={!canSend}>
            <Send className="w-3.5 h-3.5" />
            {sending
              ? t("sendNotificationPage.sending")
              : t("sendNotificationPage.sendButton", { count: selectedUsers.length || 0 })}
          </Button>
        </div>
      </form>
    </div>
  );
}
