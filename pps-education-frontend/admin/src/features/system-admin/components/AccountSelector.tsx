import React, { useMemo, useState } from "react";
import { UserCheck, X } from "lucide-react";
import { CreateUserRequest, searchUsers, UserListItemResponse } from "../api";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

export interface AccountSelection {
  userId?: number;
  newAccount?: CreateUserRequest;
}

interface AccountSelectorProps {
  value: AccountSelection;
  onChange: (value: AccountSelection) => void;
}

/**
 * Chọn tài khoản khi tạo hồ sơ Nhân sự/Học sinh/Phụ huynh — khớp đúng cơ chế
 * backend "cung cấp ĐÚNG 1 trong 2": userId (tài khoản có sẵn, tìm theo
 * email/username qua GET /api/users?keyword=) hoặc newAccount (tạo tài
 * khoản mới trong cùng transaction — CreateUserRequest).
 */
export default function AccountSelector({ value, onChange }: AccountSelectorProps) {
  const [mode, setMode] = useState<"new" | "existing">(value.userId ? "existing" : "new");
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<UserListItemResponse[]>([]);
  const [searching, setSearching] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserListItemResponse | null>(null);

  const [newAccount, setNewAccount] = useState<CreateUserRequest>({ username: "", email: "", fullName: "", phone: "", password: "" });

  const switchMode = (next: "new" | "existing") => {
    setMode(next);
    if (next === "new") {
      setSelectedUser(null);
      onChange({ newAccount: cleanNewAccount(newAccount) });
    } else {
      onChange({ userId: selectedUser?.id });
    }
  };

  const handleSearch = (q: string) => {
    setQuery(q);
    if (!q.trim()) {
      setResults([]);
      return;
    }
    setSearching(true);
    searchUsers({ keyword: q.trim() }, 0, 8)
      .then((res) => setResults(res.content))
      .finally(() => setSearching(false));
  };

  const pickUser = (u: UserListItemResponse) => {
    setSelectedUser(u);
    setQuery("");
    setResults([]);
    onChange({ userId: u.id });
  };

  const updateNewAccount = (patch: Partial<CreateUserRequest>) => {
    const next = { ...newAccount, ...patch };
    setNewAccount(next);
    onChange({ newAccount: cleanNewAccount(next) });
  };

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-2">
        <button
          type="button"
          onClick={() => switchMode("new")}
          className={`py-1.5 text-xs font-bold rounded-lg border text-center transition-all ${
            mode === "new" ? "bg-orange-50 text-brand-red border-orange-200 shadow-sm" : "bg-slate-50 border-slate-200 text-slate-500"
          }`}
        >
          Tạo tài khoản mới
        </button>
        <button
          type="button"
          onClick={() => switchMode("existing")}
          className={`py-1.5 text-xs font-bold rounded-lg border text-center transition-all ${
            mode === "existing" ? "bg-orange-50 text-brand-red border-orange-200 shadow-sm" : "bg-slate-50 border-slate-200 text-slate-500"
          }`}
        >
          Gán tài khoản có sẵn
        </button>
      </div>

      {mode === "new" ? (
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Username *</label>
            <input value={newAccount.username} onChange={(e) => updateNewAccount({ username: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>Email *</label>
            <input type="email" value={newAccount.email} onChange={(e) => updateNewAccount({ email: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>Họ tên *</label>
            <input value={newAccount.fullName} onChange={(e) => updateNewAccount({ fullName: e.target.value })} className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>Số điện thoại</label>
            <input value={newAccount.phone ?? ""} onChange={(e) => updateNewAccount({ phone: e.target.value })} className={inputClass} />
          </div>
          <div className="col-span-2">
            <label className={labelClass}>Mật khẩu ban đầu</label>
            <input
              type="password"
              value={newAccount.password ?? ""}
              onChange={(e) => updateNewAccount({ password: e.target.value })}
              className={inputClass}
              placeholder="Để trống nếu chỉ đăng nhập Google (tối thiểu 8 ký tự nếu nhập)"
            />
          </div>
        </div>
      ) : selectedUser ? (
        <div className="flex items-center justify-between bg-emerald-50 border border-emerald-100 text-emerald-700 text-xs font-semibold px-3 py-2 rounded-lg">
          <span className="flex items-center gap-1.5">
            <UserCheck className="w-4 h-4" />
            {selectedUser.fullName} ({selectedUser.username} · {selectedUser.email})
          </span>
          <button
            type="button"
            onClick={() => {
              setSelectedUser(null);
              onChange({ userId: undefined });
            }}
            className="text-emerald-600 hover:text-rose-600"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      ) : (
        <div className="relative">
          <input
            value={query}
            onChange={(e) => handleSearch(e.target.value)}
            placeholder="Tìm theo email / username / họ tên..."
            className={inputClass}
          />
          {searching && <p className="text-[10px] text-slate-400 mt-1">Đang tìm...</p>}
          {results.length > 0 && (
            <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg divide-y divide-slate-100 max-h-56 overflow-y-auto">
              {results.map((u) => (
                <button
                  key={u.id}
                  type="button"
                  onClick={() => pickUser(u)}
                  className="w-full text-left px-3 py-2 hover:bg-slate-50 text-xs"
                >
                  {u.fullName} <span className="text-slate-400">({u.username} · {u.email})</span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function cleanNewAccount(a: CreateUserRequest): CreateUserRequest {
  return { ...a, phone: a.phone?.trim() || undefined, password: a.password?.trim() || undefined };
}
