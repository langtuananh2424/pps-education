import React from "react";
import { Navigate } from "react-router-dom";
import { useApp } from "@/context/AppContext";

export default function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isLoggedIn } = useApp();
  if (!isLoggedIn) return <Navigate to="/login" replace />;
  return <>{children}</>;
}
