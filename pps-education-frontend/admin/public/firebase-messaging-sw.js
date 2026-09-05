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
 * Cùng 1 logic hiển thị dùng ở cả 2 nơi bên dưới — tag lấy theo fcmMessageId (FCM luôn kèm field
 * này) để 2 lần showNotification() cho CÙNG 1 push (xem ghi chú "push" listener bên dưới) đè lên
 * nhau (Notification API: cùng tag = thay thế, không xếp chồng) thay vì hiện popup nhân đôi.
 */
function showFromPayload(payload) {
  const title = payload.notification?.title ?? "PPS Education";
  const body = payload.notification?.body ?? "";
  const tag = payload.fcmMessageId ?? payload.messageId ?? undefined;
  return self.registration.showNotification(title, { body, icon: "/icon-192.png", badge: "/icon-192.png", tag });
}

messaging.onBackgroundMessage((payload) => showFromPayload(payload));

/**
 * Bổ sung ngoài SDD gốc (đã xác nhận với người dùng 2026-09-05, xem cùng thay đổi ở app "user") —
 * Safari/iOS im lặng hoàn toàn khi app bị thoát hẳn dù backend đã gửi SENT thành công, nghi vấn
 * firebase-messaging-compat.js tự parse payload "push" event theo đúng format Chrome/FCM, còn
 * Safari nhận qua Web Push chuẩn (relay APNs) có thể lệch format khiến onBackgroundMessage() ở trên
 * không bao giờ được gọi. Bắt thẳng "push" event ở tầng thấp nhất làm lưới an toàn cross-browser.
 */
self.addEventListener("push", (event) => {
  if (!event.data) return;
  let payload;
  try {
    payload = event.data.json();
  } catch {
    return;
  }
  event.waitUntil(showFromPayload(payload));
});
