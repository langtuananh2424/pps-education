import React, { createContext, useContext, useEffect, useMemo, useState } from "react";
import {
  CurrentUserResponse,
  fetchCurrentUser,
  login as loginApi,
  loginWithGoogle as loginWithGoogleApi,
  logout as logoutApi
} from "@/features/auth/api";
import { getAccessToken } from "@/lib/tokenStorage";
import { setupPushNotifications, teardownPushNotifications } from "@/lib/pushNotifications";

const CURRENT_USER_CACHE_KEY = "pps_portal_current_user";

interface AppContextValue {
  isLoggedIn: boolean;
  currentUser: CurrentUserResponse | null;
  isParent: boolean;
  isStudent: boolean;
  login: (usernameOrEmail: string, password: string) => Promise<void>;
  loginWithGoogle: (idToken: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AppContext = createContext<AppContextValue | null>(null);

/**
 * localStorage (đổi từ sessionStorage cùng lúc với tokenStorage.ts — xem ghi chú ở đó) — nếu chỉ
 * đổi token mà bỏ quên cache này, mở lại shortcut sẽ còn đăng nhập (isLoggedIn=true nhờ token) nhưng
 * currentUser=null cho tới khi có API call nào đó vô tình trigger refetch, khiến PortalPage hiển thị
 * sai vai trò/tên trong lúc chờ (isParent/isStudent suy ra từ currentUser?.roleCodes).
 */
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

  /**
   * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07) — trước đây setupPushNotifications()
   * CHỈ chạy trong completeLogin() (lúc gọi API đăng nhập). Từ khi có "nhớ đăng nhập" (localStorage,
   * xem tokenStorage.ts), mở lại shortcut với phiên đã đăng nhập sẵn KHÔNG đi qua completeLogin() nữa
   * — nếu lần đăng ký push tại thời điểm login đó thất bại (VD timing quirk của WebKit lúc PWA vừa
   * cài, xem push_setup_logs), người dùng vĩnh viễn không có cơ hội thử lại trừ khi tự đăng xuất/đăng
   * nhập lại thủ công. Tự thử lại mỗi khi MỞ APP mà đã sẵn đăng nhập — đồng thời đây là 1 lần tải
   * trang HOÀN TOÀN MỚI (không phải retry trong cùng session như trong pushNotifications.ts), khớp
   * đúng bằng chứng thực tế: tải lại trang mới luôn thành công hơn retry trong cùng phiên cũ.
   * Gọi teardownPushNotifications() trước (giống hệt completeLogin()) — bằng chứng từ push_setup_logs
   * cho thấy đây mới là mấu chốt: chỉ luồng nào chạy deleteToken() (huỷ hẳn PushSubscription cũ)
   * trước khi setup lại mới thành công, vì lần getToken() hỏng đầu tiên để lại 1 subscription hỏng
   * mà mọi lần thử sau đều vướng phải (xem ghi chú chi tiết ở setupPushNotifications()).
   */
  useEffect(() => {
    if (isLoggedIn) {
      teardownPushNotifications()
        .then(() => setupPushNotifications())
        .catch(() => undefined);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const completeLogin = async () => {
    const profile = await fetchCurrentUser();
    localStorage.setItem(CURRENT_USER_CACHE_KEY, JSON.stringify(profile));
    setCurrentUser(profile);
    setIsLoggedIn(true);
    // Huỷ token FCM cũ (nếu thiết bị này vừa đăng nhập tài khoản khác mà chưa
    // logout) trước khi đăng ký token mới — ép Firebase cấp token mới thay vì
    // tái sử dụng token cache, tránh 1 thiết bị nhận push của nhiều tài khoản
    // cùng lúc. Fire-and-forget — không chặn luồng login nếu trình duyệt không
    // hỗ trợ/từ chối quyền notification (VD Safari iOS chưa "Thêm vào Màn hình
    // chính", xem pushNotifications.ts). Push chỉ là 1 trong 3 kênh (Email
    // luôn bật sẵn).
    teardownPushNotifications()
      .then(() => setupPushNotifications())
      .catch(() => undefined);
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
    await teardownPushNotifications();
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
      loginWithGoogle,
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
