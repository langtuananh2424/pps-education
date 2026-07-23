import React from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useApp } from "@/context/AppContext";
import { portalOnlyRoles } from "@/constants/roles";
import { UserRole } from "@/types";
import { findNavItemForPath, isNavItemAllowed } from "@/constants/navigation";
import WrongPortalPage from "@/features/auth/pages/WrongPortalPage";

export default function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isLoggedIn, currentUser, hasPermission } = useApp();
  const location = useLocation();
  if (!isLoggedIn) return <Navigate to="/login" replace />;

  // Chặn theo blocklist (role CHỈ dùng cho Portal), không phải allowlist — vai trò tùy biến tạo qua
  // UC-03 (role code bất kỳ, không nằm trong enum UserRole cố định) phải luôn vào được Admin, vì đó
  // là nơi duy nhất tạo/gán vai trò tùy biến. Allowlist cũ (adminAppRoles) từng chặn nhầm các tài
  // khoản này — xem ghi chú tại constants/roles.ts.
  const belongsToAdminApp = currentUser?.roleCodes.some((code) => !portalOnlyRoles.includes(code as UserRole)) ?? true;
  if (!belongsToAdminApp) return <WrongPortalPage />;

  // Chặn truy cập trực tiếp qua URL tới trang không có quyền — Sidebar giờ chỉ ẩn mục đó đi, không còn icon khóa để click.
  const navItem = findNavItemForPath(location.pathname);
  if (navItem && !isNavItemAllowed(navItem, currentUser?.roleCodes ?? [], hasPermission)) {
    return <Navigate to="/access-denied" state={{ label: location.pathname }} replace />;
  }

  return <>{children}</>;
}
