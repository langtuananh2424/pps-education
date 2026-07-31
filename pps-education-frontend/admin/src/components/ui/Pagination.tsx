import React from "react";
import Button from "./Button";
import Select from "./Select";

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

interface PaginationProps {
  page: number;
  pageSize: number;
  totalElements: number;
  itemLabel: string;
  onPageChange: (page: number) => void;
  onPageSizeChange: (pageSize: number) => void;
}

/**
 * Phân trang phía CLIENT — dùng cho các danh sách mà backend chưa hỗ trợ phân trang server-side
 * (chỉ GET /users, /permission-audit-logs, /notifications có Page<T> thật). Component chỉ lo phần
 * hiển thị điều khiển trang; nơi gọi tự cắt lát mảng đã tải (`.slice(page * pageSize, ...)`) — xem
 * ví dụ StudentListPanel.tsx. Cùng khuôn UI "Trước / Trang X/Y / Sau" đã có sẵn ở UsersPage.tsx.
 */
export default function Pagination({ page, pageSize, totalElements, itemLabel, onPageChange, onPageSizeChange }: PaginationProps) {
  const totalPages = Math.max(1, Math.ceil(totalElements / pageSize));

  return (
    <div className="flex flex-col sm:flex-row items-center justify-between gap-2 px-4 py-3 border-t border-slate-100 text-[11px] text-slate-500">
      <div className="flex items-center gap-2">
        <span>
          Tổng {totalElements} {itemLabel}
        </span>
        <span className="text-slate-300">|</span>
        <label className="flex items-center gap-1.5">
          Dòng/trang:
          <Select
            value={pageSize}
            onChange={(e) => onPageSizeChange(Number(e.target.value))}
            className="bg-slate-50 border border-slate-200 text-[11px] px-1.5 py-1 rounded-md focus:outline-none cursor-pointer"
          >
            {PAGE_SIZE_OPTIONS.map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
          </Select>
        </label>
      </div>
      {totalPages > 1 && (
        <div className="flex items-center gap-2">
          <Button size="sm" variant="secondary" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
            Trước
          </Button>
          <span className="font-mono">
            Trang {page + 1}/{totalPages}
          </span>
          <Button size="sm" variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>
            Sau
          </Button>
        </div>
      )}
    </div>
  );
}
