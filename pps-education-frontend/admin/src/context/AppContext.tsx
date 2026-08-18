import React, { createContext, useContext, useMemo, useRef, useState } from "react";
import { UserRole } from "@/types";
import { CurrentUserResponse, fetchCurrentUser, login as loginApi, loginWithGoogle as loginWithGoogleApi, logout as logoutApi } from "@/features/auth/api";
import { getAccessToken } from "@/lib/tokenStorage";
import { setupPushNotifications, teardownPushNotifications } from "@/lib/pushNotifications";
import { deriveCurrentRoleLabel, rolePriorityOrder } from "@/constants/roles";

const CURRENT_USER_CACHE_KEY = "pps_current_user";

/** Bổ sung 2026-08-17 — kết quả gọi "Lưu tạm & rời đi": Sidebar cần biết CÓ lưu được không (điều
 *  hướng đi hay ở lại) và VÌ SAO nếu không (hiện thẳng trong popup xác nhận — luôn nổi giữa màn hình
 *  qua portal, không phụ thuộc vị trí cuộn trang, khác banner lỗi tĩnh trên trang dễ bị bỏ lỡ). */
export interface UnsavedSaveResult {
  ok: boolean;
  /** Lý do cụ thể khi ok=false — Sidebar hiện trực tiếp trong popup. Bỏ trống thì Sidebar tự hiện câu chung chung. */
  message?: string;
}

interface AppContextValue {
  isLoggedIn: boolean;
  currentUser: CurrentUserResponse | null;
  currentRole: UserRole;
  /** Nhãn hiển thị Header/Sidebar — khác currentRole (enum cố định) vì hỗ trợ đúng cả vai trò tùy biến tạo qua UC-03. */
  currentRoleLabel: string;
  selectedCampusId: string;
  /** Lớp đang chọn ở Header (cạnh Điểm trường) — dùng chung cho mọi trang cần lọc theo lớp (Sổ điểm, Điểm danh, Giao đề, Nhận xét, Kho bài giảng...), không phải chọn lại mỗi trang. */
  selectedClassId: number | null;
  sidebarOpen: boolean;
  setSelectedCampusId: (id: string) => void;
  setSelectedClassId: (id: number | null) => void;
  setSidebarOpen: (open: boolean) => void;
  loginNotice: string | null;
  login: (usernameOrEmail: string, password: string) => Promise<void>;
  loginWithGoogle: (idToken: string) => Promise<void>;
  logout: () => Promise<void>;
  hasPermission: (requiredPermission?: string) => boolean;
  /** Trang hiện tại (VD Nhận xét học viên) đang có dữ liệu nhập dở chưa lưu — Sidebar dùng để chặn điều hướng + hỏi xác nhận. */
  hasUnsavedChanges: boolean;
  /** Trang có dữ liệu dở gọi khi bắt đầu/kết thúc trạng thái dirty; truyền kèm hàm lưu tạm để Sidebar gọi khi người dùng chọn "Lưu tạm & rời đi" — xem UnsavedSaveResult. */
  setUnsavedChanges: (active: boolean, onSaveDraft?: (() => Promise<UnsavedSaveResult>) | null) => void;
  /**
   * Sidebar gọi khi người dùng xác nhận lưu tạm trước khi rời trang. ok=true thì an toàn điều hướng
   * đi (lưu thành công, hoặc trang không đăng ký hàm lưu nào); ok=false thì Sidebar PHẢI ở lại popup
   * và hiện message cho người dùng thấy ngay — không tự điều hướng đi (bug đã xác nhận 2026-08-17,
   * trước đây luôn điều hướng bất kể kết quả, làm mất âm thầm dữ liệu không lưu được).
   */
  saveUnsavedChanges: () => Promise<UnsavedSaveResult>;
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
  const [selectedCampusId, setSelectedCampusIdState] = useState("ALL");
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // Đổi điểm trường thì lớp đang chọn (thuộc site cũ) không còn hợp lệ — reset để tránh lọc nhầm.
  const setSelectedCampusId = (id: string) => {
    setSelectedCampusIdState(id);
    setSelectedClassId(null);
  };
  const [loginNotice, setLoginNotice] = useState<string | null>(null);

  // "Cảnh báo rời trang khi chưa lưu" (2026-08-15, bổ sung ngoài SDD gốc) — dùng chung cho mọi trang có
  // form dở dang (hiện chỉ Nhận xét học viên đăng ký). Hàm lưu tạm giữ ở ref (không phải state) vì nó
  // đổi theo từng lần gõ phím của trang con — không cần re-render Sidebar mỗi lần đổi, chỉ cần đọc đúng
  // bản mới nhất tại thời điểm người dùng bấm "Lưu tạm & rời đi".
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  const saveUnsavedChangesRef = useRef<(() => Promise<UnsavedSaveResult>) | null>(null);
  const setUnsavedChanges = (active: boolean, onSaveDraft?: (() => Promise<UnsavedSaveResult>) | null) => {
    saveUnsavedChangesRef.current = active ? onSaveDraft ?? null : null;
    setHasUnsavedChanges(active);
  };
  const saveUnsavedChanges = async (): Promise<UnsavedSaveResult> => {
    // Không có hàm nào đăng ký (hiếm, race giữa lúc dirty vừa tắt) — không có gì để chặn, cho điều hướng đi.
    return (await saveUnsavedChangesRef.current?.()) ?? { ok: true };
  };

  const completeLogin = async () => {
    const profile = await fetchCurrentUser();
    sessionStorage.setItem(CURRENT_USER_CACHE_KEY, JSON.stringify(profile));
    setCurrentUser(profile);
    setCurrentRole(deriveCurrentRole(profile.roleCodes));
    setIsLoggedIn(true);
    setLoginNotice(`Đăng nhập thành công! Chào mừng trở lại, ${profile.fullName}.`);
    setTimeout(() => setLoginNotice(null), 4000);
    // Fire-and-forget — không chặn luồng login nếu trình duyệt không hỗ trợ/từ chối
    // quyền notification (VD Safari iOS chưa "Thêm vào Màn hình chính", xem
    // pushNotifications.ts). Push chỉ là 1 trong 3 kênh (Email luôn bật sẵn).
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
      selectedClassId,
      sidebarOpen,
      setSelectedCampusId,
      setSelectedClassId,
      setSidebarOpen,
      loginNotice,
      login,
      loginWithGoogle,
      logout,
      hasPermission,
      hasUnsavedChanges,
      setUnsavedChanges,
      saveUnsavedChanges
    }),
    [isLoggedIn, currentUser, currentRole, currentRoleLabel, selectedCampusId, selectedClassId, sidebarOpen, loginNotice, hasUnsavedChanges]
  );

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useApp must be used within AppProvider");
  return ctx;
}
