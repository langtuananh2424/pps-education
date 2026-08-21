import React from "react";
import { Sparkles } from "lucide-react";
import { useTranslation } from "react-i18next";

interface UnderDevelopmentProps {
  title: string;
  description?: string;
}

/** Placeholder dùng chung cho các màn còn 100% mock data, tạm ẩn chờ phát triển tiếp ở giai đoạn sau — không xoá component/logic cũ, chỉ chưa hiển thị. */
export default function UnderDevelopment({ title, description }: UnderDevelopmentProps) {
  const { t } = useTranslation("common");
  return (
    <div className="bg-white border border-dashed border-slate-200 rounded-2xl p-10 text-center space-y-2">
      <span className="inline-flex items-center gap-1.5 bg-amber-50 text-amber-600 border border-amber-200 px-3 py-1 rounded-full text-[10px] font-extrabold uppercase tracking-wider">
        <Sparkles className="w-3 h-3" /> {t("underDevelopment.badge")}
      </span>
      <h3 className="text-sm font-bold text-slate-800">{title}</h3>
      <p className="text-xs text-slate-500 max-w-md mx-auto">{description ?? t("underDevelopment.defaultDescription")}</p>
    </div>
  );
}
