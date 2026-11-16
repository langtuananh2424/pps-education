import React from "react";
import { renderRichText } from "@/lib/richText";
import "ckeditor5/ckeditor5-content.css";
import "./ckeditor-student.css";

/** Nhận diện content/referencePassage là HTML đầy đủ soạn qua tab CKEditor5 (có thẻ mở) thay vì
 * chữ thường + cú pháp `**đậm**` cũ — mirror hệt heuristic đã dùng ở Admin
 * (CreateAndAssignExerciseModal.tsx#summarizeAttachedContent). */
export function isCkeditorHtml(text: string | null | undefined): boolean {
  return !!text && /<[a-z][\s\S]*>/i.test(text);
}

/**
 * Hiển thị CHỈ ĐỌC 1 đoạn nội dung CÓ THỂ là HTML đầy đủ (bảng/ảnh/chữ đậm...) soạn qua CKEditor5,
 * hoặc chữ thường cú pháp cũ — tự chọn đúng cách render. Dùng cho phần "đoạn dẫn" của câu Nhóm lựa
 * chọn/Khối sắp xếp/Bảng lưới/Lựa chọn trong câu (các widget đó đã BÓC khỏi content khi lưu, phần
 * còn lại chỉ là văn bản tĩnh, không cần tương tác — xem Javadoc backend CkeditorChoiceParser/
 * CkeditorChunkParser/CkeditorGroupParser). KHÔNG dùng cho Ô trống/Hộp từ vựng — loại đó cần tương
 * tác (điền ô), xem CkeditorWordBankAnswer.tsx.
 */
export default function RichQuestionContent({ content, className }: { content: string; className?: string }) {
  if (!isCkeditorHtml(content)) {
    return <span className={className}>{renderRichText(content)}</span>;
  }
  // Nội dung do chính GV soạn, đã sanitize ở backend trước khi lưu (xem CkeditorHtmlSanitizer) —
  // an toàn để render thẳng. `ck-content` bắt buộc để ăn style bảng/font-size/alignment của
  // CKEditor5 (ckeditor5-content.css). Dùng <div> (không phải <span>) — nội dung HTML thật có thể
  // mang theo bảng/đoạn văn (nội dung khối), lồng vào <span> ở nơi gọi sẽ vỡ cấu trúc HTML.
  return <div className={`ck-content ${className ?? ""}`} dangerouslySetInnerHTML={{ __html: content }} />;
}
