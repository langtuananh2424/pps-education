// Service Worker cho kênh PUSH (FCM Web) — xem src/lib/pushNotifications.ts.
// File tĩnh (Vite copy nguyên trạng từ public/, không chạy qua bundler) nên
// không đọc được import.meta.env — config Firebase được truyền qua query
// string lúc registration (serviceWorkerUrl() trong pushNotifications.ts).
importScripts("https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js");
importScripts("https://www.gstatic.com/firebasejs/10.14.1/firebase-messaging-compat.js");

const params = new URL(self.location.href).searchParams;
firebase.initializeApp({
  apiKey: params.get("apiKey"),
  authDomain: params.get("authDomain"),
  projectId: params.get("projectId"),
  messagingSenderId: params.get("messagingSenderId"),
  appId: params.get("appId")
});

const messaging = firebase.messaging();

/**
 * Chỉ xử lý khi tab đóng/ở nền — tab đang mở dùng onMessage() phía app (xem pushNotifications.ts).
 * Đã verify runtime thật hoạt động đúng trên CẢ Chrome/Android lẫn Safari/iOS 16 (background +
 * force-quit, 2026-09-05). Từng nghi ngờ Safari không tương thích format payload của
 * onBackgroundMessage() và thêm 1 self.addEventListener("push", ...) thủ công chạy song song làm
 * lưới an toàn — hóa ra SAI: nguyên nhân thật của lần thất bại trước đó chỉ là Service Worker cache
 * CŨ trên thiết bị test (fix bằng xóa hẳn shortcut + cài lại). Listener thủ công đó chạy song song
 * với onBackgroundMessage() bên dưới cho CÙNG 1 push → hiện đúp 2 thông báo trên iOS (dedupe bằng
 * tag=fcmMessageId không ăn vì Safari không có field này trong payload) — đã gỡ, giữ lại đúng 1
 * đường xử lý (onBackgroundMessage) như code gốc.
 */
messaging.onBackgroundMessage((payload) => {
  const title = payload.notification?.title ?? "PPS Education";
  const body = payload.notification?.body ?? "";
  self.registration.showNotification(title, {
    body,
    icon: "/icon-192.png",
    badge: "/icon-192.png"
  });
});
