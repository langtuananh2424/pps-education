import React, { useEffect, useState } from "react";
import { Plus, Search, Users } from "lucide-react";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import Pagination from "@/components/ui/Pagination";
import type { ParentAggregate } from "../pages/ParentsPage";

interface ParentListPanelProps {
  parents: ParentAggregate[];
  loading: boolean;
  selectedId: number | null;
  onSelect: (id: number) => void;
  onCreate: () => void;
  query: string;
  onQueryChange: (q: string) => void;
}

export default function ParentListPanel({ parents, loading, selectedId, onSelect, onCreate, query, onQueryChange }: ParentListPanelProps) {
  const filtered = parents.filter((p) => p.parentFullName.toLowerCase().includes(query.toLowerCase()));

  // Backend GET /parents chưa hỗ trợ phân trang (trả nguyên mảng, lại còn tổng hợp con em bằng N+1
  // request) — phân trang phía client theo đúng kết quả ĐÃ lọc theo từ khóa. Reset về trang 1 mỗi khi
  // kết quả lọc/tải mới đổi để không kẹt ở 1 trang rỗng.
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  useEffect(() => setPage(0), [parents, query]);
  const pageFiltered = filtered.slice(page * pageSize, (page + 1) * pageSize);

  return (
    <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col h-full">
      <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between gap-3 bg-slate-50 shrink-0">
        <div className="space-y-0.5">
          <span className="text-xs font-bold text-slate-700 font-display block">Danh sách Phụ huynh</span>
          <p className="text-[10px] text-slate-400">Tổng hợp từ danh sách con em đã liên kết</p>
        </div>
        <Button variant="primary" size="sm" onClick={onCreate}>
          <Plus className="w-3.5 h-3.5" />
          Thêm phụ huynh
        </Button>
      </div>

      <div className="px-4 py-3 border-b border-slate-100 relative shrink-0">
        <Search className="absolute left-7 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
        <input
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          placeholder="Tìm theo họ tên phụ huynh..."
          className="w-full bg-slate-50 border border-slate-200 text-xs pl-8 pr-3 py-2 rounded-lg focus:outline-none"
        />
      </div>

      <div className="divide-y divide-slate-100 overflow-y-auto max-h-[620px] lg:max-h-[680px]">
        {loading ? (
          <div className="p-8 text-center text-slate-400 text-xs">Đang tải...</div>
        ) : filtered.length === 0 ? (
          <EmptyState icon={Users} title="Không tìm thấy phụ huynh nào" description="Thử nới lỏng từ khóa, hoặc bấm 'Thêm phụ huynh'." />
        ) : (
          pageFiltered.map((p) => {
            const isSelected = p.parentId === selectedId;
            return (
              <button
                key={p.parentId}
                onClick={() => onSelect(p.parentId)}
                className={`w-full text-left p-4 flex items-center justify-between gap-4 transition-all cursor-pointer border-l-4 ${isSelected ? "bg-slate-50/90 border-brand-orange" : "hover:bg-slate-50/40 border-transparent"
                  }`}
              >
                <div className="flex items-start gap-3">
                  <div className={`w-9 h-9 rounded-full flex items-center justify-center font-bold text-sm shrink-0 shadow-sm ${isSelected ? "bg-brand-gradient text-white" : "bg-slate-100 text-slate-700"}`}>
                    {p.parentFullName.charAt(0)}
                  </div>
                  <div>
                    <h4 className="text-xs font-bold text-slate-900">{p.parentFullName}</h4>
                    <div className="flex items-center gap-1.5 text-[10px] text-slate-400 mt-1">
                      {p.children.length === 0 ? (
                        <span className="italic">Chưa liên kết con em nào</span>
                      ) : (
                        <Badge variant="info">{p.children.length} con em</Badge>
                      )}
                    </div>
                  </div>
                </div>
              </button>
            );
          })
        )}
      </div>

      {!loading && filtered.length > 0 && (
        <Pagination
          page={page}
          pageSize={pageSize}
          totalElements={filtered.length}
          itemLabel="p.huynh"
          onPageChange={setPage}
          onPageSizeChange={(size) => {
            setPageSize(size);
            setPage(0);
          }}
        />
      )}
    </div>
  );
}
