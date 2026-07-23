import React, { createContext, useContext, useMemo, useState } from "react";
import { UserRole } from "@/types";
import { CurrentUserResponse, fetchCurrentUser, login as loginApi, loginWithGoogle as loginWithGoogleApi, logout as logoutApi } from "@/features/auth/api";
import { getAccessToken } from "@/lib/tokenStorage";
import { deriveCurrentRoleLabel, rolePriorityOrder } from "@/constants/roles";

const CURRENT_USER_CACHE_KEY = "pps_current_user";

interface AppContextValue {
  isLoggedIn: boolean;
  currentUser: CurrentUserResponse | null;
  currentRole: UserRole;
  /** Nhãn hiển thị Header/Sidebar — khác currentRole (enum cố định) vì hỗ trợ đúng cả vai trò tùy biến tạo qua UC-03. */
  currentRoleLabel: string;
  selectedCampusId: string;
  sidebarOpen: boolean;
  setSelectedCampusId: (id: string) => void;
  setSidebarOpen: (open: boolean) => void;
  loginNotice: string | null;
  login: (usernameOrEmail: string, password: string) => Promise<void>;
  loginWithGoogle: (idToken: string) => Promise<void>;
  logout: () => Promise<void>;
  hasPermission: (requiredPermission?: string) => boolean;
}

const AppContext = createContext<AppContextValue | null>(null);

function readCachedUser(): CurrentUserResponse | null {
  const saved = sessionStorage.getItem(CURRENT_USER_CACHE_KEY);
  try {
    return saved ? (JSON.parse(saved) as CurrentUserResponse) : null;
  } catch {
    return null;
  }
}

/** Nhiều roleCodes có thể áp dụng cho 1 user — chọn role ưu tiên cao nhất để hiển thị Sidebar/Header chính. */
function deriveCurrentRole(roleCodes: string[]): UserRole {
  const matched = rolePriorityOrder.find((role) => roleCodes.includes(role));
  return matched ?? UserRole.STUDENT;
}

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [isLoggedIn, setIsLoggedIn] = useState(() => !!getAccessToken());
  const [currentUser, setCurrentUser] = useState<CurrentUserResponse | null>(() => readCachedUser());
  const [currentRole, setCurrentRole] = useState<UserRole>(() => {
    const cached = readCachedUser();
    return cached ? deriveCurrentRole(cached.roleCodes) : UserRole.STUDENT;
  });
  const [selectedCampusId, setSelectedCampusId] = useState("ALL");
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [loginNotice, setLoginNotice] = useState<string | null>(null);

  const completeLogin = async () => {
    const profile = await fetchCurrentUser();
    sessionStorage.setItem(CURRENT_USER_CACHE_KEY, JSON.stringify(profile));
    setCurrentUser(profile);
    setCurrentRole(deriveCurrentRole(profile.roleCodes));
    setIsLoggedIn(true);
    setLoginNotice(`Đăng nhập thành công! Chào mừng trở lại, ${profile.fullName}.`);
    setTimeout(() => setLoginNotice(null), 4000);
  };

  const login = async (usernameOrEmail: string, password: string) => {
    await loginApi(usernameOrEmail, password);
    await completeLogin();
  };

  const loginWithGoogle = async (idToken: string) => {
    await loginWithGoogleApi(idToken);
    await completeLogin();
  };

  const logout = async () => {
    await logoutApi();
    sessionStorage.removeItem(CURRENT_USER_CACHE_KEY);
    setIsLoggedIn(false);
    setCurrentUser(null);
    setCurrentRole(UserRole.STUDENT);
  };

  const hasPermission = (requiredPermission?: string) => {
    if (!requiredPermission) return true;
    // Tra thẳng CurrentUserResponse.permissions (effective permissions thật từ BE — hợp nhất role + override,
    // xem GET /api/auth/me) — không còn dùng bảng mock tĩnh, tránh lệch với quyền thật cấu hình qua UC-03/UC-04.
    return currentUser?.permissions?.includes(requiredPermission) ?? false;
  };

  const currentRoleLabel = currentUser ? deriveCurrentRoleLabel(currentUser.roleCodes) : deriveCurrentRoleLabel([]);

  const value = useMemo<AppContextValue>(
    () => ({
      isLoggedIn,
      currentUser,
      currentRole,
      currentRoleLabel,
      selectedCampusId,
      sidebarOpen,
      setSelectedCampusId,
      setSidebarOpen,
      loginNotice,
      login,
      loginWithGoogle,
      logout,
      hasPermission
    }),
    [isLoggedIn, currentUser, currentRole, currentRoleLabel, selectedCampusId, sidebarOpen, loginNotice]
  );

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useApp must be used within AppProvider");
  return ctx;
}
