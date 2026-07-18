import React from "react";
import { AppProvider, useApp } from "@/context/AppContext";
import LoginPage from "@/features/auth/pages/LoginPage";
import PortalPage from "@/features/portal/pages/PortalPage";

function Shell() {
  const { isLoggedIn } = useApp();
  return isLoggedIn ? <PortalPage /> : <LoginPage />;
}

export default function App() {
  return (
    <AppProvider>
      <Shell />
    </AppProvider>
  );
}
