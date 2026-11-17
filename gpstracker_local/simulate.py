"""Генератор фейковых точек для проверки карты без телефона.

python simulate.py  (сервер должен быть запущен)
"""

import math
import time

import requests

SERVER = "https://punctured-detail-expansive.ngrok-free.dev"  # при проверке через интернет вставьте адрес туннеля
DEVICE_ID = "device-001"
LAT0, LON0 = 55.7558, 37.6173  # поменяйте на свой город

t = 0.0
while True:
    lat = LAT0 + 0.0012 * math.sin(t / 25.0)
    lon = LON0 + 0.0012 * math.cos(t / 25.0)
    try:
        r = requests.post(
            f"{SERVER}/points",
            json={
                "device_id": DEVICE_ID,
                "lat": lat,
                "lon": lon,
                "accuracy": 5.0,
                "speed": 11.5 + 3.0 * math.sin(t / 10.0),
            },
            timeout=10,
        )
        print(f"t={t:6.1f}s  HTTP {r.status_code}")
    except Exception as e:
        print("ошибка:", e)
    t += 3
    time.sleep(3)
