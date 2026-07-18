import React, { createContext, useContext, useMemo, useState } from "react";
import { CurrentUserResponse, fetchCurrentUser, login as loginApi, logout as logoutApi } from "@/features/auth/api";
import { getAccessToken } from "@/lib/tokenStorage";

const CURRENT_USER_CACHE_KEY = "pps_portal_current_user";

interface AppContextValue {
  isLoggedIn: boolean;
  currentUser: CurrentUserResponse | null;
  isParent: boolean;
  isStudent: boolean;
  login: (usernameOrEmail: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
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

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [isLoggedIn, setIsLoggedIn] = useState(() => !!getAccessToken());
  const [currentUser, setCurrentUser] = useState<CurrentUserResponse | null>(() => readCachedUser());

  const login = async (usernameOrEmail: string, password: string) => {
    await loginApi(usernameOrEmail, password);
    const profile = await fetchCurrentUser();
    localStorage.setItem(CURRENT_USER_CACHE_KEY, JSON.stringify(profile));
    setCurrentUser(profile);
    setIsLoggedIn(true);
  };

  const logout = async () => {
    await logoutApi();
    localStorage.removeItem(CURRENT_USER_CACHE_KEY);
    setIsLoggedIn(false);
    setCurrentUser(null);
  };

  const value = useMemo<AppContextValue>(
    () => ({
      isLoggedIn,
      currentUser,
      isParent: currentUser?.roleCodes?.includes("PARENT") ?? false,
      isStudent: currentUser?.roleCodes?.includes("STUDENT") ?? false,
      login,
      logout
    }),
    [isLoggedIn, currentUser]
  );

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useApp must be used within AppProvider");
  return ctx;
}
