import React from "react";
import { Bot, Loader2 } from "lucide-react";

interface AiAssistantFabProps {
  /** Nhãn hiện khi rê chuột/đọc màn hình — mỗi màn hình tự đặt theo việc trợ lý làm (VD "Trợ lý nhận xét AI"). */
  label: string;
  onClick: () => void;
  /** Trợ lý đang chạy nền (VD đang soạn nhận xét) — đổi icon thành vòng xoay để giáo viên biết khi đã đóng sidebar. */
  busy?: boolean;
  hidden?: boolean;
}

/**
 * Nút nổi mở Trợ lý AI (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-28) — dùng chung cho mọi
 * màn hình có trợ lý: màn hình tự quyết định khi nào hiện và trợ lý nào được mở (hiện tại: UC-74 soạn nháp
 * nhận xét ở DailyCommentPanel). Ghim góc dưới phải, nằm dưới sidebar trợ lý (z-30 < z-40).
 */
export default function AiAssistantFab({ label, onClick, busy = false, hidden = false }: AiAssistantFabProps) {
  if (hidden) return null;
  return (
    <button
      type="button"
      onClick={onClick}
      title={label}
      aria-label={label}
      className="group fixed bottom-6 right-6 z-30 flex items-center gap-2 rounded-full bg-violet-600 text-white shadow-lg shadow-violet-600/30 hover:bg-violet-700 focus:outline-none focus-visible:ring-4 focus-visible:ring-violet-300 transition-all pl-4 pr-4 py-3"
    >
      {busy ? <Loader2 className="w-5 h-5 animate-spin" /> : <Bot className="w-5 h-5" />}
      <span className="hidden sm:inline text-sm font-semibold max-w-0 overflow-hidden whitespace-nowrap group-hover:max-w-xs transition-all duration-300">
        {label}
      </span>
    </button>
  );
}
