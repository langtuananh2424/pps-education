import React, { useState } from "react";
import parse, { Element as DomElement } from "html-react-parser";
import "ckeditor5/ckeditor5-content.css";
import "./ckeditor-student.css";

/**
 * Hiển thị + thu đáp án cho câu "Ô trống"/"Hộp từ vựng" soạn qua tab CKEditor5 (Admin) —
 * {@code content} là HTML ĐẦY ĐỦ (bảng/ảnh/chữ đậm, nhiều ô trống rải rác tự do trong đoạn văn dài)
 * thay vì chữ thường tách theo "___" như WordBankBlock cũ (xem Javadoc backend
 * CkeditorWordBankParser). Thay mỗi `<span class="q-blank">` bằng 1 `<input>` thật, thu giá trị
 * theo ĐÚNG THỨ TỰ TÀI LIỆU — khớp {@code structuredContent.blanks[]} mà backend đã trích lúc lưu,
 * nên nộp lên {@code onChange}/{@code onDraft} y hệt mảng `string[]` mà WordBankBlock đang dùng,
 * không cần đổi gì ở tầng chấm điểm (ExerciseAttemptService#structuredPartialScore).
 *
 * Không đọc `data-answer` của widget — Ô trống xuất ra cho Học viên (content thật, không phải bản
 * xem trước của GV) không hề mang theo đáp án đúng, nên không có gì để tự chấm/tô màu đúng-sai
 * ngay trên trình duyệt (khác pane "Xem trước" ở Admin — ở đó GV đang xem chính đáp án mình soạn).
 */
export default function CkeditorWordBankAnswer({
  content,
  initialAnswer,
  readOnly,
  onChange,
  onDraft
}: {
  content: string;
  initialAnswer: string[] | undefined;
  readOnly: boolean;
  onChange: (values: string[]) => void;
  onDraft?: (values: string[]) => void;
}) {
  // Số ô trống chỉ biết được SAU KHI parse — khởi tạo rỗng, điền lại khi parse xong (xem bên dưới).
  const [answers, setAnswers] = useState<string[]>(initialAnswer ?? []);

  let blankCursor = -1;
  const rendered = parse(content, {
    replace: (domNode) => {
      if (!(domNode instanceof DomElement)) return undefined;
      if (domNode.name !== "span" || !domNode.attribs?.class?.includes("q-blank")) return undefined;
      blankCursor += 1;
      const position = blankCursor;
      const value = answers[position] ?? "";
      return (
        <input
          key={`blank-${position}`}
          type="text"
          value={value}
          disabled={readOnly}
          onChange={(e) => {
            const next = [...answers];
            while (next.length <= position) next.push("");
            next[position] = e.target.value;
            setAnswers(next);
            onDraft?.(next);
          }}
          onBlur={() => onChange(answers)}
          className="bg-sky-2 border border-line/70 text-sm sm:text-sm lg:text-base font-bold px-2 py-1 mx-1 sm:px-3 sm:py-2 rounded-lg align-middle focus:outline-none disabled:opacity-70 w-28 sm:w-36"
        />
      );
    }
  });

  // `ck-content` bắt buộc — style bảng/font-size/alignment của CKEditor5 chỉ ăn trong class này.
  return <div className="ck-content text-sm sm:text-base lg:text-lg leading-8 lg:leading-10">{rendered}</div>;
}
