import fs from "fs";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import path from "path";
import { defineConfig } from "vite";
import { VitePWA } from "vite-plugin-pwa";

// SPIKE (2026-08-25) — test getUserMedia (ghi âm speaking, Video phản xạ) trên iPhone thật qua LAN.
// iOS/WebKit chỉ cho phép navigator.mediaDevices ở "secure context" (HTTPS/localhost) — HTTP thường
// qua IP LAN sẽ có navigator.mediaDevices === undefined, không phải do thiếu quyền micro. Cert tự ký
// bằng mkcert (không commit — xem .gitignore), chỉ dùng máy dev. Không có cert thì rơi lại HTTP thường.
const certDir = path.resolve(__dirname, ".certs");
const httpsConfig = fs.existsSync(path.join(certDir, "localhost+2.pem"))
  ? {
      key: fs.readFileSync(path.join(certDir, "localhost+2-key.pem")),
      cert: fs.readFileSync(path.join(certDir, "localhost+2.pem"))
    }
  : undefined;

// iOS Safari/Edge CHỈ nhận diện file này là "hồ sơ cấu hình" cài được (bật màn hình Install Profile)
// khi Content-Type đúng application/x-x509-ca-cert — mặc định Vite trả octet-stream cho .pem nên iOS
// chỉ tải file về như file thường. Middleware tạm này CHỈ phục vụ đúng 1 file, gỡ sau khi cài xong CA.
function mkcertRootCaMiddleware() {
  return {
    name: "mkcert-root-ca-content-type",
    configureServer(server: import("vite").ViteDevServer) {
      server.middlewares.use((req, res, next) => {
        if (req.url === "/mkcert-rootCA.pem") {
          res.setHeader("Content-Type", "application/x-x509-ca-cert");
        }
        next();
      });
    }
  };
}

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    mkcertRootCaMiddleware(),
    VitePWA({
      registerType: "autoUpdate",
      // Phase 1 (app-like): chỉ precache build assets đủ để Chrome công nhận
      // "installable". Runtime caching cho API, offline fallback, push
      // notification là phạm vi Phase 2 — chưa cấu hình ở đây.
      workbox: {
        globPatterns: ["**/*.{js,css,html,svg,png,ico,woff2}"]
      },
      manifest: {
        name: "PPS Education — Cổng thông tin Học viên & Phụ huynh",
        short_name: "PPS Portal",
        description: "Cổng thông tin dành cho Học viên & Phụ huynh PPS English",
        lang: "vi",
        start_url: "/",
        scope: "/",
        display: "standalone",
        theme_color: "#17a6a0",
        background_color: "#eaf6f6",
        icons: [
          { src: "/pwa-192.png", sizes: "192x192", type: "image/png" },
          { src: "/pwa-512.png", sizes: "512x512", type: "image/png" },
          {
            src: "/pwa-maskable-512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable"
          }
        ]
      }
    })
  ],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src")
    }
  },
  server: {
    port: 3001,
    host: true,
    https: httpsConfig,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    }
  }
});
