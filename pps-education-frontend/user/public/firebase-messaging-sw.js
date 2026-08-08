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

// Chỉ xử lý khi tab đóng/ở nền — tab đang mở dùng onMessage() phía app (chưa
// cần vì Portal chưa có toast in-app cho push, chỉ mới có NotificationBell
// đọc qua REST GET /notifications).
messaging.onBackgroundMessage((payload) => {
  const title = payload.notification?.title ?? "PPS Education";
  const body = payload.notification?.body ?? "";
  self.registration.showNotification(title, {
    body,
    icon: "/icon-192.png",
    badge: "/icon-192.png"
  });
});
