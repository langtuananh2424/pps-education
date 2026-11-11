import React from "react";

/**
 * Bổ sung 2026-10-19 (đã xác nhận với người dùng) — cú pháp chữ thuần tối giản để GV đánh dấu
 * đậm/nghiêng/gạch chân trong nội dung câu hỏi (content, choices[].content, referencePassage,
 * explanation...) mà KHÔNG cần rich-text editor thật: `**đậm**`, `*nghiêng*`, `__gạch chân__`.
 * KHÔNG phải Markdown đầy đủ (không link/list/heading...), chỉ 3 ký hiệu này — field vẫn lưu
 * NGUYÊN chữ thuần trong DB/Excel, không đổi schema/định dạng file import. Mirror nguyên hàm
 * admin/src/lib/richText.tsx (2 frontend tách riêng, không share code) — giữ ĐÚNG cùng logic để
 * học sinh thấy chữ đậm/nghiêng/gạch chân y hệt những gì GV soạn.
 *
 * Thứ tự alternation BẮT BUỘC `**` trước `*` — nếu đảo ngược, `*nghiêng*` sẽ "ăn" mất 1 nửa của
 * `**đậm**` liền kề do cùng dùng ký tự `*`.
 *
 * `__gạch chân__` chỉ khớp ĐÚNG 2 dấu `_` liên tiếp — tránh nuốt nhầm ký hiệu chỗ trống cloze
 * `(1)___` (3 dấu `_`) trong `reference_passage`, vốn gây gạch chân tràn lan từ chỗ trống này
 * sang chỗ trống kế tiếp (phát hiện 2026-10-29, đã xác nhận với người dùng). Mirror
 * admin/src/lib/richText.tsx.
 *
 * KHÔNG dùng lookbehind `(?<!_)` (bản cũ 2026-10-29 có dùng) — Safari/iOS < 16.4 không hỗ trợ
 * lookbehind, `new RegExp`/regex literal có lookbehind throw SyntaxError ngay lúc module này
 * được load, sập trắng TOÀN BỘ app (module-level constant, không chỉ riêng chỗ gọi
 * renderRichText) — sự cố xác nhận trên iPhone iOS 16.1.2, 2026-11-11 (bản admin). Lookahead
 * `(?!_)` vẫn dùng bình thường (không bị giới hạn này). Điều kiện "không đứng ngay sau dấu `_`
 * khác" được kiểm tra thủ công bằng JS trong vòng lặp bên dưới thay vì lookbehind.
 */
const RICH_TEXT_PATTERN = /(\*\*[^*]+\*\*|\*[^*]+\*|__(?!_)[^_]+__(?!_))/g;

export function renderRichText(text: string | null | undefined): React.ReactNode {
  if (!text) return text;

  const nodes: React.ReactNode[] = [];
  const regex = new RegExp(RICH_TEXT_PATTERN.source, "g");
  let lastIndex = 0;
  let key = 0;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(text))) {
    const seg = match[0];
    const start = match.index;
    if (seg.startsWith("__") && start > 0 && text[start - 1] === "_") {
      // Tuong duong (?<!_) cua ban cu: tu choi match bat dau ngay sau 1 dau `_` khac (giua 1
      // chuoi `___` cloze), lui lai 1 ky tu de regex thu khop lai tu vi tri ke tiep.
      regex.lastIndex = start + 1;
      continue;
    }
    if (start > lastIndex) nodes.push(text.slice(lastIndex, start));
    if (seg.startsWith("**") && seg.length > 4) {
      nodes.push(<strong key={key++}>{seg.slice(2, -2)}</strong>);
    } else if (seg.startsWith("__") && seg.length > 4) {
      nodes.push(<u key={key++}>{seg.slice(2, -2)}</u>);
    } else if (seg.startsWith("*") && seg.length > 2) {
      nodes.push(<em key={key++}>{seg.slice(1, -1)}</em>);
    } else {
      nodes.push(seg);
    }
    lastIndex = start + seg.length;
  }
  if (lastIndex < text.length) nodes.push(text.slice(lastIndex));
  return nodes;
}
