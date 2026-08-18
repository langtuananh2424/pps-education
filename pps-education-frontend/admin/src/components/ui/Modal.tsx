import React from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  /** Override cỡ chữ tiêu đề -- mặc định "text-sm" (khá nhỏ), dùng khi 1 modal cụ thể cần tiêu đề nổi bật hơn. */
  titleClassName?: string;
  /** Override cỡ chữ mô tả -- mặc định "text-[11px]". */
  descriptionClassName?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
  size?: "md" | "lg" | "xl";
}

const SIZE_CLASSES: Record<NonNullable<ModalProps["size"]>, string> = {
  md: "max-w-lg",
  lg: "max-w-2xl",
  xl: "max-w-7xl",
};

export default function Modal({
  open,
  onClose,
  title,
  description,
  titleClassName = "text-sm",
  descriptionClassName = "text-[11px]",
  children,
  footer,
  size = "md"
}: ModalProps) {
  if (!open) return null;

  // Portal thẳng ra document.body — nếu render lồng trong ancestor có backdrop-filter/filter/transform
  // (VD Header.tsx có class "backdrop-blur-md"), CSS sẽ biến ancestor đó thành containing block cho mọi
  // phần tử "fixed" bên trong, khiến "inset-0" bị co lại theo kích thước ancestor thay vì toàn màn hình
  // (đã xác nhận thực tế: div fixed bị co còn đúng bằng box 64px của header) — modal lệch hẳn lên trên.
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs" onClick={onClose} />
      <div
        className={`relative w-full ${SIZE_CLASSES[size]} max-h-[95vh] overflow-y-auto bg-white rounded-2xl border border-slate-200 shadow-xl animate-in fade-in duration-150`}
      >
        <div className="sticky top-0 bg-white px-5 py-4 border-b border-slate-100 flex items-start justify-between gap-3">
          <div>
            <h3 className={`${titleClassName} font-bold font-display text-slate-900`}>{title}</h3>
            {description && <p className={`${descriptionClassName} text-slate-500 mt-1`}>{description}</p>}
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-md text-slate-400 hover:text-slate-700 hover:bg-slate-100 shrink-0"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
        <div className="p-5">{children}</div>
        {footer && <div className="px-5 py-4 border-t border-slate-100 flex justify-end gap-2">{footer}</div>}
      </div>
    </div>,
    document.body
  );
}
