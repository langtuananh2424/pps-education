import { initializeApp, type FirebaseApp } from "firebase/app";
import { deleteToken, getMessaging, getToken, isSupported, onMessage, type Messaging } from "firebase/messaging";
import { apiRequest } from "./apiClient";

/** Tab đang mở (foreground) — bắn ra khi 1 push FCM tới, để bell thông báo ở Header tự refresh danh sách. */
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
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07, xem cùng thay đổi ở app "user") —
 * UUID định danh thiết bị vật lý, sinh 1 lần rồi lưu localStorage. Backend dùng để dedupe
 * device_tokens đúng theo TỪNG THIẾT BỊ (NotificationService.registerDeviceToken) — 2 thiết bị khác
 * nhau cùng platform không giành nhau 1 "suất" push.
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
 * Scope riêng cho SW của FCM (không dùng scope gốc "/") — nếu app admin sau
 * này cũng thêm PWA/Workbox (như app user, xem feat/pwa-phase1-app-like),
 * 2 service worker đăng ký cùng scope "/" sẽ ghi đè lẫn nhau (SW đăng ký sau
 * thay thế SW trước, không cùng tồn tại). Giữ nhất quán ngay từ đầu để
 * không phải sửa lại khi PWA lan sang app này.
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
  | { status: "permission-denied" }
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
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07, xem cùng thay đổi ở app "user"):
 * trước đây hardcode "WEB" cho mọi trình duyệt — khiến backend không dedupe được token theo đúng
 * loại thiết bị (NotificationService.registerDeviceToken), phát hiện qua debug push gửi trùng trên
 * iOS. Khớp đúng 3 giá trị backend chấp nhận (DeviceTokenRequest: ANDROID|IOS|WEB).
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
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07, xem cùng thay đổi ở app "user") —
 * navigator.serviceWorker.register() chỉ đảm bảo SW BẮT ĐẦU cài đặt, không đảm bảo đã "active" ngay
 * lúc đó. getToken() gọi pushManager.subscribe() cần SW đã active — nghi vấn Safari khắt khe hơn
 * Chrome ở điểm này, khiến getToken() lặng lẽ trả về rỗng (không throw) đúng ở lần cài mới.
 */
function waitForServiceWorkerActive(registration: ServiceWorkerRegistration): Promise<void> {
  if (registration.active) return Promise.resolve();
  const worker = registration.installing ?? registration.waiting;
  if (!worker) return Promise.resolve();
  return new Promise((resolve) => {
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
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-07, xem cùng thay đổi ở app "user") —
 * gửi kết quả setup push xuống backend để tra được qua SQL (push_setup_logs) thay vì chỉ nuốt lỗi
 * im lặng. Best-effort, không bao giờ throw ra ngoài.
 */
function logPushSetupResult(result: PushSetupResult): void {
  const errorMessage = result.status === "error" ? result.message : undefined;
  apiRequest("/notifications/push-setup-log", {
    method: "POST",
    body: JSON.stringify({ status: result.status, errorMessage, platform: detectPlatform() })
  }).catch(() => undefined);
}

/**
 * Gọi sau khi login thành công — xin quyền + đăng ký device token cho kênh PUSH.
 * Tự thử lại 1 lần sau 3s nếu lần đầu thất bại — bổ sung ngoài SDD gốc (đã xác nhận với người dùng
 * 2026-09-07, xem cùng thay đổi ở app "user"): ngay sau khi cài shortcut mới hoàn toàn (cold start),
 * Notification.requestPermission() có thể trả về "permission-denied" dù OS ĐÃ cấp quyền thật —
 * đăng nhập lại lần 2 (không cần bấm Allow lại) luôn thành công ngay. Tự retry để không bắt người
 * dùng phải đăng nhập 2 lần.
 */
export async function setupPushNotifications(): Promise<PushSetupResult> {
  let result = await computeSetupPushNotifications();
  logPushSetupResult(result);
  if (result.status !== "registered") {
    await new Promise((resolve) => setTimeout(resolve, 3000));
    result = await computeSetupPushNotifications();
    logPushSetupResult(result);
  }
  return result;
}

async function computeSetupPushNotifications(): Promise<PushSetupResult> {
  try {
    if (!isConfigured()) return { status: "not-configured" };
    if (isIosNonStandalone()) return { status: "needs-ios-shortcut" };

    const messagingInstance = await getMessagingInstance();
    if (!messagingInstance) return { status: "unsupported" };

    const permission = await Notification.requestPermission();
    if (permission !== "granted") return { status: "permission-denied" };

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
     * hiển thị, nếu không sẽ KHÔNG có popup đẩy dù thông báo vẫn tới được backend/bell. Dùng lại
     * registration đã đăng ký ở trên để showNotification() cho đồng nhất icon/badge với luồng nền.
     */
    if (!foregroundListenerAttached) {
      foregroundListenerAttached = true;
      onMessage(messagingInstance, (payload) => {
        const title = payload.notification?.title ?? "PPS Education";
        const body = payload.notification?.body ?? "";
        void registration.showNotification(title, { body, icon: "/icon-192.png", badge: "/icon-192.png" });
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
