import React from "react";

/**
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — SPIKE lần 2, đổi hẳn kiểu dáng sau khi
 * người dùng xem 3 mẫu Word Art và chọn phong cách mềm mại hơn (không phải "Sticker Nổi" viền đen dày ở
 * bản trước) — viền màu mảnh + nền trắng, pill nhãn đè nhẹ lên mép trên card, nghiêng nhẹ như 2 nét chữ
 * V chụm vào nhau (`tiltClass` truyền từ nơi gọi: bên trái nghiêng trái, bên phải nghiêng phải), rung lắc
 * (`animate-wiggle`, khai báo ở index.css) khi di chuột qua rồi tự về lại đúng góc nghiêng tĩnh ban đầu.
 *
 * Tách ra file dùng chung 2026-09-23 (đã xác nhận với người dùng) — ban đầu chỉ ReflexVideoTaskPage
 * (badge % "Viết"/"Nói" của Speaking) dùng, nay TakeExerciseModal (badge % chấm Writing/Essay) dùng lại
 * y hệt style để đồng bộ ngôn ngữ hình ảnh "chấm điểm" trên toàn Portal Học Sinh.
 */
export function ScoreSticker({
  icon,
  label,
  percent,
  tone,
  tiltClass
}: {
  icon: React.ReactNode;
  label: string;
  percent: number | null;
  tone: "pass" | "fail" | "pending";
  tiltClass: string;
}) {
  const border = tone === "pass" ? "border-teal" : tone === "fail" ? "border-coral" : "border-line";
  const text = tone === "pass" ? "text-teal-deep" : tone === "fail" ? "text-coral" : "text-muted";
  return (
    // Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-18 — thu nhỏ hẳn trên di động (mặc định,
    // dưới `sm:`) — sticker luôn `absolute` ghim góc phải trên (xem nơi gọi) ở MỌI kích thước màn hình,
    // nên phải đủ nhỏ để không đè lên dòng "CÂU HỎI n" bên trái kể cả ở điện thoại nhỏ nhất (320px, đã
    // test — 68px/thẻ vẫn hụt vài px, giảm xuống 58px mới đủ an toàn).
    <div className={`${tiltClass} wiggle-on-hover shrink-0 cursor-default`}>
      <span
        className={`relative z-10 -mb-1.5 ml-2 flex w-fit items-center gap-1 rounded-full border-2 ${border} bg-white px-1.5 py-0.5 text-[8px] font-extrabold uppercase ${text} whitespace-nowrap sm:-mb-2 sm:ml-3 sm:px-2.5 sm:py-1 sm:text-[10px]`}
      >
        {icon} {label}
      </span>
      <div
        className={`flex w-[58px] items-center justify-center rounded-xl border-2 ${border} bg-white pt-2 pb-1 shadow-md sm:w-[114px] sm:rounded-2xl sm:pt-4 sm:pb-2.5`}
      >
        <span className={`font-display text-base font-extrabold sm:text-4xl ${text}`}>
          {percent != null ? percent : "—"}
          {percent != null && <span className="align-top text-[9px] sm:text-lg">%</span>}
        </span>
      </div>
    </div>
  );
}
