import React from "react";
import { Smartphone, X } from "lucide-react";

export default function NotificationBanner({ message, onClose }: { message: string | null; onClose: () => void }) {
  if (!message) return null;
  return (
    <div className="p-4 bg-brand-orange/10 text-brand-dark rounded-xl border border-brand-orange/30 shadow-soft flex items-start gap-3 animate-in fade-in slide-in-from-top-3 duration-200">
      <Smartphone className="w-5 h-5 text-brand-red mt-0.5 shrink-0 animate-bounce" />
      <div className="text-xs font-medium leading-relaxed whitespace-pre-line">{message}</div>
      <button onClick={onClose} className="ml-auto text-brand-dark/40 hover:text-brand-dark shrink-0">
        <X className="w-4 h-4" />
      </button>
    </div>
  );
}
