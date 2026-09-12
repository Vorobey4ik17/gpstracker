"""GPS Tracker server: принимает точки, хранит в SQLite, раздаёт live-карту.

Запуск:  pip install flask flask-socketio   (один раз)
         python server.py
Карта:   http://localhost:5000  (с телефона: http://IP-ноутбука:5000)
"""

import sqlite3
import time
from pathlib import Path

from flask import Flask, jsonify, request, send_file
from flask_socketio import SocketIO

DB = Path(__file__).parent / "gps.db"
app = Flask(__name__)
socketio = SocketIO(app, cors_allowed_origins="*")

CENTER = (55.7558, 37.6173)  # стартовый центр карты, поменяйте на свой город


def get_db():
    con = sqlite3.connect(DB)
    con.row_factory = sqlite3.Row
    return con


def init_db():
    con = get_db()
    con.execute(
        """create table if not exists locations(
               id integer primary key autoincrement,
               device_id text not null,
               lat real not null,
               lon real not null,
               accuracy real,
               speed real,
               created_at real not null)"""
    )
    con.execute(
        "create index if not exists idx_dev_time on locations(device_id, created_at)"
    )
    con.commit()
    con.close()


@app.route("/")
def index():
    return send_file(Path(__file__).parent / "dashboard.html")


@app.route("/points", methods=["POST"])
def points():
    """Приём точек: одна или массив. Отправляет их всем открытым картам."""
    rows = request.get_json(force=True)
    if isinstance(rows, dict):
        rows = [rows]
    con = get_db()
    now = time.time()
    for r in rows:
        row = {
            "device_id": str(r.get("device_id", "unknown")),
            "lat": float(r["lat"]),
            "lon": float(r["lon"]),
            "accuracy": r.get("accuracy"),
            "speed": r.get("speed"),
            "created_at": now,
        }
        con.execute(
            "insert into locations(device_id,lat,lon,accuracy,speed,created_at)"
            " values(?,?,?,?,?,?)",
            (row["device_id"], row["lat"], row["lon"],
             row["accuracy"], row["speed"], row["created_at"]),
        )
        socketio.emit("point", row)  # live на всех картах
    con.commit()
    con.close()
    return jsonify(ok=True, received=len(rows))


@app.route("/history")
def history():
    dev = request.args.get("device", "device-001")
    con = get_db()
    rows = con.execute(
        "select * from locations where device_id=? order by created_at asc limit 10000",
        (dev,),
    ).fetchall()
    con.close()
    return jsonify([dict(r) for r in rows])


if __name__ == "__main__":
    init_db()
    print("=" * 50)
    print("Карта диспетчера:  http://localhost:5000")
    print("Телефон шлёт на:   http://<IP-этого-ноутбука>:5000/points")
    print("=" * 50)
    socketio.run(app, host="0.0.0.0", port=5000, allow_unsafe_werkzeug=True)
