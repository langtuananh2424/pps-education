import React from "react";
import { ChevronRight } from "lucide-react";

export interface BreadcrumbItem {
  label: string;
  onClick?: () => void;
}

/**
 * Breadcrumb dùng chung đầu tiên trong app (trước đây chưa có) — thêm cho màn BTVN (Portal Học Sinh)
 * điều hướng phân cấp Unit → Lesson → Bài tập, xem AssignmentsTab.tsx. Mục cuối (đang đứng) không phải
 * link; các mục trước có `onClick` render thành nút bấm để quay lại cấp tương ứng.
 */
export default function Breadcrumb({ items }: { items: BreadcrumbItem[] }) {
  return (
    <nav aria-label="breadcrumb" className="flex items-center gap-1.5 flex-wrap text-sm">
      {items.map((item, idx) => {
        const isLast = idx === items.length - 1;
        return (
          <React.Fragment key={idx}>
            {idx > 0 && <ChevronRight size={14} className="text-muted shrink-0" aria-hidden="true" />}
            {isLast || !item.onClick ? (
              <span className={`font-black truncate max-w-[220px] ${isLast ? "text-ink" : "text-muted"}`}>{item.label}</span>
            ) : (
              <button
                type="button"
                onClick={item.onClick}
                className="font-bold text-muted hover:text-teal-deep transition-colors truncate max-w-[220px] cursor-pointer"
              >
                {item.label}
              </button>
            )}
          </React.Fragment>
        );
      })}
    </nav>
  );
}
