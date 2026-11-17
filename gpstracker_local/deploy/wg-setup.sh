#!/bin/bash
# Быстрая установка WireGuard на этот же VPS (свой VPN, без абонентки)
set -e
apt update && apt install -y wireguard ufw
cd /etc/wireguard

# ключи сервера
umask 077
wg genkey | tee server_private.key | wg pubkey > server_public.key
# ключи устройства (для MyVpnService.kt)
wg genkey | tee phone_private.key | wg pubkey > phone_public.key

cat > wg0.conf <<EOF
[Interface]
PrivateKey = $(cat server_private.key)
Address = 10.8.0.1/24
ListenPort = 51820

[Peer]
PublicKey = $(cat phone_public.key)
AllowedIPs = 10.8.0.2/32
EOF

sysctl -w net.ipv4.ip_forward=1
echo "net.ipv4.ip_forward=1" >> /etc/sysctl.conf
ufw allow 51820/udp
ufw allow 80/tcp
ufw allow 443/tcp
systemctl enable --now wg-quick@wg0

echo "============================================"
echo "ГОТОВО. Впишите в MyVpnService.kt:"
echo "WG_PRIVATE_KEY = $(cat phone_private.key)"
echo "WG_ADDRESS     = 10.8.0.2/32"
echo "WG_PUBLIC_KEY  = $(cat server_public.key)"
echo "WG_ENDPOINT    = IP_ЭТОГО_СЕРВЕРА:51820"
echo "============================================"
