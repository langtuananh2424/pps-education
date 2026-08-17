/**
 * Số đếm nhỏ dạng pill (khác Badge.tsx — đó là chip nhãn trạng thái uppercase, sai hình dạng cho 1 con số
 * cạnh dòng Sidebar). Bổ sung ngoài SDD gốc, xác nhận 2026-08-17 — dùng cho badge "Hàng chờ chấm bài".
 */
export default function CountBadge({ count }: { count: number }) {
  if (count <= 0) return null;
  return (
    <span className="ml-auto inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full bg-brand-orange text-white text-[10px] font-bold leading-none">
      {count > 99 ? "99+" : count}
    </span>
  );
}
