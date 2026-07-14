import React, { createContext, useContext, useMemo, useState } from "react";
import { UserRole } from "@/types";
import { mockRolePermissions } from "@/data/mockData";
import { CurrentUserResponse, fetchCurrentUser, login as loginApi, logout as logoutApi } from "@/features/auth/api";
import { getAccessToken } from "@/lib/tokenStorage";
import { rolePriorityOrder } from "@/constants/roles";

const CURRENT_USER_CACHE_KEY = "pps_current_user";

interface AppContextValue {
  isLoggedIn: boolean;
  currentUser: CurrentUserResponse | null;
  currentRole: UserRole;
  selectedCampusId: string;
  sidebarOpen: boolean;
  setSelectedCampusId: (id: string) => void;
  setSidebarOpen: (open: boolean) => void;
  login: (usernameOrEmail: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hasPermission: (requiredPermission?: string) => boolean;
}

const AppContext = createContext<AppContextValue | null>(null);

function readCachedUser(): CurrentUserResponse | null {
  const saved = localStorage.getItem(CURRENT_USER_CACHE_KEY);
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

  const login = async (usernameOrEmail: string, password: string) => {
    await loginApi(usernameOrEmail, password);
    const profile = await fetchCurrentUser();
    localStorage.setItem(CURRENT_USER_CACHE_KEY, JSON.stringify(profile));
    setCurrentUser(profile);
    setCurrentRole(deriveCurrentRole(profile.roleCodes));
    setIsLoggedIn(true);
  };

  const logout = async () => {
    await logoutApi();
    localStorage.removeItem(CURRENT_USER_CACHE_KEY);
    setIsLoggedIn(false);
    setCurrentUser(null);
    setCurrentRole(UserRole.STUDENT);
  };

  const hasPermission = (requiredPermission?: string) => {
    if (!requiredPermission) return true;
    const roleConfig = mockRolePermissions.find((rp) => rp.role === currentRole);
    if (!roleConfig) return false;
    return roleConfig.permissions.includes(requiredPermission) || roleConfig.permissions.includes("system.admin");
  };

  const value = useMemo<AppContextValue>(
    () => ({
      isLoggedIn,
      currentUser,
      currentRole,
      selectedCampusId,
      sidebarOpen,
      setSelectedCampusId,
      setSidebarOpen,
      login,
      logout,
      hasPermission
    }),
    [isLoggedIn, currentUser, currentRole, selectedCampusId, sidebarOpen]
  );

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useApp must be used within AppProvider");
  return ctx;
}
