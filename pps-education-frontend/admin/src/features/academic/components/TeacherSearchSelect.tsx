import { useEffect, useRef, useState } from "react";
import { Search, X } from "lucide-react";
import { searchUsers, UserListItemResponse } from "@/features/system-admin/api";

const inputClass = "w-full bg-white border border-slate-200 text-sm p-2 rounded-lg focus:outline-none";
const SEARCH_DEBOUNCE_MS = 250;
const RESULT_LIMIT = 10;

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
 * còn tự động suy ra từ class_teachers PRIMARY). Search theo email/username/họ tên.
 *
 * Lọc role TEACHER + ACTIVE ngay ở server (xác nhận với người dùng 2026-09-29) —
 * trước đây lấy 8 tài khoản đầu thuộc MỌI role rồi mới lọc ở client, nên từ khoá
 * phổ biến ("duy", "minh") hay trả về rỗng dù có giáo viên khớp.
 */
export default function TeacherSearchSelect({ label, required, value, valueName, onChange, placeholder }: TeacherSearchSelectProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserListItemResponse[]>([]);
  const [searching, setSearching] = useState(false);
  const [open, setOpen] = useState(false);
  const requestSeqRef = useRef(0);
  const wrapperRef = useRef<HTMLDivElement>(null);

  // Debounce + chỉ nhận kết quả của lần gõ mới nhất (tránh response cũ về sau ghi đè response mới).
  useEffect(() => {
    const keyword = query.trim();
    const seq = ++requestSeqRef.current;
    if (!keyword) {
      setResults([]);
      setSearching(false);
      return;
    }
    setSearching(true);
    const timer = window.setTimeout(() => {
      searchUsers({ keyword, roleCode: "TEACHER", status: "ACTIVE" }, 0, RESULT_LIMIT)
        .then((res) => {
          if (seq === requestSeqRef.current) setResults(res.content);
        })
        .catch(() => {
          if (seq === requestSeqRef.current) setResults([]);
        })
        .finally(() => {
          if (seq === requestSeqRef.current) setSearching(false);
        });
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    function onMouseDown(e: MouseEvent) {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) setOpen(false);
    }
    window.addEventListener("mousedown", onMouseDown);
    return () => window.removeEventListener("mousedown", onMouseDown);
  }, []);

  if (value != null) {
    return (
      <div>
        <label className="text-[12px] uppercase font-bold text-slate-500 block mb-1">
          {label} {required && "*"}
        </label>
        <div className="flex items-center justify-between gap-2 bg-slate-50 border border-slate-200 rounded-lg p-2 text-sm">
          <span className="font-bold text-slate-800 truncate">{valueName ?? `#${value}`}</span>
          <button type="button" onClick={() => onChange(null, null)} className="text-slate-400 hover:text-rose-600 shrink-0">
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    );
  }

  const keyword = query.trim();
  const showDropdown = open && keyword.length > 0;

  return (
    <div ref={wrapperRef}>
      <label className="text-[12px] uppercase font-bold text-slate-500 block mb-1">
        {label} {required && "*"}
      </label>
      <div className="relative">
        <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
        <input
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          placeholder={placeholder ?? "Tìm giáo viên theo họ tên / username / email..."}
          className={`${inputClass} pl-8`}
        />
        {showDropdown && (
          <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg divide-y divide-slate-100 max-h-56 overflow-y-auto">
            {results.length > 0 ? (
              results.map((u) => (
                <button
                  key={u.id}
                  type="button"
                  onClick={() => {
                    onChange(u.id, u.fullName);
                    setQuery("");
                    setResults([]);
                    setOpen(false);
                  }}
                  className="w-full text-left px-3 py-2 hover:bg-slate-50 text-sm"
                >
                  {u.fullName} <span className="text-slate-400">({u.username} · {u.email})</span>
                </button>
              ))
            ) : (
              <p className="px-3 py-2 text-sm text-slate-400 italic">{searching ? "Đang tìm..." : "Không tìm thấy giáo viên phù hợp."}</p>
            )}
          </div>
        )}
      </div>
      {keyword.length > 0 && !open && (
        <p className="text-[12px] text-amber-600 italic mt-1">Chưa chọn — bấm vào ô và chọn 1 giáo viên trong danh sách.</p>
      )}
    </div>
  );
}
