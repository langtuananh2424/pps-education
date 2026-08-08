import React from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

interface PaginationProps {
  page: number;
  pageSize: number;
  totalElements: number;
  itemLabel: string;
  onPageChange: (page: number) => void;
}

/**
 * Phân trang phía CLIENT dùng chung cho các danh sách dài của Portal học sinh (BTVN, Video ôn tập...)
 * — bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-06 để tránh phải cuộn quá nhiều khi 1
 * lớp có nhiều bài. Mirror admin/src/components/ui/Pagination.tsx nhưng bớt phần chọn số dòng/trang
 * (cố định 1 mức, giữ giao diện gọn cho mobile) và đổi màu theo đúng bảng màu Portal (teal/ink/muted).
 */
export default function Pagination({ page, pageSize, totalElements, itemLabel, onPageChange }: PaginationProps) {
  const totalPages = Math.max(1, Math.ceil(totalElements / pageSize));
  if (totalPages <= 1) return null;

  return (
    <div className="flex items-center justify-between gap-3 pt-1">
      <span className="text-[11px] font-bold text-muted">
        Tổng {totalElements} {itemLabel}
      </span>
      <div className="flex items-center gap-2">
        <button
          type="button"
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
          aria-label="Trang trước"
          className="w-8 h-8 rounded-lg border border-line flex items-center justify-center text-muted hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          <ChevronLeft size={14} />
        </button>
        <span className="text-xs font-black text-ink font-mono">
          {page + 1}/{totalPages}
        </span>
        <button
          type="button"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
          aria-label="Trang sau"
          className="w-8 h-8 rounded-lg border border-line flex items-center justify-center text-muted hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          <ChevronRight size={14} />
        </button>
      </div>
    </div>
  );
}
