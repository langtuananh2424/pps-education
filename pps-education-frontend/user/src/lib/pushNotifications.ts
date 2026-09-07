import { initializeApp, type FirebaseApp } from "firebase/app";
import { deleteToken, getMessaging, getToken, isSupported, onMessage, type Messaging } from "firebase/messaging";
import { apiRequest } from "./apiClient";

/** Tab đang mở (foreground) — bắn ra khi 1 push FCM tới, để NotificationBell tự refresh danh sách. */
export const PUSH_RECEIVED_EVENT = "pps:push-received";

/**
 * Kênh PUSH (FCM Web) — xem PushNotificationSender.java +
 * NotificationController#registerDeviceToken ở backend. Config Firebase Web
 * KHÔNG phải secret (được thiết kế để lộ ra client, xác thực thật nằm ở
 * Firebase Security Rules/backend service account) — an toàn khi đặt trong
 * VITE_* và bundle vào JS công khai.
 */
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY ?? "",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN ?? "",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID ?? "",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID ?? "",
  appId: import.meta.env.VITE_FIREBASE_APP_ID ?? ""
};

const vapidKey: string = import.meta.env.VITE_FIREBASE_VAPID_KEY ?? "";

const DEVICE_ID_KEY = "pps_device_id";

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07) — UUID định danh thiết bị vật lý,
 * sinh 1 lần rồi lưu localStorage (sống qua đóng/mở lại app, xem tokenStorage.ts). Backend dùng để
 * dedupe device_tokens đúng theo TỪNG THIẾT BỊ (NotificationService.registerDeviceToken) — 2 thiết
 * bị khác nhau cùng platform (VD 2 điện thoại Android) không giành nhau 1 "suất" push. Lưu ý: xoá
 * hẳn app/xoá site data sẽ sinh ID mới (không có cách nào định danh thiết bị bền hơn localStorage
 * từ web) — token cũ khi đó tự dọn qua cơ chế FCM báo UNREGISTERED có sẵn, chỉ trễ hơn dedupe tức thì.
 */
function getOrCreateDeviceId(): string {
  let id = localStorage.getItem(DEVICE_ID_KEY);
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem(DEVICE_ID_KEY, id);
  }
  return id;
}

/**
 * PWA Phase 1 (feat/pwa-phase1-app-like) đã cài vite-plugin-pwa (Workbox) —
 * tự đăng ký 1 service worker (mặc định sw.js) ở scope gốc "/". Nếu
 * firebase-messaging-sw.js cũng đăng ký ở "/", đăng ký sau sẽ THAY THẾ đăng
 * ký trước (2 SW không cùng tồn tại ở cùng 1 scope) — dẫn tới cache/update
 * PWA của Workbox bị vô hiệu hoá ngầm, không báo lỗi. Dùng scope riêng
 * (khuyến nghị chính thức của Firebase khi kết hợp với SW có sẵn) để 2 SW
 * độc lập nhau — FCM background message không cần kiểm soát toàn trang.
 */
const PUSH_SW_SCOPE = "/firebase-cloud-messaging-push-scope";

let app: FirebaseApp | null = null;
let messaging: Messaging | null = null;
/** Chặn đăng ký onMessage() nhiều lần (setupPushNotifications có thể gọi lại sau mỗi lần login). */
let foregroundListenerAttached = false;

export type PushSetupResult =
  | { status: "registered" }
  | { status: "unsupported" }
  /** iOS Safari chỉ cho phép Web Push khi đã "Thêm vào Màn hình chính" — xin quyền lúc chưa cài sẽ luôn thất bại. */
  | { status: "needs-ios-shortcut" }
  /**
   * detail: bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07) — ghi rõ giá trị
   * Notification.permission TRƯỚC/SAU khi gọi requestPermission() + có "user activation" hay không.
   * Cần để phân biệt 2 nguyên nhân hoàn toàn khác nhau mà trước đây bị gộp chung: người dùng THẬT SỰ
   * bấm từ chối ("denied"), hay iOS từ chối thẳng không thèm hiện dialog vì thiếu user gesture
   * ("default" — Apple bắt buộc requestPermission() phải gọi trực tiếp trong 1 thao tác chạm).
   */
  | { status: "permission-denied"; detail?: string }
  /**
   * Quyền chưa được cấp và luồng hiện tại KHÔNG có user gesture nên không được phép xin quyền — cần
   * người dùng bấm nút "Bật thông báo" (EnablePushBanner). Bổ sung ngoài SDD gốc 2026-09-07.
   */
  | { status: "needs-user-gesture"; detail?: string }
  | { status: "not-configured" }
  /** Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07) — permission ĐÃ granted nhưng getToken() vẫn trả về rỗng (không throw) — trước đây gộp chung nhầm vào "permission-denied", gây hiểu sai nguyên nhân khi debug qua log. */
  | { status: "token-unavailable" }
  /** Exception bất ngờ (VD getToken()/serviceWorker.register() lỗi trên Safari), trước đây bị nuốt hoàn toàn không dấu vết. */
  | { status: "error"; message: string };

function isConfigured(): boolean {
  return Boolean(firebaseConfig.apiKey && firebaseConfig.projectId && vapidKey);
}

function isIosNonStandalone(): boolean {
  const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent);
  const isStandalone =
    window.matchMedia("(display-mode: standalone)").matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true;
  return isIos && !isStandalone;
}

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07): trước đây hardcode "WEB" cho mọi
 * trình duyệt — khiến backend không dedupe được token theo đúng loại thiết bị (xem
 * NotificationService.registerDeviceToken), phát hiện qua debug push bị gửi trùng 2 lần trên iOS.
 * Khớp đúng 3 giá trị backend chấp nhận (DeviceTokenRequest: ANDROID|IOS|WEB).
 */
function detectPlatform(): "ANDROID" | "IOS" | "WEB" {
  const ua = navigator.userAgent;
  if (/android/i.test(ua)) return "ANDROID";
  if (/iphone|ipad|ipod/i.test(ua)) return "IOS";
  return "WEB";
}

async function getMessagingInstance(): Promise<Messaging | null> {
  if (messaging) return messaging;
  if (!(await isSupported())) return null;
  app ??= initializeApp(firebaseConfig);
  messaging = getMessaging(app);
  return messaging;
}

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07) — navigator.serviceWorker.register()
 * chỉ đảm bảo SW BẮT ĐẦU cài đặt, không đảm bảo đã ở trạng thái "active" ngay lúc đó (lần cài
 * shortcut mới hoàn toàn phải qua install→activate, dù rất nhanh nhưng có độ trễ). getToken() gọi
 * pushManager.subscribe() cần SW đã active — nghi vấn Safari khắt khe hơn Chrome ở điểm này, khiến
 * getToken() lặng lẽ trả về rỗng (không throw) đúng ở lần cài mới (phát hiện qua push_setup_logs:
 * status "permission-denied" dù user đã Allow — thực ra là getToken() fail, không phải do quyền).
 */
function waitForServiceWorkerActive(registration: ServiceWorkerRegistration): Promise<void> {
  if (registration.active) return Promise.resolve();
  const worker = registration.installing ?? registration.waiting;
  if (!worker) return Promise.resolve();
  return new Promise((resolve) => {
    // Timeout an toàn (5s) — tránh treo vô hạn nếu vì lý do gì đó "statechange" không bao giờ bắn.
    const timeoutId = setTimeout(resolve, 5000);
    worker.addEventListener("statechange", () => {
      if (worker.state === "activated") {
        clearTimeout(timeoutId);
        resolve();
      }
    });
  });
}

function serviceWorkerUrl(): string {
  // Service Worker là file tĩnh (public/), không đọc được import.meta.env — truyền config qua
  // query string, firebase-messaging-sw.js tự parse lại từ self.location.search.
  const params = new URLSearchParams(firebaseConfig);
  return `/firebase-messaging-sw.js?${params.toString()}`;
}

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07) — gửi kết quả setup push xuống
 * backend để tra được qua SQL (push_setup_logs) thay vì chỉ nuốt lỗi im lặng. Best-effort, không
 * bao giờ throw ra ngoài — bản thân việc log thất bại không được làm hỏng luồng login chính.
 */
function logPushSetupResult(result: PushSetupResult): void {
  const errorMessage =
    result.status === "error"
      ? result.message
      : result.status === "permission-denied" || result.status === "needs-user-gesture"
        ? result.detail
        : undefined;
  apiRequest("/notifications/push-setup-log", {
    method: "POST",
    body: JSON.stringify({ status: result.status, errorMessage, platform: detectPlatform() })
  }).catch(() => undefined);
}

/**
 * Gọi sau khi login thành công / mở lại app — đăng ký device token cho kênh PUSH nếu quyền ĐÃ được
 * cấp từ trước. KHÔNG xin quyền ở đây (xem enablePushFromUserGesture cho luồng xin quyền).
 *
 * Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07 — bằng chứng quyết định từ
 * push_setup_logs trên staging (iOS 16.7): dòng `before=granted after=denied userActivation=false`.
 * Tức là quyền ĐÃ LÀ "granted", nhưng vì gọi requestPermission() KHÔNG có user activation nên iOS
 * trả về "denied" GIẢ — chính là nguyên nhân gốc của toàn bộ ~40 lần thất bại trước đó (mọi fix
 * trước nhắm vào Service Worker/getToken đều vô nghĩa vì luồng chưa từng chạy tới đó).
 *
 * Vì vậy luồng TỰ ĐỘNG chỉ ĐỌC Notification.permission, tuyệt đối không gọi requestPermission():
 * - Đã "granted" → đăng ký bình thường.
 * - Chưa "granted" → dừng, trả "needs-user-gesture" để banner EnablePushBanner xin quyền qua nút bấm.
 *
 * Cũng KHÔNG teardown giữa các lần retry nữa: teardown gọi deleteToken(), đã từng xoá mất chính
 * subscription mà nút bấm vừa tạo thành công (log id 45 "registered" rồi id 46 "error" 3 giây sau).
 */
export async function setupPushNotifications(): Promise<PushSetupResult> {
  let result = await computeSetupPushNotifications();
  logPushSetupResult(result);

  // Chỉ retry cho lỗi tạm thời phía đăng ký (getToken/SW), không retry khi thiếu quyền — retry
  // không có user gesture chỉ tạo thêm "denied" giả, không bao giờ thành công.
  if (result.status === "token-unavailable" || result.status === "error") {
    await new Promise((resolve) => setTimeout(resolve, 3000));
    result = await computeSetupPushNotifications();
    logPushSetupResult(result);
  }

  return result;
}

function permissionDeniedResult(permissionBefore: NotificationPermission, permission: NotificationPermission,
                                 hadUserActivation: boolean | undefined): PushSetupResult {
  return {
    status: "permission-denied",
    detail: `before=${permissionBefore} after=${permission} userActivation=${hadUserActivation ?? "unknown"}`
  };
}

function currentUserActivation(): boolean | undefined {
  return (navigator as Navigator & { userActivation?: { isActive: boolean } }).userActivation?.isActive;
}

/**
 * Xin quyền + đăng ký device token NGAY TRONG 1 THAO TÁC CHẠM THẬT của người dùng (nút bấm) — bổ
 * sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-07.
 *
 * Apple BẮT BUỘC Notification.requestPermission() phải được gọi trực tiếp bên trong 1 sự kiện tương
 * tác của người dùng. Luồng tự động sau khi login (setupPushNotifications) gọi hàm này SAU nhiều
 * await (loginApi → fetchCurrentUser → ...) nên "user activation" đã hết hiệu lực, iOS từ chối thẳng
 * KHÔNG hiện dialog và trả về "default" — khớp đúng bằng chứng thực tế trên staging (mọi lần tự động
 * đều permission-denied). Vì vậy phải có đường xin quyền từ nút bấm như hàm này.
 *
 * ⚠️ Notification.requestPermission() PHẢI là lệnh await ĐẦU TIÊN — mọi await chèn trước nó đều làm
 * mất user activation trên iOS. Các kiểm tra đồng bộ (isConfigured/isIosNonStandalone) thì an toàn.
 */
export async function enablePushFromUserGesture(): Promise<PushSetupResult> {
  if (!isConfigured()) return { status: "not-configured" };
  if (isIosNonStandalone()) return { status: "needs-ios-shortcut" };

  const permissionBefore = Notification.permission;
  const hadUserActivation = currentUserActivation();
  // Đã "granted" thì KHÔNG gọi lại requestPermission() — trên iOS lệnh này có thể trả về "denied"
  // giả khi user activation đã hết hiệu lực, làm hỏng trạng thái đang đúng (xem ghi chú ở
  // setupPushNotifications). Chỉ gọi khi thật sự cần hiện dialog xin quyền lần đầu.
  const permission = permissionBefore === "granted" ? "granted" : await Notification.requestPermission();

  const result =
    permission !== "granted"
      ? permissionDeniedResult(permissionBefore, permission, hadUserActivation)
      : await registerAfterPermissionGranted();
  logPushSetupResult(result);
  return result;
}

async function computeSetupPushNotifications(): Promise<PushSetupResult> {
  if (!isConfigured()) return { status: "not-configured" };
  if (isIosNonStandalone()) return { status: "needs-ios-shortcut" };

  // Luồng tự động: CHỈ đọc trạng thái quyền, không bao giờ gọi requestPermission() (xem ghi chú ở
  // setupPushNotifications — gọi khi thiếu user gesture sẽ nhận "denied" giả trên iOS).
  if (Notification.permission !== "granted") {
    return {
      status: "needs-user-gesture",
      detail: `permission=${Notification.permission} userActivation=${currentUserActivation() ?? "unknown"}`
    };
  }

  return registerAfterPermissionGranted();
}

/** Phần đăng ký thật sự — chỉ chạy khi permission đã chắc chắn "granted" (dùng chung 2 luồng ở trên). */
async function registerAfterPermissionGranted(): Promise<PushSetupResult> {
  try {
    const messagingInstance = await getMessagingInstance();
    if (!messagingInstance) return { status: "unsupported" };

    // Trước đây có bước unregister() registration cũ ở đây (dựa trên giả thuyết SW context giữ
    // permission cũ) — ĐÃ GỠ 2026-09-07: giả thuyết đó sai (luồng chưa từng chạy tới đây, xem ghi chú
    // ở setupPushNotifications) và chính nó gây lỗi thật "Getting push subscription requires a
    // service worker" khi teardown/getToken chạy ngay sau đó mà không còn registration nào.
    const registration = await navigator.serviceWorker.register(serviceWorkerUrl(), { scope: PUSH_SW_SCOPE });
    await waitForServiceWorkerActive(registration);
    const token = await getToken(messagingInstance, { vapidKey, serviceWorkerRegistration: registration });
    if (!token) return { status: "token-unavailable" };

    await apiRequest("/notifications/device-token", {
      method: "POST",
      body: JSON.stringify({ token, platform: detectPlatform(), deviceId: getOrCreateDeviceId() })
    });

    /**
     * FCM chỉ tự gọi Service Worker (onBackgroundMessage trong firebase-messaging-sw.js) khi tab
     * KHÔNG ở foreground — lúc app đang mở, Firebase kỳ vọng code chính tự bắt bằng onMessage() rồi tự
     * hiển thị, nếu không sẽ KHÔNG có popup đẩy dù thông báo vẫn tới được backend/chuông. Dùng lại
     * registration đã đăng ký ở trên để showNotification() cho đồng nhất icon/badge với luồng nền.
     */
    if (!foregroundListenerAttached) {
      foregroundListenerAttached = true;
      onMessage(messagingInstance, (payload) => {
        const title = payload.notification?.title ?? "PPS Education";
        const body = payload.notification?.body ?? "";
        // /icon-192.png KHÔNG tồn tại (sửa 2026-09-07) — dùng pwa-192.png như firebase-messaging-sw.js.
        void registration.showNotification(title, { body, icon: "/pwa-192.png", badge: "/pwa-192.png" });
        window.dispatchEvent(new CustomEvent(PUSH_RECEIVED_EVENT));
      });
    }

    return { status: "registered" };
  } catch (err) {
    return { status: "error", message: err instanceof Error ? err.message : String(err) };
  }
}

/** Gọi lúc logout — hủy token khỏi FCM lẫn backend, best-effort (không chặn logout nếu lỗi). */
export async function teardownPushNotifications(): Promise<void> {
  try {
    const messagingInstance = await getMessagingInstance();
    if (!messagingInstance) return;
    const token = await getToken(messagingInstance, { vapidKey });
    if (!token) return;
    await deleteToken(messagingInstance);
    await apiRequest(`/notifications/device-token/${encodeURIComponent(token)}`, { method: "DELETE" });
  } catch {
    // best-effort
  }
}
