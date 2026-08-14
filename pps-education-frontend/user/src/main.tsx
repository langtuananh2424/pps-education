import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { GoogleOAuthProvider } from "@react-oauth/google";
import { useTranslation } from "react-i18next";
import App from "./App.tsx";
import "./index.css";
import "./i18n";

function Root() {
  // key={i18n.language} buộc GoogleOAuthProvider unmount/remount để tải lại script
  // accounts.google.com/gsi/client?hl=... theo đúng ngôn ngữ đang chọn — bản thân
  // GoogleOAuthProvider không tự reload script khi prop locale đổi (chỉ phụ thuộc nonce).
  const { i18n } = useTranslation();
  return (
    <GoogleOAuthProvider
      key={i18n.language}
      clientId={import.meta.env.VITE_GOOGLE_CLIENT_ID || ""}
      locale={i18n.language === "en" ? "en" : "vi"}
    >
      <App />
    </GoogleOAuthProvider>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Root />
  </StrictMode>
);
