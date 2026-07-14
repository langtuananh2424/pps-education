import React from "react";

export default function Toast({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div className="fixed bottom-5 right-5 z-50 bg-slate-900 text-white px-4 py-3 rounded-xl shadow-2xl flex items-center gap-2.5 animate-in slide-in-from-bottom-4 duration-300 border border-slate-800">
      <div className="w-2.5 h-2.5 rounded-full bg-brand-orange animate-pulse shrink-0" />
      <span className="text-xs font-semibold">{message}</span>
    </div>
  );
}
