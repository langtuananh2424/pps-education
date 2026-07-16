import React from "react";
import { Plus, Search, Users } from "lucide-react";
import { StudentResponse } from "../api";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";

export const studentStatusLabels: Record<string, string> = {
  ACTIVE: "Đang học",
  SUSPENDED: "Tạm dừng",
  EXPELLED: "Buộc thôi học",
  GRADUATED: "Đã tốt nghiệp",
  WITHDRAWN: "Đã rút hồ sơ",
  DEFERRAL: "Bảo lưu"
};

export const studentStatusVariants: Record<string, BadgeVariant> = {
  ACTIVE: "success",
  SUSPENDED: "warning",
  EXPELLED: "danger",
  GRADUATED: "info",
  WITHDRAWN: "neutral",
  DEFERRAL: "neutral"
};

interface StudentListPanelProps {
  students: StudentResponse[];
  loading: boolean;
  selectedId: number | null;
  onSelect: (id: number) => void;
  onCreate: () => void;
  query: string;
  onQueryChange: (q: string) => void;
  onSearch: () => void;
}

export default function StudentListPanel({ students, loading, selectedId, onSelect, onCreate, query, onQueryChange, onSearch }: StudentListPanelProps) {
  return (
    <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col h-full">
      <div className="px-5 py-4 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-slate-50 shrink-0">
        <div className="space-y-0.5">
          <span className="text-xs font-bold text-slate-700 font-display block">Danh sách Học sinh</span>
          <p className="text-[10px] text-slate-400">Nhấp chọn học sinh để xem chi tiết & quản lý phụ huynh</p>
        </div>
        <Button variant="primary" size="sm" onClick={onCreate}>
          <Plus className="w-3.5 h-3.5" />
          Thêm học sinh mới
        </Button>
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          onSearch();
        }}
        className="px-4 py-3 border-b border-slate-100 relative shrink-0"
      >
        <Search className="absolute left-7 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
        <input
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          placeholder="Tìm theo họ tên / mã học sinh..."
          className="w-full bg-slate-50 border border-slate-200 text-xs pl-8 pr-3 py-2 rounded-lg focus:outline-none"
        />
      </form>

      <div className="divide-y divide-slate-100 overflow-y-auto max-h-[620px] lg:max-h-[680px]">
        {loading ? (
          <div className="p-8 text-center text-slate-400 text-xs">Đang tải...</div>
        ) : students.length === 0 ? (
          <EmptyState icon={Users} title="Không tìm thấy học sinh nào" description="Thử nới lỏng từ khóa tìm kiếm." />
        ) : (
          students.map((s) => {
            const isSelected = s.id === selectedId;
            return (
              <button
                key={s.id}
                onClick={() => onSelect(s.id)}
                className={`w-full text-left p-4 flex items-center justify-between gap-4 transition-all cursor-pointer border-l-4 ${
                  isSelected ? "bg-slate-50/90 border-brand-orange" : "hover:bg-slate-50/40 border-transparent"
                }`}
              >
                <div className="flex items-start gap-3">
                  <div className={`w-9 h-9 rounded-full flex items-center justify-center font-bold text-sm shrink-0 shadow-sm overflow-hidden ${isSelected ? "bg-brand-gradient text-white" : "bg-slate-100 text-slate-700"}`}>
                    {s.portraitUrl ? <img src={s.portraitUrl} alt="" className="w-full h-full object-cover" /> : s.fullName.charAt(0)}
                  </div>
                  <div>
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <h4 className="text-xs font-bold text-slate-900">{s.fullName}</h4>
                      <Badge variant={studentStatusVariants[s.status]}>{studentStatusLabels[s.status]}</Badge>
                    </div>
                    <div className="flex items-center gap-1.5 text-[10px] text-slate-400 mt-1">
                      <span className="font-mono">{s.studentCode}</span>
                      {s.primarySiteName && (
                        <>
                          <span>•</span>
                          <span className="text-brand-orange font-bold">{s.primarySiteName}</span>
                        </>
                      )}
                    </div>
                  </div>
                </div>
              </button>
            );
          })
        )}
      </div>
    </div>
  );
}
