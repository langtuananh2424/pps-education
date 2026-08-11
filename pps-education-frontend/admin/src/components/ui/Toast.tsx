import React from "react";
import { createPortal } from "react-dom";
import { CheckCircle2 } from "lucide-react";
import { cn } from "@/lib/cn";

type ToastPosition = "bottom-right" | "top-center";

interface ToastProps {
  message: string | null;
  position?: ToastPosition;
}

const positionClasses: Record<ToastPosition, string> = {
  "bottom-right": "bottom-5 right-5 animate-in slide-in-from-bottom-4",
  "top-center": "top-5 left-1/2 -translate-x-1/2 animate-in slide-in-from-top-4"
};

export default function Toast({ message, position = "bottom-right" }: ToastProps) {
  if (!message) return null;
  // Portal thẳng ra document.body — nếu render lồng trong ancestor có backdrop-filter/filter/transform
  // (VD <header className="backdrop-blur-md">), "fixed" sẽ bị neo theo containing block của ancestor đó
  // thay vì viewport (đúng theo spec CSS), khiến toast lệch vị trí hẳn.
  return createPortal(
    <div
      className={cn(
        "fixed z-50 bg-slate-900 text-white px-4 py-3 rounded-xl shadow-2xl flex items-center gap-2.5 duration-300 border border-slate-800",
        positionClasses[position]
      )}
    >
      {position === "top-center" ? (
        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
      ) : (
        <div className="w-2.5 h-2.5 rounded-full bg-brand-orange animate-pulse shrink-0" />
      )}
      <span className="text-xs font-semibold whitespace-nowrap">{message}</span>
    </div>,
    document.body
  );
}
