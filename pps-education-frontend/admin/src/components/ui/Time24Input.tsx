import React from "react";
import { cn } from "@/lib/cn";

interface Time24InputProps {
  value: string; // "HH:mm" hoặc ""
  onChange: (value: string) => void;
  required?: boolean;
  disabled?: boolean;
  className?: string;
}

/**
 * Ô nhập giờ ép cứng định dạng 24h (bổ sung ngoài SDD gốc, xác nhận với
 * người dùng 2026-08-20) — thay `<input type="time">` gốc vì định dạng
 * hiển thị (12h AM/PM hay 24h) của input đó do TRÌNH DUYỆT đọc theo
 * Region của hệ điều hành quyết định, không phải trang web — dù đã đặt
 * `lang="vi"` ở index.html, Chrome trên Windows vẫn hiện AM/PM nếu Region
 * Windows là "English (United States)". Ô tự dựng này không dùng picker
 * gốc nên luôn hiện đúng "HH:mm" bất kể máy nào.
 */
export default function Time24Input({ value, onChange, required, disabled, className }: Time24InputProps) {
  const handleChange = (raw: string) => {
    const digits = raw.replace(/\D/g, "").slice(0, 4);
    if (digits.length <= 2) {
      onChange(digits);
      return;
    }
    onChange(`${digits.slice(0, 2)}:${digits.slice(2)}`);
  };

  const handleBlur = () => {
    if (!value) return;
    const [hStr, mStr] = value.split(":");
    let h = Number(hStr ?? 0);
    let m = Number(mStr ?? 0);
    if (Number.isNaN(h) || h > 23) h = 23;
    if (Number.isNaN(m) || m > 59) m = 59;
    onChange(`${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`);
  };

  return (
    <input
      type="text"
      inputMode="numeric"
      placeholder="HH:mm"
      maxLength={5}
      value={value}
      onChange={(e) => handleChange(e.target.value)}
      onBlur={handleBlur}
      required={required}
      disabled={disabled}
      className={cn("font-mono", className)}
    />
  );
}
