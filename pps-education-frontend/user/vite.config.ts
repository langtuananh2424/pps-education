import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import path from "path";
import { defineConfig } from "vite";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
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
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    }
  }
});
