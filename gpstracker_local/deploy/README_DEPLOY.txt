РАЗВЁРТЫВАНИЕ НА VPS (500+ УСТРОЙСТВ)
=====================================

1. КУПИТЕ VPS: Ubuntu 22.04, 2 vCPU, 4 ГБ RAM, 40 ГБ SSD.
   Этого хватит с запасом (наша нагрузка ~50 точек/сек).

2. ДОМЕН: купите дешёвый (например gps.вашдомен.ru) и создайте
   A-запись на IP сервера.

3. НА СЕРВЕРЕ (под root):
   apt update && apt install -y python3-pip caddy
   pip3 install flask flask-socketio gunicorn eventlet
   mkdir -p /opt/gpstracker
   # загрузите сюда: server.py, dashboard.html, gps.service, Caddyfile
   sed -i 's/gps.example.com/gps.вашдомен.ru/' /etc/caddy/Caddyfile  (или правим вручную)
   cp Caddyfile /etc/caddy/Caddyfile && systemctl reload caddy
   cp gps.service /etc/systemd/system/ && systemctl enable --now gps
   Карта станет доступна: https://gps.вашдомен.ru

4. СВОЙ VPN (вместо покупного!):
   chmod +x wg-setup.sh && ./wg-setup.sh
   Скрипт выдаст 4 значения — впишите их в MyVpnService.kt

5. ПРИЛОЖЕНИЕ: в LocationService.kt и MyVpnService.kt замените
   SERVER_URL: https://punctured-detail-expansive.ngrok-free.dev
                -> https://gps.вашдомен.ru
   Пересоберите APK (Actions -> Build APK), раздайте телефонам.
   ngrok и ноутбук больше не нужны — сервер работает 24/7.

6. НАГРУЗКА 500 УСТРОЙСТВ: текущий стек справится.
   Если дойдёте до 2000+ — переезд на PostgreSQL (один вечер).
