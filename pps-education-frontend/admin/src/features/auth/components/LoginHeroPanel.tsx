import React from "react";

export default function LoginHeroPanel() {
  return (
    <div className="hidden md:block md:col-span-6 relative overflow-hidden select-none border-l border-slate-100 min-h-[600px]">
      <img
        src="https://images.unsplash.com/photo-1523240795612-9a054b0db644?auto=format&fit=crop&w=1200&q=80"
        alt="PPS English Learning"
        className="absolute inset-0 w-full h-full object-cover"
        referrerPolicy="no-referrer"
      />
      <div className="absolute inset-0 bg-gradient-to-t from-slate-950/80 via-slate-950/20 to-slate-950/30 flex flex-col justify-end p-10">
        <div className="flex items-center gap-2 mb-2.5">
          <span className="text-sm bg-[#EA580C] text-white px-3 py-1 rounded font-extrabold uppercase tracking-wider">
            PPS English
          </span>
          <span className="text-sm bg-white/20 text-white backdrop-blur-xs px-2.5 py-1 rounded font-bold uppercase tracking-wider">
            Cambridge Standard
          </span>
        </div>
        <h3 className="text-white text-base md:text-lg font-extrabold tracking-tight leading-snug max-w-[340px]">
          Hệ thống Quản lý Đào tạo & Khóa học Tiếng Anh thông minh
        </h3>
        <p className="text-slate-300 text-sm mt-1.5 font-medium max-w-[320px]">
          Đồng bộ dữ liệu học tập trực tiếp và lộ trình chuẩn hóa quốc tế của PPS Vietnam.
        </p>
      </div>
    </div>
  );
}
