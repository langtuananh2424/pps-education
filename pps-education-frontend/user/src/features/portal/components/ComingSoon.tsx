import React from "react";
import { Sparkles } from "lucide-react";
import { useTranslation } from "react-i18next";

interface ComingSoonProps {
  title: string;
  description: string;
}

/** Dùng cho các phần đã có trong thiết kế nhưng chưa có API thật (Luyện Nghe-Nói, Rewards/EXP...). */
export default function ComingSoon({ title, description }: ComingSoonProps) {
  const { t } = useTranslation("portal");
  return (
    <div className="bg-white border border-dashed border-line/80 p-6 rounded-[20px] space-y-2 relative overflow-hidden">
      <span className="inline-flex items-center gap-1.5 bg-gold/10 text-gold border border-gold/30 px-3 py-1 rounded-full text-[10px] font-extrabold uppercase tracking-wider">
        <Sparkles size={12} /> {t("comingSoon.badge")}
      </span>
      <h3 className="text-base font-extrabold text-ink">{title}</h3>
      <p className="text-xs text-muted font-bold leading-relaxed">{description}</p>
    </div>
  );
}
