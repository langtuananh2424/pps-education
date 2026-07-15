import React from "react";
import { Outlet } from "react-router-dom";
import { useApp } from "@/context/AppContext";
import Toast from "@/components/ui/Toast";
import Sidebar from "./Sidebar";
import Header from "./Header";

export default function AppShell() {
  const { loginNotice } = useApp();

  return (
    <div className="min-h-screen bg-brand-bg flex overflow-x-hidden">
      <Toast message={loginNotice} position="top-center" />

      <Sidebar />

      <div className="flex-1 flex flex-col lg:pl-[320px] min-h-screen lg:pr-4 lg:py-4 overflow-x-hidden">
        <Header />

        <main className="flex-1 bg-white rounded-3xl border border-slate-200/40 shadow-soft p-4 md:p-8 animate-in fade-in duration-300">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
