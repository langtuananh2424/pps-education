import React from "react";
import { BookOpen, Plus, Search } from "lucide-react";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import type { CurriculumResponse } from "../api";

export const curriculumStatusLabels: Record<string, string> = { DRAFT: "Nháp", PENDING_APPROVAL: "Chờ duyệt", ACTIVE: "Đang áp dụng", ARCHIVED: "Lưu trữ" };
export const curriculumStatusVariants: Record<string, BadgeVariant> = { DRAFT: "neutral", PENDING_APPROVAL: "warning", ACTIVE: "success", ARCHIVED: "danger" };

interface CurriculumListPanelProps {
  curriculums: CurriculumResponse[];
  loading: boolean;
  selectedId: number | null;
  onSelect: (id: number) => void;
  onCreate: () => void;
  query: string;
  onQueryChange: (q: string) => void;
}

export default function CurriculumListPanel({ curriculums, loading, selectedId, onSelect, onCreate, query, onQueryChange }: CurriculumListPanelProps) {
  const filtered = curriculums.filter((c) => !query.trim() || c.name.toLowerCase().includes(query.toLowerCase()) || c.code.toLowerCase().includes(query.toLowerCase()));

  return (
    <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col h-full">
      <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between gap-3 bg-slate-50 shrink-0">
        <div className="space-y-0.5">
          <span className="text-xs font-bold text-slate-700 font-display block">Khung chương trình</span>
          <p className="text-[10px] text-slate-400">Chuẩn + bản tùy biến theo điểm trường</p>
        </div>
        <Button variant="primary" size="sm" onClick={onCreate}>
          <Plus className="w-3.5 h-3.5" />
          Thêm khung
        </Button>
      </div>

      <div className="px-4 py-3 border-b border-slate-100 relative shrink-0">
        <Search className="absolute left-7 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
        <input
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          placeholder="Tìm theo tên / mã khung..."
          className="w-full bg-slate-50 border border-slate-200 text-xs pl-8 pr-3 py-2 rounded-lg focus:outline-none"
        />
      </div>

      <div className="divide-y divide-slate-100 overflow-y-auto max-h-[560px] lg:max-h-[620px]">
        {loading ? (
          <div className="p-8 text-center text-slate-400 text-xs">Đang tải...</div>
        ) : filtered.length === 0 ? (
          <EmptyState icon={BookOpen} title="Không tìm thấy khung chương trình nào" description="Thử nới lỏng từ khóa, hoặc bấm 'Thêm khung'." />
        ) : (
          filtered.map((c) => {
            const isSelected = c.id === selectedId;
            return (
              <button
                key={c.id}
                onClick={() => onSelect(c.id)}
                className={`w-full text-left p-4 flex items-start justify-between gap-3 transition-all cursor-pointer border-l-4 ${
                  isSelected ? "bg-slate-50/90 border-brand-orange" : "hover:bg-slate-50/40 border-transparent"
                }`}
              >
                <div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-[10px] font-mono font-bold uppercase text-brand-red bg-orange-50 border border-orange-100 px-1.5 py-0.5 rounded">{c.code}</span>
                    <Badge variant={curriculumStatusVariants[c.status] ?? "neutral"}>{curriculumStatusLabels[c.status] ?? c.status}</Badge>
                    {c.siteId != null && <Badge variant="info">Tùy biến</Badge>}
                  </div>
                  <h4 className="text-xs font-bold text-slate-900 mt-1.5">{c.name}</h4>
                  <p className="text-[10px] text-slate-400 mt-1">{c.level ?? "—"} · {c.classCategory ?? "—"}</p>
                </div>
              </button>
            );
          })
        )}
      </div>
    </div>
  );
}
