import React from "react";
import { LogOut, School } from "lucide-react";
import { Trans, useTranslation } from "react-i18next";
import { useApp } from "@/context/AppContext";
import { roleLabel } from "@/constants/roles";
import { UserRole } from "@/types";
import Button from "@/components/ui/Button";

/** Chặn tài khoản STUDENT/PARENT/PARTNER_REP đăng nhập nhầm vào app Admin — 3 role này thuộc app Portal (`user/`), xem plan kiến trúc 2 app. */
export default function WrongPortalPage() {
  const { currentUser, logout } = useApp();
  const { t } = useTranslation(["auth", "layout"]);

  const roleLabelText = currentUser?.roleCodes.map((code) => roleLabel(t, code as UserRole) ?? code).join(", ") ?? "";

  return (
    <div className="min-h-screen flex items-center justify-center bg-brand-bg p-6">
      <div className="bg-white p-8 rounded-xl border border-slate-200 shadow-soft max-w-md w-full text-center space-y-6 animate-in fade-in duration-200">
        <div className="w-16 h-16 bg-orange-50 rounded-full flex items-center justify-center text-brand-red mx-auto shadow-sm">
          <School className="w-8 h-8" />
        </div>

        <div className="space-y-2">
          <h2 className="text-lg font-bold text-slate-900 font-display">{t("wrongPortal.title")}</h2>
          <p className="text-xs text-slate-500 leading-relaxed">
            <Trans
              i18nKey="wrongPortal.description"
              t={t}
              values={{ fullName: currentUser?.fullName, roleLabels: roleLabelText }}
              components={{ b1: <strong />, b2: <strong /> }}
            />
          </p>
        </div>

        <Button variant="dark" onClick={logout} className="mx-auto">
          <LogOut className="w-4 h-4" />
          <span>{t("wrongPortal.logout")}</span>
        </Button>
      </div>
    </div>
  );
}
