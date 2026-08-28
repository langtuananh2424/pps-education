import React, { isValidElement, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { ChevronDown } from "lucide-react";
import { useTranslation } from "react-i18next";
import { cn } from "@/lib/cn";

interface SelectOptionData {
  value: string;
  label: React.ReactNode;
  disabled?: boolean;
}

interface SelectProps {
  value: string | number | undefined;
  onChange: (e: { target: { value: string } }) => void;
  onBlur?: () => void;
  children: React.ReactNode;
  className?: string;
  disabled?: boolean;
  required?: boolean;
  id?: string;
  name?: string;
  "aria-label"?: string;
}

function extractOptions(children: React.ReactNode): SelectOptionData[] {
  const options: SelectOptionData[] = [];
  React.Children.forEach(children, (child) => {
    if (!isValidElement(child)) return;
    // React.Children.forEach không tự flatten Fragment (<>...</>) — 1 nhánh JSX kiểu
    // {cond ? <opt/> : <><opt/><opt/></>} (VD skillCategory ở CreateAndAssignExerciseModal)
    // bị đếm là 1 "option" duy nhất có value="" thay vì đọc tiếp các <option> bên trong,
    // khiến các thẻ <option> lọt ra ngoài <select> gốc và trình duyệt tự render mỗi option
    // thành 1 khối riêng. Đệ quy vào bên trong Fragment để lấy đúng danh sách option thật.
    if (child.type === React.Fragment) {
      options.push(...extractOptions((child.props as { children?: React.ReactNode }).children));
      return;
    }
    const props = child.props as { value?: string | number; children?: React.ReactNode; disabled?: boolean };
    options.push({ value: props.value == null ? "" : String(props.value), label: props.children, disabled: props.disabled });
  });
  return options;
}

/** Gom text thuần từ label (có thể là JSX icon+chữ) để lọc theo từ khoá tìm kiếm. */
function nodeToText(node: React.ReactNode): string {
  if (node == null || typeof node === "boolean") return "";
  if (typeof node === "string" || typeof node === "number") return String(node);
  if (Array.isArray(node)) return node.map(nodeToText).join(" ");
  if (isValidElement(node)) return nodeToText((node.props as { children?: React.ReactNode })?.children);
  return "";
}

/** Danh sách dài hơn ngưỡng này mới hiện ô tìm kiếm — tránh thêm ô lọc thừa cho dropdown chỉ vài lựa chọn. */
const SEARCH_THRESHOLD = 8;

/**
 * Thay thế <select> gốc — giữ nguyên API value/onChange/children (<option>) nên chỉ cần đổi tên thẻ ở
 * hầu hết chỗ dùng, nhưng tự vẽ panel lựa chọn thay vì list xổ mặc định của trình duyệt. Panel render
 * qua portal vào document.body (position: fixed theo toạ độ trigger) để không bị cắt mất khi đặt trong
 * ô bảng có overflow-x-auto (VD DailyCommentPanel, GradeSheetTable) — khác với dropdown thường chỉ absolute
 * trong DOM cha, sẽ bị overflow-x-auto của bảng cắt theo cả trục dọc.
 */
export default function Select({ value, onChange, children, className, disabled, id, name, ...aria }: SelectProps) {
  const { t } = useTranslation("common");
  const [open, setOpen] = useState(false);
  const [rect, setRect] = useState<{ top?: number; bottom?: number; left: number; width: number; maxHeight: number; openUp: boolean } | null>(
    null
  );
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const options = useMemo(() => extractOptions(children), [children]);
  const selected = options.find((o) => o.value === String(value ?? ""));
  const [query, setQuery] = useState("");
  const searchable = options.length > SEARCH_THRESHOLD;
  const filteredOptions = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!searchable || !q) return options;
    return options.filter((o) => nodeToText(o.label).toLowerCase().includes(q));
  }, [options, searchable, query]);

  useEffect(() => {
    if (!open) setQuery("");
  }, [open]);

  // Tự lật panel lên trên trigger khi không đủ chỗ bên dưới (VD dropdown "Dòng/trang" ở
  // thanh phân trang cuối bảng, sát mép dưới màn hình) — trước đây luôn ép mở xuống dưới
  // (top: r.bottom + 4), panel bị tràn ra ngoài viewport không bấm chọn được.
  const updateRect = () => {
    const el = triggerRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    const margin = 8;
    const preferredMax = 256; // khớp max-h-64 cũ
    const spaceBelow = window.innerHeight - r.bottom - margin;
    const spaceAbove = r.top - margin;
    const openUp = spaceBelow < preferredMax && spaceAbove > spaceBelow;
    const maxHeight = Math.max(120, Math.min(preferredMax, openUp ? spaceAbove : spaceBelow));
    setRect({
      left: r.left,
      width: r.width,
      maxHeight,
      openUp,
      top: openUp ? undefined : r.bottom + 4,
      bottom: openUp ? window.innerHeight - r.top + 4 : undefined
    });
  };

  useLayoutEffect(() => {
    if (open) updateRect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const handlePointerDown = (e: MouseEvent) => {
      if (triggerRef.current?.contains(e.target as Node)) return;
      if (panelRef.current?.contains(e.target as Node)) return;
      setOpen(false);
    };
    const handleReposition = () => updateRect();
    document.addEventListener("mousedown", handlePointerDown);
    window.addEventListener("scroll", handleReposition, true);
    window.addEventListener("resize", handleReposition);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      window.removeEventListener("scroll", handleReposition, true);
      window.removeEventListener("resize", handleReposition);
    };
  }, [open]);

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        id={id}
        name={name}
        disabled={disabled}
        onClick={() => !disabled && setOpen((v) => !v)}
        className={cn("flex items-center justify-between gap-1.5 text-left cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed", className)}
        {...aria}
      >
        <span className="truncate">{selected ? selected.label : ""}</span>
        <ChevronDown className={`w-3.5 h-3.5 text-slate-400 shrink-0 transition-transform ${open ? "rotate-180" : ""}`} aria-hidden="true" />
      </button>
      {open &&
        rect &&
        createPortal(
          <div
            ref={panelRef}
            style={{
              position: "fixed",
              top: rect.top,
              bottom: rect.bottom,
              left: rect.left,
              width: Math.max(rect.width, 160),
              maxHeight: rect.maxHeight
            }}
            className="z-[200] flex flex-col overflow-hidden bg-white border border-slate-200 rounded-lg shadow-lg animate-in fade-in duration-100"
          >
            {searchable && (
              <div className="p-1.5 border-b border-slate-100 shrink-0">
                <input
                  autoFocus
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  onClick={(e) => e.stopPropagation()}
                  placeholder={t("select.searchPlaceholder")}
                  className="w-full text-xs px-2 py-1.5 bg-slate-50 border border-slate-200 rounded-md focus:outline-none focus:border-brand-orange"
                />
              </div>
            )}
            <div className="flex-1 min-h-0 overflow-y-auto py-1">
              {filteredOptions.length === 0 ? (
                <p className="px-3 py-2 text-xs text-slate-400 italic">{t("select.noResults")}</p>
              ) : (
                filteredOptions.map((opt, i) => (
                  <button
                    key={`${opt.value}-${i}`}
                    type="button"
                    disabled={opt.disabled}
                    onClick={() => {
                      onChange({ target: { value: opt.value } });
                      setOpen(false);
                    }}
                    className={`w-full text-left px-3 py-2 text-xs transition-colors disabled:opacity-40 disabled:cursor-not-allowed ${
                      opt.value === String(value ?? "") ? "bg-brand-orange/10 text-brand-orange font-semibold" : "text-slate-700 hover:bg-slate-50"
                    }`}
                  >
                    {opt.label}
                  </button>
                ))
              )}
            </div>
          </div>,
          document.body
        )}
    </>
  );
}
