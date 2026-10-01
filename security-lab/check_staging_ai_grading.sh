#!/bin/bash
# Chạy TRÊN server staging (SSH vào trước) để xác định chính xác vì sao
# WritingAiGradingService.grade() trả null hàng loạt lúc ~13:45-13:46 giờ VN
# hôm nay (2026-09-23) -- xem trao đổi trong security-lab về load test Writing.

echo "=== 1. .env có WRITING_AI_MODEL / NINE_ROUTER_BASE_URL không ==="
cd /opt/pps-education/staging 2>/dev/null || cd ~/pps-education/staging 2>/dev/null
grep -n "WRITING_AI_MODEL\|NINE_ROUTER_BASE_URL\|NINE_ROUTER_API_KEY" .env

echo ""
echo "=== 2. Log backend quanh khung giờ 13:45-13:47 (giờ VN) -- tìm đúng dòng cảnh báo ==="
sudo docker compose logs backend --since 2026-09-23T13:44:00+07:00 --until 2026-09-23T13:47:00+07:00 \
  | grep -iE "RubricByGradeTrackLoader|WritingAiGradingService|NineRouterAiClient"

echo ""
echo "=== 3. Backend đang chạy image build từ commit nào (xác nhận đã deploy PR #535 chưa) ==="
sudo docker compose images backend
sudo docker compose logs backend --since 2026-09-23T00:00:00+07:00 | grep -i "started\|commit\|version" | tail -20

echo ""
echo "=== 4. 9Router có đang chạy trên host và Dashboard có combo 'writing-pps' không ==="
curl -s http://localhost:20128/v1/models 2>&1 | head -50
echo "(nếu lệnh trên rỗng/lỗi connection refused -> 9Router KHÔNG chạy trên host này)"
