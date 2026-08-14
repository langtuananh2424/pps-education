import React from "react";
import { GoogleLogin } from "@react-oauth/google";
import { useTranslation } from "react-i18next";
import { useApp } from "@/context/AppContext";
import { ApiError } from "@/lib/apiClient";

interface GoogleSignInButtonProps {
  onSuccess: () => void;
  onError: (message: string) => void;
}

/** UC-01 Main Flow bước 4 — Sign in with Google, trả về id_token (credential) gửi thẳng cho backend verify. */
export default function GoogleSignInButton({ onSuccess, onError }: GoogleSignInButtonProps) {
  const { t } = useTranslation("auth");
  const { loginWithGoogle } = useApp();

  return (
    <div className="flex justify-center">
      <GoogleLogin
        theme="outline"
        size="large"
        shape="pill"
        text="continue_with"
        // Chế độ popup mặc định (ux_mode="popup") dựa vào 1 trang relay
        // (accounts.google.com/gsi/transform) đọc cookie bên thứ 3 để trả
        // credential về cho tab gốc — trình duyệt chặn cookie bên thứ 3
        // (mặc định ở Safari/Brave, đang rollout ở Chrome) làm trang này
        // treo vĩnh viễn sau khi chọn tài khoản. FedCM (trình duyệt làm
        // trung gian trực tiếp, không qua cookie/relay) là hướng thay thế
        // Google khuyến nghị, tránh hẳn lỗi treo này.
        use_fedcm_for_button
        onSuccess={async (credentialResponse) => {
          if (!credentialResponse.credential) {
            onError(t("errors.googleNoCredential"));
            return;
          }
          try {
            await loginWithGoogle(credentialResponse.credential);
            onSuccess();
          } catch (err) {
            onError(err instanceof ApiError ? err.message : t("errors.googleFailed"));
          }
        }}
        onError={() => onError(t("errors.googleFailed"))}
      />
    </div>
  );
}
