import React from "react";
import { GoogleLogin } from "@react-oauth/google";
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
  const { loginWithGoogle } = useApp();

  return (
    <div className="flex justify-center">
      <GoogleLogin
        theme="outline"
        size="large"
        shape="pill"
        text="continue_with"
        onSuccess={async (credentialResponse) => {
          if (!credentialResponse.credential) {
            onError("Không nhận được thông tin xác thực từ Google.");
            return;
          }
          try {
            await loginWithGoogle(credentialResponse.credential);
            onSuccess();
          } catch (err) {
            onError(err instanceof ApiError ? err.message : "Đăng nhập Google thất bại. Vui lòng thử lại.");
          }
        }}
        onError={() => onError("Đăng nhập Google thất bại. Vui lòng thử lại.")}
      />
    </div>
  );
}
