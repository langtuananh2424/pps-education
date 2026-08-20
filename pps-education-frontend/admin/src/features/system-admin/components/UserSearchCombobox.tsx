import React, { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Search, UserCheck, X } from "lucide-react";
import { searchUsers, UserListItemResponse } from "../api";

/** Hiện tối đa từng này kết quả 1 lúc trong dropdown — đủ rộng để "lướt" khi chưa nhớ tên, nhưng
 * vẫn gõ thêm để lọc tiếp nếu trường/tổ chức có nhiều hơn số này. */
const RESULT_LIMIT = 50;

interface UserSearchComboboxProps {
  value: UserListItemResponse | null;
  onChange: (user: UserListItemResponse | null) => void;
  placeholder?: string;
  /** Lọc theo mã vai trò (VD "TEACHER") — GET /api/users chưa có param roleCode nên lọc tạm ở FE,
   * giống hệt cách UsersPage.tsx đang làm: tải 1 lô lớn rồi tự lọc theo u.roles, không phải lọc theo
   * kết quả trang nhỏ (tránh sót tài khoản đúng vai trò nhưng rớt ra ngoài kết quả đầu). */
  roleFilter?: string;
}

/**
 * Chọn 1 tài khoản dạng select + search (thay cho ô tìm-mới-thấy trước đây) — bấm vào là hiện ngay
 * 1 trang danh sách tài khoản để chọn, không bắt buộc phải nhớ và gõ đúng tên/email/username trước
 * mới thấy gì (đã xác nhận với người dùng 2026-07-30, dùng cho các màn Xếp buổi học/Sinh lịch hàng loạt).
 */
export default function UserSearchCombobox({ value, onChange, placeholder, roleFilter }: UserSearchComboboxProps) {
  const { t } = useTranslation("system-admin-users");
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserListItemResponse[]>([]);
  const [truncated, setTruncated] = useState(false);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  const runSearch = (q: string) => {
    setLoading(true);
    if (roleFilter) {
      searchUsers({ keyword: q.trim() || undefined }, 0, 1000)
        .then((res) => {
          const filtered = res.content.filter((u) => u.roles.some((r) => r.code === roleFilter));
          setResults(filtered.slice(0, RESULT_LIMIT));
          setTruncated(filtered.length > RESULT_LIMIT);
        })
        .finally(() => setLoading(false));
      return;
    }
    searchUsers({ keyword: q.trim() || undefined }, 0, RESULT_LIMIT)
      .then((res) => {
        setResults(res.content);
        setTruncated(res.totalElements > RESULT_LIMIT);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (!open) return;
    const handle = setTimeout(() => runSearch(query), 250);
    return () => clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query, open]);

  useEffect(() => {
    if (!open) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  if (value) {
    return (
      <div className="flex items-center justify-between bg-emerald-50 border border-emerald-100 text-emerald-700 text-xs font-semibold px-3 py-2 rounded-lg">
        <span className="flex items-center gap-1.5 truncate">
          <UserCheck className="w-3.5 h-3.5 shrink-0" />
          {value.fullName} ({value.username})
        </span>
        <button type="button" onClick={() => onChange(null)} className="text-emerald-600 hover:text-rose-600 shrink-0">
          <X className="w-3.5 h-3.5" />
        </button>
      </div>
    );
  }

  return (
    <div className="relative" ref={rootRef}>
      <div className="relative">
        <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
        <input
          value={query}
          autoComplete="off"
          onFocus={() => {
            setOpen(true);
            if (results.length === 0) runSearch(query);
          }}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          placeholder={placeholder ?? t("userSearchCombobox.defaultPlaceholder")}
          className="w-full bg-white border border-slate-200 text-xs p-2 pl-8 rounded-lg focus:outline-none"
        />
      </div>
      {open && (
        <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg max-h-80 overflow-y-auto">
          {loading ? (
            <p className="px-3 py-2 text-xs text-slate-400">{t("userSearchCombobox.loading")}</p>
          ) : results.length === 0 ? (
            <p className="px-3 py-2 text-xs text-slate-400 italic">{t("userSearchCombobox.empty")}</p>
          ) : (
            <div className="divide-y divide-slate-100">
              {results.map((u) => (
                <button
                  key={u.id}
                  type="button"
                  onClick={() => {
                    onChange(u);
                    setQuery("");
                    setOpen(false);
                  }}
                  className="w-full text-left px-3 py-2 hover:bg-slate-50 text-xs"
                >
                  {u.fullName} <span className="text-slate-400">({u.username} · {u.email})</span>
                </button>
              ))}
            </div>
          )}
          {!loading && truncated && (
            <p className="px-3 py-1.5 text-[10px] text-amber-600 bg-amber-50 border-t border-amber-100 italic">
              {t("userSearchCombobox.truncatedHint")}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
