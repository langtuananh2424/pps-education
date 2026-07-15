import React from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useApp } from "@/context/AppContext";
import { adminAppRoles } from "@/constants/roles";
import { UserRole } from "@/types";
import { findRequiredPermissionForPath } from "@/constants/navigation";
import WrongPortalPage from "@/features/auth/pages/WrongPortalPage";

export default function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isLoggedIn, currentUser, hasPermission } = useApp();
  const location = useLocation();
  if (!isLoggedIn) return <Navigate to="/login" replace />;

  const belongsToAdminApp = currentUser?.roleCodes.some((code) => adminAppRoles.includes(code as UserRole)) ?? true;
  if (!belongsToAdminApp) return <WrongPortalPage />;

  // Chặn truy cập trực tiếp qua URL tới trang không có quyền — Sidebar giờ chỉ ẩn mục đó đi, không còn icon khóa để click.
  const requiredPermission = findRequiredPermissionForPath(location.pathname);
  if (requiredPermission && !hasPermission(requiredPermission)) {
    return <Navigate to="/access-denied" state={{ label: location.pathname }} replace />;
  }

  return <>{children}</>;
}
