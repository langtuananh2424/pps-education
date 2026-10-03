import { BadgeVariant } from "@/components/ui/Badge";
import { ExpenseStatus, InvoiceStatus } from "./api";

const vndFormatter = new Intl.NumberFormat("vi-VN");

/** Số tiền VND, không phần thập phân — VD 2.000.000 ₫. */
export function formatVnd(value: number | null | undefined): string {
  return `${vndFormatter.format(Math.round(value ?? 0))} ₫`;
}

/** "YYYY-MM-DD" theo giờ máy người dùng (không dùng toISOString vì lệch ngày khi trước 7h sáng giờ VN). */
export function toIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function todayIso(): string {
  return toIsoDate(new Date());
}

/** Ngày đầu và cuối của tháng chứa `date`, lệch `offset` tháng. */
export function monthRange(date: Date = new Date(), offset = 0): { from: string; to: string } {
  const first = new Date(date.getFullYear(), date.getMonth() + offset, 1);
  const last = new Date(date.getFullYear(), date.getMonth() + offset + 1, 0);
  return { from: toIsoDate(first), to: toIsoDate(last) };
}

export function addDays(iso: string, days: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  return toIsoDate(new Date(y, m - 1, d + days));
}

/** "YYYY-MM-DD" -> "DD/MM/YYYY". */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const [y, m, d] = iso.slice(0, 10).split("-");
  return `${d}/${m}/${y}`;
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("vi-VN", { hour12: false });
}

export const invoiceStatusVariants: Record<InvoiceStatus, BadgeVariant> = {
  DRAFT: "neutral",
  ISSUED: "info",
  PARTIAL_PAID: "warning",
  PAID: "success",
  OVERDUE: "danger",
  CANCELLED: "neutral"
};

export const expenseStatusVariants: Record<ExpenseStatus, BadgeVariant> = {
  RECORDED: "warning",
  APPROVED: "success",
  REJECTED: "danger"
};

export const inputClass = "w-full bg-slate-50 border border-slate-200 text-sm p-2.5 rounded-lg focus:outline-none";
export const labelClass = "text-[12px] uppercase font-bold text-slate-500 block mb-1";
