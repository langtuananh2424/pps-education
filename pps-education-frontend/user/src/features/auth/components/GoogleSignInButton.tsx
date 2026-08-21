import React from "react";
import { GoogleLogin } from "@react-oauth/google";
import { useTranslation } from "react-i18next";
import { useApp } from "@/context/AppContext";
import { ApiError } from "@/lib/apiClient";

interface GoogleSignInButtonProps {
  onSuccess: () => void;
  onError: (message: string) => void;
}

/**
 * UC-01 Main Flow bước 4 — Sign in with Google. A4: nếu email chưa khớp tài khoản
 * Phụ huynh/Học sinh nào đã được cấp phát, backend trả message "Vui lòng liên hệ
 * Quản trị viên" — hiện thẳng message đó, không tự viết lại khác đi.
 */
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
        // Xem giải thích ở admin/src/features/auth/components/GoogleSignInButton.tsx —
        // FedCM tránh lỗi treo ở accounts.google.com/gsi/transform khi trình duyệt chặn cookie bên thứ 3.
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
