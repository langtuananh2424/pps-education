# Deploy lên server vật lý — runbook

Server vật lý riêng (không dùng Railway nữa), chạy **cả 2 stack staging +
production** cùng lúc, tách biệt hoàn toàn bằng Docker network/volume/
container riêng. Không có static IP → dùng Cloudflare Tunnel. File lưu trữ
media dùng MinIO tự host (thay Cloudflare R2 thật) nhưng giữ nguyên code
`MediaStorageService`/cấu trúc key hiện có. Có bổ sung 9Router (chấm AI) và
quy trình tắt máy an toàn từ xa (chưa có UPS).

Xem chi tiết plan gốc: hỏi lại trong phiên Claude Code đã tạo repo này nếu
cần đối chiếu — file README này là bản rút gọn để thao tác trực tiếp trên
server.

## 0. Hệ điều hành

**Ubuntu Server 26.04 LTS** (bản không GUI) — hỗ trợ chính thức tới ~2031,
tương thích tốt Docker Engine/`cloudflared`/Node.js (9Router). Vì là LTS còn
mới, nếu 1 repo apt bên thứ 3 (Docker/Cloudflare/NodeSource) chưa kịp build
riêng cho codename của 26.04, trỏ tạm repo đó sang codename LTS trước đó
(24.04 "noble") — không ảnh hưởng gì tới ứng dụng, chỉ là nguồn cài đặt.

## 1. Cài packages + user + firewall

```bash
apt update && apt upgrade -y
apt install -y ufw fail2ban curl gnupg git rsync unattended-upgrades nginx

# Docker Engine + Compose plugin
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  > /etc/apt/sources.list.d/docker.list
apt update
apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# User deploy (dùng cho CI SSH) - KHÁC key cá nhân quản trị từ máy nhà
adduser --disabled-password --gecos "" deploy
usermod -aG docker deploy
mkdir -p /home/deploy/.ssh
# dán public key CI vào /home/deploy/.ssh/authorized_keys, chmod 600, chown deploy:deploy

# Firewall: KHÔNG mở 80/443 (dùng Cloudflare Tunnel, xem mục 4) — SSH chỉ cho LAN
ufw default deny incoming
ufw default allow outgoing
ufw allow from <LAN_SUBNET, VD 192.168.1.0/24> to any port 22 proto tcp
ufw enable
```

## 2. Thư mục + secret trên server

```
/opt/pps-education/staging/{docker-compose.yml,.env,frontend/{admin,user}}
/opt/pps-education/production/{docker-compose.yml,.env,frontend/{admin,user}}
```

Copy `deploy/docker-compose.staging.yml` → `/opt/pps-education/staging/docker-compose.yml`
(và tương tự cho production), thay `<owner>/<repo>` và `<DOMAIN>` bằng giá
trị thật.

`.env` mỗi stack (tạo tay 1 lần, `chmod 600`, **không** đi qua GitHub/CI):

```
DB_PASSWORD=...
JWT_SECRET=...
GOOGLE_OAUTH_CLIENT_IDS=...
S3_ACCESS_KEY=...        # dùng chung cho MinIO root user + R2_ACCESS_KEY_ID trong compose
S3_SECRET_KEY=...        # dùng chung cho MinIO root password + R2_SECRET_ACCESS_KEY trong compose
MAIL_HOST=...
MAIL_PORT=587
MAIL_USERNAME=...
MAIL_PASSWORD=...
MAIL_CONTACT_HOTLINE=...
MAIL_CONTACT_SUPPORT_EMAIL=...
MAIL_CONTACT_WEBSITE=...
FIREBASE_CREDENTIALS_BASE64=...
OPENAI_API_KEY=...
ANTHROPIC_API_KEY=...
GEMINI_API_KEY=...
NINE_ROUTER_API_KEY=...
NINE_ROUTER_MODEL=...
```

```bash
docker compose up -d   # chạy ở cả 2 thư mục — Flyway tự chạy migration khi backend khởi động
```

## 3. MinIO (thay Cloudflare R2)

Sau lần `docker compose up -d` đầu, tạo bucket + bật public read (giữ đúng
hành vi bucket public của R2 hiện tại):

```bash
docker run --rm --network pps-staging_internal minio/mc \
  alias set s http://minio:9000 <S3_ACCESS_KEY> <S3_SECRET_KEY>
docker run --rm --network pps-staging_internal minio/mc mb s/pps-media
docker run --rm --network pps-staging_internal minio/mc anonymous set download s/pps-media
```

(Lặp lại với network `pps-production_internal` cho stack production.)

Nếu có dữ liệu cũ thật trên R2 cần giữ lại: dùng `rclone`/`mc mirror` chuyển
1 lần trước khi cắt hẳn sang MinIO (không tự động, làm tay khi cần).

## 4. Nginx + Cloudflare Tunnel

1. Copy `deploy/nginx/admin.conf.template`, `user.conf.template`,
   `files.conf.template` vào `/etc/nginx/sites-available/`, thay placeholder
   theo bảng:

   | File | `__HOSTNAME__` | `__ROOT_PATH__` | `__API_PORT__` / `__MINIO_PORT__` |
   |---|---|---|---|
   | admin-staging | `admin-staging.<DOMAIN>` | `/opt/pps-education/staging/frontend/admin` | 8081 |
   | admin (prod) | `admin.<DOMAIN>` | `/opt/pps-education/production/frontend/admin` | 8080 |
   | app-staging | `app-staging.<DOMAIN>` | `/opt/pps-education/staging/frontend/user` | 8081 |
   | app (prod) | `app.<DOMAIN>` | `/opt/pps-education/production/frontend/user` | 8080 |
   | files-staging | `files-staging.<DOMAIN>` | — | 9002 |
   | files (prod) | `files.<DOMAIN>` | — | 9000 |

2. `ln -s` từng file vào `sites-enabled/`, `nginx -t && systemctl reload nginx`.
3. Cài `cloudflared` (gói `.deb` chính thức Cloudflare), `cloudflared tunnel login`,
   `cloudflared tunnel create pps-education`.
4. Tạo `~/.cloudflared/config.yml`:
   ```yaml
   tunnel: pps-education
   credentials-file: /root/.cloudflared/<TUNNEL_ID>.json
   ingress:
     - hostname: admin.<DOMAIN>
       service: http://localhost:80
     - hostname: app.<DOMAIN>
       service: http://localhost:80
     - hostname: admin-staging.<DOMAIN>
       service: http://localhost:80
     - hostname: app-staging.<DOMAIN>
       service: http://localhost:80
     - hostname: files.<DOMAIN>
       service: http://localhost:80
     - hostname: files-staging.<DOMAIN>
       service: http://localhost:80
     - service: http_status:404
   ```
5. `cloudflared tunnel route dns pps-education <hostname>` cho từng hostname ở trên.
6. `cloudflared service install && systemctl enable --now cloudflared`.
7. Trên Cloudflare Dashboard: bật "Always Use HTTPS" + SSL/TLS mode "Full".

## 5. 9Router (chấm AI)

```bash
# Node.js LTS qua NodeSource (không dùng bản Ubuntu mặc định)
curl -fsSL https://deb.nodesource.com/setup_lts.x | bash -
apt install -y nodejs
npm install -g 9router
```

Tạo systemd unit `/etc/systemd/system/9router.service`:

```ini
[Unit]
Description=9Router
After=network.target

[Service]
ExecStart=/usr/bin/9router
Restart=on-failure
User=deploy

[Install]
WantedBy=multi-user.target
```

`systemctl enable --now 9router` — nghe mặc định `127.0.0.1:20128`, backend
gọi vào qua `host.docker.internal:20128` (đã map `extra_hosts: host-gateway`
trong docker-compose, xem mục 3).

Cấu hình Dashboard 9Router (Combo & Vision Adapter, Media Providers → STT)
qua SSH tunnel từ máy cá nhân, KHÔNG public hostname nào cho việc này:

```bash
ssh -L 20128:localhost:20128 deploy@<LAN_IP>
# rồi mở http://localhost:20128 trên trình duyệt máy nhà
```

Set `NINE_ROUTER_MODEL`/`NINE_ROUTER_STT_MODEL`/`NINE_ROUTER_AUDIO_MODEL`
trong `.env` mỗi stack theo combo đã tạo (xem `.env.example` gốc repo, mục
9Router, để biết ý nghĩa từng biến).

## 6. Quản trị từ xa trong LAN

1. Đặt IP LAN tĩnh cho server qua DHCP reservation trên router (theo MAC).
2. `sshd_config`: `PasswordAuthentication no` (chỉ key-auth); ufw đã giới
   hạn SSH chỉ nhận từ LAN subnet (mục 1).
3. (Tùy chọn) `apt install cockpit` — web UI xem CPU/RAM/disk, restart/
   shutdown service, chỉ bind LAN (cổng 9090).

## 7. Tắt server an toàn từ xa (chưa có UPS)

`/usr/local/bin/safe-shutdown.sh` (root sở hữu, `chmod 700`):

```bash
#!/usr/bin/env bash
set -euo pipefail
cd /opt/pps-education/staging && docker compose down
cd /opt/pps-education/production && docker compose down
systemctl stop cloudflared 9router
sync
shutdown -h now
```

`visudo`, thêm dòng (chỉ cho phép đúng script này, không phải toàn quyền root):

```
deploy ALL=(root) NOPASSWD: /usr/local/bin/safe-shutdown.sh
```

Tắt từ máy cá nhân trong LAN: `ssh deploy@<LAN_IP> sudo /usr/local/bin/safe-shutdown.sh`

**Bật lại (Wake-on-LAN)**: bật WOL trong BIOS/UEFI +
`sudo ethtool -s <iface> wol g` (thêm vào netplan để giữ qua reboot), rồi từ
máy khác trong LAN: `wakeonlan <MAC_ADDRESS>`.

Khi có UPS sau này: cài **NUT (Network UPS Tools)** để tự gọi
`safe-shutdown.sh` khi phát hiện mất điện — script đã sẵn sàng để tái dùng.

## 8. GitHub Secrets/Environments cần tạo

- Repo-level: `DEPLOY_HOST`, `DEPLOY_USER` (`deploy`), `DEPLOY_SSH_KEY`, `DEPLOY_SSH_PORT`.
- Environment `staging` và `production` (production có required reviewer):
  `VITE_GOOGLE_CLIENT_ID`, `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_AUTH_DOMAIN`,
  `VITE_FIREBASE_PROJECT_ID`, `VITE_FIREBASE_MESSAGING_SENDER_ID`,
  `VITE_FIREBASE_APP_ID`, `VITE_FIREBASE_VAPID_KEY` (giá trị đúng stack).

## 9. Rollback

- Image có cả tag bất biến (`backend:staging-<sha>`/`prod-<sha>`) và tag di
  động (`staging-latest`/`prod-latest`).
- Rollback: SSH vào server, sửa dòng `image:` trong `docker-compose.yml` của
  đúng stack sang tag `<sha>` cũ, `docker compose up -d --no-deps backend`.
- Frontend rollback: re-run job cũ trong tab GitHub Actions (rsync `--delete`
  ghi đè lại đúng bản build đó).
