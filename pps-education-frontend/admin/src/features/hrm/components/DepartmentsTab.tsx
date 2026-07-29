import React, { useEffect, useState } from "react";
import { Plus, Search, Trash2, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { searchUsers, UserListItemResponse } from "@/features/system-admin/api";
import { createDepartment, deleteDepartment, DepartmentResponse, listDepartments, updateDepartment } from "../api";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import { useToast } from "@/lib/useToast";
import Toast from "@/components/ui/Toast";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-xs p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

/** UC-08 bổ sung: danh mục Phòng ban (đổ dropdown "Phòng ban" ở hồ sơ nhân sự) — hiển thị dạng cây theo parentDepartmentId để xem tổng quan bộ máy. */
export default function DepartmentsTab() {
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const { message: toastMessage, showToast } = useToast();

  const load = () => {
    setLoading(true);
    listDepartments()
      .then(setDepartments)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được danh sách phòng ban."))
      .finally(() => setLoading(false));
  };
  useEffect(load, []);

  const handleDelete = async (id: number) => {
    if (!window.confirm("Xóa phòng ban này? Chỉ xóa được nếu chưa có nhân sự nào gán vào.")) return;
    setError(null);
    try {
      await deleteDepartment(id);
      load();
      showToast("Đã xóa phòng ban thành công!");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Xóa phòng ban thất bại.");
    }
  };

  // Nhóm theo parentDepartmentId để dựng cây — null/không khớp phòng ban nào khác đều coi là gốc (tránh mất tích nếu dữ liệu có vòng lặp/tham chiếu hỏng).
  const byId = new Map(departments.map((d) => [d.id, d]));
  const childrenOf = new Map<number | null, DepartmentResponse[]>();
  for (const d of departments) {
    const parentKey = d.parentDepartmentId != null && byId.has(d.parentDepartmentId) ? d.parentDepartmentId : null;
    if (!childrenOf.has(parentKey)) childrenOf.set(parentKey, []);
    childrenOf.get(parentKey)!.push(d);
  }
  for (const list of childrenOf.values()) list.sort((a, b) => a.name.localeCompare(b.name));

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-bold uppercase text-slate-500">Sơ đồ tổ chức — Phòng ban ({departments.length})</span>
        <Button size="sm" variant="secondary" onClick={() => setCreating(true)}>
          <Plus className="w-3.5 h-3.5" />
          Thêm phòng ban
        </Button>
      </div>

      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

      <Modal open={creating} onClose={() => setCreating(false)} title="Thêm phòng ban">
        <DepartmentForm
          departments={departments}
          onDone={() => {
            setCreating(false);
            load();
            showToast("Đã tạo phòng ban thành công!");
          }}
          onCancel={() => setCreating(false)}
        />
      </Modal>

      {loading ? (
        <p className="text-xs text-slate-500">Đang tải...</p>
      ) : departments.length === 0 ? (
        <p className="text-xs text-slate-400 italic">Chưa có phòng ban nào.</p>
      ) : (
        <DepartmentTreeLevel
          parentId={null}
          childrenOf={childrenOf}
          departments={departments}
          editingId={editingId}
          onEdit={setEditingId}
          onCancelEdit={() => setEditingId(null)}
          onDelete={handleDelete}
          onChanged={() => {
            load();
            showToast("Đã lưu phòng ban thành công!");
          }}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  );
}

function DepartmentTreeLevel({
  parentId,
  childrenOf,
  departments,
  editingId,
  onEdit,
  onCancelEdit,
  onDelete,
  onChanged,
  depth = 0
}: {
  parentId: number | null;
  childrenOf: Map<number | null, DepartmentResponse[]>;
  departments: DepartmentResponse[];
  editingId: number | null;
  onEdit: (id: number) => void;
  onCancelEdit: () => void;
  onDelete: (id: number) => void;
  onChanged: () => void;
  depth?: number;
}) {
  const nodes = childrenOf.get(parentId) ?? [];
  if (nodes.length === 0) return null;

  return (
    <div className={depth > 0 ? "ml-4 pl-4 border-l-2 border-slate-200 space-y-2" : "space-y-2"}>
      {nodes.map((d) => (
        <div key={d.id}>
          {editingId === d.id ? (
            <DepartmentForm initial={d} departments={departments} onDone={() => { onCancelEdit(); onChanged(); }} onCancel={onCancelEdit} />
          ) : (
            <div className="border border-slate-200 rounded-lg p-3 text-xs flex items-center justify-between bg-white">
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-mono font-bold text-brand-red bg-orange-50 border border-orange-100 px-1.5 py-0.5 rounded text-[10px]">{d.code}</span>
                  <span className="font-bold text-slate-800">{d.name}</span>
                </div>
                <p className="text-[10px] text-slate-400 mt-1">{d.headUserFullName ? `Trưởng phòng: ${d.headUserFullName}` : "Chưa có trưởng phòng"}</p>
              </div>
              <div className="flex items-center gap-2">
                <button onClick={() => onEdit(d.id)} className="text-slate-500 hover:text-slate-800 text-[11px] font-semibold">
                  Sửa
                </button>
                <button onClick={() => onDelete(d.id)} className="text-rose-500 hover:text-rose-700">
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          )}
          <DepartmentTreeLevel
            parentId={d.id}
            childrenOf={childrenOf}
            departments={departments}
            editingId={editingId}
            onEdit={onEdit}
            onCancelEdit={onCancelEdit}
            onDelete={onDelete}
            onChanged={onChanged}
            depth={depth + 1}
          />
        </div>
      ))}
    </div>
  );
}

function DepartmentForm({
  initial,
  departments,
  onDone,
  onCancel
}: {
  initial?: DepartmentResponse;
  departments: DepartmentResponse[];
  onDone: () => void;
  onCancel: () => void;
}) {
  const [code, setCode] = useState(initial?.code ?? "");
  const [name, setName] = useState(initial?.name ?? "");
  const [parentDepartmentId, setParentDepartmentId] = useState(initial?.parentDepartmentId ? String(initial.parentDepartmentId) : "");
  const [headQuery, setHeadQuery] = useState("");
  const [headResults, setHeadResults] = useState<UserListItemResponse[]>([]);
  const [selectedHead, setSelectedHead] = useState<{ id: number; fullName: string } | null>(
    initial?.headUserId ? { id: initial.headUserId, fullName: initial.headUserFullName ?? "" } : null
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSearchHead = (q: string) => {
    setHeadQuery(q);
    if (!q.trim()) {
      setHeadResults([]);
      return;
    }
    searchUsers({ keyword: q.trim() }, 0, 8).then((res) => setHeadResults(res.content));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || (!initial && !code.trim())) {
      setError("Vui lòng điền đủ mã và tên phòng ban.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      if (initial) {
        await updateDepartment(initial.id, {
          name: name.trim(),
          headUserId: selectedHead?.id,
          parentDepartmentId: parentDepartmentId ? Number(parentDepartmentId) : undefined
        });
      } else {
        await createDepartment({
          code: code.trim(),
          name: name.trim(),
          headUserId: selectedHead?.id,
          parentDepartmentId: parentDepartmentId ? Number(parentDepartmentId) : undefined
        });
      }
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu phòng ban thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
      {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}
      <div className="grid grid-cols-2 gap-2">
        <div>
          <label className={labelClass}>Mã phòng ban *</label>
          <input
            value={code}
            onChange={(e) => setCode(e.target.value)}
            disabled={!!initial}
            className={`${inputClass} font-mono disabled:opacity-50`}
          />
          {initial && <p className="text-[10px] text-slate-400 mt-1">Không sửa được mã sau khi tạo.</p>}
        </div>
        <div>
          <label className={labelClass}>Tên phòng ban *</label>
          <input value={name} onChange={(e) => setName(e.target.value)} className={name.trim() ? inputClass : inputErrorClass} />
        </div>
      </div>

      <div>
        <label className={labelClass}>Phòng ban cấp trên</label>
        <Select value={parentDepartmentId} onChange={(e) => setParentDepartmentId(e.target.value)} className={inputClass}>
          <option value="">-- Không có (phòng ban gốc) --</option>
          {departments
            .filter((d) => d.id !== initial?.id)
            .map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
        </Select>
      </div>

      <div>
        <label className={labelClass}>Trưởng phòng</label>
        {selectedHead ? (
          <div className="flex items-center justify-between bg-emerald-50 border border-emerald-100 text-emerald-700 text-xs font-semibold px-3 py-2 rounded-lg">
            <span>{selectedHead.fullName}</span>
            <button type="button" onClick={() => setSelectedHead(null)} className="text-emerald-600 hover:text-rose-600">
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        ) : (
          <div className="relative">
            <Search className="absolute left-3 top-2.5 w-3.5 h-3.5 text-slate-400" />
            <input value={headQuery} onChange={(e) => handleSearchHead(e.target.value)} placeholder="Tìm theo họ tên / email..." className={`${inputClass} pl-8`} />
            {headResults.length > 0 && (
              <div className="absolute z-10 mt-1 w-full bg-white border border-slate-200 rounded-lg shadow-lg divide-y divide-slate-100 max-h-56 overflow-y-auto">
                {headResults.map((u) => (
                  <button
                    key={u.id}
                    type="button"
                    onClick={() => {
                      setSelectedHead({ id: u.id, fullName: u.fullName });
                      setHeadResults([]);
                      setHeadQuery("");
                    }}
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

      <div className="flex gap-2">
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          Hủy
        </Button>
        <Button type="submit" variant="primary" size="sm" disabled={submitting}>
          {submitting ? "Đang lưu..." : initial ? "Lưu thay đổi" : "Tạo phòng ban"}
        </Button>
      </div>
    </form>
  );
}
