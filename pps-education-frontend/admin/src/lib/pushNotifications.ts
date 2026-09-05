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
  | { status: "not-configured" };

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

async function getMessagingInstance(): Promise<Messaging | null> {
  if (messaging) return messaging;
  if (!(await isSupported())) return null;
  app ??= initializeApp(firebaseConfig);
  messaging = getMessaging(app);
  return messaging;
}

function serviceWorkerUrl(): string {
  // Service Worker là file tĩnh (public/), không đọc được import.meta.env — truyền config qua
  // query string, firebase-messaging-sw.js tự parse lại từ self.location.search.
  const params = new URLSearchParams(firebaseConfig);
  return `/firebase-messaging-sw.js?${params.toString()}`;
}

/** Gọi sau khi login thành công — xin quyền + đăng ký device token cho kênh PUSH. */
export async function setupPushNotifications(): Promise<PushSetupResult> {
  if (!isConfigured()) return { status: "not-configured" };
  if (isIosNonStandalone()) return { status: "needs-ios-shortcut" };

  const messagingInstance = await getMessagingInstance();
  if (!messagingInstance) return { status: "unsupported" };

  const permission = await Notification.requestPermission();
  if (permission !== "granted") return { status: "permission-denied" };

  const registration = await navigator.serviceWorker.register(serviceWorkerUrl(), { scope: PUSH_SW_SCOPE });
  const token = await getToken(messagingInstance, { vapidKey, serviceWorkerRegistration: registration });
  if (!token) return { status: "permission-denied" };

  await apiRequest("/notifications/device-token", {
    method: "POST",
    body: JSON.stringify({ token, platform: "WEB" })
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
