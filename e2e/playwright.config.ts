import { defineConfig, devices } from "@playwright/test";

/**
 * Plan link hoá thông báo (2026-09-22) — E2E verify bấm 1 thông báo ở quả chuông mở đúng đích.
 *
 * 2 project độc lập (app admin :3000 http, Portal user :3001 https tự ký qua mkcert — xem
 * pps-education-frontend/user/vite.config.ts) vì 2 SPA khác baseURL/origin, không so sánh được
 * chung 1 "project" browser thường. Backend (:8080) KHÔNG tự khởi động ở đây — phải chạy sẵn qua
 * `docker compose up -d backend` (xem README/CONTRIBUTING) trước khi chạy suite này; test tự kiểm
 * tra /actuator/health lúc setup và báo lỗi rõ ràng nếu chưa lên, không đoán mò.
 */
export default defineConfig({
  testDir: "./tests",
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false, // các test chia sẻ dữ liệu demo (lớp 8, ph.nguyen...) — chạy tuần tự tránh đụng nhau
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    trace: "retain-on-failure",
    screenshot: "only-on-failure"
  },
  projects: [
    {
      name: "admin",
      testMatch: /admin\.spec\.ts/,
      use: { ...devices["Desktop Chrome"], baseURL: "http://localhost:3000" }
    },
    {
      name: "user",
      testMatch: /user\.spec\.ts/,
      use: { ...devices["Desktop Chrome"], baseURL: "https://localhost:3001", ignoreHTTPSErrors: true }
    }
  ],
  webServer: [
    {
      command: "npm run dev",
      cwd: "../pps-education-frontend/admin",
      url: "http://localhost:3000",
      reuseExistingServer: true,
      timeout: 60_000
    },
    {
      command: "npm run dev",
      cwd: "../pps-education-frontend/user",
      url: "https://localhost:3001",
      ignoreHTTPSErrors: true,
      reuseExistingServer: true,
      timeout: 60_000
    }
  ]
});
