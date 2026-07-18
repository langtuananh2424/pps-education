import React, { useMemo, useState } from "react";
import { Search, UserPlus } from "lucide-react";
import { UserListItemResponse } from "../api";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";

interface AssignUserModalProps {
  open: boolean;
  candidates: UserListItemResponse[];
  busyUserId: number | null;
  onClose: () => void;
  onAssign: (userId: number) => void;
}

export default function AssignUserModal({ open, candidates, busyUserId, onClose, onAssign }: AssignUserModalProps) {
  const [keyword, setKeyword] = useState("");

  const filtered = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    if (!q) return candidates;
    return candidates.filter(
      (u) => u.username.toLowerCase().includes(q) || u.fullName.toLowerCase().includes(q) || u.email.toLowerCase().includes(q)
    );
  }, [candidates, keyword]);

  return (
    <Modal open={open} onClose={onClose} title="Gán tài khoản vào vai trò" size="md">
      <div className="space-y-3">
        <label className="text-[10px] uppercase font-bold text-slate-500 block flex items-center gap-1.5">
          <UserPlus className="w-3.5 h-3.5 text-brand-red" />
          Tìm tài khoản (username / họ tên / email)
        </label>
        <div className="relative">
          <Search className="absolute left-2.5 top-2.5 w-3.5 h-3.5 text-slate-400" />
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Nhập để tìm..."
            className="w-full bg-slate-50 border border-slate-200 text-xs pl-8 pr-3 py-2.5 rounded-lg focus:outline-none"
          />
        </div>

        <div className="max-h-72 overflow-y-auto divide-y divide-slate-100 border border-slate-200 rounded-lg">
          {filtered.length === 0 ? (
            <p className="text-xs text-slate-400 text-center py-6">Không tìm thấy tài khoản phù hợp.</p>
          ) : (
            filtered.map((u) => (
              <div key={u.id} className="flex items-center justify-between gap-3 p-2.5 hover:bg-slate-50/60">
                <div className="min-w-0">
                  <p className="text-xs font-bold text-slate-800 truncate">{u.fullName}</p>
                  <p className="text-[10px] text-slate-400 truncate">
                    {u.username} · {u.email}
                  </p>
                </div>
                <Button size="sm" variant="dark" disabled={busyUserId === u.id} onClick={() => onAssign(u.id)}>
                  {busyUserId === u.id ? "..." : "Gán"}
                </Button>
              </div>
            ))
          )}
        </div>
      </div>

      <div className="flex justify-end gap-2 pt-4 mt-4 border-t border-slate-100">
        <Button variant="secondary" onClick={onClose}>
          Đóng
        </Button>
      </div>
    </Modal>
  );
}
