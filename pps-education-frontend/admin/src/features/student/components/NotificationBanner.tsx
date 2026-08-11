import React from "react";
import { Smartphone, X } from "lucide-react";

interface NotificationBannerAction {
  label: string;
  onClick: () => void;
}

interface NotificationBannerProps {
  message: string | null;
  onClose: () => void;
  action?: NotificationBannerAction;
}

export default function NotificationBanner({ message, onClose, action }: NotificationBannerProps) {
  if (!message) return null;
  return (
    <div className="p-4 bg-brand-orange/10 text-brand-dark rounded-xl border border-brand-orange/30 shadow-soft flex items-start gap-3 animate-in fade-in slide-in-from-top-3 duration-200">
      <Smartphone className="w-5 h-5 text-brand-red mt-0.5 shrink-0 animate-bounce" />
      <div className="text-xs font-medium leading-relaxed whitespace-pre-line flex-1">{message}</div>
      {action && (
        <button
          onClick={action.onClick}
          className="shrink-0 px-2.5 py-1 bg-brand-red hover:bg-red-700 text-white text-[10px] font-bold rounded-lg whitespace-nowrap"
        >
          {action.label}
        </button>
      )}
      <button onClick={onClose} className="text-brand-dark/40 hover:text-brand-dark shrink-0">
        <X className="w-4 h-4" />
      </button>
    </div>
  );
}
