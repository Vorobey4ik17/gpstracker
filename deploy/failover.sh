#!/bin/bash
# /opt/gpstracker/failover.sh — крутить на РЕЗЕРВНОМ сервере через cron каждую минуту
# Если главный не отвечает 3 проверки подряд — переносим плавающий IP на себя.

MAIN_IP="IP_ГЛАВНОГО_СЕРВЕРА"
FLOATING_IP="ПЛАВАЮЩИЙ_IP"            # Hetzner Floating IP
HCLOUD_TOKEN="ТОКЕН_HCLOUD"           # console.hetzner.cloud -> Security -> API Tokens
STATE_FILE=/tmp/failover_state
FAILS=0

for i in 1 2 3; do
  if curl -sf --max-time 5 "https://${MAIN_IP}:5000/history?device=ping" > /dev/null 2>&1; then
    FAILS=0
  else
    FAILS=$((FAILS+1))
  fi
  sleep 3
done

if [ "$FAILS" -ge 3 ]; then
  if [ "$(cat $STATE_FILE 2>/dev/null)" != "active" ]; then
    echo "Главный недоступен. Переношу плавающий IP на себя..."
    curl -s -X POST -H "Authorization: Bearer $HCLOUD_TOKEN" \
      "https://api.hetzner.cloud/v1/floating_ips/${FLOATING_IP}/actions/assign" \
      -H "Content-Type: application/json" \
      -d "{"server": $(curl -s -H "Authorization: Bearer $HCLOUD_TOKEN" https://api.hetzner.cloud/v1/servers | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)}" > /dev/null
    # litestream превращаем из "restore" в обычную базу
    systemctl stop litestream-restore 2>/dev/null
    echo "active" > $STATE_FILE
    echo "Я теперь ГЛАВНЫЙ. Карта: https://gps.вашдомен.ru"
  fi
else
  if [ "$(cat $STATE_FILE 2>/dev/null)" == "active" ]; then
    echo "Главный восстановлен. Возвращаю роль резерва (IP переносится вручную/скриптом)."
    echo "standby" > $STATE_FILE
  fi
fi
