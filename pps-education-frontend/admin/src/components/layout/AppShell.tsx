import React from "react";
import { Outlet } from "react-router-dom";
import { useApp } from "@/context/AppContext";
import Toast from "@/components/ui/Toast";
import Sidebar from "./Sidebar";
import Header from "./Header";

export default function AppShell() {
  const { loginNotice } = useApp();

  return (
    // Không đặt overflow-x-hidden ở 2 div bao ngoài này — CSS quy đổi overflow-y "visible" thành "auto"
    // khi overflow-x khác "visible" trên cùng 1 phần tử, khiến div đó trở thành scroll container theo
    // spec, làm Header (position: sticky) bám theo scroll container SAI đó thay vì viewport thật, hậu
    // quả: header cuộn mất thay vì ghim lại. Clip ngang thật sự đặt ở <main> (không phải ancestor của Header).
    <div className="min-h-screen bg-brand-bg flex">
      <Toast message={loginNotice} position="top-center" />

      <Sidebar />

      <div className="flex-1 flex flex-col lg:pl-[288px] min-h-screen lg:pr-4 lg:py-4">
        <Header />

        <main className="flex-1 bg-white rounded-3xl border border-slate-200/40 shadow-soft p-4 md:p-8 animate-in fade-in duration-300 overflow-x-hidden">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
