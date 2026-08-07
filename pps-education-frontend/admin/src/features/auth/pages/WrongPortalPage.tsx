import React from "react";
import { LogOut, School } from "lucide-react";
import { useApp } from "@/context/AppContext";
import { roleLabels } from "@/constants/roles";
import { UserRole } from "@/types";
import Button from "@/components/ui/Button";

/** Chặn tài khoản STUDENT/PARENT/PARTNER_REP đăng nhập nhầm vào app Admin — 3 role này thuộc app Portal (`user/`), xem plan kiến trúc 2 app. */
export default function WrongPortalPage() {
  const { currentUser, logout } = useApp();

  const roleLabelText = currentUser?.roleCodes.map((code) => roleLabels[code as UserRole] ?? code).join(", ") ?? "";

  return (
    <div className="min-h-screen flex items-center justify-center bg-brand-bg p-6">
      <div className="bg-white p-8 rounded-xl border border-slate-200 shadow-soft max-w-md w-full text-center space-y-6 animate-in fade-in duration-200">
        <div className="w-16 h-16 bg-orange-50 rounded-full flex items-center justify-center text-brand-red mx-auto shadow-sm">
          <School className="w-8 h-8" />
        </div>

        <div className="space-y-2">
          <h2 className="text-lg font-bold text-slate-900 font-display">Tài khoản thuộc ứng dụng Portal</h2>
          <p className="text-sm text-slate-500 leading-relaxed">
            Tài khoản <strong>{currentUser?.fullName}</strong> có vai trò <strong>{roleLabelText}</strong> — thuộc ứng dụng
            Portal Học sinh/Phụ huynh/Trường liên kết, không phải ứng dụng Quản trị này. Vui lòng đăng nhập vào ứng dụng
            Portal để tiếp tục.
          </p>
        </div>

        <Button variant="dark" onClick={logout} className="mx-auto">
          <LogOut className="w-4 h-4" />
          <span>Đăng xuất</span>
        </Button>
      </div>
    </div>
  );
}
