import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import LoginForm from "../components/LoginForm";
import PublicLeaveRequestForm from "../components/PublicLeaveRequestForm";
import LoginHeroPanel from "../components/LoginHeroPanel";
import LanguageSwitcher from "@/components/ui/LanguageSwitcher";

export default function LoginPage() {
  const navigate = useNavigate();
  const { t } = useTranslation("auth");
  const [usernameOrEmail, setUsernameOrEmail] = useState("");
  const [showPublicForm, setShowPublicForm] = useState(false);

  const handleLoginSuccess = () => {
    navigate("/dashboard");
  };

  return (
    <div className="min-h-screen bg-[#FFF4EA] flex items-center justify-center p-4 md:p-8 font-sans select-none relative overflow-hidden">
      {/* Quầng màu nền trang trí — dùng radial-gradient thay vì filter:blur() (2026-08-08, đã xác nhận
          với người dùng: filter:blur()/backdrop-filter khiến Safari mobile (kể cả iPhone XS) phải
          recomposite vùng blur mỗi khi có repaint lân cận, gây gõ phím ở form đăng nhập bị trễ ~1s).
          radial-gradient cho hiệu ứng quầng sáng tương tự nhưng không tốn GPU vì không dùng filter. */}
      <div
        className="absolute top-[-10%] left-[-10%] w-96 h-96 rounded-full pointer-events-none"
        style={{ background: "radial-gradient(circle, rgba(254,215,170,0.35) 0%, rgba(254,215,170,0) 70%)" }}
      />
      <div
        className="absolute bottom-[-10%] right-[-10%] w-96 h-96 rounded-full pointer-events-none"
        style={{ background: "radial-gradient(circle, rgba(252,211,77,0.3) 0%, rgba(252,211,77,0) 70%)" }}
      />

      <div className="w-full max-w-[1000px] bg-white rounded-3xl shadow-[0_20px_50px_rgba(234,88,12,0.06)] border border-slate-100 overflow-hidden grid grid-cols-1 md:grid-cols-12 min-h-[600px] z-10 relative">
        <div className="md:col-span-6 p-8 md:p-14 flex flex-col justify-between bg-white">
          <div className="flex items-center justify-between gap-2.5">
            <div className="flex items-center gap-2.5">
              <div className="w-7 h-7 rounded-full bg-gradient-to-tr from-[#EA580C] to-[#FB923C] p-[1.5px] flex items-center justify-center shadow-sm">
                <div className="w-full h-full rounded-full bg-white flex items-center justify-center text-[#EA580C] font-display font-black text-xs">
                  P
                </div>
              </div>
              <span className="font-display font-extrabold text-xs text-slate-800 tracking-wider uppercase">{t("brand")}</span>
            </div>
            <LanguageSwitcher />
          </div>

          <div className="my-auto py-8 space-y-1">
            {showPublicForm ? (
              <PublicLeaveRequestForm onClose={() => setShowPublicForm(false)} />
            ) : (
              <>
                <LoginForm usernameOrEmail={usernameOrEmail} onUsernameOrEmailChange={setUsernameOrEmail} onLoginSuccess={handleLoginSuccess} />
              </>
            )}
          </div>

          <div className="text-[10px] text-slate-400 font-medium font-sans text-center">{t("footer")}</div>
        </div>

        <LoginHeroPanel />
      </div>
    </div>
  );
}
