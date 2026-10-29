import React from "react";

/**
 * Bổ sung 2026-10-19 (đã xác nhận với người dùng) — cú pháp chữ thuần tối giản để GV đánh dấu
 * đậm/nghiêng/gạch chân trong nội dung câu hỏi (content, choices[].content, referencePassage,
 * explanation...) mà KHÔNG cần rich-text editor thật: `**đậm**`, `*nghiêng*`, `__gạch chân__`.
 * KHÔNG phải Markdown đầy đủ (không link/list/heading...), chỉ 3 ký hiệu này — field vẫn lưu
 * NGUYÊN chữ thuần trong DB/Excel, không đổi schema/định dạng file import. Mirror renderFormBold
 * cũ (trước đây chỉ có **đậm**, chỉ bật cho loại NGHE_PHIEU_THONG_TIN) — giờ dùng CHUNG cho mọi
 * nơi hiện content câu hỏi, không giới hạn theo loại nữa.
 *
 * Thứ tự alternation BẮT BUỘC `**` trước `*` — nếu đảo ngược, `*nghiêng*` sẽ "ăn" mất 1 nửa của
 * `**đậm**` liền kề do cùng dùng ký tự `*`.
 *
 * `__gạch chân__` dùng lookbehind/lookahead (?<!_)/(?!_) để chỉ khớp ĐÚNG 2 dấu `_` liên tiếp —
 * tránh nuốt nhầm ký hiệu chỗ trống cloze `(1)___` (3 dấu `_`) trong `reference_passage`, vốn gây
 * gạch chân tràn lan từ chỗ trống này sang chỗ trống kế tiếp (phát hiện 2026-10-29, đã xác nhận
 * với người dùng).
 */
const RICH_TEXT_PATTERN = /(\*\*[^*]+\*\*|\*[^*]+\*|(?<!_)__(?!_)[^_]+(?<!_)__(?!_))/g;

export function renderRichText(text: string | null | undefined): React.ReactNode {
  if (!text) return text;
  return text.split(RICH_TEXT_PATTERN).map((seg, i) => {
    if (seg.startsWith("**") && seg.endsWith("**") && seg.length > 4) {
      return <strong key={i}>{seg.slice(2, -2)}</strong>;
    }
    if (seg.startsWith("__") && seg.endsWith("__") && seg.length > 4) {
      return <u key={i}>{seg.slice(2, -2)}</u>;
    }
    if (seg.startsWith("*") && seg.endsWith("*") && seg.length > 2) {
      return <em key={i}>{seg.slice(1, -1)}</em>;
    }
    return seg;
  });
}
