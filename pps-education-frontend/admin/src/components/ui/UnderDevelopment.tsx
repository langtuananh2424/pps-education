import React from "react";
import { Sparkles } from "lucide-react";

interface UnderDevelopmentProps {
  title: string;
  description?: string;
}

/** Placeholder dùng chung cho các màn còn 100% mock data, tạm ẩn chờ phát triển tiếp ở giai đoạn sau — không xoá component/logic cũ, chỉ chưa hiển thị. */
export default function UnderDevelopment({ title, description }: UnderDevelopmentProps) {
  return (
    <div className="bg-white border border-dashed border-slate-200 rounded-2xl p-10 text-center space-y-2">
      <span className="inline-flex items-center gap-1.5 bg-amber-50 text-amber-600 border border-amber-200 px-3 py-1 rounded-full text-sm font-extrabold uppercase tracking-wider">
        <Sparkles className="w-3 h-3" /> Đang phát triển
      </span>
      <h3 className="text-sm font-bold text-slate-800">{title}</h3>
      <p className="text-sm text-slate-500 max-w-md mx-auto">{description ?? "Tính năng đang được phát triển ở giai đoạn sau, chưa sẵn sàng sử dụng."}</p>
    </div>
  );
}
