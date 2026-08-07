import React, { useEffect, useRef, useState } from "react";
import { Search, UserCheck, X } from "lucide-react";
import { searchSubstituteTeacherCandidates, TeacherLookupResponse } from "../api";

const RESULT_LIMIT = 50;

interface SubstituteTeacherComboboxProps {
  value: TeacherLookupResponse | null;
  onChange: (teacher: TeacherLookupResponse | null) => void;
  placeholder?: string;
}

/**
 * UC-10 bước 3 — chọn giáo viên dạy thay. Bản rút gọn của
 * UserSearchCombobox (system-admin), nhưng gọi
 * GET /api/leave-requests/substitute-teacher-candidates thay vì
 * GET /api/users — endpoint đó đòi quyền user.view (chỉ SYS_ADMIN), trong
 * khi đây là luồng self-service của Giáo viên khi tự nộp đơn xin nghỉ.
 */
export default function SubstituteTeacherCombobox({ value, onChange, placeholder }: SubstituteTeacherComboboxProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<TeacherLookupResponse[]>([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  const runSearch = (q: string) => {
    setLoading(true);
    searchSubstituteTeacherCandidates(q)
      .then((res) => setResults(res.slice(0, RESULT_LIMIT)))
      .catch(() => setResults([]))
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
      <div className="flex items-center justify-between bg-emerald-50 border border-emerald-100 text-emerald-700 text-sm font-semibold px-3 py-2 rounded-lg">
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
          placeholder={placeholder ?? "Bấm để xem danh sách hoặc gõ để tìm giáo viên..."}
          className="w-full bg-white border border-slate-200 text-sm p-2 pl-8 rounded-lg focus:outline-none"
        />
      </div>
      {open && (
        <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg max-h-80 overflow-y-auto">
          {loading ? (
            <p className="px-3 py-2 text-sm text-slate-400">Đang tải...</p>
          ) : results.length === 0 ? (
            <p className="px-3 py-2 text-sm text-slate-400 italic">Không tìm thấy giáo viên nào.</p>
          ) : (
            <div className="divide-y divide-slate-100">
              {results.map((t) => (
                <button
                  key={t.id}
                  type="button"
                  onClick={() => {
                    onChange(t);
                    setQuery("");
                    setOpen(false);
                  }}
                  className="w-full text-left px-3 py-2 hover:bg-slate-50 text-sm"
                >
                  {t.fullName} <span className="text-slate-400">({t.username} · {t.email})</span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
