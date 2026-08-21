import React, { useEffect, useState } from "react";
import { Plus, Search, Users } from "lucide-react";
import { useTranslation } from "react-i18next";
import { StudentResponse } from "../api";
import Badge, { BadgeVariant } from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import Pagination from "@/components/ui/Pagination";

/** Nhãn dịch qua i18next namespace "student" — xem src/i18n/locales/{vi,en}/student.json. */
export function studentStatusLabel(t: (key: string) => string, status: string): string {
  return t(`studentStatus.${status}`);
}

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
  const { t } = useTranslation("student");
  // Backend GET /students chưa hỗ trợ phân trang (trả nguyên mảng) — phân trang phía client để danh
  // sách không dài vô hạn khi xem "Tất cả điểm trường". Reset về trang 1 mỗi khi kết quả tải mới
  // (đổi điểm trường/tìm kiếm) để không kẹt ở 1 trang rỗng.
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  useEffect(() => setPage(0), [students]);
  const pageStudents = students.slice(page * pageSize, (page + 1) * pageSize);

  return (
    <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden flex flex-col h-full">
      <div className="px-5 py-4 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-slate-50 shrink-0">
        <div className="space-y-0.5">
          <span className="text-xs font-bold text-slate-700 font-display block">{t("studentList.title")}</span>
          <p className="text-[10px] text-slate-400">{t("studentList.subtitle")}</p>
        </div>
        <Button variant="primary" size="sm" onClick={onCreate} className="whitespace-nowrap shrink-0">
          <Plus className="w-3.5 h-3.5" />
          {t("studentList.addButton")}
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
          placeholder={t("studentList.searchPlaceholder")}
          className="w-full bg-slate-50 border border-slate-200 text-xs pl-8 pr-3 py-2 rounded-lg focus:outline-none"
        />
      </form>

      <div className="divide-y divide-slate-100 overflow-y-auto max-h-[620px] lg:max-h-[680px]">
        {loading ? (
          <div className="p-8 text-center text-slate-400 text-xs">{t("studentList.loading")}</div>
        ) : students.length === 0 ? (
          <EmptyState icon={Users} title={t("studentList.emptyTitle")} description={t("studentList.emptyDescription")} />
        ) : (
          pageStudents.map((s) => {
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
                      <Badge variant={studentStatusVariants[s.status]}>{studentStatusLabel(t, s.status)}</Badge>
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

      {!loading && students.length > 0 && (
        <Pagination
          page={page}
          pageSize={pageSize}
          totalElements={students.length}
          itemLabel={t("studentList.itemLabel")}
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
