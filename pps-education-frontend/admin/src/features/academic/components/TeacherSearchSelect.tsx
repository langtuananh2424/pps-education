import { useState } from "react";
import { Search, X } from "lucide-react";
import { searchUsers, UserListItemResponse } from "@/features/system-admin/api";

const inputClass = "w-full bg-white border border-slate-200 text-xs p-2 rounded-lg focus:outline-none";

interface TeacherSearchSelectProps {
  label: string;
  required?: boolean;
  value: number | null;
  valueName?: string | null;
  onChange: (userId: number | null, fullName: string | null) => void;
  placeholder?: string;
}

/**
 * Ô tìm + chọn tay 1 giáo viên (role TEACHER) — dùng chung cho GV chính/phụ/CM
 * khi xếp lịch buổi học (bổ sung ngoài SDD gốc, xác nhận 2026-08-19: không
 * còn tự động suy ra từ class_teachers PRIMARY). Cùng pattern tìm kiếm với
 * SiteTeachersTab (facility) — search theo email/username/họ tên.
 */
export default function TeacherSearchSelect({ label, required, value, valueName, onChange, placeholder }: TeacherSearchSelectProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserListItemResponse[]>([]);

  const handleSearch = (q: string) => {
    setQuery(q);
    if (!q.trim()) {
      setResults([]);
      return;
    }
    searchUsers({ keyword: q.trim() }, 0, 8).then((res) => setResults(res.content.filter((u) => u.roles.some((r) => r.code === "TEACHER"))));
  };

  if (value != null) {
    return (
      <div>
        <label className="text-[10px] uppercase font-bold text-slate-500 block mb-1">
          {label} {required && "*"}
        </label>
        <div className="flex items-center justify-between gap-2 bg-slate-50 border border-slate-200 rounded-lg p-2 text-xs">
          <span className="font-bold text-slate-800 truncate">{valueName ?? `#${value}`}</span>
          <button type="button" onClick={() => onChange(null, null)} className="text-slate-400 hover:text-rose-600 shrink-0">
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    );
  }

  return (
    <div>
      <label className="text-[10px] uppercase font-bold text-slate-500 block mb-1">
        {label} {required && "*"}
      </label>
      <div className="relative">
        <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
        <input
          value={query}
          onChange={(e) => handleSearch(e.target.value)}
          placeholder={placeholder ?? "Tìm theo email / username / họ tên..."}
          className={`${inputClass} pl-8`}
        />
        {results.length > 0 && (
          <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg divide-y divide-slate-100 max-h-56 overflow-y-auto">
            {results.map((u) => (
              <button
                key={u.id}
                type="button"
                onClick={() => {
                  onChange(u.id, u.fullName);
                  setQuery("");
                  setResults([]);
                }}
                className="w-full text-left px-3 py-2 hover:bg-slate-50 text-xs"
              >
                {u.fullName} <span className="text-slate-400">({u.username} · {u.email})</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
